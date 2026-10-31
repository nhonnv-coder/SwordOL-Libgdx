"""Decode the original client's sprite files (`res/role/d<ID>.dat`) into readable documents.

Evidence: `base/draw/ISpriteEx.readSpriteEx` (the little-endian 16 bit length header),
`getImgAmount`, `getModulesAmount`, `getFramesAmount`, `getActionsAmount`, `getModulesPos`,
`getFramesPos`, `getActionsPos`, `loadActionIndex` and the drawing loop in `drawFrame`, all in the
decompiled client. Method references are reproducible with the CFR command in
`docs/analysis/README.md`.

Layout of the payload that follows the length header:

    0      image count (unsigned byte)
    1..2   module count, little endian
    3..4   frame count, little endian
    5      action count (unsigned byte)
    6..7   length of the frame section, little endian
    8..    image IDs, 4 bytes each, big endian
    then   modules, 5 bytes each: image index, x, y, width, height (all unsigned)
    then   frames: a count byte, then that many placements
           placement: little-endian 16 bit word (module index in bits 0..11, transform in bits
           12..15) then the offsets the client draws the module at, either two signed bytes
           (4 byte placement) or two little-endian signed shorts (6 byte placement)
    then   actions: a count byte, a 4 byte big-endian action ID, then that many 3 byte entries
           entry: big-endian 16 bit frame index, then one byte the shipped client never reads

Two placement layouts exist in the shipped files. 60 of the 61 sprites use 4 byte placements,
which is the only layout `ISpriteEx.drawFrame` reads. `d8060000.dat`, the mount sprite the other
port of this client names `AniChar_RiderDeDiem`, uses 6 byte placements with 16 bit offsets: only
that reading walks its frame section to exactly the length its own header declares, and only that
reading keeps every module index inside the module table, every transform inside 0..7 and the
offsets inside a believable range (x -58..39, y -79..1, against modules of about 36 by 36 pixels).
The decoder picks the layout by walking with 4 bytes first and falling back to 6 only when the
declared frame section length says so, and it records which layout it used. The shipped client
cannot draw the 6 byte file; that is a statement about the file, not a licence to reinterpret the
others.

The transform is the MIDP `Graphics.drawRegion` transform; it is kept as the stored number rather
than renamed, because no evidence in the client fixes a different meaning. The third byte of an
action entry is kept as `flag` and documented as unread: frame advance in the original is a fixed
50 ms tick, so nothing establishes it as a duration.

Usage:

    python tools/analysis/decode_sprites.py dev/assets dev/legacy-sprites
"""

import json
import os
import struct
import sys

HEAD = 8
PLACEMENT = 4
PLACEMENT_WIDE = 6
MAX_IMAGES = 255
MAX_MODULES = 65535
TRANSFORMS = 8
NOTE_FLAG = (
    "third byte of each action frame entry; the shipped client never reads it, so its meaning"
    " is unknown"
)
NOTE_TRANSFORM = "MIDP drawRegion transform code as stored, 0..7"
NOTE_PLACEMENT = (
    "bytes per frame placement: 4 with signed byte offsets, which is all the shipped client"
    " reads, or 6 with signed 16 bit offsets, used by one mount sprite"
)
NOTE_DECLARED = (
    "length written in the file header; the shipped files declare one byte less than their"
    " records occupy, and the client never reads that last byte"
)


def _unsigned(data, index):
    return data[index] if isinstance(data[index], int) else ord(data[index])


