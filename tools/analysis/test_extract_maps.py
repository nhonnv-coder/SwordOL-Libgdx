import io
import json
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

from extract_maps import extract_maps, export_maps
from test_decode_images import build_record


def zipped(raw):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("data", raw)
    return stream.getvalue()


def frame(opcode, fields):
    payload = struct.pack(">h", opcode) + b"".join(fields)
    return struct.pack(">h", len(payload) + 2) + payload


def number(value):
    return b"\x04" + struct.pack(">i", value)


def info(map_id, name):
    text = name.encode("utf-8")
    return frame(
        1110,
        [number(map_id), number(map_id), number(0), b"\x06" + struct.pack(">H", len(text)) + text],
    )


def resource(action, raw):
    return frame(
        1407,
        [
            bytes((2, action)),
            b"\x03" + struct.pack(">h", len(raw)),
            b"\x07" + struct.pack(">H", len(raw)) + raw,
        ],
    )


class ExtractMapsTest(unittest.TestCase):
    def test_image_index_archives_replace_each_other_instead_of_concatenating(self):
        image = build_record(1, 1, [0], [[0]])
        first = zipped(struct.pack(">hIBh", 7, 685, 0, 0))
        second = zipped(struct.pack(">hIBh", 7, 686, 0, 0))
        raw = bytes([0, 1, 1, 16, 16, 0, 0, 0, 0])
        wire = resource(10, image) + resource(13, first) + resource(14, second)
        maps, _ = extract_maps(wire + resource(16, zipped(raw)) + info(5, "Updated index"))
        self.assertIn("images/686.png", maps[0]["assets"])
        self.assertNotIn("images/685.png", maps[0]["assets"])

    def test_decodes_raw_downloaded_png_pack_using_compressed_image_index(self):
        image = build_record(1, 1, [0], [[0]])
        index = struct.pack(">hIBh", 7, 685, 0, 0)
        raw = bytes([0, 1, 1, 16, 16, 0, 0, 0, 0])
        wire = resource(10, image) + resource(14, zipped(index))
        wire += resource(16, zipped(raw)) + info(5, "Downloaded image")
        wire += resource(16, zipped(raw)) + info(6, "Packaged images")
        maps, _ = extract_maps(wire)
        self.assertEqual(image, maps[0]["assets"]["map-png.bin"])
        png = maps[0]["assets"]["images/685.png"]
        self.assertEqual(b"\x89PNG\r\n\x1a\n", png[:8])
        self.assertEqual((1, 1), struct.unpack_from(">II", png, 16))
        self.assertNotIn("images/685.png", maps[1]["assets"])

    def test_associates_resources_with_following_map_and_does_not_mix_maps(self):
        first = bytes([0, 2, 3, 16, 16, 1, 2, 3, 4, 5, 6, 1, 2, 4])
        second = bytes([0, 1, 1, 16, 16, 9, 0, 0, 0])
        packed = zipped(first)
        wire = resource(12, zipped(b"reference-a")) + resource(15, packed[:20])
        wire += resource(16, packed[20:]) + info(101, "Trung Nguyên")
        wire += (
            resource(12, zipped(b"reference-b"))
            + resource(16, zipped(second))
            + info(102, "Đại Lý")
        )
        maps, pending = extract_maps(wire + b"\x00\x20\x00")
        self.assertEqual(3, pending)
        self.assertEqual([101, 102], [m["map"]["mapId"] for m in maps])
        self.assertEqual([[1, 2], [3, 4], [5, 6]], maps[0]["map"]["tileRows"])
        self.assertEqual("01", maps[0]["map"]["blockingBitsHex"])
        self.assertEqual(b"reference-b", maps[1]["assets"]["map-ref.bin"])
        self.assertNotIn("map-png.bin", maps[1]["assets"])
        with tempfile.TemporaryDirectory() as directory:
            paths = export_maps(maps, Path(directory), "sample.bin", wire)
            again = export_maps(maps, Path(directory), "sample.bin", wire)
            self.assertEqual(paths, again)
            saved = json.loads((paths[0] / "map.json").read_text("utf-8"))
            self.assertEqual("Trung Nguyên", saved["name"])

    def test_does_not_publish_unfinished_chunks_or_invalid_crc(self):
        raw = bytes([0, 1, 1, 16, 16, 9, 0, 0, 0])
        maps, _ = extract_maps(resource(15, zipped(raw)) + info(1, "unfinished"))
        self.assertEqual([], maps)
        damaged = bytearray(zipped(raw))
        damaged[14] ^= 1
        with self.assertRaisesRegex(ValueError, "CRC"):
            extract_maps(resource(16, bytes(damaged)) + info(1, "broken"))

    def test_rejects_bad_frame_lengths_and_map_dimensions(self):
        with self.assertRaisesRegex(ValueError, "frame length"):
            extract_maps(b"\x00\x03\x00")
        with self.assertRaisesRegex(ValueError, "map size"):
            extract_maps(resource(16, zipped(bytes([0, 2, 2, 16, 16, 9]))) + info(1, "broken"))


if __name__ == "__main__":
    unittest.main()
