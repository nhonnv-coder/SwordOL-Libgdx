"""Editable model + export for the asset studio.

Reads a decoded sprite document (the combined `.dat`: images + modules + frames + actions, as
decode_sprites.py writes it) and exposes it in the project's naming:

    sprite    = one frame, a still composed from parts
    animation = a sequence of sprites grouped into actions

Part-swap is a slot remap on the image table, exactly what the original's ISpriteImageInit does.
Export writes the project layout (sprites/data/*.sprite, animation/data/*.ani). Pure logic, no GUI,
so it is unit-tested without a display.
"""

import json
from pathlib import Path

FRAME_MS = 50  # the original advances an action frame on a fixed 50 ms tick (Look.FRAME_MILLIS)


def load_doc(path):
    return json.loads(Path(path).read_text("utf-8-sig"))


class SpriteSet:
    """One decoded `.dat`: shared image table + modules, its frames (sprites) and actions."""

    def __init__(self, doc):
        self.sprite_id = int(doc["sprite_id"])
        self.images = [int(i) for i in doc["images"]]
        self.modules = list(doc["modules"])
        self.frames = list(doc["frames"])
        self.actions = list(doc["actions"])

    def frame_count(self):
        return len(self.frames)

    def action_ids(self):
        return [int(a["id"]) for a in self.actions]

    def action_frames(self, action_id):
        """The frame indexes one action plays, in order."""
        for a in self.actions:
            if int(a["id"]) == action_id:
                return [int(e["frame"]) for e in a["frames"]]
        raise KeyError("Sprite %d has no action %d" % (self.sprite_id, action_id))

    def swap_part(self, slot, image_id):
        """Point an image-table slot at a different image id — the original's part swap.

        Every frame placement whose module reads this slot now draws the new image, so one swap
        changes the part (hair/body/weapon…) across the whole set.
        """
        if not 0 <= slot < len(self.images):
            raise IndexError("Image slot %d outside 0..%d" % (slot, len(self.images) - 1))
        if not isinstance(image_id, int) or image_id < 0:
            raise ValueError("Image id must be a non-negative int")
        self.images[slot] = image_id

    def export_sprite(self, frame_index):
        """One frame as a `.sprite`: each placement flattened to a part naming its image + rect."""
        if not 0 <= frame_index < len(self.frames):
            raise IndexError("Frame %d outside 0..%d" % (frame_index, len(self.frames) - 1))
        parts = []
        for placement in self.frames[frame_index]:
            module = self.modules[int(placement["module"])]
            image_id = self.images[int(module["image"])]
            parts.append(
                {
                    "image": str(image_id),
                    "src": [int(module["x"]), int(module["y"]), int(module["width"]), int(module["height"])],
                    "dst": [int(placement["x"]), int(placement["y"])],
                    "transform": int(placement["transform"]),
                }
            )
        return {"parts": parts}

    def export_animation(self):
        """The whole set as one `.ani`: the frames it uses + actions indexing into them.

        Frame refs are sprite ids `<spriteId>_<frame>`; the caller writes a matching `.sprite` for
        each. east/west mirroring stays a render rule (action id = action*100 + dir), not stored.
        """
        used = sorted({fi for a in self.actions for fi in (int(e["frame"]) for e in a["frames"])})
        index_of = {fi: i for i, fi in enumerate(used)}
        frames = [{"sprite": "%d_%d" % (self.sprite_id, fi), "ms": FRAME_MS} for fi in used]
        actions = [
            {"id": int(a["id"]), "frames": [index_of[int(e["frame"])] for e in a["frames"]]}
            for a in self.actions
        ]
        return {"frames": frames, "actions": actions}, used


def export_project(sprite_set, project_root):
    """Write one sprite set to the project layout. Returns the files written."""
    root = Path(project_root)
    sprites_dir = root / "sprites" / "data"
    anim_dir = root / "animation" / "data"
    sprites_dir.mkdir(parents=True, exist_ok=True)
    anim_dir.mkdir(parents=True, exist_ok=True)

    animation, used = sprite_set.export_animation()
    written = []
    for frame_index in used:
        path = sprites_dir / ("%d_%d.sprite" % (sprite_set.sprite_id, frame_index))
        path.write_text(json.dumps(sprite_set.export_sprite(frame_index), indent=1), "utf-8")
        written.append(path)
    anim_path = anim_dir / ("%d.ani" % sprite_set.sprite_id)
    anim_path.write_text(json.dumps(animation, indent=1), "utf-8")
    written.append(anim_path)
    return written
