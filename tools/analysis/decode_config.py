"""Decode the original client's `.o` files into readable documents.

`.o` is not one format. The client reads two kinds, chosen by the loader rather than by the
extension, and this tool routes each file the same way:

* `res/connect_b.o` and `res/start.o` are compact line files read by `base/utils/OFileReader`:
  a line count, then per line a byte length followed by values. A value whose first byte has both
  top bits set is a string (14 bit byte length); otherwise it is an integer whose first byte
  carries the width in bits 6 and 7 and the sign in bit 5.
* `res/mimg.o` and `res/rimg.o` are packed-image index tables read by `Utils.loadScript(name,
  false, 2)` and walked in `work/api/ImagePointer.NewCreatePng`: a 16 bit length then 7 byte
  records of image ID, pack index and offset.

Method references are reproducible with the CFR command in `docs/analysis/README.md`. The same
first-byte pattern means "string" in `OFileReader.read` and "five byte integer" in
`OFileReader.readInteger`; the context decides, so this decoder keeps the two readers apart
instead of guessing from the bytes.

Usage:

    python tools/analysis/decode_config.py dev/assets dev/legacy-config
"""

import json
import os
import struct
import sys

INDEX_TABLES = ("mimg.o", "rimg.o")


def _signed_byte(value):
    return value - 256 if value > 127 else value


def _int32(value):
    value &= 0xFFFFFFFF
    return value - 0x100000000 if value >= 0x80000000 else value


def _sizeof(value):
    if -32 <= value <= 31:
        return 1
    if -8192 <= value <= 8191:
        return 2
    if -536870912 <= value <= 0x1FFFFFFF:
        return 4
    return 5


class _Reader(object):
    def __init__(self, data):
        self.data = data
        self.position = 0

    def byte(self, what):
        if self.position >= len(self.data):
            raise ValueError("%s runs past the file at offset %d" % (what, self.position))
        value = self.data[self.position]
        value = value if isinstance(value, int) else ord(value)
        self.position += 1
        return value

    def take(self, count, what):
        if count < 0 or self.position + count > len(self.data):
            raise ValueError("%s needs %d bytes at offset %d" % (what, count, self.position))
        chunk = self.data[self.position : self.position + count]
        self.position += count
        return chunk

    def integer(self, what):
        """Reads one compact integer, returning (value, encoded size)."""
        first = self.byte(what)
        flag = first & 0xC0
        signed = _signed_byte(first)
        body = (signed & 0x3F) if (first & 0x20) == 0 else (signed | 0xC0)
        if flag == 0x00:
            return _signed_byte(body & 0xFF), 1
        if flag == 0x40:
            value = (body << 8) | self.byte(what)
            value &= 0xFFFF
            return value - 0x10000 if value >= 0x8000 else value, 2
        rounds = 3 if flag == 0x80 else 4
        value = body
        for _ in range(rounds):
            value = _int32((value << 8) | self.byte(what))
        return value, rounds + 1


def decode_lines(data):
    """Decodes a compact line file the way `OFileReader` reads it."""
    reader = _Reader(data)
    line_count, _ = reader.integer("line count")
    if line_count < 0:
        raise ValueError("Line count %d is negative" % line_count)
    lines = []
    for index in range(line_count):
        length, _ = reader.integer("length of line %d" % index)
        if length < 0:
            raise ValueError("Line %d declares %d bytes" % (index, length))
        end = reader.position + length
        if end > len(data):
            raise ValueError(
                "Line %d declares %d bytes but only %d remain"
                % (index, length, len(data) - reader.position)
            )
        values = []
        while reader.position < end:
            first = reader.data[reader.position]
            first = first if isinstance(first, int) else ord(first)
            if (first & 0xC0) == 0xC0:
                reader.position += 1
                text_length = ((first & 0x3F) << 8) | reader.byte("string length")
                if reader.position + text_length > end:
                    raise ValueError(
                        "String of %d bytes does not fit line %d" % (text_length, index)
                    )
                raw = reader.take(text_length, "string of line %d" % index)
                values.append(raw.decode("utf-8", "replace"))
            else:
                value, _ = reader.integer("value of line %d" % index)
                values.append(value)
        if reader.position != end:
            raise ValueError("Line %d overran its declared length" % index)
        lines.append(values)
    if reader.position != len(data):
        raise ValueError("%d bytes remain after the last line" % (len(data) - reader.position))
    return {"line_count": line_count, "lines": lines}


def decode_index(data):
    """Decodes a packed-image index table."""
    if len(data) < 2:
        raise ValueError("Index table is shorter than its length header")
    declared = struct.unpack_from(">h", data, 0)[0]
    if declared < 0 or 2 + declared > len(data):
        raise ValueError("Index table declares %d bytes but holds %d" % (declared, len(data) - 2))
    body = data[2 : 2 + declared]
    if len(body) % 7 != 0:
        raise ValueError("Index table body is not a whole number of 7 byte records")
    records = []
    for position in range(0, len(body), 7):
        image_id, pack, offset = struct.unpack_from(">IBh", body, position)
        records.append({"image_id": image_id, "pack": pack, "offset": offset})
    return {"declared_length": declared, "records": records}


def extract(files, out_dir):
    """Decodes every `.o` of an export with the reader its loader uses."""
    extract.failures = []
    written = []
    if not os.path.isdir(out_dir):
        os.makedirs(out_dir)
    for source in sorted(files):
        name = os.path.basename(source)
        if not name.lower().endswith(".o"):
            continue
        kind = "index" if name.lower() in INDEX_TABLES else "lines"
        try:
            document = (
                decode_index(files[source]) if kind == "index" else decode_lines(files[source])
            )
        except (ValueError, struct.error) as failure:
            extract.failures.append({"source": source, "kind": kind, "error": str(failure)})
            continue
        document["source"] = source
        document["kind"] = kind
        relative = name[:-2] + "." + kind + ".json"
        with open(os.path.join(out_dir, relative), "wb") as handle:
            handle.write(
                (json.dumps(document, indent=1, sort_keys=True, ensure_ascii=False) + "\n").encode(
                    "utf-8"
                )
            )
        written.append({"source": source, "kind": kind, "path": relative})
    return written


extract.failures = []


def main(original_root, out_dir):
    with open(os.path.join(original_root, "manifest.json"), "rb") as handle:
        manifest = json.loads(handle.read().decode("utf-8"))
    files = {}
    for member in manifest["members"]:
        if member["source_path"].lower().endswith(".o"):
            with open(os.path.join(original_root, member["exported_path"]), "rb") as handle:
                files[member["source_path"]] = handle.read()
    written = extract(files, out_dir)
    report = {
        "source": manifest.get("source"),
        "source_sha256": manifest.get("source_sha256"),
        "files": written,
        "failures": extract.failures,
    }
    with open(os.path.join(out_dir, "config.json"), "wb") as handle:
        handle.write((json.dumps(report, indent=2, sort_keys=True) + "\n").encode("utf-8"))
    print("decoded %d .o files into %s" % (len(written), out_dir))
    print("failed files: %d" % len(extract.failures))
    for failure in extract.failures[:10]:
        print("  %s" % failure)
    return 0 if not extract.failures else 1


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: decode_config.py ORIGINAL_EXPORT_DIR OUTPUT_DIR")
    sys.exit(main(sys.argv[1], sys.argv[2]))
