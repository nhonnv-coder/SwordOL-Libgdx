import json
import struct
import tempfile
import unittest
from pathlib import Path

from collect_server_data import collect_stream, collect


def frame(op, fields):
    raw = struct.pack(">h", op)
    for kind, value in fields:
        raw += bytes((kind,))
        if kind == 6:
            value = value.encode("utf-8")
            raw += struct.pack(">H", len(value)) + value
        else:
            raw += int(value).to_bytes({2: 1, 3: 2, 4: 4}[kind], "big", signed=True)
    return struct.pack(">h", len(raw) + 2) + raw


class ServerDataTest(unittest.TestCase):
    def test_keeps_item_types_seen_only_in_detail_responses(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "session"
            source.mkdir()
            wire = frame(1032, [(2, 0), (3, 17), (4, 3001), (6, "A_B_C")])
            (source / "000001-tcp.bin").write_bytes(wire)
            report = collect([source], root / "out", root / "maps")
            self.assertEqual(1, report["counts"]["itemTypes"])
            items = json.loads(
                (root / "out" / report["generation"] / "items.json").read_text("utf-8")
            )
            self.assertEqual(3001, items["types"][0]["typeId"])
            self.assertEqual(0, items["types"][0]["observations"])
            self.assertEqual(1032, items["types"][0]["source"]["opcode"])

    def test_resolves_npc_templates_without_cross_stream_or_secret_export(self):
        mapinfo = frame(1110, [(4, 7), (4, 9), (4, 0), (6, "Map")])
        template = [
            (4, 800),
            (4, 315000),
            (3, 2),
            (6, "Wolf"),
            (4, 315000),
            (4, 0),
            (3, 5),
            (4, 31),
            (4, 0),
            (2, 0),
        ]
        spawn = [
            (2, 2),
            (4, 800),
            (4, 1000),
            (3, 4),
            (3, 6),
            (4, 0),
            (2, 0),
            (4, 50),
            (4, 60),
            (4, 20),
            (4, 30),
        ]
        secret = frame(1004, [(6, "PRIVATE_CHAT_TOKEN")])
        wire = mapinfo + frame(2030, [(2, 1)] + template) + frame(2030, spawn) + secret
        out = collect_stream(wire + b"\x00\x20\x00", "test.bin")
        self.assertEqual(3, out["pendingBytes"])
        npc = out["npcs"][0]
        self.assertEqual(
            ("Wolf", 4, 6, 60, 5, 800),
            (npc["name"], npc["x"], npc["y"], npc["maxHp"], npc["level"], npc["templateId"]),
        )
        self.assertEqual(7, npc["mapId"])
        self.assertNotIn("PRIVATE_CHAT_TOKEN", json.dumps(out))
        missing = collect_stream(frame(2030, spawn), "other.bin")
        self.assertEqual([], missing["npcs"])
        self.assertTrue(missing["diagnostics"])

    def test_item_fields_exclude_keeper_and_report_is_reusable(self):
        fields = [
            (2, 1),
            (4, 999),
            (4, 123456789),
            (3, 2),
            (3, 99),
            (2, 0),
            (3, 4),
            (3, 0),
            (4, 1),
            (4, 50),
            (4, 2020001),
            (6, "Potion"),
            (3, 0),
            (3, 1),
            (2, 5),
            (3, 111),
            (4, 0),
        ]
        wire = frame(1008, fields)
        result = collect_stream(wire, "test.bin")
        self.assertEqual("Potion", result["items"][0]["name"])
        self.assertNotIn("123456789", json.dumps(result))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "session"
            source.mkdir()
            (source / "000001-tcp.bin").write_bytes(wire)
            first = collect([source], root / "out", root / "maps")
            second = collect([source], root / "out", root / "maps")
            self.assertEqual(first["generation"], second["generation"])
            self.assertEqual(1, first["counts"]["itemTypes"])
            self.assertTrue((root / "out" / first["generation"] / "summary.md").is_file())


if __name__ == "__main__":
    unittest.main()
