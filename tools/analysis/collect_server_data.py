"""One-shot, offline collection of map/NPC/item observations from inbound captures.

No network requests or game actions. Schema evidence: CmdProcessor.processNpcInfoCmd,
Npc getters, processItemInfoCmd and processItemTypeInfoCmd in the original client.
"""

import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import struct
import tempfile

from decode_responses import packet
from extract_maps import extract_maps, export_maps

ROOT = Path(__file__).resolve().parents[2]


def number(fields, index):
    if index >= len(fields) or fields[index]["type"] not in (1, 2, 3, 4, 5):
        raise ValueError("Expected numeric field %d" % index)
    return fields[index]["value"]


def string(fields, index):
    if index >= len(fields) or fields[index]["type"] != 6:
        raise ValueError("Expected string field %d" % index)
    return fields[index]["value"]


def collect_stream(data, source):
    result = {
        "npcs": [],
        "items": [],
        "itemDetails": [],
        "diagnostics": [],
        "pendingBytes": 0,
        "packetCounts": {},
    }
    templates = {}
    current_map = None
    offset = 0
    counts = Counter()
    while offset + 2 <= len(data):
        length = int.from_bytes(data[offset : offset + 2], "big", signed=True)
        if length < 4:
            result["diagnostics"].append({"offset": offset, "error": "Invalid frame length"})
            break
        if offset + length > len(data):
            break
        opcode = int.from_bytes(data[offset + 2 : offset + 4], "big", signed=True)
        counts[opcode] += 1
        if opcode not in (1110, 2030, 1008, 1032):
            offset += length
            continue
        evidence = {"capture": str(source), "offset": offset, "opcode": opcode}
        try:
            fields = packet(data[offset + 2 : offset + length])["fields"]
            if opcode == 1110:
                current_map = number(fields, 0)
            elif opcode == 2030:
                action = number(fields, 0)
                template_id = None
                if action == 1:
                    templates[number(fields, 1)] = fields[1:]
                elif action in (0, 2):
                    if action == 2:
                        template_id = number(fields, 1)
                        cached = templates.get(template_id)
                        if cached is None:
                            raise ValueError("NPC template not present in this stream")
                        # Original removes template ID then appends template fields after its ID.
                        fields = fields[:1] + fields[2:] + cached[1:]
                    if len(fields) < 17:
                        raise ValueError("Incomplete NPC observation")
                    npc = dict(
                        runtimeId=number(fields, 1),
                        mapId=current_map,
                        x=number(fields, 2),
                        y=number(fields, 3),
                        hp=number(fields, 6),
                        maxHp=number(fields, 7),
                        mp=number(fields, 8),
                        maxMp=number(fields, 9),
                        lookId=number(fields, 10),
                        npcType=number(fields, 11),
                        name=string(fields, 12),
                        level=number(fields, 15),
                        campId=number(fields, 16),
                        templateId=template_id,
                        source=evidence,
                    )
                    result["npcs"].append(npc)
                else:
                    raise ValueError("Unsupported NPC action")
            elif opcode == 1008:
                if len(fields) < 16:
                    raise ValueError("Incomplete item observation")
                action = number(fields, 0)
                if action not in (1, 3, 12, 13):
                    raise ValueError("Unsupported item action")
                # Keeper/owner field2 is intentionally excluded. Instances are NOT drop table evidence.
                item = dict(
                    instanceId=number(fields, 1),
                    typeId=number(fields, 10),
                    name=string(fields, 11),
                    amount=number(fields, 3),
                    amountLimit=number(fields, 4),
                    position=number(fields, 5),
                    identified=number(fields, 7),
                    binding=number(fields, 8),
                    moneyField=number(fields, 9),
                    monopoly=number(fields, 12),
                    useRule=number(fields, 13),
                    level=number(fields, 14),
                    iconId=number(fields, 15),
                    action=action,
                    source=evidence,
                )
                result["items"].append(item)
            elif opcode == 1032:
                action = number(fields, 0)
                if action == 99:
                    raise ValueError("Script-form item detail not exported until schema is mapped")
                result["itemDetails"].append(
                    dict(
                        action=action,
                        iconId=number(fields, 1),
                        typeId=number(fields, 2),
                        displayFields=string(fields, 3).split("_"),
                        source=evidence,
                    )
                )
        except (ValueError, IndexError, KeyError, struct.error) as error:
            result["diagnostics"].append(dict(evidence, error=str(error)))
        offset += length
    result["pendingBytes"] = len(data) - offset
    result["packetCounts"] = {str(key): value for key, value in sorted(counts.items())}
    return result


def atomic_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        mode="w", encoding="utf-8", dir=path.parent, delete=False
    ) as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
        temporary = stream.name
    os.replace(temporary, path)


