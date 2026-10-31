"""Load, summarise and render a `.map` document — shared by the Asset Studio map tab.

Rendering draws each layer's tiles from the tileset sheets (`image_for_asset` resolves an asset id
to a PIL sheet). The `.map` grid is bottom-left row major; this flips Y to the screen's top-left.
"""

import json
from pathlib import Path

from PIL import Image

MASK = 0x0FFFFFFF
MAP_H, MAP_V, MAP_D = 1 << 28, 1 << 29, 1 << 30


def load_map(path):
    return json.loads(Path(path).read_text("utf-8-sig"))


def summary(doc):
    lines = [
        "id: %s" % doc.get("id", ""),
        "size: %d x %d  tile %d x %d" % (doc["width"], doc["height"], doc["tileWidth"], doc["tileHeight"]),
        "layers: " + ", ".join(l["name"] for l in doc["layers"]),
        "tilesets: " + ", ".join("%s(%d)" % (t["name"], t["tileCount"]) for t in doc["tilesets"]),
        "portals: %d   spawns: %d" % (len(doc.get("portals", [])), len(doc.get("spawns", []))),
        "blocked cells: %d" % sum(1 for c in doc.get("collision", []) if c),
    ]
    return "\n".join(lines)


def _apply_flips(tile, gid):
    if gid & MAP_D:
        tile = tile.transpose(Image.Transpose.TRANSPOSE)
    if gid & MAP_H:
        tile = tile.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    if gid & MAP_V:
        tile = tile.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
    return tile


def render_map(doc, image_for_asset, max_px=1200):
    """Compose every layer into one RGBA image, or None if nothing drew."""
    w, h, tw, th = doc["width"], doc["height"], doc["tileWidth"], doc["tileHeight"]
    ranges = []
    first = 1
    for ts in doc["tilesets"]:
        sheet = image_for_asset(ts["asset"])
        ranges.append((first, first + ts["tileCount"] - 1, ts["columns"], sheet))
        first += ts["tileCount"]
    canvas = Image.new("RGBA", (w * tw, h * th))
    drew = False
    for layer in doc["layers"]:
        for i, gid in enumerate(layer["tiles"]):
            if not gid:
                continue
            tid = gid & MASK
            for lo, hi, cols, sheet in ranges:
                if lo <= tid <= hi and sheet is not None:
                    local = tid - lo
                    sx, sy = (local % cols) * tw, (local // cols) * th
                    if sx + tw > sheet.width or sy + th > sheet.height:
                        break
                    tile = _apply_flips(sheet.crop((sx, sy, sx + tw, sy + th)).convert("RGBA"), gid)
                    cx, cy = i % w, i // w  # bottom-left row major
                    canvas.alpha_composite(tile, (cx * tw, (h - 1 - cy) * th))
                    drew = True
                    break
    if not drew:
        return None
    if canvas.width > max_px or canvas.height > max_px:
        scale = min(max_px / canvas.width, max_px / canvas.height)
        canvas = canvas.resize((max(1, int(canvas.width * scale)), max(1, int(canvas.height * scale))))
    return canvas
