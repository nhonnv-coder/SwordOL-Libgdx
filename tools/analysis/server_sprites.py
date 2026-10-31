"""Recover captured NPC sprite packs (1407 actions30..43) without network access."""

import hashlib
import json
import os
from pathlib import Path
import tempfile
from decode_responses import packet
from decode_sprites import decode
from decode_images import parse_index, decode_record, verify_png
from extract_maps import unpack_resource, MAX_RESOURCE


def recover_stream(data):
    pending = {}
    complete = {}
    found = {}
    errors = []
    offset = 0
    kinds = {
        30: "png",
        31: "png",
        38: "png",
        39: "png",
        32: "dat",
        33: "dat",
        40: "dat",
        41: "dat",
        34: "index",
        35: "index",
        42: "index",
        43: "index",
    }
    while offset + 2 <= len(data):
        length = int.from_bytes(data[offset : offset + 2], "big", signed=True)
        if length < 4:
            errors.append(dict(offset=offset, error="Invalid frame length"))
            break
        if offset + length > len(data):
            break
        if int.from_bytes(data[offset + 2 : offset + 4], "big", signed=True) != 1407:
            offset += length
            continue
        try:
            fields = packet(data[offset + 2 : offset + length])["fields"]
            action = fields[0].get("value")
            if action not in kinds:
                offset += length
                continue
            if [f["type"] for f in fields] != [2, 4, 3, 7]:
                raise ValueError("Invalid sprite resource fields")
            sprite_id = fields[1]["value"]
            raw = bytes.fromhex(fields[3]["value_hex"])
            if fields[2]["value"] != len(raw):
                raise ValueError("Sprite chunk length mismatch")
            kind = kinds[action]
            key = (sprite_id, kind)
            done = complete.setdefault(sprite_id, {})
            if kind == "index":
                unpack_resource(raw)
                if action in (35, 43):
                    done[kind] = raw
            else:
                current = pending.setdefault(key, bytearray())
                if len(current) + len(raw) > MAX_RESOURCE:
                    raise ValueError("Oversized sprite pack")
                current.extend(raw)
                if action in (31, 39, 33, 41):
                    done[kind] = bytes(pending.pop(key))
            if all(k in done for k in ("png", "dat", "index")):
                # A bundle is consumed once; subsequent downloads cannot mix with its old parts.
                images = {}
                for image_id, _, position in parse_index(unpack_resource(done["index"])):
                    image = decode_record(done["png"], position)
                    verify_png(image.png)
                    images[image_id] = image.png
                doc = decode(unpack_resource(done["dat"]), sprite_id, set(images))
                found[sprite_id] = dict(doc=doc, images=images, offset=offset)
                del complete[sprite_id]
        except (ValueError, KeyError, IndexError) as error:
            errors.append(dict(offset=offset, error=str(error)))
        offset += length
    return found, errors


def refresh(capture_roots, destination):
    destination = Path(destination)
    sources = sorted(
        {p for root in capture_roots for p in Path(root).rglob("*-tcp.bin")},
        key=lambda p: (p.stat().st_mtime_ns, str(p)),
    )
    entries = {}
    errors = []
    for source in sources:
        try:
            if source.stat().st_size > 64 * 1024 * 1024:
                raise ValueError("Capture over recorder limit")
            snapshot = source.read_bytes()
            found, problems = recover_stream(snapshot)
            errors.extend(dict(p, capture=str(source)) for p in problems)
            for sprite_id, bundle in found.items():
                files = {
                    "sprite.json": (
                        json.dumps(bundle["doc"], ensure_ascii=False, indent=2) + "\n"
                    ).encode("utf-8")
                }
                files.update({"images/%d.png" % k: v for k, v in bundle["images"].items()})
                digest = hashlib.sha256()
                for name, raw in sorted(files.items()):
                    digest.update(name.encode() + b"\0" + hashlib.sha256(raw).digest())
                folder = destination / str(sprite_id) / digest.hexdigest()[:16]
                if not folder.exists():
                    folder.parent.mkdir(parents=True, exist_ok=True)
                    with tempfile.TemporaryDirectory(prefix=".sprite-", dir=folder.parent) as tmp:
                        staged = Path(tmp) / "ready"
                        staged.mkdir()
                        for name, raw in files.items():
                            (staged / name).parent.mkdir(parents=True, exist_ok=True)
                            (staged / name).write_bytes(raw)
                        (staged / "source.json").write_text(
                            json.dumps(
                                dict(
                                    capture=str(source),
                                    snapshotSha256=hashlib.sha256(snapshot).hexdigest(),
                                    offset=bundle["offset"],
                                    contentSha256=digest.hexdigest(),
                                ),
                                indent=2,
                            ),
                            encoding="utf-8",
                        )
                        os.rename(staged, folder)
                doc = bundle["doc"]
                entries[sprite_id] = dict(
                    sprite_id=sprite_id,
                    frames=len(doc["frames"]),
                    actions=len(doc["actions"]),
                    images=len(doc["images"]),
                    path=str(folder.relative_to(destination) / "sprite.json"),
                    server=True,
                    sourceCapture=str(source),
                    warnings=doc["warnings"],
                )
        except (OSError, ValueError) as error:
            errors.append(dict(capture=str(source), error=str(error)))
    return list(entries.values()), errors
