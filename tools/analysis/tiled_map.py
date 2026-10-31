"""Round-trip the project's `.map` document to Tiled JSON (`.tmj`) so maps are edited in Tiled.

The two formats are close (orthogonal, gid + flip bits, columns/tilecount) but differ in field
names, in where the flip bits sit, and in the Y origin — this module translates all three and
carries collision, portals and spawns as editable Tiled layers. Pure functions, round-trip tested.

    .map                      Tiled .tmj
    tileWidth/Height          tilewidth/tileheight
    layers[].tiles (flat)     tilelayers data (flat), rows flipped top<->bottom
    flip bits 28/29/30 (HVD)  31/30/29
    collision (flat 0/1)      tilelayer "collision", gid 1 = blocked
    portals / spawns          objectgroup with per-object properties
"""

import argparse
import json
import math
from pathlib import Path

MASK = 0x0FFFFFFF  # tile id; both formats clear the top 4 bits
MAP_H, MAP_V, MAP_D = 1 << 28, 1 << 29, 1 << 30
TMX_H, TMX_V, TMX_D = 0x80000000, 0x40000000, 0x20000000
COLLISION = "collision"


def _gid_to_tiled(gid):
    out = gid & MASK
    if gid & MAP_H:
        out |= TMX_H
    if gid & MAP_V:
        out |= TMX_V
    if gid & MAP_D:
        out |= TMX_D
    return out


def _gid_from_tiled(gid):
    out = gid & MASK
    if gid & TMX_H:
        out |= MAP_H
    if gid & TMX_V:
        out |= MAP_V
    if gid & TMX_D:
        out |= MAP_D
    return out


def _flip_rows(flat, w, h):
    """Swap the Y origin: `.map` stores row 0 at the bottom, Tiled at the top (self-inverse)."""
    rows = [flat[y * w : (y + 1) * w] for y in range(h)]
    return [cell for row in reversed(rows) for cell in row]


def _props(pairs):
    return [{"name": k, "type": "string", "value": "" if v is None else str(v)} for k, v in pairs]


def _prop_map(obj):
    return {p["name"]: p["value"] for p in obj.get("properties", [])}


def to_tiled(doc, resolver=None):
    """`resolver(asset_id) -> (image_name, width, height)` lets the tileset point at the real png
    with true dimensions; without it the image name is `<asset>.png` and dims are computed."""
    w, h, tw, th = doc["width"], doc["height"], doc["tileWidth"], doc["tileHeight"]
    tilesets = []
    firstgid = 1
    for ts in doc["tilesets"]:
        rows = max(1, math.ceil(ts["tileCount"] / ts["columns"]))
        resolved = resolver(ts["asset"]) if resolver else None
        image, iw, ih = resolved if resolved else (ts["asset"] + ".png", ts["columns"] * tw, rows * th)
        tilesets.append(
            {
                "firstgid": firstgid,
                "name": ts["name"],
                "image": image,
                "imagewidth": iw,
                "imageheight": ih,
                "tilewidth": tw,
                "tileheight": th,
                "columns": ts["columns"],
                "tilecount": ts["tileCount"],
            }
        )
        firstgid += ts["tileCount"]
    collision_firstgid = firstgid

    layers = []
    lid = 1
    for layer in doc["layers"]:
        layers.append(
            {
                "type": "tilelayer", "id": lid, "name": layer["name"],
                "width": w, "height": h, "visible": True, "opacity": 1,
                "data": [_gid_to_tiled(g) for g in _flip_rows(layer["tiles"], w, h)],
            }
        )
        lid += 1
    # collision as its own tile layer (gid = collision tileset's firstgid where blocked)
    layers.append(
        {
            "type": "tilelayer", "id": lid, "name": COLLISION,
            "width": w, "height": h, "visible": True, "opacity": 0.4,
            "data": [collision_firstgid if c else 0 for c in _flip_rows(doc["collision"], w, h)],
        }
    )
    lid += 1
    tilesets.append(
        {"firstgid": collision_firstgid, "name": COLLISION, "image": COLLISION + ".png",
         "imagewidth": tw, "imageheight": th, "tilewidth": tw, "tileheight": th,
         "columns": 1, "tilecount": 1}
    )

    def point(cx, cy):  # cell -> Tiled pixel (centre), Y top-down
        return (cx + 0.5) * tw, (h - 1 - cy + 0.5) * th

    for name, items, fields in (
        ("portals", doc["portals"], ("id", "targetMap", "targetX", "targetY")),
        ("spawns", doc["spawns"], ("id", "kind", "template", "hp")),
    ):
        objects = []
        for i, it in enumerate(items, 1):
            px, py = point(it["x"], it["y"])
            objects.append(
                {"id": i, "name": str(it.get("id", "")), "x": px, "y": py, "width": 0, "height": 0,
                 "point": True, "properties": _props([(f, it.get(f)) for f in fields])}
            )
        layers.append({"type": "objectgroup", "id": lid, "name": name, "visible": True,
                       "opacity": 1, "objects": objects})
        lid += 1

    return {
        "type": "map", "orientation": "orthogonal", "renderorder": "right-down", "infinite": False,
        "width": w, "height": h, "tilewidth": tw, "tileheight": th, "nextlayerid": lid,
        "nextobjectid": 1, "tilesets": tilesets, "layers": layers,
        "properties": _props([("id", doc["id"]), ("schemaVersion", doc.get("schemaVersion", 1))]),
    }


