"""Generate the demo map documents of the sample asset project.

These maps are project content, not recovered original world data. The output is written in the
same canonical form the editor writes (keys sorted, no whitespace), so re-saving a map in the
editor without editing it produces identical bytes. Run from the repository root:

    python scripts/make-demo-maps.py assets/source/demo/maps
"""

import json
import os
import sys

FLIP_H = 1 << 28
FLIP_V = 1 << 29
FLIP_D = 1 << 30
GRASS, PATH, WATER, WALL = 1, 2, 3, 4
TILESET = {"asset": "tileset.terrain", "columns": 2, "name": "terrain", "tileCount": 4}


def empty(width, height, value=0):
    return [value] * (width * height)


def put(cells, width, x, y, value):
    cells[y * width + x] = value


def field():
    width, height = 20, 15
    ground = empty(width, height, GRASS)
    decor = empty(width, height)
    blocked = empty(width, height)
    for x in range(width):
        put(ground, width, x, 1, PATH)
    # A pond, kept away from the cells the server tests walk through.
    for y in range(9, 13):
        for x in range(13, 17):
            put(ground, width, x, y, WATER)
            put(blocked, width, x, y, 1)
    # An interior wall, drawn on the decor layer with flips so both are exercised.
    for x in range(6, 9):
        put(decor, width, x, 5, WALL | (FLIP_H if x == 7 else 0) | (FLIP_D if x == 8 else 0))
        put(blocked, width, x, 5, 1)
    put(decor, width, 19, 7, PATH | FLIP_V)
    return {
        "collision": blocked,
        "height": height,
        "id": "world.demo-field",
        "layers": [
            {"name": "ground", "tiles": ground},
            {"name": "decor", "tiles": decor},
        ],
        "orientation": "orthogonal",
        "portals": [
            {
                "id": "to-town",
                "targetMap": "world.town",
                "targetX": 1,
                "targetY": 1,
                "x": 19,
                "y": 7,
            }
        ],
        "schemaVersion": 1,
        "spawns": [
            {
                "hp": 100,
                "id": "player-start",
                "kind": "player-start",
                "template": "hero",
                "x": 1,
                "y": 1,
            },
            {"hp": 30, "id": "slime-1", "kind": "monster", "template": "slime", "x": 3, "y": 1},
        ],
        "tileHeight": 32,
        "tileWidth": 32,
        "tilesets": [TILESET],
        "width": width,
    }


def town():
    width, height = 10, 8
    ground = empty(width, height, PATH)
    blocked = empty(width, height)
    for x in range(width):
        put(ground, width, x, height - 1, WALL)
        put(blocked, width, x, height - 1, 1)
    return {
        "collision": blocked,
        "height": height,
        "id": "world.town",
        "layers": [{"name": "ground", "tiles": ground}],
        "orientation": "orthogonal",
        "portals": [
            {
                "id": "to-field",
                "targetMap": "world.demo-field",
                "targetX": 18,
                "targetY": 7,
                "x": 0,
                "y": 1,
            }
        ],
        "schemaVersion": 1,
        "spawns": [
            {
                "hp": 100,
                "id": "player-start",
                "kind": "player-start",
                "template": "hero",
                "x": 1,
                "y": 1,
            }
        ],
        "tileHeight": 32,
        "tileWidth": 32,
        "tilesets": [TILESET],
        "width": width,
    }


def write(directory, name, document):
    path = os.path.join(directory, name)
    data = json.dumps(document, sort_keys=True, separators=(",", ":")).encode("utf-8")
    with open(path, "wb") as output:
        output.write(data)
    print("wrote %s (%d bytes)" % (path, len(data)))


def main(directory):
    if not os.path.isdir(directory):
        os.makedirs(directory)
    write(directory, "demo-field.map.json", field())
    write(directory, "town.map.json", town())


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: make-demo-maps.py OUTPUT_DIRECTORY")
    main(sys.argv[1])
