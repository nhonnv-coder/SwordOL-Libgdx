"""SwordOL Asset Studio — view/part-swap/export sprites, and view/edit maps via Tiled.

A thin GUI over tested logic (`asset_model`, `map_render`, `tiled_map`) and `data_manager`'s PIL
renderer. Two tabs: Sprite (browse sets, play actions, swap a part, export) and Map (browse maps,
render, inspect, round-trip to Tiled for editing). Run with run-asset-studio.ps1.
"""

import argparse
import json
from pathlib import Path

from asset_model import SpriteSet, export_project
from data_manager import render_sprite_frame, search_key
import map_render
import preview_maps
import tiled_map

ROOT = Path(__file__).resolve().parents[2]

# A cohesive dark palette with one cyan accent and an amber highlight.
BG = "#0b0e14"        # app background
PANEL = "#121824"     # cards
PANEL2 = "#1a2231"    # elevated row / hover
LINE = "#263040"      # hairline borders
TEXT = "#eaf2ff"
MUTED = "#7f93b4"
ACCENT = "#5cc8ff"
ACCENT_HI = "#8bd9ff"
AMBER = "#ffb454"
CANVAS = "#0a0d13"
CHECK_A, CHECK_B = "#0d1119", "#111622"  # preview checkerboard
FONT = "Segoe UI"
MONO = "Consolas"


def draw_checker(canvas, size=18):
    """A faint checkerboard so transparent sprites/maps read against it."""
    canvas.delete("bg")
    w, h = max(canvas.winfo_width(), 1), max(canvas.winfo_height(), 1)
    for yi, y in enumerate(range(0, h, size)):
        for xi, x in enumerate(range(0, w, size)):
            canvas.create_rectangle(
                x, y, x + size, y + size, width=0,
                fill=CHECK_A if (xi + yi) % 2 == 0 else CHECK_B, tags="bg")


def discover_sprites(sprites_dir):
    out = []
    for path in sorted(Path(sprites_dir).glob("*.sprite.json")):
        try:
            out.append((int(json.loads(path.read_text("utf-8-sig"))["sprite_id"]), path))
        except Exception:
            continue
    return out


def discover_maps(maps_dir):
    d = Path(maps_dir)
    return sorted(list(d.glob("*.map")) + list(d.glob("*.map.json")))