def decode(data, sprite_id, known_images=None):
    """Decodes one `.dat` file, including its length header."""
    if len(data) < 2:
        raise ValueError("Sprite file is shorter than its length header")
    declared = struct.unpack_from("<H", data, 0)[0]
    available = data[2:]
    if declared > len(available):
        raise ValueError(
            "Sprite declares %d payload bytes but holds %d" % (declared, len(available))
        )
    # Every shipped sprite declares one byte less than its records occupy. The byte the client
    # therefore never loads is the last action entry flag, which it never reads either, so the
    # shortfall is invisible in the game. Decode the records that are actually there.
    payload = available
    if len(payload) < HEAD:
        raise ValueError("Sprite payload is shorter than its header")
    image_count = _unsigned(payload, 0)
    module_count = struct.unpack_from("<H", payload, 1)[0]
    frame_count = struct.unpack_from("<H", payload, 3)[0]
    action_count = _unsigned(payload, 5)
    frame_section = struct.unpack_from("<H", payload, 6)[0]
    if image_count == 0 or module_count == 0 or frame_count == 0 or action_count == 0:
        raise ValueError(
            "Sprite declares %d images, %d modules, %d frames, %d actions"
            % (image_count, module_count, frame_count, action_count)
        )
    warnings = []
    position = HEAD
    images = []
    for _ in range(image_count):
        if position + 4 > len(payload):
            raise ValueError("Image ID table runs past the payload")
        images.append(struct.unpack_from(">I", payload, position)[0])
        position += 4
    modules = []
    for index in range(module_count):
        if position + 5 > len(payload):
            raise ValueError("Module table runs past the payload")
        image_index = _unsigned(payload, position)
        if image_index >= image_count:
            warnings.append(
                "module %d uses image index %d of %d" % (index, image_index, image_count)
            )
        modules.append(
            {
                "image": image_index,
                "x": _unsigned(payload, position + 1),
                "y": _unsigned(payload, position + 2),
                "width": _unsigned(payload, position + 3),
                "height": _unsigned(payload, position + 4),
            }
        )
        position += 5
    frames_start = position
    placement_size = _placement_size(payload, frames_start, frame_count, frame_section)
    frames, position = _read_frames(
        payload, frames_start, frame_count, module_count, placement_size, warnings
    )
    if position - frames_start != frame_section:
        raise ValueError(
            "Frame section is %d bytes but the header declares %d"
            % (position - frames_start, frame_section)
        )
    actions = []
    for index in range(action_count):
        if position + 5 > len(payload):
            raise ValueError("Action table runs past the payload")
        entries = _unsigned(payload, position)
        action_id = struct.unpack_from(">I", payload, position + 1)[0]
        position += 5
        frame_list = []
        for _ in range(entries):
            if position + 3 > len(payload):
                raise ValueError("Action %d runs past the payload" % index)
            frame_index = struct.unpack_from(">H", payload, position)[0]
            if frame_index >= frame_count:
                warnings.append("action %d uses frame %d of %d" % (index, frame_index, frame_count))
            frame_list.append({"frame": frame_index, "flag": _unsigned(payload, position + 2)})
            position += 3
        actions.append({"id": action_id, "frames": frame_list})
    if position != len(payload):
        raise ValueError("%d bytes remain after the last action" % (len(payload) - position))
    if known_images is not None:
        for image_id in images:
            if image_id not in known_images:
                warnings.append("missing image %d" % image_id)
    return {
        "sprite_id": sprite_id,
        "placement_size": placement_size,
        "declared_payload_length": declared,
        "payload_length": len(payload),
        "images": images,
        "modules": modules,
        "frames": frames,
        "actions": actions,
        "warnings": warnings,
        "notes": {
            "flag": NOTE_FLAG,
            "transform": NOTE_TRANSFORM,
            "placement_size": NOTE_PLACEMENT,
            "declared_payload_length": NOTE_DECLARED,
        },
    }


def _walks(payload, frames_start, frame_count, frame_section, size):
    """True when frames of this placement size fill exactly the declared frame section."""
    position = frames_start
    for _ in range(frame_count):
        if position >= len(payload):
            return False
        position += 1 + _unsigned(payload, position) * size
        if position > len(payload):
            return False
    return position - frames_start == frame_section


def _placement_size(payload, frames_start, frame_count, frame_section):
    """Chooses the placement layout the file's own declared frame section length agrees with.

    4 bytes is what the shipped client reads, so it is tried first; 6 bytes is accepted only when
    4 does not account for the declared length and 6 does exactly.
    """
    for size in (PLACEMENT, PLACEMENT_WIDE):
        if _walks(payload, frames_start, frame_count, frame_section, size):
            return size
    return PLACEMENT


