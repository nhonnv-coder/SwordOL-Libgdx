"""Tests for the asset studio model: part-swap and export to the project layout."""

import json
import tempfile
import unittest
from pathlib import Path

from asset_model import SpriteSet, export_project


def sample_doc():
    # Two image slots, one module per slot, two frames, one action playing both frames.
    return {
        "sprite_id": 10000,
        "images": [1001, 1002],
        "modules": [
            {"image": 0, "x": 0, "y": 0, "width": 8, "height": 8},
            {"image": 1, "x": 2, "y": 3, "width": 4, "height": 5},
        ],
        "frames": [
            [{"module": 0, "x": -4, "y": -8, "transform": 0}],
            [{"module": 1, "x": 1, "y": 2, "transform": 2}],
        ],
        "actions": [{"id": 100, "frames": [{"flag": 1, "frame": 0}, {"flag": 1, "frame": 1}]}],
    }


class AssetModelTest(unittest.TestCase):
    def test_export_sprite_flattens_placement_to_image_and_rect(self):
        s = SpriteSet(sample_doc())
        sprite = s.export_sprite(0)
        self.assertEqual(1, len(sprite["parts"]))
        part = sprite["parts"][0]
        self.assertEqual("1001", part["image"])  # slot 0 -> image id
        self.assertEqual([0, 0, 8, 8], part["src"])  # module rect
        self.assertEqual([-4, -8], part["dst"])  # placement offset
        self.assertEqual(0, part["transform"])

    def test_swap_part_changes_every_frame_that_reads_the_slot(self):
        s = SpriteSet(sample_doc())
        s.swap_part(0, 9999)
        self.assertEqual("9999", s.export_sprite(0)["parts"][0]["image"])
        self.assertEqual("1002", s.export_sprite(1)["parts"][0]["image"])  # other slot untouched

    def test_swap_part_rejects_bad_slot(self):
        s = SpriteSet(sample_doc())
        with self.assertRaises(IndexError):
            s.swap_part(5, 1)

    def test_export_animation_indexes_actions_into_used_frames(self):
        s = SpriteSet(sample_doc())
        animation, used = s.export_animation()
        self.assertEqual([0, 1], used)
        self.assertEqual([{"sprite": "10000_0", "ms": 50}, {"sprite": "10000_1", "ms": 50}],
                         animation["frames"])
        self.assertEqual([{"id": 100, "frames": [0, 1]}], animation["actions"])

    def test_export_project_writes_the_layout(self):
        s = SpriteSet(sample_doc())
        with tempfile.TemporaryDirectory() as tmp:
            written = export_project(s, tmp)
            root = Path(tmp)
            self.assertTrue((root / "sprites/data/10000_0.sprite").is_file())
            self.assertTrue((root / "sprites/data/10000_1.sprite").is_file())
            self.assertTrue((root / "animation/data/10000.ani").is_file())
            ani = json.loads((root / "animation/data/10000.ani").read_text("utf-8"))
            self.assertEqual(100, ani["actions"][0]["id"])


if __name__ == "__main__":
    unittest.main()
