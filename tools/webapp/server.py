"""Local web app for managing SwordOL assets — a stdlib HTTP server over the tested Python logic.

No third-party deps. Serves a single-page frontend and a JSON/PNG API that reads the decoded
project (maps incl. originals, sprites, per-map NPC/monster observations) and writes names + Tiled
and animation exports. Bind to localhost only; this is a dev tool. Run with run-web-studio.ps1.
"""

import io
import json
import re
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/analysis"))

import map_render  # noqa: E402
import preview_maps  # noqa: E402
import tiled_map  # noqa: E402
from asset_model import SpriteSet, export_project  # noqa: E402
from asset_studio import discover_originals, discover_sprites  # noqa: E402
from data_manager import render_sprite_frame  # noqa: E402
from server_sprites import refresh as refresh_server_sprites  # noqa: E402

STATIC = Path(__file__).resolve().parent / "static"
NAMES_FILE = ROOT / "dev/OL/names.json"
OL_SPRITES = ROOT / "dev/OL/sprites"
IMAGE_ROOTS = [str(ROOT / "dev/legacy-images")]
DEMO_PROJECT = ROOT / "assets/source/demo"  # only for resolving .map tileset images
CATALOG = ROOT / "dev/server-maps/catalog.json"
SERVER_SPRITES = ROOT / "dev/server-sprites"  # sprites recovered from capture; kept apart from JAR ones
CAPTURE_ROOTS = [ROOT / "captures", ROOT / "dist/SwordOL-PC-Capture/captures"]


import unicodedata

# trailing "<n>Tuyến" is the channel, not the map name. Normalize so composed/decomposed ế both match.
_CHANNEL = re.compile(unicodedata.normalize("NFC", r"\s*\d*Tuyến\s*$"))


def clean_map_name(name):
    name = unicodedata.normalize("NFC", name or "")
    return _CHANNEL.sub("", name).strip() or name


def load_names():
    try:
        return json.loads(NAMES_FILE.read_text("utf-8-sig"))
    except Exception:
        return {}


def save_names(names):
    NAMES_FILE.parent.mkdir(parents=True, exist_ok=True)
    NAMES_FILE.write_text(json.dumps(names, ensure_ascii=False, indent=1), "utf-8")


def load_observations():
    """NPC/monster observations grouped by mapId, plus forms, from the latest collection."""
    by_map = {}
    try:
        gen = ROOT / "dev/server-data" / json.loads((ROOT / "dev/server-data/latest.json").read_text("utf-8-sig"))["generation"]
        npcs = json.loads((gen / "npcs.json").read_text("utf-8-sig"))
        for ob in npcs.get("observations", []):
            by_map.setdefault(int(ob["mapId"]), []).append(ob)
    except Exception:
        pass
    return by_map


class Index:
    def __init__(self):
        self.maps = discover_originals(CATALOG)  # original game maps only (demo maps hidden)
        self.sprites = discover_sprites(OL_SPRITES)
        recovered, _ = refresh_server_sprites(CAPTURE_ROOTS, SERVER_SPRITES)
        self.sprites += [(e["sprite_id"], SERVER_SPRITES / e["path"]) for e in recovered]
        self.by_id = {sid: path for sid, path in self.sprites}
        self.obs = load_observations()

    def map_list(self, names):
        out = []
        for i, m in enumerate(self.maps):
            if m["kind"] == "map":
                doc = map_render.load_map(m["path"])
                mid, w, h = doc["id"], doc["width"], doc["height"]
            else:
                e = m["entry"]
                mid, w, h = str(e.get("mapId")), e["columns"], e["rows"]
            obs = self.obs.get(_int(mid), [])
            out.append({
                "idx": i, "kind": m["kind"], "mapId": mid,
                "name": names.get("map:" + mid, clean_map_name(m.get("entry", {}).get("name")) or mid),
                "w": w, "h": h,
                "npc": sum(1 for o in obs if o.get("npcType") == "npc"),
                "monster": sum(1 for o in obs if o.get("npcType") != "npc"),
            })
        return out

    def map_detail(self, idx, names):
        m = self.maps[idx]
        if m["kind"] == "map":
            doc = map_render.load_map(m["path"])
            mid = doc["id"]
            portals, spawns = doc.get("portals", []), doc.get("spawns", [])
        else:
            e = m["entry"]
            doc = json.loads((m["folder"] / "map.json").read_text("utf-8-sig"))
            mid = str(e.get("mapId"))
            portals = doc.get("portalPackets", doc.get("portals", []))
            spawns = []
        obs = self.obs.get(_int(mid), [])
        return {
            "idx": idx, "kind": m["kind"], "mapId": mid,
            "name": names.get("map:" + mid, clean_map_name(m.get("entry", {}).get("name")) or mid),
            "size": [doc.get("width", m.get("entry", {}).get("columns")), doc.get("height", m.get("entry", {}).get("rows"))],
            "portals": portals, "spawns": spawns,
            "npcs": [o for o in obs if o.get("npcType") == "npc"],
            "monsters": [o for o in obs if o.get("npcType") != "npc"],
        }

    def map_png(self, idx):
        m = self.maps[idx]
        if m["kind"] == "map":
            doc = map_render.load_map(m["path"])
            return map_render.render_map(doc, self._image_for_asset())
        doc = json.loads((m["folder"] / "map.json").read_text("utf-8-sig"))
        return preview_maps.render_map(m["folder"], doc, IMAGE_ROOTS[0]).image

    def _image_for_asset(self):
        sources = {}
        try:
            sources = tiled_map.registry_sources(DEMO_PROJECT)
        except Exception:
            pass
        cache = {}

        def get(asset):
            if asset not in cache:
                from PIL import Image
                p = sources.get(asset)
                cache[asset] = Image.open(p).convert("RGBA") if p and Path(p).is_file() else None
            return cache[asset]

        return get

    def sprite_list(self, names):
        out = []
        for sid, path in self.sprites:
            doc = json.loads(path.read_text("utf-8-sig"))
            s = SpriteSet(doc)
            out.append({"id": sid, "name": names.get("sprite:%d" % sid, str(sid)),
                        "server": SERVER_SPRITES in path.parents,
                        "frames": s.frame_count(), "actions": s.action_ids(),
                        "actionFrames": {str(a): s.action_frames(a) for a in s.action_ids()}})
        return out

    def sprite_doc(self, sid):
        path = self.by_id.get(sid)
        return json.loads(path.read_text("utf-8-sig")) if path else None

    def image_roots(self, sid):
        """A recovered sprite carries its own image pack; fall back to the decoded JAR images."""
        path = self.by_id.get(sid)
        if path and SERVER_SPRITES in path.parents:
            return [str(path.parent / "images")] + IMAGE_ROOTS
        return IMAGE_ROOTS