def _read_frames(payload, frames_start, frame_count, module_count, placement_size, warnings):
    position = frames_start
    frames = []
    for index in range(frame_count):
        if position >= len(payload):
            raise ValueError("Frame table runs past the payload")
        placements = _unsigned(payload, position)
        position += 1
        frame = []
        for _ in range(placements):
            if position + placement_size > len(payload):
                raise ValueError("Frame %d runs past the payload" % index)
            word = struct.unpack_from("<H", payload, position)[0]
            module_index = word & 0xFFF
            transform = word >> 12
            if module_index >= module_count:
                warnings.append(
                    "frame %d uses module %d of %d" % (index, module_index, module_count)
                )
            if transform >= TRANSFORMS:
                warnings.append("frame %d uses transform %d" % (index, transform))
            if placement_size == PLACEMENT:
                offset_x, offset_y = struct.unpack_from("bb", payload, position + 2)
            else:
                offset_x, offset_y = struct.unpack_from("<hh", payload, position + 2)
            frame.append(
                {"module": module_index, "transform": transform, "x": offset_x, "y": offset_y}
            )
            position += placement_size
        frames.append(frame)
    return frames, position


def extract(files, out_dir, known_images):
    """Decodes every `d<ID>.dat` of an export, collecting failures as it goes."""
    extract.failures = []
    written = []
    if not os.path.isdir(out_dir):
        os.makedirs(out_dir)
    for source in sorted(files):
        name = os.path.basename(source)
        if not name.lower().startswith("d") or not name.lower().endswith(".dat"):
            continue
        try:
            sprite_id = int(name[1:-4])
        except ValueError:
            extract.failures.append({"source": source, "error": "unexpected sprite file name"})
            continue
        try:
            sprite = decode(files[source], sprite_id, known_images)
        except (ValueError, struct.error) as failure:
            extract.failures.append({"source": source, "error": str(failure)})
            continue
        relative = "%d.sprite.json" % sprite_id
        with open(os.path.join(out_dir, relative), "wb") as handle:
            handle.write((json.dumps(sprite, indent=1, sort_keys=True) + "\n").encode("utf-8"))
        written.append(
            {
                "sprite_id": sprite_id,
                "path": relative,
                "source": source,
                "images": len(sprite["images"]),
                "modules": len(sprite["modules"]),
                "frames": len(sprite["frames"]),
                "actions": len(sprite["actions"]),
                "warnings": sprite["warnings"],
                "placement_size": sprite["placement_size"],
                "declared_payload_length": sprite["declared_payload_length"],
                "payload_length": sprite["payload_length"],
            }
        )
    return written


extract.failures = []


def main(original_root, out_dir, images_report=None):
    with open(os.path.join(original_root, "manifest.json"), "rb") as handle:
        manifest = json.loads(handle.read().decode("utf-8"))
    files = {}
    for member in manifest["members"]:
        if member["source_path"].lower().endswith(".dat"):
            with open(os.path.join(original_root, member["exported_path"]), "rb") as handle:
                files[member["source_path"]] = handle.read()
    known = set()
    if images_report and os.path.isfile(images_report):
        with open(images_report, "rb") as handle:
            known = {
                entry["image_id"] for entry in json.loads(handle.read().decode("utf-8"))["images"]
            }
    written = extract(files, out_dir, known if known else None)
    report = {
        "source": manifest.get("source"),
        "source_sha256": manifest.get("source_sha256"),
        "sprites": written,
        "failures": extract.failures,
    }
    with open(os.path.join(out_dir, "sprites.json"), "wb") as handle:
        handle.write((json.dumps(report, indent=2, sort_keys=True) + "\n").encode("utf-8"))
    missing = sum(len(entry["warnings"]) for entry in written)
    print("decoded %d sprites into %s" % (len(written), out_dir))
    print("sprites referencing images this export does not hold: %d" % missing)
    print("failed files: %d" % len(extract.failures))
    for failure in extract.failures[:10]:
        print("  %s" % failure)
    return 0 if not extract.failures else 1


if __name__ == "__main__":
    if len(sys.argv) not in (3, 4):
        raise SystemExit("Usage: decode_sprites.py ORIGINAL_EXPORT_DIR OUTPUT_DIR [images.json]")
    sys.exit(main(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) == 4 else None))
