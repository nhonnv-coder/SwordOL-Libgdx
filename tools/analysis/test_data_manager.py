import json
import tempfile
import unittest
from pathlib import Path
from PIL import Image

from data_manager import item_icon, render_sprite_frame, npc_sprite_id, search_key, entry_properties


class DataManagerTest(unittest.TestCase):
    def test_item_properties_keep_detail_only_packets_and_numeric_observation_count(self):
        item = {"typeId": 17, "iconId": 1, "name": "Unknown", "observations": 0}
        detail = {
            "typeId": 17,
            "iconId": 2,
            "displayFields": ["Description"],
            "source": {"opcode": 1032, "offset": 22},
        }
        data = {"items": {"observations": [], "detailPackets": [detail, {"typeId": 18}]}}
        result = entry_properties("items", item, data)
        self.assertEqual([detail], result["detailPackets"])
        self.assertEqual(0, result["observations"])
        self.assertEqual([], result["sampleObservations"])

    def test_slices_item_atlas_horizontally_using_icon_not_type_id(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            atlas = Image.new("RGBA", (44, 22), "red")
            atlas.paste(Image.new("RGBA", (22, 22), "blue"), (22, 0))
            atlas.save(root / "4012222.png")
            image, meta = item_icon(101, root)
            self.assertEqual((22, 22), image.size)
            self.assertEqual((0, 0, 255, 255), image.getpixel((0, 0)))
            self.assertEqual(4012222, meta["atlasId"])
            self.assertIsNone(item_icon(102, root)[0])

    def test_composes_transformed_sprite_modules_with_negative_offsets(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            sheet = Image.new("RGBA", (2, 1))
            sheet.putdata([(255, 0, 0, 255), (0, 0, 255, 255)])
            sheet.save(root / "9.png")
            doc = {
                "images": [9],
                "modules": [{"image": 0, "x": 0, "y": 0, "width": 2, "height": 1}],
                "frames": [[{"module": 0, "x": -2, "y": -1, "transform": 2}]],
                "actions": [],
            }
            image, meta = render_sprite_frame(doc, 0, root)
            self.assertEqual((0, 0, 255, 255), image.getpixel((0, 0)))
            self.assertEqual((255, 0, 0, 255), image.getpixel((1, 0)))
            self.assertEqual([-2, -1], meta["origin"])
            doc["frames"][0][0]["module"] = 99
            self.assertTrue(render_sprite_frame(doc, 0, root)[1]["warnings"])

    def test_npc_mapping_is_source_based_and_search_handles_vietnamese(self):
        self.assertEqual(1010000, npc_sprite_id(410100)[0])
        self.assertEqual(100, npc_sprite_id(310000)[0])
        self.assertIsNone(npc_sprite_id(110000)[0])
        self.assertEqual("dai ly", search_key("Đại Lý"))


if __name__ == "__main__":
    unittest.main()
