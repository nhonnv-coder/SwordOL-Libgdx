"""Decode the original client's UI layouts (`res/ui/u<ID>.ui`) into readable documents.

Evidence: `work/ui/CtrlManager.loadData`, `loadMainScreen`, `loadScreen`, `newScreen`,
`work/ui/CustomScreen.newCtrl`, `work/ui/ScreenBase.newCtrl` and the `newCtrl` of `Button`,
`StringList`, `Edit`, `TextEx` and `Grid` in the decompiled client. Method references are
reproducible with the CFR command in `docs/analysis/README.md`.

File layout:

    main screen: int id, byte class flag, common block,
                 byte focused control, byte left soft key, byte right soft key
    byte control count
    per control: int id, byte class flag, common block, then the fields of that class

Common block, from `ScreenBase.newCtrl` (23 bytes):

    4 signed bytes: left, right, up, down neighbour, -1 meaning none
    4 signed shorts: x, y, width, height
    int text colour, int position index, short mode, byte optimisation flags

Classes, from `CtrlManager.newScreen`: 2 button, 4 list, 7 edit, 11 text, 12 grid. Any other class
makes the original reader return without consuming its bytes, so the rest of the file would be
read at the wrong offset: this decoder refuses such a file rather than guessing.

Strings are Java modified UTF-8 as `DataInputStream.readUTF` writes them. Fields the client reads
and then ignores are kept under their stored names; nothing is renamed to a meaning the client
does not establish.

Usage:

    python tools/analysis/decode_ui.py dev/assets dev/legacy-ui
"""

import json
import os
import struct
import sys

COMMON = 23
CLASSES = {2: "button", 4: "list", 7: "edit", 11: "text", 12: "grid"}


class _Reader(object):
    def __init__(self, data):
        self.data = data
        self.position = 0

    def take(self, count, what):
        if self.position + count > len(self.data):
            raise ValueError(
                "%s needs %d bytes at offset %d but the file holds %d"
                % (what, count, self.position, len(self.data))
            )
        chunk = self.data[self.position : self.position + count]
        self.position += count
        return chunk

    def byte(self, what):
        return struct.unpack(">b", self.take(1, what))[0]

    def unsigned(self, what):
        return struct.unpack(">B", self.take(1, what))[0]

    def short(self, what):
        return struct.unpack(">h", self.take(2, what))[0]

    def integer(self, what):
        return struct.unpack(">i", self.take(4, what))[0]

    def text(self, what):
        length = struct.unpack(">H", self.take(2, what))[0]
        raw = self.take(length, what)
        try:
            return raw.decode("utf-8")
        except UnicodeDecodeError as failure:
            raise ValueError("%s is not valid UTF-8: %s" % (what, failure))

    def done(self):
        return self.position == len(self.data)


def _common(reader, what):
    neighbours = struct.unpack(">bbbb", reader.take(4, what + " neighbours"))
    x, y, width, height = struct.unpack(">hhhh", reader.take(8, what + " bounds"))
    color = reader.integer(what + " colour")
    position_index = reader.integer(what + " position index")
    mode = reader.short(what + " mode")
    flags = reader.byte(what + " flags")
    return {
        "left_id": None if neighbours[0] == -1 else neighbours[0],
        "right_id": None if neighbours[1] == -1 else neighbours[1],
        "up_id": None if neighbours[2] == -1 else neighbours[2],
        "down_id": None if neighbours[3] == -1 else neighbours[3],
        "x": x,
        "y": y,
        "width": width,
        "height": height,
        "text_color": color & 0xFFFFFF,
        "position_index": position_index,
        "mode": mode & 0xFFFF,
        "flags": flags & 0xFF,
    }


def _control(reader, warnings):
    control_id = reader.integer("control id")
    classflag = reader.unsigned("control class")
    if classflag not in CLASSES:
        raise ValueError(
            "Unsupported control class %d for control %d; the original reader consumes no bytes"
            " for it, so the rest of the file cannot be read" % (classflag, control_id)
        )
    kind = CLASSES[classflag]
    control = {"id": control_id, "type": kind, "class_flag": classflag}
    control.update(_common(reader, kind))
    if kind == "button":
        control["text"] = reader.text("button text")
        control["image_id"] = reader.integer("button image")
        control["image_index"] = reader.unsigned("button image index")
        control["unused_byte"] = reader.unsigned("button unused byte")
        control["background_flip_flags"] = reader.unsigned("button background flip")
    elif kind == "list":
        control["attribute"] = reader.unsigned("list attribute")
        control["text"] = reader.text("list text")
        count = reader.unsigned("list size count")
        control["item_sizes"] = [reader.unsigned("list size") for _ in range(count)]
        control["unused_int"] = reader.integer("list unused int")
        if count < 2:
            warnings.append(
                "list %d declares %d item sizes; the client reads two" % (control_id, count)
            )
    elif kind == "edit":
        control["char_limit"] = reader.unsigned("edit char limit")
        control["text"] = reader.text("edit text")
        control["input_flags"] = reader.unsigned("edit input flags")
    elif kind == "text":
        control["text"] = reader.text("text content")
    else:
        rows = reader.unsigned("grid row count")
        control["rows"] = [reader.unsigned("grid row") for _ in range(rows)]
        columns = reader.unsigned("grid column count")
        control["columns"] = [reader.unsigned("grid column") for _ in range(columns)]
        data = reader.unsigned("grid data count")
        control["data_ids"] = [reader.byte("grid data id") for _ in range(data)]
    return control


