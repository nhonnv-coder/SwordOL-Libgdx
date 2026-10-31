"""Round-trip tests: `.map` -> Tiled -> `.map` must preserve everything that matters."""

import json
import unittest

from tiled_map import to_tiled, from_tiled, _gid_to_tiled, _gid_from_tiled, MAP_H, MAP_D


def sample_map():
    w, h = 3, 2
    return {
        "schemaVersion": 1, "id": "world.test", "orientation": "orthogonal",
        "width": w, "height": h, "tileWidth": 32, "tileHeight": 32,
        "layers": [{"name": "ground", "tiles": [1, 2, 3, 4, 5, (6 | MAP_H | MAP_D)]}],
        "collision": [0, 1, 0, 0, 0, 1],
        "portals": [{"id": "p1", "x": 2, "y": 0, "targetMap": "world.other", "targetX": 1, "targetY": 1}],
        "spawns": [{"id": "s1", "kind": "monster", "template": "slime", "x": 0, "y": 1, "hp": 30}],
        "tilesets": [{"name": "terrain", "asset": "tileset.terrain", "columns": 2, "tileCount": 4}],
    }


class TiledMapTest(unittest.TestCase):
    def test_gid_flip_bits_round_trip(self):
        for gid in (1, 1 | MAP_H, 1 | MAP_D, 7 | MAP_H | MAP_D):
            self.assertEqual(gid, _gid_from_tiled(_gid_to_tiled(gid)))

    def test_tiled_is_top_down_and_flagged(self):
        tmj = to_tiled(sample_map())
        self.assertEqual("map", tmj["type"])
        ground = next(l for l in tmj["layers"] if l["name"] == "ground")
        # row 0 of .map (bottom) becomes the LAST row of Tiled data
        self.assertEqual([4, 5], ground["data"][:2])  # top row = .map row 1 = tiles[3:5]
        self.assertEqual(0x80000000, ground["data"][2] & 0x80000000)  # flagged tile, H moved to bit 31

    def test_full_round_trip_preserves_everything(self):
        src = sample_map()
        back = from_tiled(to_tiled(src))
        for key in ("width", "height", "tileWidth", "tileHeight", "id", "layers",
                    "collision", "portals", "spawns", "tilesets"):
            self.assertEqual(src[key], back[key], key)

    def test_round_trip_a_real_committed_map(self):
        from pathlib import Path
        src = json.loads(Path("../../assets/source/demo/maps/demo-field.map.json").read_text("utf-8-sig"))
        back = from_tiled(to_tiled(src))
        self.assertEqual(src["layers"], back["layers"])
        self.assertEqual(src["collision"], back["collision"])
        self.assertEqual(src["portals"], back["portals"])
        self.assertEqual(src["spawns"], back["spawns"])


if __name__ == "__main__":
    unittest.main()