def from_tiled(tmj):
    w, h, tw, th = tmj["width"], tmj["height"], tmj["tilewidth"], tmj["tileheight"]
    props = _prop_map(tmj)
    layers, collision = [], [0] * (w * h)
    portals, spawns = [], []
    for layer in tmj["layers"]:
        if layer["type"] == "tilelayer":
            data = _flip_rows(layer["data"], w, h)
            if layer["name"] == COLLISION:
                collision = [1 if g else 0 for g in data]
            else:
                layers.append({"name": layer["name"], "tiles": [_gid_from_tiled(g) for g in data]})
        elif layer["type"] == "objectgroup":
            for obj in layer["objects"]:
                p = _prop_map(obj)
                cx = int(round(obj["x"] / tw - 0.5))
                cy = h - 1 - int(round(obj["y"] / th - 0.5))
                if layer["name"] == "portals":
                    portals.append({"id": p.get("id", obj.get("name", "")), "x": cx, "y": cy,
                                    "targetMap": p.get("targetMap", ""),
                                    "targetX": int(p.get("targetX", 0)), "targetY": int(p.get("targetY", 0))})
                elif layer["name"] == "spawns":
                    spawns.append({"id": p.get("id", obj.get("name", "")), "kind": p.get("kind", ""),
                                   "template": p.get("template", ""), "x": cx, "y": cy,
                                   "hp": int(p.get("hp", 0))})
    return {
        "schemaVersion": int(props.get("schemaVersion", 1)), "id": props.get("id", ""),
        "orientation": "orthogonal", "width": w, "height": h, "tileWidth": tw, "tileHeight": th,
        "layers": layers, "collision": collision, "portals": portals, "spawns": spawns,
        "tilesets": _tilesets_from(tmj, tw),
    }


def _tilesets_from(tmj, tw):
    out = []
    for ts in tmj["tilesets"]:
        if ts["name"] == COLLISION:
            continue
        out.append({"name": ts["name"], "asset": ts["image"][:-4] if ts["image"].endswith(".png") else ts["image"],
                    "columns": ts["columns"], "tileCount": ts["tilecount"]})
    return out


def registry_sources(project_dir):
    """Asset id -> source file path, from a project's registry.json."""
    reg = json.loads((Path(project_dir) / "registry.json").read_text("utf-8-sig"))
    return {a["id"]: Path(project_dir) / a["source"] for a in reg["assets"] if a.get("source")}


def _png_dims(path):
    from PIL import Image

    with Image.open(path) as im:
        return im.width, im.height


def export_with_images(doc, dst, project_dir):
    """Export to Tiled and copy each tileset png beside `dst` with its real dimensions."""
    sources = registry_sources(project_dir)
    import shutil

    def resolver(asset):
        p = sources.get(asset)
        if p and p.is_file():
            w, h = _png_dims(p)
            return p.name, w, h
        return None

    tmj = to_tiled(doc, resolver)
    dst = Path(dst)
    for ts in doc["tilesets"]:
        p = sources.get(ts["asset"])
        if p and p.is_file():
            shutil.copy(p, dst.parent / p.name)
    dst.write_text(json.dumps(tmj, indent=1), "utf-8")
    return tmj


def main():
    ap = argparse.ArgumentParser(description=".map <-> Tiled .tmj")
    ap.add_argument("mode", choices=["export", "import"])
    ap.add_argument("src")
    ap.add_argument("dst")
    ap.add_argument("--project", help="project dir with registry.json: resolve + copy tileset pngs")
    a = ap.parse_args()
    doc = json.loads(Path(a.src).read_text("utf-8-sig"))
    if a.mode == "export" and a.project:
        export_with_images(doc, a.dst, a.project)
    else:
        out = to_tiled(doc) if a.mode == "export" else from_tiled(doc)
        Path(a.dst).write_text(json.dumps(out, indent=1), "utf-8")
    print("wrote", a.dst)


if __name__ == "__main__":
    main()