def decode(data, window_id):
    """Decodes one `.ui` file into a layout document."""
    reader = _Reader(data)
    warnings = []
    screen_id = reader.integer("screen id")
    screen_class = reader.unsigned("screen class")
    layout = {
        "window_id": window_id,
        "screen_id": screen_id,
        "screen_class": screen_class,
        "controls": [],
        "warnings": warnings,
    }
    layout.update(_common(reader, "screen"))
    layout["focus_id"] = reader.byte("focused control")
    layout["soft_left_id"] = reader.byte("left soft key")
    layout["soft_right_id"] = reader.byte("right soft key")
    count = reader.byte("control count")
    if count < 0:
        raise ValueError("Control count %d is negative" % count)
    for _ in range(count):
        layout["controls"].append(_control(reader, warnings))
    if not reader.done():
        raise ValueError("%d bytes remain after the last control" % (len(data) - reader.position))
    return layout


def extract(files, out_dir):
    """Decodes every `u<ID>.ui` of an export, collecting failures as it goes."""
    extract.failures = []
    written = []
    if not os.path.isdir(out_dir):
        os.makedirs(out_dir)
    for source in sorted(files):
        name = os.path.basename(source)
        if not name.lower().startswith("u") or not name.lower().endswith(".ui"):
            continue
        try:
            window_id = int(name[1:-3])
        except ValueError:
            extract.failures.append({"source": source, "error": "unexpected layout file name"})
            continue
        try:
            layout = decode(files[source], window_id)
        except (ValueError, struct.error) as failure:
            extract.failures.append({"source": source, "error": str(failure)})
            continue
        relative = "%d.ui.json" % window_id
        with open(os.path.join(out_dir, relative), "wb") as handle:
            handle.write(
                (json.dumps(layout, indent=1, sort_keys=True, ensure_ascii=False) + "\n").encode(
                    "utf-8"
                )
            )
        kinds = {}
        for control in layout["controls"]:
            kinds[control["type"]] = kinds.get(control["type"], 0) + 1
        written.append(
            {
                "window_id": window_id,
                "path": relative,
                "source": source,
                "screen_id": layout["screen_id"],
                "controls": len(layout["controls"]),
                "kinds": kinds,
                "warnings": layout["warnings"],
            }
        )
    return written


extract.failures = []


def main(original_root, out_dir):
    with open(os.path.join(original_root, "manifest.json"), "rb") as handle:
        manifest = json.loads(handle.read().decode("utf-8"))
    files = {}
    for member in manifest["members"]:
        if member["source_path"].lower().endswith(".ui"):
            with open(os.path.join(original_root, member["exported_path"]), "rb") as handle:
                files[member["source_path"]] = handle.read()
    written = extract(files, out_dir)
    totals = {}
    for entry in written:
        for kind, count in entry["kinds"].items():
            totals[kind] = totals.get(kind, 0) + count
    report = {
        "source": manifest.get("source"),
        "source_sha256": manifest.get("source_sha256"),
        "screens": written,
        "control_totals": totals,
        "failures": extract.failures,
    }
    with open(os.path.join(out_dir, "layouts.json"), "wb") as handle:
        handle.write((json.dumps(report, indent=2, sort_keys=True) + "\n").encode("utf-8"))
    print("decoded %d layouts into %s" % (len(written), out_dir))
    print("controls by kind: %s" % totals)
    print("failed files: %d" % len(extract.failures))
    for failure in extract.failures[:10]:
        print("  %s" % failure)
    return 0 if not extract.failures else 1


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: decode_ui.py ORIGINAL_EXPORT_DIR OUTPUT_DIR")
    sys.exit(main(sys.argv[1], sys.argv[2]))
