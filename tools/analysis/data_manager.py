"""Read-only typed data manager for captured SwordOL data and decoded visual assets."""

import argparse
import json
from pathlib import Path
import unicodedata
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
IMAGES = ROOT / "dev/legacy-images"
SPRITES = ROOT / "dev/legacy-sprites"
DATA = ROOT / "dev/server-data"
SERVER_SPRITES = ROOT / "dev/server-sprites"


def search_key(text):
    return "".join(
        c
        for c in unicodedata.normalize("NFD", str(text).casefold().replace("đ", "d"))
        if not unicodedata.combining(c)
    )


def read_json(path):
    return json.loads(Path(path).read_text("utf-8-sig"))


def inside(root, path):
    root = Path(root).resolve()
    path = (root / path).resolve()
    if path != root and root not in path.parents:
        raise ValueError("Đường dẫn ngoài thư mục dữ liệu")
    return path


def item_icon(icon_id, images=IMAGES):
    if not isinstance(icon_id, int) or icon_id < 0:
        return None, {"status": "Icon ID không hợp lệ"}
    atlas_id = 4000000 + (icon_id // 100) * 10000 + 2222
    path = Path(images) / ("%d.png" % atlas_id)
    slot = icon_id % 100
    meta = dict(atlasId=atlas_id, slot=slot, cellWidth=22, source=str(path))
    if not path.is_file():
        return None, dict(meta, status="Chưa có atlas ảnh")
    with Image.open(path) as image:
        if slot * 22 + 22 > image.width:
            return None, dict(meta, status="Slot vượt kích thước atlas")
        return image.convert("RGBA").crop((slot * 22, 0, slot * 22 + 22, image.height)), dict(
            meta, status="Đã giải đúng atlas ItemEx"
        )


def npc_sprite_id(look_id):
    family = look_id // 100000
    if family == 3:
        return (look_id % 100000) // 100, "Sprite ID theo MapObject, chưa áp dụng skin/equipment"
    if family == 4:
        return ((look_id % 100000) // 100) * 10000, "Sprite ID theo MapObject"
    if family == 7:
        return look_id, "Sprite tải riêng theo look ID"
    if look_id in (2010000, 1110000):
        return look_id, "Sprite đặc biệt theo MapObject"
    return None, "Cần ghép body/skin/trang bị; chưa xác nhận đầy đủ ánh xạ"


def transformed(image, code):
    operations = {
        0: None,
        1: Image.Transpose.FLIP_TOP_BOTTOM,
        2: Image.Transpose.FLIP_LEFT_RIGHT,
        3: Image.Transpose.ROTATE_180,
        4: Image.Transpose.TRANSPOSE,
        5: Image.Transpose.ROTATE_270,
        6: Image.Transpose.ROTATE_90,
        7: Image.Transpose.TRANSVERSE,
    }
    if code not in operations:
        raise ValueError("MIDP transform ngoài 0..7")
    return image.transpose(operations[code]) if code else image


def render_sprite_frame(doc, frame_index, images=IMAGES):
    warnings = []
    parts = []
    sheets = {}
    frames = doc["frames"]
    modules = doc["modules"]
    ids = doc["images"]
    if not 0 <= frame_index < len(frames):
        return None, {"warnings": ["Frame không tồn tại"], "origin": [0, 0]}
    for placement in frames[frame_index]:
        module_index = placement["module"]
        if not 0 <= module_index < len(modules):
            warnings.append("Thiếu module %s" % module_index)
            continue
        module = modules[module_index]
        slot = module["image"]
        if not 0 <= slot < len(ids):
            warnings.append("Thiếu image slot %s" % slot)
            continue
        image_id = ids[slot]
        roots = images if isinstance(images, (list, tuple)) else [images]
        path = next(
            (
                Path(root) / ("%d.png" % image_id)
                for root in roots
                if (Path(root) / ("%d.png" % image_id)).is_file()
            ),
            None,
        )
        if path is None:
            warnings.append("Thiếu ảnh %s" % image_id)
            continue
        if image_id not in sheets:
            with Image.open(path) as image:
                sheets[image_id] = image.convert("RGBA")
        image = sheets[image_id]
        x, y, w, h = (module[k] for k in ("x", "y", "width", "height"))
        if w <= 0 or h <= 0 or x < 0 or y < 0 or x + w > image.width or y + h > image.height:
            warnings.append("Module %s vượt ảnh" % module_index)
            continue
        part = transformed(image.crop((x, y, x + w, y + h)), placement["transform"])
        parts.append((placement["x"], placement["y"], part))
    if not parts:
        return None, {"warnings": sorted(set(warnings)) or ["Frame rỗng"], "origin": [0, 0]}
    left = min(x for x, y, im in parts)
    top = min(y for x, y, im in parts)
    right = max(x + im.width for x, y, im in parts)
    bottom = max(y + im.height for x, y, im in parts)
    if right - left > 4096 or bottom - top > 4096:
        raise ValueError("Sprite vượt giới hạn preview 4096px")
    output = Image.new("RGBA", (right - left, bottom - top))
    for x, y, image in parts:
        output.alpha_composite(image, (x - left, y - top))
    return output, dict(origin=[left, top], warnings=sorted(set(warnings)), frame=frame_index)


def load_datasets():
    latest = read_json(DATA / "latest.json")
    generation = inside(DATA, latest["generation"])
    npcs = read_json(generation / "npcs.json")
    items = read_json(generation / "items.json")
    images = read_json(IMAGES / "images.json")
    sprites = read_json(SPRITES / "sprites.json")
    from server_sprites import refresh

    recovered, errors = refresh(
        [ROOT / "captures", ROOT / "dist/SwordOL-PC-Capture/captures"], SERVER_SPRITES
    )
    return dict(
        latest=latest,
        npcs=npcs,
        items=items,
        images=images["images"],
        sprites=sprites["sprites"] + recovered,
        serverSprites={item["sprite_id"]: item for item in recovered},
        spriteDiagnostics=errors,
    )


def entry_properties(kind, item, data):
    result = dict(item)
    if kind == "items":
        result["sampleObservations"] = [
            v for v in data["items"]["observations"] if v["typeId"] == item["typeId"]
        ][:8]
        result["detailPackets"] = [
            v for v in data["items"].get("detailPackets", []) if v["typeId"] == item["typeId"]
        ]
        result["typeIdUnsigned32"] = item["typeId"] & 0xFFFFFFFF
        result["sampleLimit"] = 8
    elif kind == "npcs":
        result["sampleObservations"] = [
            v
            for v in data["npcs"]["observations"]
            if (v["lookId"], v["npcType"], v["name"], v["level"])
            == (item["lookId"], item["npcType"], item["name"], item["level"])
        ][:8]
        result["sampleLimit"] = 8
    return result


def launch():
    import tkinter as tk
    from tkinter import ttk, messagebox, filedialog
    from PIL import ImageTk, ImageDraw
    from preview_maps import viewer

    root = tk.Tk()
    root.title("SwordOL · Data Manager")
    root.geometry("1440x920")
    root.minsize(1100, 700)
    style = ttk.Style(root)
    style.theme_use("clam")
    style.configure("TFrame", background="#161d29")
    style.configure("TLabel", background="#161d29", foreground="#e2e8f0")
    style.configure("TCheckbutton", background="#161d29", foreground="#e2e8f0")
    style.configure("TNotebook", background="#10151e")
    style.configure("TNotebook.Tab", padding=(18, 9))
    top = ttk.Frame(root, padding=12)
    top.pack(fill="x")
    ttk.Label(top, text="SWORDOL  /  DATA MANAGER", font=("Segoe UI", 17, "bold")).pack(side="left")
    totals = tk.StringVar()
    ttk.Label(top, textvariable=totals).pack(side="left", padx=20)
    notebook = ttk.Notebook(root)
    notebook.pack(fill="both", expand=True)
    state = {"data": None}

    class AssetPanel:
        def __init__(self, title, kind):
            self.kind = kind
            self.frame = ttk.Frame(notebook)
            notebook.add(self.frame, text=title)
            side = ttk.Frame(self.frame, padding=12)
            side.pack(side="left", fill="y")
            self.query = tk.StringVar()
            ttk.Entry(side, textvariable=self.query, width=34).pack(fill="x")
            self.count = tk.StringVar()
            ttk.Label(side, textvariable=self.count).pack(anchor="w", pady=5)
            listframe = ttk.Frame(side)
            listframe.pack(fill="both", expand=True)
            self.list = tk.Listbox(
                listframe,
                width=37,
                bg="#10151e",
                fg="#e2e8f0",
                selectbackground="#2563eb",
                highlightthickness=0,
                font=("Segoe UI", 10),
                exportselection=False,
            )
            scrollbar = ttk.Scrollbar(listframe, command=self.list.yview)
            self.list.configure(yscrollcommand=scrollbar.set)
            scrollbar.pack(side="right", fill="y")
            self.list.pack(side="left", fill="both", expand=True)
            content = ttk.Frame(self.frame, padding=12)
            content.pack(side="left", fill="both", expand=True)
            self.title = tk.StringVar()
            ttk.Label(content, textvariable=self.title, font=("Segoe UI", 15, "bold")).pack(
                anchor="w"
            )
            self.note = tk.StringVar(value="Chọn dữ liệu để xem")
            ttk.Label(content, textvariable=self.note, wraplength=880).pack(anchor="w", pady=8)
            controls = ttk.Frame(content)
            controls.pack(fill="x")
            self.action = ttk.Combobox(controls, state="readonly", width=18)
            self.action.pack(side="left")
            self.action.bind("<<ComboboxSelected>>", lambda event: self.reset_frame())
            self.frame_number = tk.IntVar()
            self.spin = ttk.Spinbox(
                controls,
                from_=0,
                to=0,
                width=8,
                textvariable=self.frame_number,
                command=self.render,
            )
            self.spin.pack(side="left", padx=6)
            self.spin.bind("<Return>", lambda event: self.render())
            self.play = tk.BooleanVar()
            ttk.Checkbutton(controls, text="Chạy frame (10 FPS)", variable=self.play).pack(
                side="left"
            )
            for label, command in [
                ("−", lambda: self.zoom_by(0.8)),
                ("+", lambda: self.zoom_by(1.25)),
                ("Vừa khung", self.fit),
                ("Lưu PNG", self.save),
            ]:
                ttk.Button(controls, text=label, command=command).pack(side="right", padx=3)
            self.canvas = tk.Canvas(content, bg="#10151e", height=390, highlightthickness=0)
            self.canvas.pack(fill="both", expand=True, pady=8)
            self.details = tk.Text(
                content,
                height=13,
                bg="#10151e",
                fg="#cbd5e1",
                insertbackground="white",
                font=("Consolas", 10),
                wrap="word",
            )
            self.details.pack(fill="x")
            self.details.configure(state="disabled")
            self.entries = []
            self.shown = []
            self.selected = None
            self.image = None
            self.doc = None
            self.origin = None
            self.zoom = 3.0
            self.photo = None
            self.query.trace_add("write", lambda *args: self.filter())
            self.list.bind("<<ListboxSelect>>", self.select)
            self.canvas.bind("<Configure>", lambda event: self.paint())
            self.canvas.bind(
                "<MouseWheel>", lambda event: self.zoom_by(1.25 if event.delta > 0 else 0.8)
            )
            root.after(100, self.tick)

        def label(self, item):
            if self.kind == "npcs":
                return "%s · Lv%s · %s" % (item["name"], item["level"], item["lookId"])
            if self.kind == "items":
                return "%s · %s" % (item["name"], item["typeId"])
            if self.kind == "images":
                return "%s · %s×%s" % (item["image_id"], item["width"], item["height"])
            return "%s · %s frame" % (item["sprite_id"], item["frames"])

        def reload(self):
            data = state["data"]
            self.entries = (
                data["npcs"]["forms"]
                if self.kind == "npcs"
                else data["items"]["types"] if self.kind == "items" else data[self.kind]
            )
            self.filter()
            self.clear()

        def filter(self):
            query = search_key(self.query.get())
            self.shown = [v for v in self.entries if query in search_key(self.label(v))]
            self.list.delete(0, "end")
            for item in self.shown:
                self.list.insert("end", self.label(item))
            self.count.set("%d / %d mục" % (len(self.shown), len(self.entries)))
            self.clear()

        def clear(self):
            self.play.set(False)
            self.image = None
            self.doc = None
            self.selected = None
            self.canvas.delete("all")
            self.action.configure(values=[])
            self.action.set("")
            self.frame_number.set(0)
            self.spin.configure(to=0)
            self.title.set("Chọn dữ liệu để xem")
            self.note.set("")
            self.render_note = ""
            self.details.configure(state="normal")
            self.details.delete("1.0", "end")
            self.details.configure(state="disabled")

        def select(self, event=None):
            selected = self.list.curselection()
            if not selected:
                return
            item = self.shown[selected[0]]
            self.clear()
            self.selected = item
            self.title.set(self.label(item))
            detail = entry_properties(self.kind, item, state["data"])
            self.origin = None
            self.image_roots = IMAGES
            try:
                if self.kind == "items":
                    self.image, meta = item_icon(item["iconId"])
                    self.note.set(meta["status"])
                    detail["render"] = meta
                elif self.kind == "images":
                    path = inside(IMAGES, item["path"])
                    with Image.open(path) as image:
                        self.image = image.convert("RGBA")
                    self.note.set("Ảnh đã giải mã từ tài nguyên gốc · ID %s" % item["image_id"])
                else:
                    if self.kind == "npcs":
                        sprite_id, note = npc_sprite_id(item["lookId"])
                        detail["spriteMapping"] = dict(requestedSpriteId=sprite_id, note=note)
                        path = SPRITES / ("%s.sprite.json" % sprite_id)
                        downloaded = state["data"]["serverSprites"].get(sprite_id)
                        if sprite_id is None:
                            self.note.set(note)
                            path = None
                        elif downloaded:
                            path = inside(SERVER_SPRITES, downloaded["path"])
                            self.image_roots = (path.parent / "images", IMAGES)
                            self.note.set(
                                "Sprite từ capture server · Các phần ảnh chưa đủ sẽ được báo riêng"
                            )
                            detail["spriteMapping"]["serverSource"] = downloaded
                        elif not path.is_file():
                            # readSpriteEx's exact original fallback. Never label it as a recovered skin.
                            path = SPRITES / "1000000.sprite.json"
                            detail["spriteMapping"]["fallbackSpriteId"] = 1000000
                            self.note.set(
                                "CHƯA CÓ SPRITE %s · đang xem sprite dự phòng 1000000 của JAR, không phải hình đã xác nhận cho NPC này"
                                % sprite_id
                            )
                        else:
                            self.note.set(note)
                    else:
                        path = inside(
                            SERVER_SPRITES if item.get("server") else SPRITES, item["path"]
                        )
                        if item.get("server"):
                            self.image_roots = (path.parent / "images", IMAGES)
                        self.note.set(
                            ("Sprite server" if item.get("server") else "Sprite gốc")
                            + " · Frame flags không được diễn giải thành thời lượng"
                        )
                    if path is not None and path.is_file():
                        self.render_note = self.note.get()
                        self.doc = read_json(path)
                        actions = self.doc["actions"]
                        self.action.configure(
                            values=["Action %s" % a["id"] for a in actions] or ["Toàn bộ frame"]
                        )
                        self.action.current(0)
                        self.reset_frame()
                self.details.configure(state="normal")
                self.details.delete("1.0", "end")
                self.details.insert("1.0", json.dumps(detail, ensure_ascii=False, indent=2))
                self.details.configure(state="disabled")
                self.fit()
            except (OSError, ValueError, KeyError, IndexError) as error:
                self.image = None
                self.canvas.delete("all")
                self.note.set("Không render được: " + str(error))

        def sequence(self):
            if not self.doc:
                return []
            actions = self.doc["actions"]
            index = self.action.current()
            return (
                [v["frame"] for v in actions[index]["frames"]]
                if actions and 0 <= index < len(actions)
                else list(range(len(self.doc["frames"])))
            )

        def reset_frame(self):
            self.frame_number.set(0)
            self.spin.configure(to=max(0, len(self.sequence()) - 1))
            self.render()

        def render(self):
            if not self.doc:
                return
            try:
                sequence = self.sequence()
                position = self.frame_number.get()
                if not sequence or not 0 <= position < len(sequence):
                    raise ValueError("Frame ngoài action đã chọn")
                self.image, meta = render_sprite_frame(
                    self.doc, sequence[position], self.image_roots
                )
                self.origin = meta["origin"]
                self.note.set(
                    self.render_note
                    + (
                        " · Cảnh báo: " + "; ".join(meta["warnings"][:6])
                        if meta["warnings"]
                        else ""
                    )
                )
                self.paint()
            except (ValueError, IndexError, KeyError, tk.TclError) as error:
                self.play.set(False)
                self.image = None
                self.canvas.delete("all")
                self.note.set(str(error))

        def tick(self):
            if self.play.get() and self.frame.winfo_viewable() and self.doc:
                try:
                    size = len(self.sequence())
                    self.frame_number.set((self.frame_number.get() + 1) % size if size else 0)
                    self.render()
                except tk.TclError:
                    self.play.set(False)
            root.after(100, self.tick)

        def fit(self):
            if self.image:
                self.zoom = min(
                    8,
                    max(
                        0.05,
                        min(
                            (self.canvas.winfo_width() - 60) / self.image.width,
                            (self.canvas.winfo_height() - 60) / self.image.height,
                        ),
                    ),
                )
            self.paint()

        def zoom_by(self, factor):
            self.zoom = max(0.05, min(32, self.zoom * factor))
            self.paint()

        def paint(self):
            self.canvas.delete("all")
            if self.image is None:
                return
            w = max(1, self.canvas.winfo_width())
            h = max(1, self.canvas.winfo_height())
            z = self.zoom
            if self.origin is None:
                ox = (w - self.image.width * z) / 2
                oy = (h - self.image.height * z) / 2
            else:
                ox = w / 2 + self.origin[0] * z
                oy = h * 0.78 + self.origin[1] * z
            view = Image.new("RGBA", (w, h), "#171f2d")
            draw = ImageDraw.Draw(view)
            for y in range(0, h, 24):
                for x in range(0, w, 24):
                    if (x // 24 + y // 24) % 2:
                        draw.rectangle((x, y, x + 23, y + 23), fill="#1d2838")
            rendered = self.image.transform(
                (w, h),
                Image.Transform.AFFINE,
                (1 / z, 0, -ox / z, 0, 1 / z, -oy / z),
                resample=Image.Resampling.NEAREST,
            )
            view.alpha_composite(rendered)
            self.photo = ImageTk.PhotoImage(view)
            self.canvas.create_image(0, 0, image=self.photo, anchor="nw")
            self.canvas.create_text(
                10,
                10,
                anchor="nw",
                text="%d × %d px · %.0f%%" % (self.image.width, self.image.height, z * 100),
                fill="#a6b6ce",
            )

        def save(self):
            if self.image is None:
                return
            path = filedialog.asksaveasfilename(
                defaultextension=".png", filetypes=[("PNG", "*.png")], title="Lưu ảnh render"
            )
            if path:
                try:
                    self.image.save(path)
                except OSError as error:
                    messagebox.showerror("Lưu ảnh", str(error))

    map_tab = tk.Frame(notebook, bg="#161d29")
    notebook.add(map_tab, text="Map")
    map_reload = viewer(ROOT / "dev/server-maps/catalog.json", IMAGES, parent=map_tab)
    panels = [
        AssetPanel("Quái / NPC", "npcs"),
        AssetPanel("Item", "items"),
        AssetPanel("Ảnh", "images"),
        AssetPanel("Sprite / Animation", "sprites"),
    ]

    def refresh():
        try:
            state["data"] = load_datasets()
            count = state["data"]["latest"]["counts"]
            totals.set(
                "%d map   ·   %d dạng NPC   ·   %d loại item"
                % (count["maps"], count["npcForms"], count["itemTypes"])
            )
            for panel in panels:
                panel.reload()
            map_reload()
        except (OSError, ValueError, KeyError) as error:
            messagebox.showerror("Nạp dữ liệu", str(error))

    ttk.Button(top, text="Nạp lại báo cáo", command=refresh).pack(side="right")
    refresh()
    root.mainloop()


if __name__ == "__main__":
    argparse.ArgumentParser(description=__doc__).parse_args()
    launch()
