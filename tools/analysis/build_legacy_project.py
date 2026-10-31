"""Assemble the decoded original assets into an asset project the pipeline can build.

Inputs are the outputs of the decoders in this directory: `decode_images.py` (packed `.p` images),
`decode_sprites.py` (`.dat`), `decode_ui.py` (`.ui`) and `decode_config.py` (`.o`), plus the
original export's own PNG members. Every asset keeps the provenance of the member it came from, so
a later reader can trace any byte back to the JAR.

Dependencies are declared where the decoded data states them: a sprite depends on the images its
image table names, and a UI layout depends on the images its buttons name. References the export
does not contain are left out of the dependency list and kept in the asset's provenance as
unresolved, because the registry requires every declared dependency to exist.

Usage:

    python tools/analysis/build_legacy_project.py dev/assets dev \\
        dev/OL
"""

import json
import os
import shutil
import sys

IMAGE = "legacy.image.%d"
ORIGINAL_PNG = "legacy.png.%d"
SPRITE = "legacy.sprite.%d"
LAYOUT = "legacy.ui.%d"
CONFIG = "legacy.config.%s"


def _read_json(path):
    with open(path, "rb") as handle:
        return json.loads(handle.read().decode("utf-8"))


def _copy(source, destination):
    directory = os.path.dirname(destination)
    if not os.path.isdir(directory):
        os.makedirs(directory)
    shutil.copyfile(source, destination)


def build(original_root, decoded_root, project_root):
    if os.path.exists(project_root):
        raise SystemExit("Destination already exists: " + project_root)
    manifest = _read_json(os.path.join(original_root, "manifest.json"))
    jar = {"source": manifest.get("source"), "source_sha256": manifest.get("source_sha256")}
    images = _read_json(os.path.join(decoded_root, "legacy-images", "images.json"))
    sprites = _read_json(os.path.join(decoded_root, "legacy-sprites", "sprites.json"))
    layouts = _read_json(os.path.join(decoded_root, "legacy-ui", "layouts.json"))
    configs = _read_json(os.path.join(decoded_root, "legacy-config", "config.json"))
    assets = []
    known_images = set()

    for entry in images["images"]:
        asset_id = IMAGE % entry["image_id"]
        known_images.add(entry["image_id"])
        relative = "images/%d.png" % entry["image_id"]
        _copy(
            os.path.join(decoded_root, "legacy-images", entry["path"]),
            os.path.join(project_root, relative),
        )
        provenance = dict(jar)
        provenance.update(
            {
                "decoded_by": "tools/analysis/decode_images.py",
                "pack": entry["pack"],
                "pack_offset": entry["pack_offset"],
                "width": entry["width"],
                "height": entry["height"],
                "colors": entry["colors"],
                "transparent_index": entry["transparent_index"],
                "stored_crc_ok": entry["crc_ok"],
                "sha256": entry["sha256"],
            }
        )
        assets.append(
            {
                "id": asset_id,
                "type": "image",
                "source": relative,
                "dependencies": [],
                "provenance": provenance,
            }
        )

    for member in manifest["members"]:
        if not member["source_path"].lower().endswith(".png"):
            continue
        ordinal = member["archive_index"]
        relative = "images/original/%d.png" % ordinal
        _copy(
            os.path.join(original_root, member["exported_path"]),
            os.path.join(project_root, relative),
        )
        provenance = dict(jar)
        provenance.update(
            {
                "original_member": member["source_path"],
                "archive_index": ordinal,
                "sha256": member["sha256"],
                "note": "byte preserved PNG member of the original archive",
            }
        )
        assets.append(
            {
                "id": ORIGINAL_PNG % ordinal,
                "type": "image",
                "source": relative,
                "dependencies": [],
                "provenance": provenance,
            }
        )

    for entry in sprites["sprites"]:
        relative = "sprites/%d.sprite.json" % entry["sprite_id"]
        source_path = os.path.join(decoded_root, "legacy-sprites", entry["path"])
        _copy(source_path, os.path.join(project_root, relative))
        document = _read_json(source_path)
        resolved = []
        unresolved = []
        for image_id in document["images"]:
            if image_id in known_images:
                candidate = IMAGE % image_id
                if candidate not in resolved:
                    resolved.append(candidate)
            elif image_id not in unresolved:
                unresolved.append(image_id)
        provenance = dict(jar)
        provenance.update(
            {
                "decoded_by": "tools/analysis/decode_sprites.py",
                "original_member": entry["source"],
                "modules": entry["modules"],
                "frames": entry["frames"],
                "actions": entry["actions"],
                "unresolved_images": unresolved,
                "warnings": entry["warnings"],
            }
        )
        assets.append(
            {
                "id": SPRITE % entry["sprite_id"],
                "type": "sprite",
                "source": relative,
                "dependencies": sorted(resolved),
                "provenance": provenance,
            }
        )

    for entry in layouts["screens"]:
        relative = "ui/%d.ui.json" % entry["window_id"]
        source_path = os.path.join(decoded_root, "legacy-ui", entry["path"])
        _copy(source_path, os.path.join(project_root, relative))
        document = _read_json(source_path)
        resolved = []
        unresolved = []
        for control in document["controls"]:
            image_id = control.get("image_id", 0)
            if image_id > 0:
                if image_id in known_images:
                    candidate = IMAGE % image_id
                    if candidate not in resolved:
                        resolved.append(candidate)
                elif image_id not in unresolved:
                    unresolved.append(image_id)
        provenance = dict(jar)
        provenance.update(
            {
                "decoded_by": "tools/analysis/decode_ui.py",
                "original_member": entry["source"],
                "screen_id": entry["screen_id"],
                "controls": entry["controls"],
                "control_kinds": entry["kinds"],
                "unresolved_images": unresolved,
            }
        )
        assets.append(
            {
                "id": LAYOUT % entry["window_id"],
                "type": "ui",
                "source": relative,
                "dependencies": sorted(resolved),
                "provenance": provenance,
            }
        )

    for entry in configs["files"]:
        name = os.path.basename(entry["source"])[:-2]
        relative = "config/%s.json" % name
        _copy(
            os.path.join(decoded_root, "legacy-config", entry["path"]),
            os.path.join(project_root, relative),
        )
        provenance = dict(jar)
        provenance.update(
            {
                "decoded_by": "tools/analysis/decode_config.py",
                "original_member": entry["source"],
                "reader": entry["kind"],
                "note": "decoded configuration data, registered as binary because the registry"
                " has no config type",
            }
        )
        assets.append(
            {
                "id": CONFIG % name.replace("_", "-"),
                "type": "binary",
                "source": relative,
                "dependencies": [],
                "provenance": provenance,
            }
        )

    assets.sort(key=lambda asset: asset["id"])
    registry = {"schemaVersion": 1, "assets": assets}
    with open(os.path.join(project_root, "registry.json"), "wb") as handle:
        handle.write((json.dumps(registry, indent=1, sort_keys=True) + "\n").encode("utf-8"))
    kinds = {}
    for asset in assets:
        kinds[asset["type"]] = kinds.get(asset["type"], 0) + 1
    print("wrote %s with %d assets %s" % (project_root, len(assets), kinds))
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit(
            "Usage: build_legacy_project.py ORIGINAL_EXPORT_DIR DECODED_DIR NEW_PROJECT_DIR"
        )
    sys.exit(build(sys.argv[1], sys.argv[2], sys.argv[3]))
