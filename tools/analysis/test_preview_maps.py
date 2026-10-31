import struct
import tempfile
import unittest
from pathlib import Path
from PIL import Image

from preview_maps import parse_references, render_map, map_state, overlay


def references(records):
    body = bytearray()
    offsets = []
    for record in records:
        offsets.append(len(body))
        body.extend(record)
    return (
        struct.pack(">hh", len(records), 700)
        + b"".join(struct.pack(">h", n) for n in offsets)
        + body
    )


def obj(image_id):
    return struct.pack(">hbbBhB", 0x4000, 1, 1, 1, image_id, 0)


class PreviewMapsTest(unittest.TestCase):
    def test_foreground_is_drawn_after_later_rows_in_background_and_low_decoration(self):
        for later_flags in (0x4000, 0x0800):
            with self.subTest(later_flags=later_flags), tempfile.TemporaryDirectory() as tmp:
                folder = Path(tmp)
                Image.new("RGBA", (4, 8), "blue").save(folder / "1.png")
                Image.new("RGBA", (4, 4), "green").save(folder / "2.png")
                front = struct.pack(">hbbBhhhB", 0, 1, 1, 1, 1, -2, 0, 0)
                later = (
                    obj(2)
                    if later_flags == 0x4000
                    else struct.pack(">hbbBhhhB", 0x0800, 1, 1, 1, 2, -2, -4, 0)
                )
                (folder / "map-ref.bin").write_bytes(references([front, later]))
                doc = {
                    "columns": 1,
                    "rows": 2,
                    "header": [0, 1, 2, 4, 4],
                    "tileRows": [[0], [1]],
                    "blockingBitsHex": "00",
                    "rotationBitsHex": "00",
                    "flightBlockingBitsHex": "00",
                    "portalPackets": [],
                }
                result = render_map(folder, doc, folder, padding=0)
                self.assertEqual((0, 0, 255, 255), result.image.getpixel((0, 4)))

    def test_applies_dynamic_tile_collision_and_portal_updates(self):
        def packet(values):
            return {"fields": [{"value": v} for v in values]}

        doc = {
            "columns": 2,
            "rows": 1,
            "header": [0, 2, 1, 4, 4],
            "tileRows": [[0, 0]],
            "blockingBitsHex": "00",
            "rotationBitsHex": "00",
            "flightBlockingBitsHex": "00",
            "portalPackets": [
                packet([0, 1, 7, 1, 0, "Exit"]),
                packet([2, 1, 1, 0, 11]),
                packet([3, 0, 0, 2, 1]),
            ],
        }
        tiles, masks, portals = map_state(doc)
        self.assertEqual([[2, 0]], tiles)
        self.assertEqual([b"\x02", b"\x01", b"\x02"], [bytes(m) for m in masks])
        self.assertEqual([{"id": 7, "x": 1, "y": 0, "name": "Exit"}], portals)
        self.assertEqual([[0, 0]], doc["tileRows"])

    def test_collision_and_portal_overlays_do_not_change_base_image(self):
        from preview_maps import Preview

        base = Image.new("RGBA", (8, 4), (0, 0, 0, 255))
        preview = Preview(
            base,
            [],
            [],
            [[0, 0]],
            [b"\x01", b"\x00", b"\x00"],
            [{"x": 1, "y": 0, "name": "Exit"}],
            0,
            4,
            4,
        )
        rendered = overlay(preview, blocked=True, portals=True)
        self.assertEqual((100, 25, 35, 255), rendered.getpixel((1, 1)))
        self.assertEqual((255, 223, 91, 255), rendered.getpixel((4, 0)))
        self.assertEqual((0, 0, 0, 255), base.getpixel((1, 1)))

    def test_row_major_placement_and_downloaded_image_priority(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            original = folder / "original"
            original.mkdir()
            (folder / "images").mkdir()
            Image.new("RGBA", (2, 2), "red").save(original / "1.png")
            Image.new("RGBA", (2, 2), "green").save(original / "2.png")
            Image.new("RGBA", (2, 2), "blue").save(folder / "images/1.png")
            (folder / "map-ref.bin").write_bytes(references([obj(1), obj(2)]))
            doc = {
                "columns": 2,
                "rows": 1,
                "header": [0, 2, 1, 4, 4],
                "tileRows": [[0, 1]],
                "blockingBitsHex": "01",
                "rotationBitsHex": "00",
                "flightBlockingBitsHex": "00",
                "portalPackets": [],
            }
            result = render_map(folder, doc, original, padding=0)
            self.assertEqual((0, 0, 255, 255), result.image.getpixel((0, 0)))
            self.assertEqual((0, 128, 0, 255), result.image.getpixel((4, 0)))
            self.assertEqual([], result.missing_images)
            self.assertEqual((8, 4), result.image.size)

    def test_offset_reflection_and_animation_cycle_are_bounded(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            image = Image.new("RGBA", (2, 1))
            image.putdata([(255, 0, 0, 255), (0, 0, 255, 255)])
            image.save(folder / "1.png")
            tile = struct.pack(">hbbBhhhB", 0, 1, 1, 1, 1, 0, 0, 1)
            (folder / "map-ref.bin").write_bytes(references([tile]))
            doc = {
                "columns": 1,
                "rows": 1,
                "header": [0, 1, 1, 4, 4],
                "tileRows": [[0]],
                "rotationBitsHex": "00",
                "blockingBitsHex": "00",
                "flightBlockingBitsHex": "00",
                "portalPackets": [],
            }
            result = render_map(folder, doc, folder, padding=4)
            self.assertEqual((0, 0, 255, 255), result.image.getpixel((4, 8)))
            self.assertEqual((255, 0, 0, 255), result.image.getpixel((5, 8)))
            (folder / "map-ref.bin").write_bytes(
                references([struct.pack(">hbbBh", 0x2000, 1, 1, 1, 0)])
            )
            self.assertTrue(render_map(folder, doc, folder).warnings)

    def test_rejects_truncated_reference_offsets(self):
        with self.assertRaises(ValueError):
            parse_references(b"\x00\x02\x00\x01\x00\x00")
        with self.assertRaises(ValueError):
            parse_references(struct.pack(">hhh", 1, 1, 300))


if __name__ == "__main__":
    unittest.main()
