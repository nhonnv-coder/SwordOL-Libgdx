"""Extract map resources from local, possibly still-growing, legacy TCP recordings.

Evidence: CmdProcessor.processMapDataCmd (1407 resources BEFORE 1110 map identity),
processMapPortalCmd, MapEx.loadZipData and ImagePointer.initCurMapRefDataEx.
Only map packets are exported, never account/chat/player packets. No network I/O.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import tempfile
import zlib

from decode_responses import packet
from decode_images import decode_record, parse_index, verify_png

MAX_RESOURCE = 16 * 1024 * 1024
KINDS = {
    9: "map-png",
    10: "map-png",
    11: "map-ref",
    12: "map-ref",
    13: "map-images",
    14: "map-images",
    15: "map-data",
    16: "map-data",
}


def unpack_resource(data):
    if len(data) < 30 or data[:4] != b"PK\x03\x04":
        raise ValueError("Missing ZIP local header")
    _, _, flags, method, _, _, crc, packed, size, name_len, extra_len = struct.unpack_from(
        "<IHHHHHIIIHH", data
    )
    start = 30 + name_len + extra_len
    if flags & 9 or size > MAX_RESOURCE or packed > MAX_RESOURCE or start + packed > len(data):
        raise ValueError("Unsupported or truncated ZIP resource")
    payload = data[start : start + packed]
    if method == 8:
        decoder = zlib.decompressobj(-15)
        try:
            raw = decoder.decompress(payload, MAX_RESOURCE + 1)
        except zlib.error as error:
            raise ValueError("Invalid ZIP compression") from error
        if not decoder.eof or decoder.unused_data or decoder.unconsumed_tail:
            raise ValueError("Invalid or oversized ZIP stream")
    elif method == 0:
        raw = payload
    else:
        raise ValueError("Unsupported ZIP compression method")
    if len(raw) != size or zlib.crc32(raw) & 0xFFFFFFFF != crc:
        raise ValueError("ZIP size/CRC mismatch")
    return raw


def map_document(info, raw):
    if len(raw) < 5:
        raise ValueError("Invalid map size: missing header")
    cols, rows = raw[1:3]
    cells = cols * rows
    bits = (cells + 7) // 8
    if not 0 < cols < 128 or not 0 < rows < 128 or len(raw) != 5 + cells + 3 * bits:
        raise ValueError("Invalid map size or signed dimensions")
    return dict(
        info,
        schemaVersion=1,
        columns=cols,
        rows=rows,
        header=list(raw[:5]),
        tileStorage="row-major: y * columns + x",
        bitsetStorage="row-major cell index, least significant bit first",
        tileRows=[[raw[5 + y * cols + x] for x in range(cols)] for y in range(rows)],
        blockingBitsHex=raw[5 + cells : 5 + cells + bits].hex(),
        rotationBitsHex=raw[5 + cells + bits : 5 + cells + 2 * bits].hex(),
        flightBlockingBitsHex=raw[5 + cells + 2 * bits :].hex(),
        portalPackets=[],
    )


def extract_maps(data):
    """Return complete maps and unconsumed tail size; incomplete final frames are retained for next run."""
    result, chunks, complete = [], {}, {}
    active = None
    offset = 0
    while offset + 2 <= len(data):
        length = int.from_bytes(data[offset : offset + 2], "big", signed=True)
        if length < 4:
            raise ValueError("Invalid TCP frame length at byte %d" % offset)
        if offset + length > len(data):
            break
        # Ignore unrelated packet schemas instead of exporting account/chat data.
        opcode = int.from_bytes(data[offset + 2 : offset + 4], "big", signed=True)
        if opcode not in (1110, 1126, 1407):
            offset += length
            continue
        msg = packet(data[offset + 2 : offset + length])
        fields = msg["fields"]
        if opcode == 1407:
            if not fields or fields[0]["type"] != 2:
                raise ValueError("Invalid resource action")
            action = fields[0]["value"]
            if action in KINDS:
                if [f["type"] for f in fields] != [2, 3, 7]:
                    raise ValueError("Invalid resource packet fields")
                raw = bytes.fromhex(fields[2]["value_hex"])
                if fields[1]["value"] != len(raw):
                    raise ValueError("Resource chunk length mismatch")
                kind = KINDS[action]
                if action in (13, 14):
                    # updateArrayBoosImages replaces the index for EACH archive, unlike dataArrrayCopy.
                    unpack_resource(raw)
                    complete[kind] = raw
                    offset += length
                    continue
                pending = chunks.setdefault(kind, bytearray())
                if len(pending) + len(raw) > MAX_RESOURCE:
                    raise ValueError("Oversized map resource")
                pending.extend(raw)
                if action in (10, 12, 14, 16):
                    complete[kind] = bytes(chunks.pop(kind))
        elif opcode == 1110:
            if [f["type"] for f in fields] != [4, 4, 4, 6]:
                raise ValueError("Invalid map identity fields")
            info = dict(
                zip(("mapId", "resourceId", "mapType", "name"), (f["value"] for f in fields))
            )
            active = None
            if "map-data" in complete and not chunks:
                assets = {}
                for kind, zipped in complete.items():
                    if kind == "map-png":
                        # The client copies this directly into NewCreatePng's packed-image reader.
                        assets["map-png.bin"] = zipped
                        continue
                    assets[kind + ".zip"] = zipped
                    assets[kind + ".bin"] = unpack_resource(zipped)
                if "map-images.bin" in assets and "map-png.bin" in assets:
                    for image_id, _, image_offset in parse_index(assets["map-images.bin"]):
                        decoded = decode_record(assets["map-png.bin"], image_offset)
                        verify_png(decoded.png)
                        assets["images/%d.png" % image_id] = decoded.png
                active = {
                    "map": map_document(info, assets["map-data.bin"]),
                    "assets": assets,
                    "identityFrameOffset": offset,
                }
                result.append(active)
            chunks, complete = {}, {}
        elif opcode == 1126 and active is not None:
            active["map"]["portalPackets"].append(msg)
        offset += length
    return result, len(data) - offset


def export_maps(maps, destination, source_name, snapshot):
    """Publish immutable content versions; rerunning the same snapshot does not overwrite previous files."""
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    paths = []
    source_hash = hashlib.sha256(snapshot).hexdigest()
    for item in maps:
        files = dict(item["assets"])
        files["map.json"] = (json.dumps(item["map"], ensure_ascii=False, indent=2) + "\n").encode(
            "utf-8"
        )
        fingerprint = hashlib.sha256()
        entries = []
        for name, raw in sorted(files.items()):
            digest = hashlib.sha256(raw).hexdigest()
            fingerprint.update(name.encode("ascii") + b"\0" + digest.encode("ascii"))
            entries.append({"path": name, "bytes": len(raw), "sha256": digest})
        version = fingerprint.hexdigest()
        parent = destination / str(item["map"]["mapId"])
        parent.mkdir(exist_ok=True)
        target = parent / version[:16]
        if target.exists():
            manifest = json.loads((target / "manifest.json").read_text("utf-8"))
            if manifest["contentSha256"] != version:
                raise ValueError("Map version hash collision")
        else:
            manifest = dict(
                schemaVersion=1,
                mapId=item["map"]["mapId"],
                name=item["map"]["name"],
                contentSha256=version,
                sourceCapture=str(source_name),
                sourceSnapshotSha256=source_hash,
                snapshotBytes=len(snapshot),
                identityFrameOffset=item["identityFrameOffset"],
                files=entries,
                notes=[
                    "Tile images may refer to assets packaged in the original client.",
                    "Blocking bitsets are retained exactly as sent by the server.",
                    "Portal packets include later dynamic cell updates.",
                ],
            )
            with tempfile.TemporaryDirectory(prefix=".map-", dir=parent) as staging:
                staged = Path(staging) / "ready"
                staged.mkdir()
                for name, raw in files.items():
                    (staged / name).parent.mkdir(parents=True, exist_ok=True)
                    (staged / name).write_bytes(raw)
                (staged / "manifest.json").write_text(
                    json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
                )
                os.rename(staged, target)
        paths.append(target)
    return paths


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "captures", nargs="+", type=Path, help="TCP .bin files or capture directories"
    )
    parser.add_argument("--output", type=Path, default=Path("dev/server-maps"))
    args = parser.parse_args()
    sources = sorted(
        {
            p
            for source in args.captures
            for p in (source.rglob("*-tcp.bin") if source.is_dir() else [source])
        }
    )
    failed = False
    catalog = {}
    for source in sources:
        try:
            snapshot = source.read_bytes()
            maps, remaining = extract_maps(snapshot)
            outputs = export_maps(maps, args.output, source, snapshot)
            for item, output in zip(maps, outputs):
                doc = item["map"]
                catalog[doc["mapId"]] = dict(
                    mapId=doc["mapId"],
                    name=doc["name"],
                    columns=doc["columns"],
                    rows=doc["rows"],
                    path=str(output.relative_to(args.output)),
                    downloadedImages=sum(name.startswith("images/") for name in item["assets"]),
                )
            print(
                json.dumps(
                    {
                        "source": str(source),
                        "snapshotBytes": len(snapshot),
                        "pendingBytes": remaining,
                        "maps": [
                            {
                                "id": m["map"]["mapId"],
                                "name": m["map"]["name"],
                                "columns": m["map"]["columns"],
                                "rows": m["map"]["rows"],
                                "path": str(p),
                            }
                            for m, p in zip(maps, outputs)
                        ],
                    },
                    ensure_ascii=True,
                )
            )
        except (OSError, ValueError) as error:
            failed = True
            print(json.dumps({"source": str(source), "error": str(error)}, ensure_ascii=True))
    if not failed:
        args.output.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(
            mode="w", encoding="utf-8", dir=args.output, delete=False
        ) as stream:
            json.dump(
                {"schemaVersion": 1, "maps": [catalog[key] for key in sorted(catalog)]},
                stream,
                ensure_ascii=False,
                indent=2,
            )
            staging = stream.name
        os.replace(staging, args.output / "catalog.json")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
