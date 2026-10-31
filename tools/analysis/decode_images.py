"""Rebuild the original client's packed indexed images (`.p`) into ordinary PNG files.

Evidence: `work/api/ImagePointer.NewCreatePng`, `NewCreatePngEx`, `createIHDRChunkEx`,
`createPLTEChunkEx`, `createTRNSChunkEx`, `createIDATChunkEx` and `getPngLenght` in the
decompiled client, plus `base/utils/Utils.loadScriptEx` for the index tables. Method references
are reproducible with the CFR command in `docs/analysis/README.md`.

A packed record stores a PNG with its IHDR, PLTE, tRNS and IDAT CRCs precomputed and its palette
kept as RGB565. The client rebuilds the PNG in memory; this tool performs the same reconstruction
offline and writes the result to disk. Because the stored CRCs were produced by whoever packed the
images, recomputing them over our reconstruction is an independent check: a record whose CRCs all
match is byte-identical to what the client feeds to its image decoder.

The palette expansion deliberately reproduces the client's arithmetic, including its signed shift
for red values of 16 and above. Producing "nicer" colours here would mean shipping images the
original never displayed, and every stored PLTE CRC would stop matching.

Usage:

    python tools/analysis/decode_images.py dev/assets dev/legacy-images
"""

import json
import os
import struct
import sys
import zlib

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
PNG_END = b"\x00\x00\x00\x00IEND\xae\x42\x60\x82"
RECORD_HEAD = 22
MAX_DECLARED = 65535