# ----------------------------------------------------------------------- sprite tab
def build_sprite_tab(tk, ttk, messagebox, ImageTk, parent, sprites_dir, image_roots, project_out):
    entries = discover_sprites(sprites_dir)
    state = {"doc": None, "set": None, "frame": 0, "action": None, "step": 0, "zoom": 2,
             "playing": False, "photo": None}

    parent.columnconfigure(0, minsize=230)
    parent.columnconfigure(1, weight=1)
    parent.columnconfigure(2, minsize=280)
    parent.rowconfigure(0, weight=1)

    left = ttk.Frame(parent, style="Panel.TFrame", padding=10)
    left.grid(row=0, column=0, sticky="nsew", padx=(0, 10), pady=4)
    query = tk.StringVar()
    ttk.Entry(left, textvariable=query).pack(fill="x")
    listbox = tk.Listbox(left, bg=PANEL, fg=TEXT, selectbackground=ACCENT, selectforeground="#05121c",
                         borderwidth=0, highlightthickness=0, activestyle="none")
    listbox.pack(fill="both", expand=True, pady=(8, 0))

    center = ttk.Frame(parent, style="Panel.TFrame", padding=10)
    center.grid(row=0, column=1, sticky="nsew", pady=4)
    canvas = tk.Canvas(center, bg=CANVAS, highlightthickness=1, highlightbackground=LINE)
    canvas.pack(fill="both", expand=True)
    controls = ttk.Frame(center, style="Panel.TFrame", padding=(0, 10, 0, 0))
    controls.pack(fill="x")
    ttk.Label(controls, text="Action", style="Muted.TLabel").pack(side="left")
    action_box = ttk.Combobox(controls, state="readonly", width=10)
    action_box.pack(side="left", padx=6)
    play_var = tk.BooleanVar(value=False)
    ttk.Checkbutton(controls, text="Play", variable=play_var, command=lambda: toggle_play()).pack(side="left", padx=10)
    frame_scale = ttk.Scale(controls, from_=0, to=0, orient="horizontal",
                            command=lambda v: set_frame(int(float(v)), True))
    frame_scale.pack(side="left", fill="x", expand=True, padx=10)
    frame_label = tk.StringVar(value="—")
    ttk.Label(controls, textvariable=frame_label, style="Muted.TLabel", width=12).pack(side="left")

    right = ttk.Frame(parent, style="Panel.TFrame", padding=10)
    right.grid(row=0, column=2, sticky="nsew", padx=(10, 0), pady=4)
    ttk.Label(right, text="PARTS (image slots)", style="Muted.TLabel").pack(anchor="w")
    slots = tk.Listbox(right, bg=PANEL, fg=TEXT, selectbackground=ACCENT, selectforeground="#05121c",
                       borderwidth=0, highlightthickness=0, activestyle="none", height=10)
    slots.pack(fill="x", pady=(4, 8))
    swap = ttk.Frame(right, style="Panel.TFrame")
    swap.pack(fill="x")
    ttk.Label(swap, text="→ image id", style="Muted.TLabel").pack(side="left")
    new_image = tk.StringVar()
    ttk.Entry(swap, textvariable=new_image, width=12).pack(side="left", padx=6)
    ttk.Button(swap, text="Swap", command=lambda: do_swap()).pack(side="left")
    ttk.Button(right, text="Export project", style="Accent.TButton", command=lambda: do_export()).pack(fill="x", pady=(10, 0))
    info = tk.StringVar(value="")
    ttk.Label(right, textvariable=info, style="Muted.TLabel", wraplength=260, justify="left").pack(anchor="w", pady=10)

    def refresh_list(*_):
        key = search_key(query.get())
        listbox.delete(0, "end")
        listbox.visible = [e for e in entries if key in search_key("%d %s" % (e[0], e[1].name))]
        for sid, _ in listbox.visible:
            listbox.insert("end", "  %d" % sid)

    def select_sprite(*_):
        sel = listbox.curselection()
        if not sel:
            return
        _, path = listbox.visible[sel[0]]
        doc = json.loads(path.read_text("utf-8-sig"))
        state["doc"], state["set"], state["step"] = doc, SpriteSet(doc), 0
        ids = state["set"].action_ids()
        action_box["values"] = ids
        state["action"] = ids[0] if ids else None
        if ids:
            action_box.set(ids[0])
        frame_scale.configure(to=max(0, state["set"].frame_count() - 1))
        refresh_slots()
        set_frame(first_frame())

    def first_frame():
        s = state["set"]
        if s is None or state["action"] is None:
            return 0
        f = s.action_frames(state["action"])
        return f[state["step"] % len(f)] if f else 0

    def refresh_slots():
        slots.delete(0, "end")
        for i, image_id in enumerate(state["set"].images):
            slots.insert("end", "  [%2d]  %d" % (i, image_id))

    def set_frame(frame_index, from_scale=False):
        s = state["set"]
        if s is None:
            return
        state["frame"] = max(0, min(frame_index, s.frame_count() - 1))
        if not from_scale:
            frame_scale.set(state["frame"])
        frame_label.set("frame %d / %d" % (state["frame"], s.frame_count() - 1))
        render()

    def render():
        image, meta = render_sprite_frame(state["doc"], state["frame"], images=image_roots)
        canvas.delete("all")
        draw_checker(canvas)
        if image is None:
            canvas.create_text(canvas.winfo_width() // 2, canvas.winfo_height() // 2,
                               text="\n".join(meta.get("warnings", ["(frame rỗng)"])), fill=MUTED)
            return
        zoom = state["zoom"]
        state["photo"] = ImageTk.PhotoImage(image.resize((image.width * zoom, image.height * zoom)))
        left_, top_ = meta.get("origin", [0, 0])
        cx, cy = canvas.winfo_width() // 2, canvas.winfo_height() // 2
        canvas.create_image(cx + left_ * zoom, cy + top_ * zoom, anchor="nw", image=state["photo"])
        info.set("origin %s   %s" % (meta.get("origin"), "; ".join(meta.get("warnings", [])) or "ok"))

    def on_action(*_):
        try:
            state["action"] = int(action_box.get())
        except ValueError:
            state["action"] = None
        state["step"] = 0
        set_frame(first_frame())

    def toggle_play():
        state["playing"] = play_var.get()
        if state["playing"]:
            tick()

    def tick():
        if not state["playing"] or state["set"] is None or state["action"] is None:
            return
        f = state["set"].action_frames(state["action"])
        if f:
            state["step"] = (state["step"] + 1) % len(f)
            set_frame(f[state["step"]])
        parent.after(100, tick)

    def do_swap():
        s = state["set"]
        if s is None:
            return
        sel = slots.curselection()
        if not sel:
            messagebox.showinfo("Swap", "Chọn một slot PARTS trước.")
            return
        try:
            image_id = int(new_image.get())
        except ValueError:
            messagebox.showerror("Swap", "image id phải là số.")
            return
        s.swap_part(sel[0], image_id)
        state["doc"]["images"][sel[0]] = image_id
        refresh_slots()
        slots.selection_set(sel[0])
        render()

    def do_export():
        if state["set"] is None:
            messagebox.showinfo("Export", "Chọn một sprite trước.")
            return
        written = export_project(state["set"], project_out)
        messagebox.showinfo("Export", "Đã ghi %d file vào\n%s" % (len(written), project_out))

    query.trace_add("write", refresh_list)
    listbox.bind("<<ListboxSelect>>", select_sprite)
    action_box.bind("<<ComboboxSelected>>", on_action)
    refresh_list()
    return len(entries)