def collect(captures, output, map_root):
    output, map_root = Path(output), Path(map_root)
    sources = {
        p
        for root in captures
        for p in (Path(root).rglob("*-tcp.bin") if Path(root).is_dir() else [Path(root)])
    }
    sources = sorted(sources, key=lambda p: (p.stat().st_mtime_ns, str(p)))
    map_catalog = {}
    if (map_root / "catalog.json").is_file():
        map_catalog = {
            m["mapId"]: m
            for m in json.loads((map_root / "catalog.json").read_text("utf-8"))["maps"]
        }
    npcs = []
    items = []
    details = []
    diagnostics = []
    snapshots = []
    counts = Counter()
    for source in sources:
        try:
            if source.stat().st_size > 64 * 1024 * 1024:
                raise ValueError("Capture exceeds 64MiB recorder limit")
            snapshot = source.read_bytes()
            parsed = collect_stream(snapshot, source)
            snapshots.append(
                dict(
                    path=str(source),
                    bytes=len(snapshot),
                    sha256=hashlib.sha256(snapshot).hexdigest(),
                    pendingBytes=parsed["pendingBytes"],
                )
            )
            npcs.extend(parsed["npcs"])
            items.extend(parsed["items"])
            details.extend(parsed["itemDetails"])
            diagnostics.extend(parsed["diagnostics"])
            counts.update(parsed["packetCounts"])
            try:
                maps, pending = extract_maps(snapshot)
                paths = export_maps(maps, map_root, source, snapshot)
                for item, path in zip(maps, paths):
                    doc = item["map"]
                    map_catalog[doc["mapId"]] = dict(
                        mapId=doc["mapId"],
                        name=doc["name"],
                        columns=doc["columns"],
                        rows=doc["rows"],
                        path=str(path.relative_to(map_root)),
                        downloadedImages=sum(name.startswith("images/") for name in item["assets"]),
                    )
            except (OSError, ValueError) as error:
                diagnostics.append(dict(capture=str(source), section="maps", error=str(error)))
        except (OSError, ValueError) as error:
            diagnostics.append(dict(capture=str(source), error=str(error)))
    maps = [map_catalog[k] for k in sorted(map_catalog)]
    atomic_json(map_root / "catalog.json", {"schemaVersion": 1, "maps": maps})
    # Group forms conservatively; numeric NPC type does not establish monster/NPC taxonomy.
    forms = {}
    item_types = {}
    for npc in npcs:
        key = (npc["lookId"], npc["npcType"], npc["name"], npc["level"])
        form = forms.setdefault(
            key,
            dict(
                lookId=key[0],
                npcType=key[1],
                name=key[2],
                level=key[3],
                mapIds=set(),
                observations=0,
            ),
        )
        if npc["mapId"] is not None:
            form["mapIds"].add(npc["mapId"])
        form["observations"] += 1
    forms = [dict(value, mapIds=sorted(value["mapIds"])) for _, value in sorted(forms.items())]
    for item in items:
        key = (item["typeId"], item["name"], item["iconId"])
        entry = item_types.setdefault(
            key,
            dict(typeId=key[0], name=key[1], iconId=key[2], observations=0, source=item["source"]),
        )
        entry["observations"] += 1
    known_types = {item["typeId"] for item in items}
    for detail in details:
        if detail["typeId"] not in known_types:
            key = (detail["typeId"], "(chưa xác định tên)", detail["iconId"])
            item_types.setdefault(
                key,
                dict(
                    typeId=key[0],
                    name=key[1],
                    iconId=key[2],
                    observations=0,
                    source=detail["source"],
                ),
            )
    item_types = [v for _, v in sorted(item_types.items())]
    totals = dict(
        maps=len(maps),
        npcForms=len(forms),
        npcObservations=len(npcs),
        itemTypes=len({i["typeId"] for i in items + details}),
        itemObservations=len(items),
        itemDetailPackets=len(details),
        diagnostics=len(diagnostics),
    )
    files = {
        "maps.json": dict(mapsRoot=str(map_root), maps=maps),
        "npcs.json": dict(forms=forms, observations=npcs),
        "items.json": dict(types=item_types, observations=items, detailPackets=details),
        "diagnostics.json": diagnostics,
        "sources.json": snapshots,
        "counts.json": dict(counts=totals, packetCounts=dict(counts)),
    }
    digest = hashlib.sha256(
        json.dumps(files, ensure_ascii=True, sort_keys=True).encode("utf-8")
    ).hexdigest()
    generation = "runs/" + digest[:16]
    target = output / generation
    output.mkdir(parents=True, exist_ok=True)
    (output / "runs").mkdir(exist_ok=True)
    summary = build_summary(totals, maps, forms, item_types, diagnostics)
    if not target.exists():
        with tempfile.TemporaryDirectory(prefix=".collect-", dir=output) as temp:
            staged = Path(temp) / "ready"
            staged.mkdir()
            for name, value in files.items():
                atomic_json(staged / name, value)
            (staged / "summary.md").write_text(summary, encoding="utf-8")
            manifest = dict(schemaVersion=1, contentSha256=digest, counts=totals, files=[])
            for path in sorted(staged.iterdir()):
                content = path.read_bytes()
                manifest["files"].append(
                    dict(
                        path=path.name,
                        bytes=len(content),
                        sha256=hashlib.sha256(content).hexdigest(),
                    )
                )
            atomic_json(staged / "manifest.json", manifest)
            os.rename(staged, target)
    else:
        if json.loads((target / "manifest.json").read_text("utf-8"))["contentSha256"] != digest:
            raise ValueError("Report generation hash collision")
    latest = dict(schemaVersion=1, generation=generation, counts=totals)
    atomic_json(output / "latest.json", latest)
    # Stable convenience copy, published atomically; immutable generation remains authoritative.
    with tempfile.NamedTemporaryFile(
        mode="w", encoding="utf-8", dir=output, delete=False
    ) as stream:
        stream.write(summary)
        temporary = stream.name
    os.replace(temporary, output / "summary.md")
    return latest