def _java_divide(value, divisor):
    """Integer division truncating toward zero, as Java does."""
    if value < 0:
        return -((-value) // divisor)
    return value // divisor


def expand_palette(palette):
    """Expands stored RGB565 colours into PLTE bytes exactly as the client does."""
    if len(palette) % 2 != 0:
        raise ValueError("Palette length must be a whole number of 16 bit colours")
    out = bytearray()
    for index in range(0, len(palette), 2):
        value = struct.unpack_from(">h", palette, index)[0]
        red = _java_divide((value >> 11) * 255, 31) & 0xFF
        green = _java_divide(((value >> 5) & 0x3F) * 255, 63) & 0xFF
        blue = _java_divide((value & 0x1F) * 255, 31) & 0xFF
        out += bytes((red, green, blue))
    return bytes(out)


def parse_index(data):
    """Parses `mimg.o` or `rimg.o`: a 16 bit length header then 7 byte records."""
    if len(data) < 2:
        raise ValueError("Index table is shorter than its length header")
    declared = struct.unpack_from(">h", data, 0)[0]
    if declared < 0 or 2 + declared > len(data):
        raise ValueError("Index table declares %d bytes but holds %d" % (declared, len(data) - 2))
    body = data[2 : 2 + declared]
    if len(body) % 7 != 0:
        raise ValueError("Index table body is not a whole number of 7 byte records")
    table = []
    for position in range(0, len(body), 7):
        # The offset is unsigned: a pack over 32KB pushes later records past the signed short range.
        image_id, pack_index, offset = struct.unpack_from(">IBH", body, position)
        table.append((image_id, pack_index, offset))
    return table


class DecodedImage(object):
    """One rebuilt PNG with the checks that were run on it."""

    def __init__(self, png, width, height, bit_depth, colors, trns_index, crc_mismatch):
        self.png = png
        self.width = width
        self.height = height
        self.bit_depth = bit_depth
        self.colors = colors
        self.trns_index = trns_index
        self.crc_mismatch = crc_mismatch
        self.crc_ok = not crc_mismatch


def _chunk(kind, data, stored_crc, mismatches):
    computed = zlib.crc32(kind + data) & 0xFFFFFFFF
    if computed != stored_crc:
        mismatches.append(kind.decode("ascii"))
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", stored_crc)


def decode_record(pack, offset):
    """Rebuilds the PNG stored at `offset` of a `.p` pack."""
    if offset < 0 or offset + RECORD_HEAD > len(pack):
        raise ValueError("Record head at offset %d runs past the pack" % offset)
    head = pack[offset : offset + RECORD_HEAD]
    width, height, bit_depth, trns_index = struct.unpack_from(">HHBb", head, 0)
    plte_crc, trns_crc, ihdr_crc = struct.unpack_from(">III", head, 6)
    palette_length, total_length = struct.unpack_from(">HH", head, 18)
    if width == 0 or height == 0:
        raise ValueError("Record declares an empty image")
    if bit_depth not in (1, 2, 4, 8):
        raise ValueError("Unsupported indexed bit depth %d" % bit_depth)
    body_start = offset + RECORD_HEAD
    if body_start + total_length > len(pack):
        raise ValueError(
            "Record declares %d data bytes but only %d remain"
            % (total_length, len(pack) - body_start)
        )
    data = pack[body_start : body_start + total_length]
    if palette_length > total_length or palette_length == 0:
        raise ValueError("Record palette length %d does not fit its data" % palette_length)
    idat_length = total_length - palette_length
    if idat_length <= 4:
        raise ValueError("Record holds no compressed image data")
    colors = palette_length // 2
    if trns_index != -1 and not 0 <= trns_index < colors:
        raise ValueError("Transparent index %d is outside the palette" % trns_index)
    mismatches = []
    ihdr = struct.pack(">II", width, height) + bytes((bit_depth, 3, 0, 0, 0))
    png = PNG_SIGNATURE + _chunk(b"IHDR", ihdr, ihdr_crc, mismatches)
    png += _chunk(b"PLTE", expand_palette(data[:palette_length]), plte_crc, mismatches)
    if trns_index != -1:
        alpha = bytes(0 if index == trns_index else 255 for index in range(colors))
        png += _chunk(b"tRNS", alpha, trns_crc, mismatches)
    tail = data[len(data) - idat_length :]
    png += _chunk(b"IDAT", tail[:-4], struct.unpack_from(">I", tail, len(tail) - 4)[0], mismatches)
    png += PNG_END
    return DecodedImage(png, width, height, bit_depth, colors, trns_index, mismatches)


def iter_chunks(png):
    """Yields (kind, data) for every chunk of a PNG, validating the declared lengths."""
    if not png.startswith(PNG_SIGNATURE):
        raise ValueError("Not a PNG")
    position = len(PNG_SIGNATURE)
    while position < len(png):
        if position + 12 > len(png):
            raise ValueError("Truncated chunk header")
        length = struct.unpack_from(">I", png, position)[0]
        kind = png[position + 4 : position + 8]
        end = position + 8 + length
        if length > len(png) or end + 4 > len(png):
            raise ValueError("Chunk %s runs past the file" % kind)
        yield kind, png[position + 8 : end]
        position = end + 4


def verify_png(png, check_crc=True):
    """Checks the structure, and with `check_crc` the chunk CRCs, of a rebuilt PNG."""
    chunks = {}
    for kind, data in iter_chunks(png):
        chunks.setdefault(kind, data)
    if check_crc:
        for _, kind, data, stored in _chunk_positions(png):
            if (zlib.crc32(kind + data) & 0xFFFFFFFF) != stored:
                raise ValueError("Chunk %s has a wrong CRC" % kind.decode("ascii"))
    if b"IHDR" not in chunks or b"IDAT" not in chunks:
        raise ValueError("PNG is missing IHDR or IDAT")
    width, height, bit_depth, color_type = struct.unpack_from(">IIBB", chunks[b"IHDR"], 0)
    if color_type != 3:
        raise ValueError("Expected an indexed PNG")
    raw = zlib.decompress(chunks[b"IDAT"])
    stride = 1 + (width * bit_depth + 7) // 8
    if len(raw) != stride * height:
        raise ValueError(
            "Decompressed %d bytes, expected %d for %dx%d at %d bpp"
            % (len(raw), stride * height, width, height, bit_depth)
        )
    return width, height


def _chunk_positions(png):
    position = len(PNG_SIGNATURE)
    while position < len(png):
        length = struct.unpack_from(">I", png, position)[0]
        kind = png[position + 4 : position + 8]
        data = png[position + 8 : position + 8 + length]
        stored = struct.unpack_from(">I", png, position + 8 + length)[0]
        yield length, kind, data, stored
        position += 12 + length


def extract(table, packs, prefix, out_dir):
    """Decodes every record of an index table into `out_dir`, collecting failures as it goes."""
    extract.failures = []
    written = []
    if not os.path.isdir(out_dir):
        os.makedirs(out_dir)
    for image_id, pack_index, offset in table:
        name = "%s%d.p" % (prefix, pack_index)
        pack = packs.get(name)
        if pack is None:
            extract.failures.append(
                {"image_id": image_id, "pack": name, "error": "missing pack " + name}
            )
            continue
        try:
            image = decode_record(pack, offset)
            # The stored CRCs are reported, not enforced: the original ships at least one record
            # whose tRNS CRC is wrong, and the client passes those bytes through unchecked.
            verify_png(image.png, check_crc=False)
        except (ValueError, zlib.error) as failure:
            extract.failures.append({"image_id": image_id, "pack": name, "error": str(failure)})
            continue
        relative = "%d.png" % image_id
        with open(os.path.join(out_dir, relative), "wb") as handle:
            handle.write(image.png)
        written.append(
            {
                "image_id": image_id,
                "path": relative,
                "pack": name,
                "pack_offset": offset,
                "width": image.width,
                "height": image.height,
                "bit_depth": image.bit_depth,
                "colors": image.colors,
                "transparent_index": image.trns_index,
                "crc_ok": image.crc_ok,
                "crc_mismatch": image.crc_mismatch,
                "sha256": _sha256(image.png),
                "size": len(image.png),
            }
        )
    return written


extract.failures = []


def _sha256(data):
    import hashlib

    return hashlib.sha256(data).hexdigest()


def _read_export(original_root):
    """Loads the exporter manifest and returns {original path: bytes}."""
    manifest_path = os.path.join(original_root, "manifest.json")
    with open(manifest_path, "rb") as handle:
        manifest = json.loads(handle.read().decode("utf-8"))
    files = {}
    for member in manifest["members"]:
        path = member["source_path"]
        if path.lower().endswith((".p", ".o")):
            with open(os.path.join(original_root, member["exported_path"]), "rb") as handle:
                files[path] = handle.read()
    return manifest, files


def main(original_root, out_dir):
    manifest, files = _read_export(original_root)
    report = {
        "source": manifest.get("source"),
        "source_sha256": manifest.get("source_sha256"),
        "tables": {},
        "images": [],
        "failures": [],
    }
    for table_name, prefix in (("res/mimg.o", "mp"), ("res/rimg.o", "rp")):
        if table_name not in files:
            report["failures"].append({"error": "missing index table " + table_name})
            continue
        table = parse_index(files[table_name])
        packs = {
            os.path.basename(path): data
            for path, data in files.items()
            if os.path.basename(path).startswith(prefix) and path.endswith(".p")
        }
        written = extract(table, packs, prefix, out_dir)
        report["tables"][table_name] = {
            "records": len(table),
            "packs": len(packs),
            "decoded": len(written),
            "failed": len(extract.failures),
        }
        report["images"] += written
        report["failures"] += extract.failures
    report["images"].sort(key=lambda entry: entry["image_id"])
    with open(os.path.join(out_dir, "images.json"), "wb") as handle:
        handle.write((json.dumps(report, indent=2, sort_keys=True) + "\n").encode("utf-8"))
    mismatched = [entry for entry in report["images"] if not entry["crc_ok"]]
    print("decoded %d images into %s" % (len(report["images"]), out_dir))
    print("stored CRC mismatches: %d" % len(mismatched))
    print("failed records: %d" % len(report["failures"]))
    for failure in report["failures"][:10]:
        print("  %s" % failure)
    return 0 if not report["failures"] and not mismatched else 1


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: decode_images.py ORIGINAL_EXPORT_DIR OUTPUT_DIR")
    sys.exit(main(sys.argv[1], sys.argv[2]))