# -------------------------------------------------------------------------- map tab
def discover_originals(catalog_path):
    """Original decoded maps from the server-maps catalog: (label, folder, entry)."""
    out = []
    try:
        cat = json.loads(Path(catalog_path).read_text("utf-8-sig"))
        cat_dir = Path(catalog_path).parent
        for e in cat.get("maps", []):
            out.append({
                "kind": "orig",
                "label": "★ %s  %dx%d" % (e.get("mapId"), e["columns"], e["rows"]),
                "folder": cat_dir / e["path"], "entry": e,
            })
    except Exception:
        pass
    return out


def build_map_tab(tk, ttk, messagebox, filedialog, ImageTk, parent, maps_dir, project_dir,
                  catalog_path, orig_images):
    cache = {}
    try:
        sources = tiled_map.registry_sources(project_dir)
    except Exception:
        sources = {}
    # Clean .map projects first, then original decoded game maps.
    items = [{"kind": "map", "label": p.name, "path": p} for p in discover_maps(maps_dir)]
    items += discover_originals(catalog_path)
    state = {"item": None, "doc": None, "photo": None}

    def image_for_asset(asset):
        if asset in cache:
            return cache[asset]
        p = sources.get(asset)
        img = None
        if p and Path(p).is_file():
            from PIL import Image
            img = Image.open(p).convert("RGBA")
        cache[asset] = img
        return img

    parent.columnconfigure(0, minsize=230)
    parent.columnconfigure(1, weight=1)
    parent.columnconfigure(2, minsize=280)
    parent.rowconfigure(0, weight=1)

    left = ttk.Frame(parent, style="Panel.TFrame", padding=10)
    left.grid(row=0, column=0, sticky="nsew", padx=(0, 10), pady=4)
    ttk.Label(left, text="MAPS  (★ = gốc)", style="Muted.TLabel").pack(anchor="w")
    listbox = tk.Listbox(left, bg=PANEL, fg=TEXT, selectbackground=ACCENT, selectforeground="#05121c",
                         borderwidth=0, highlightthickness=0, activestyle="none")
    listbox.pack(fill="both", expand=True, pady=(6, 0))
    for it in items:
        listbox.insert("end", "  " + it["label"])

    center = ttk.Frame(parent, style="Panel.TFrame", padding=10)
    center.grid(row=0, column=1, sticky="nsew", pady=4)
    canvas = tk.Canvas(center, bg=CANVAS, highlightthickness=1, highlightbackground=LINE)
    canvas.pack(fill="both", expand=True)

    right = ttk.Frame(parent, style="Panel.TFrame", padding=10)
    right.grid(row=0, column=2, sticky="nsew", padx=(10, 0), pady=4)
    ttk.Label(right, text="MAP", style="Muted.TLabel").pack(anchor="w")
    inspect = tk.StringVar(value="")
    ttk.Label(right, textvariable=inspect, style="Muted.TLabel", justify="left").pack(anchor="w", pady=(4, 10))
    ttk.Button(right, text="Export → Tiled", command=lambda: do_export()).pack(fill="x", pady=2)
    ttk.Button(right, text="Import Tiled →", command=lambda: do_import()).pack(fill="x", pady=2)
    note = tk.StringVar(value="")
    ttk.Label(right, textvariable=note, style="Muted.TLabel", wraplength=260, justify="left").pack(anchor="w", pady=10)

    def draw(image):
        canvas.delete("all")
        draw_checker(canvas)
        cx, cy = canvas.winfo_width() // 2, canvas.winfo_height() // 2
        if image is None:
            canvas.create_text(cx, cy, text="(thiếu ảnh tileset — chỉ xem thuộc tính)", fill=MUTED)
            return
        if max(image.size) > 1100:
            s = 1100 / max(image.size)
            image = image.resize((max(1, int(image.width * s)), max(1, int(image.height * s))))
        state["photo"] = ImageTk.PhotoImage(image)
        canvas.create_image(cx, cy, image=state["photo"])

    def select_map(*_):
        sel = listbox.curselection()
        if not sel:
            return
        it = items[sel[0]]
        state["item"] = it
        if it["kind"] == "map":
            state["doc"] = map_render.load_map(it["path"])
            inspect.set(map_render.summary(state["doc"]))
            draw(map_render.render_map(state["doc"], image_for_asset))
            note.set("")
        else:
            folder = it["folder"]
            doc = json.loads((folder / "map.json").read_text("utf-8-sig"))
            state["doc"] = doc
            e = it["entry"]
            try:
                prev = preview_maps.render_map(folder, doc, orig_images)
                inspect.set("map gốc: %s\n%d x %d\nportals: %d   thiếu ảnh: %d"
                            % (e.get("mapId"), e["columns"], e["rows"], len(prev.portals), len(prev.missing_images)))
                draw(prev.image)
            except Exception as ex:
                inspect.set("map gốc: %s\nkhông render được: %s" % (e.get("mapId"), ex))
                draw(None)
            note.set("Map gốc (raw) — Tiled chỉ áp cho map .map.")

    def do_export():
        it = state["item"]
        if not it or it["kind"] != "map":
            messagebox.showinfo("Tiled", "Chỉ xuất Tiled cho map .map (không phải map gốc raw).")
            return
        out = filedialog.asksaveasfilename(defaultextension=".tmj",
                                           initialfile=Path(it["path"]).stem + ".tmj")
        if not out:
            return
        tiled_map.export_with_images(state["doc"], out, project_dir)
        note.set("Đã xuất Tiled + copy ảnh tileset:\n%s" % out)

    def do_import():
        it = state["item"]
        if not it or it["kind"] != "map":
            messagebox.showinfo("Tiled", "Chỉ nạp Tiled vào map .map.")
            return
        src = filedialog.askopenfilename(filetypes=[("Tiled", "*.tmj *.json")])
        if not src:
            return
        tmj = json.loads(Path(src).read_text("utf-8-sig"))
        doc = tiled_map.from_tiled(tmj)
        doc["id"] = state["doc"].get("id", doc.get("id", ""))  # keep original id
        doc["tilesets"] = state["doc"]["tilesets"]  # keep original tileset asset ids
        Path(it["path"]).write_text(json.dumps(doc, indent=1), "utf-8")
        state["doc"] = doc
        inspect.set(map_render.summary(doc))
        draw(map_render.render_map(doc, image_for_asset))
        note.set("Đã nạp Tiled về %s" % Path(it["path"]).name)

    listbox.bind("<<ListboxSelect>>", select_map)
    return len(items)


