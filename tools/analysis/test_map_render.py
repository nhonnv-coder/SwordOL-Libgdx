"""Smoke tests for map summary + tile rendering on the committed demo map."""

import unittest
from pathlib import Path

from PIL import Image

import map_render
from tiled_map import registry_sources, to_tiled

DEMO = "../../assets/source/demo"
MAP = DEMO + "/maps/demo-field.map.json"


class MapRenderTest(unittest.TestCase):
    def test_summary_reports_size_and_counts(self):
        doc = map_render.load_map(MAP)
        text = map_render.summary(doc)
        self.assertIn("20 x 15", text)
        self.assertIn("portals: 1", text)

    def test_render_map_composes_full_grid(self):
        doc = map_render.load_map(MAP)
        sheets = {"tileset.terrain": Image.open(DEMO + "/images/terrain.png").convert("RGBA")}
        image = map_render.render_map(doc, sheets.get)
        self.assertIsNotNone(image)
        self.assertEqual((20 * 32, 15 * 32), image.size)  # fits under max_px, unscaled

    def test_resolver_points_tileset_at_real_png(self):
        doc = map_render.load_map(MAP)
        sources = registry_sources(DEMO)

        def resolver(asset):
            p = sources.get(asset)
            return (p.name, 64, 32) if p else None

        tmj = to_tiled(doc, resolver)
        self.assertEqual("terrain.png", tmj["tilesets"][0]["image"])
        self.assertEqual(64, tmj["tilesets"][0]["imagewidth"])


if __name__ == "__main__":
    unittest.main()