def safe_text(value):
    return (
        str(value)
        .replace("\n", " ")
        .replace("\r", " ")
        .replace("|", "\\|")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    )


def build_summary(counts, maps, forms, items, diagnostics):
    lines = [
        "# Dữ liệu SwordOL đã quan sát",
        "",
        "Chỉ tổng hợp phản hồi client đã nhận; chưa phải cơ sở dữ liệu server đầy đủ. Không suy ra bảng rơi đồ, AI, respawn hoặc công thức chiến đấu.",
        "",
        "## Map",
        "",
        f"Đã tách **{counts['maps']} map**. Dữ liệu từng map và đường dẫn preview nằm trong maps.json và catalog của server-maps.",
        "",
        "| ID | Tên | Kích thước |",
        "|---|---|---|",
    ]
    lines.extend(
        "| %s | %s | %s × %s |" % (m["mapId"], safe_text(m["name"]), m["columns"], m["rows"])
        for m in maps
    )
    lines += [
        "",
        "## Quái / NPC",
        "",
        f"Có **{counts['npcForms']} dạng hiển thị**, từ **{counts['npcObservations']} quan sát**. Đây là nhóm theo look ID, mã loại, tên và cấp; chưa kết luận tất cả là quái. Vị trí/HP chỉ là trạng thái đã thấy, không phải định nghĩa spawn/respawn.",
        "",
        "| Tên | Cấp | Mã loại | Look ID | Map đã thấy |",
        "|---|---|---|---|---|",
    ]
    lines.extend(
        "| %s | %s | %s | %s | %s |"
        % (
            safe_text(n["name"]),
            n["level"],
            n["npcType"],
            n["lookId"],
            ", ".join(map(str, n["mapIds"])),
        )
        for n in forms
    )
    lines += [
        "",
        "## Item",
        "",
        f"Có **{counts['itemTypes']} type ID**, từ **{counts['itemObservations']} quan sát**. Bỏ trường chủ đồ; không coi đồ đang có trong túi là bằng chứng về nơi rơi hoặc giá cửa hàng. Trường moneyField giữ tên trung lập vì chưa xác nhận nghĩa kinh tế.",
        "",
        "| Type ID | Tên | Icon | Số quan sát |",
        "|---|---|---|---|",
    ]
    lines.extend(
        "| %s | %s | %s | %s |"
        % (i["typeId"], safe_text(i["name"]), i["iconId"], i["observations"])
        for i in items
    )
    lines += [
        "",
        "## Chất lượng và phần chưa có",
        "",
        f"Có {len(diagnostics)} chẩn đoán/schema chưa xử lý; xem diagnostics.json. Frame cuối đang ghi dở được giữ lại cho lần đọc sau. Mỗi quan sát có file nguồn và offset; sources.json giữ SHA-256 snapshot.",
        "",
        "Chưa thu được dữ liệu mới nếu client chưa đăng nhập/vào map hoặc chưa mở chi tiết vật phẩm. Không tự đăng nhập, gửi opcode dò ID, mua/bán/xóa đồ hoặc quét endpoint.",
        "",
    ]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "captures",
        nargs="*",
        type=Path,
        default=[ROOT / "captures", ROOT / "dist/SwordOL-PC-Capture/captures"],
    )
    parser.add_argument("--output", type=Path, default=ROOT / "dev/server-data")
    parser.add_argument("--maps", type=Path, default=ROOT / "dev/server-maps")
    args = parser.parse_args()
    print(json.dumps(collect(args.captures, args.output, args.maps), ensure_ascii=True))


if __name__ == "__main__":
    main()