def _int(v):
    try:
        return int(v)
    except (TypeError, ValueError):
        return None


def png_bytes(image):
    buf = io.BytesIO()
    (image if image is not None else _blank()).save(buf, "PNG")
    return buf.getvalue()


def _blank():
    from PIL import Image
    return Image.new("RGBA", (2, 2))


INDEX = None


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def _send(self, code, body, ctype="application/json"):
        if isinstance(body, (dict, list)):
            body = json.dumps(body, ensure_ascii=False).encode("utf-8")
        elif isinstance(body, str):
            body = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = urlparse(self.path).path
        try:
            if path == "/" or path == "/index.html":
                return self._send(200, (STATIC / "index.html").read_text("utf-8"), "text/html; charset=utf-8")
            if path.startswith("/static/"):
                f = (STATIC / path[len("/static/"):]).resolve()
                if STATIC.resolve() in f.parents and f.is_file():
                    ctype = {"css": "text/css", "js": "text/javascript"}.get(f.suffix[1:], "text/plain")
                    return self._send(200, f.read_text("utf-8"), ctype + "; charset=utf-8")
                return self._send(404, {"error": "not found"})
            if path == "/api/maps":
                return self._send(200, INDEX.map_list(load_names()))
            if path.startswith("/api/map/") and path.endswith(".png"):
                return self._send(200, png_bytes(INDEX.map_png(int(path[9:-4]))), "image/png")
            if path.startswith("/api/map/"):
                return self._send(200, INDEX.map_detail(int(path[9:]), load_names()))
            if path == "/api/sprites":
                return self._send(200, INDEX.sprite_list(load_names()))
            if path.startswith("/api/sprite/") and path.endswith(".png"):
                sid, frame = (int(x) for x in path[len("/api/sprite/"):-4].split("/"))
                image, _ = render_sprite_frame(INDEX.sprite_doc(sid), frame, images=INDEX.image_roots(sid))
                return self._send(200, png_bytes(image), "image/png")
            return self._send(404, {"error": "unknown route"})
        except Exception as ex:
            return self._send(500, {"error": str(ex)})

    def do_POST(self):
        path = urlparse(self.path).path
        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(length) or b"{}") if length else {}
        try:
            if path == "/api/reload":
                global INDEX
                INDEX = Index()
                return self._send(200, {"ok": True, "maps": len(INDEX.maps), "sprites": len(INDEX.sprites)})
            if path == "/api/names":
                names = load_names()
                names[body["id"]] = body["name"]
                save_names(names)
                return self._send(200, {"ok": True})
            if path.startswith("/api/export/ani/"):
                sid = int(path[len("/api/export/ani/"):])
                written = export_project(SpriteSet(INDEX.sprite_doc(sid)), str(ROOT / "dev/OL"))
                return self._send(200, {"ok": True, "written": [str(p) for p in written]})
            return self._send(404, {"error": "unknown route"})
        except Exception as ex:
            return self._send(500, {"error": str(ex)})


def main(port=8777):
    global INDEX
    INDEX = Index()
    server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print("SwordOL Web Studio on http://127.0.0.1:%d  (%d maps, %d sprites)"
          % (port, len(INDEX.maps), len(INDEX.sprites)))
    server.serve_forever()


if __name__ == "__main__":
    main(int(sys.argv[1]) if len(sys.argv) > 1 else 8777)