def launch(sprites_dir, image_roots, project_out, maps_dir, map_project, catalog_path, orig_images):
    import tkinter as tk
    from tkinter import ttk, messagebox, filedialog
    from PIL import ImageTk

    root = tk.Tk()
    root.title("SwordOL · Asset Studio")
    root.configure(bg=BG)
    root.geometry("1180x720")
    root.minsize(960, 600)

    style = ttk.Style(root)
    style.theme_use("clam")
    style.configure(".", background=BG, foreground=TEXT, fieldbackground=PANEL2, bordercolor=LINE,
                    lightcolor=LINE, darkcolor=LINE, focuscolor=ACCENT)
    style.configure("TFrame", background=BG)
    style.configure("Panel.TFrame", background=PANEL)
    style.configure("TLabel", background=BG, foreground=TEXT, font=(FONT, 10))
    style.configure("Muted.TLabel", background=PANEL, foreground=MUTED, font=(FONT, 9, "bold"))
    style.configure("Head.TLabel", background=BG, foreground=TEXT, font=(FONT, 18, "bold"))
    style.configure("Sub.TLabel", background=BG, foreground=MUTED, font=(FONT, 10))
    style.configure("TButton", background=PANEL2, foreground=TEXT, borderwidth=0, padding=(16, 9),
                    font=(FONT, 10), focuscolor=PANEL2)
    style.map("TButton", background=[("active", LINE)])
    style.configure("Accent.TButton", background=ACCENT, foreground="#04121c", borderwidth=0,
                    padding=(16, 9), font=(FONT, 10, "bold"), focuscolor=ACCENT)
    style.map("Accent.TButton", background=[("active", ACCENT_HI)])
    style.configure("TCombobox", fieldbackground=PANEL2, background=PANEL2, foreground=TEXT,
                    arrowcolor=ACCENT, bordercolor=LINE, padding=5)
    style.map("TCombobox", fieldbackground=[("readonly", PANEL2)], foreground=[("readonly", TEXT)])
    style.configure("TEntry", fieldbackground=PANEL2, foreground=TEXT, bordercolor=LINE,
                    insertcolor=ACCENT, padding=6)
    style.configure("TCheckbutton", background=PANEL, foreground=MUTED, font=(FONT, 9))
    style.map("TCheckbutton", foreground=[("active", TEXT)])
    style.configure("Horizontal.TScale", background=PANEL, troughcolor=PANEL2, bordercolor=LINE)
    style.configure("TNotebook", background=BG, borderwidth=0, tabmargins=(4, 6, 4, 0))
    style.configure("TNotebook.Tab", background=BG, foreground=MUTED, padding=(22, 11),
                    font=(FONT, 10, "bold"), borderwidth=0)
    style.map("TNotebook.Tab", background=[("selected", PANEL)],
              foreground=[("selected", ACCENT), ("active", TEXT)])

    header = ttk.Frame(root, padding=(20, 16, 20, 12))
    header.pack(fill="x")
    mark = tk.Canvas(header, width=14, height=22, bg=BG, highlightthickness=0)
    mark.create_rectangle(0, 2, 5, 20, fill=ACCENT, width=0)
    mark.create_rectangle(7, 6, 12, 20, fill=AMBER, width=0)
    mark.pack(side="left", padx=(0, 12))
    ttk.Label(header, text="Asset Studio", style="Head.TLabel").pack(side="left")
    status = tk.StringVar(value="")
    ttk.Label(header, textvariable=status, style="Sub.TLabel").pack(side="right")
    tk.Frame(root, bg=LINE, height=1).pack(fill="x")  # divider under header
    root.option_add("*Listbox.font", "{%s} 11" % FONT)
    root.option_add("*Listbox.selectBorderWidth", 0)
    root.option_add("*Listbox.activeStyle", "none")

    nb = ttk.Notebook(root)
    nb.pack(fill="both", expand=True, padx=16, pady=14)
    sprite_frame = ttk.Frame(nb, style="TFrame", padding=4)
    map_frame = ttk.Frame(nb, style="TFrame", padding=4)
    nb.add(sprite_frame, text="Sprite / Animation")
    nb.add(map_frame, text="Map")

    n_sprites = build_sprite_tab(tk, ttk, messagebox, ImageTk, sprite_frame, sprites_dir, image_roots, project_out)
    n_maps = build_map_tab(tk, ttk, messagebox, filedialog, ImageTk, map_frame, maps_dir, map_project,
                           catalog_path, orig_images)
    status.set("%d sprite · %d map" % (n_sprites, n_maps))
    root.mainloop()


def main():
    p = argparse.ArgumentParser(description="SwordOL Asset Studio")
    p.add_argument("--sprites", default=str(ROOT / "dev/OL/sprites"))
    p.add_argument("--images", nargs="+", default=[str(ROOT / "dev/legacy-images")])
    p.add_argument("--out", default=str(ROOT / "dev/OL"))
    p.add_argument("--maps", default=str(ROOT / "assets/source/demo/maps"))
    p.add_argument("--map-project", default=str(ROOT / "assets/source/demo"))
    p.add_argument("--catalog", default=str(ROOT / "dev/server-maps/catalog.json"))
    p.add_argument("--orig-images", default=str(ROOT / "dev/legacy-images"))
    a = p.parse_args()
    launch(a.sprites, a.images, a.out, a.maps, a.map_project, a.catalog, a.orig_images)


if __name__ == "__main__":
    main()
