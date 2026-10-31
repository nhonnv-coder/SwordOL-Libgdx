"""Read-only desktop preview of extracted server maps. Requires existing Pillow + Tkinter.

Static first-frame reconstruction based on ImagePointer.drawTileObj and MapEx tile indexing.
No network access, characters or NPC simulation. Captured assets are never modified.
"""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import struct
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]


def parse_references(data):
    if len(data) < 4:
        raise ValueError("Reference header is truncated")
    count, image_count = struct.unpack_from(">hh", data)
    base = 4 + 2 * count
    if not 0 <= count <= 32767 or image_count < 0 or base > len(data):
        raise ValueError("Invalid reference count")
    records = []
    for i in range(count):
        offset = base + struct.unpack_from(">h", data, 4 + i * 2)[0]
        if offset < base or offset + 5 > len(data):
            raise ValueError("Reference offset outside buffer")
        flags, offx, offy, size = struct.unpack_from(">hbbB", data, offset)
        width = 2 if flags & 0x2000 else 3 if flags & 0x4000 else 7
        if offset + 5 + size * width > len(data):
            raise ValueError("Reference components are truncated")
        pieces = []
        for n in range(size):
            pos = offset + 5 + n * width
            if width == 2:
                pieces.append(struct.unpack_from(">h", data, pos)[0])
            elif width == 3:
                image_id, transform = struct.unpack_from(">hB", data, pos)
                pieces.append((image_id, 0, 0, transform))
            else:
                pieces.append(struct.unpack_from(">hhhB", data, pos))
        records.append(dict(flags=flags & 65535, offx=offx, offy=offy, pieces=pieces))
    return records


def bit(bits, index):
    return bool(bits[index // 8] & (1 << (index % 8)))


def map_state(doc):
    cols, rows = doc["columns"], doc["rows"]
    if not 0 < cols < 128 or not 0 < rows < 128:
        raise ValueError("Map dimensions are invalid")
    tiles = [list(row) for row in doc["tileRows"]]
    if len(tiles) != rows or any(len(row) != cols for row in tiles):
        raise ValueError("Map tile dimensions do not match")
    masks = [
        bytearray.fromhex(doc[key])
        for key in ("blockingBitsHex", "rotationBitsHex", "flightBlockingBitsHex")
    ]
    if any(len(mask) != (cols * rows + 7) // 8 for mask in masks):
        raise ValueError("Invalid map bitset size")
    portals = []

    def setbit(mask, index, value):
        if value:
            mask[index // 8] |= 1 << (index % 8)
        else:
            mask[index // 8] &= ~(1 << (index % 8))

    for packet in doc.get("portalPackets", []):
        values = [field.get("value") for field in packet["fields"]]
        if not values:
            continue
        if values[0] == 0 and len(values) >= 2:
            count = values[1]
            if count < 0 or len(values) != 2 + 4 * count:
                raise ValueError("Invalid portal list")
            portals = [
                dict(
                    id=values[2 + i * 4],
                    x=values[3 + i * 4],
                    y=values[4 + i * 4],
                    name=values[5 + i * 4],
                )
                for i in range(count)
            ]
        elif values[0] == 2 and len(values) >= 2:
            if values[1] < 0 or len(values) != 2 + 3 * values[1]:
                raise ValueError("Invalid collision update")
            for i in range(values[1]):
                x, y, sign = values[2 + i * 3 : 5 + i * 3]
                if 0 <= x < cols and 0 <= y < rows:
                    setbit(masks[0], y * cols + x, sign % 10 != 0)
                    setbit(masks[2], y * cols + x, sign // 10 != 0)
        elif values[0] == 3 and len(values) == 5:
            _, x, y, tile, turn = values
            if 0 <= x < cols and 0 <= y < rows:
                tiles[y][x] = tile & 255
                if turn in (0, 1):
                    setbit(masks[1], y * cols + x, turn)
    return tiles, masks, portals


@dataclass
class Preview:
    image: Image.Image
    missing_images: list
    warnings: list
    tiles: list
    masks: list
    portals: list
    padding: int
    cell_width: int
    cell_height: int
    render_layers: tuple = ()


def render_map(folder, doc, original_images, padding=192):
    folder, original_images = Path(folder), Path(original_images)
    refs = parse_references((folder / "map-ref.bin").read_bytes())
    tiles, masks, portals = map_state(doc)
    cw, ch = doc["header"][3:5]
    if not 0 < cw < 128 or not 0 < ch < 128 or not 0 <= padding <= 512:
        raise ValueError("Invalid cell dimensions/padding")
    if (doc["columns"] * cw + padding * 2) * (doc["rows"] * ch + padding * 2) > 32 * 1024 * 1024:
        raise ValueError("Preview exceeds 32 megapixel limit")
    canvas = Image.new(
        "RGBA", (doc["columns"] * cw + padding * 2, doc["rows"] * ch + padding * 2), "#17202a"
    )
    cache, missing, warnings = {}, set(), set()

    def load(image_id):
        if image_id not in cache:
            path = folder / "images" / ("%d.png" % image_id)
            if not path.is_file():
                path = original_images / ("%d.png" % image_id)
            if not path.is_file():
                cache[image_id] = None
                missing.add(image_id)
            else:
                try:
                    with Image.open(path) as image:
                        cache[image_id] = image.convert("RGBA")
                except (OSError, ValueError):
                    cache[image_id] = None
                    missing.add(image_id)
        return cache[image_id]

    def draw_tile(index, x, y, turn, trail):
        if index in trail or len(trail) > 32:
            warnings.add("Chu kỳ tham chiếu animation: %d" % index)
            return
        if not 0 <= index < len(refs):
            warnings.add("Tile không có định nghĩa: %d" % index)
            return
        tile = refs[index]
        if tile["flags"] & 0x2000:
            if tile["pieces"]:
                draw_tile(tile["pieces"][0], x, y, turn, trail + (index,))
            return
        for image_id, ox, oy, transform in tile["pieces"]:
            image = load(image_id)
            if image is None:
                continue
            transform ^= turn
            if not tile["flags"] & 0x4000:
                if transform & 32:
                    oy = -oy - image.height
                if transform & 1:
                    ox = -ox - image.width
            native = {1: 2, 32: 1, 33: 3}.get(transform, transform)
            operation = {
                1: Image.Transpose.FLIP_TOP_BOTTOM,
                2: Image.Transpose.FLIP_LEFT_RIGHT,
                3: Image.Transpose.ROTATE_180,
                4: Image.Transpose.TRANSPOSE,
                5: Image.Transpose.ROTATE_270,
                6: Image.Transpose.ROTATE_90,
                7: Image.Transpose.TRANSVERSE,
            }.get(native)
            if native and operation is None:
                warnings.add("Transform chưa hỗ trợ: %d" % transform)
                continue
            if operation is not None:
                image = image.transpose(operation)
            canvas.alpha_composite(image, (x + ox, y + oy))

    # MapEx paints immediate 0x4000 tiles, then its 0x0800 buffer queue,
    # then m_DrawObjVec. Entity.compareMapPos sorts each queue by (y,x).
    queues = [[], [], []]
    for y, row in enumerate(tiles):
        for x, index in enumerate(row):
            if index == 255:
                continue
            if not 0 <= index < len(refs):
                warnings.add("Tile không có định nghĩa: %d" % index)
                continue
            flags = refs[index]["flags"]
            obj = flags & 0x4000 and not flags & 0x2000
            px = padding + x * cw + (0 if obj else cw // 2)
            py = padding + y * ch + (0 if obj else ch)
            layer = 0 if obj else 1 if flags & 0x0800 else 2
            queues[layer].append((index, px, py, int(bit(masks[1], y * doc["columns"] + x))))
    background = canvas
    rendered_layers = []
    for queue in queues:
        canvas = Image.new("RGBA", background.size)
        for index, px, py, turn in queue:
            draw_tile(index, px, py, turn, ())
        rendered_layers.append(canvas)
        background = Image.alpha_composite(background, canvas)
    return Preview(
        background,
        sorted(missing),
        sorted(warnings),
        tiles,
        masks,
        portals,
        padding,
        cw,
        ch,
        tuple(rendered_layers),
    )


def overlay(preview, grid=False, blocked=False, portals=True, visible_layers=(True, True, True)):
    base = preview.image
    if preview.render_layers and not all(visible_layers):
        base = Image.new("RGBA", preview.image.size, "#17202a")
        for visible, rendered in zip(visible_layers, preview.render_layers):
            if visible:
                base = Image.alpha_composite(base, rendered)
    layer = Image.new("RGBA", preview.image.size)
    draw = ImageDraw.Draw(layer)
    p = preview.padding
    cw = preview.cell_width
    ch = preview.cell_height
    rows = len(preview.tiles)
    cols = len(preview.tiles[0])
    if blocked:
        for y in range(rows):
            for x in range(cols):
                if bit(preview.masks[0], y * cols + x):
                    draw.rectangle(
                        (p + x * cw, p + y * ch, p + (x + 1) * cw - 1, p + (y + 1) * ch - 1),
                        fill=(255, 65, 90, 100),
                    )
    if grid:
        for x in range(cols + 1):
            draw.line((p + x * cw, p, p + x * cw, p + rows * ch), fill=(215, 230, 245, 70))
        for y in range(rows + 1):
            draw.line((p, p + y * ch, p + cols * cw, p + y * ch), fill=(215, 230, 245, 70))
    if portals:
        for portal in preview.portals:
            x = p + portal["x"] * cw
            y = p + portal["y"] * ch
            draw.rectangle((x, y, x + cw, y + ch), outline="#ffdf5b", width=3)
    return Image.alpha_composite(base, layer)


def viewer(catalog_path, original_images, parent=None):
    import tkinter as tk
    from tkinter import ttk, filedialog, messagebox
    from PIL import ImageTk

    own_window = parent is None
    root = tk.Tk() if own_window else parent
    if own_window:
        root.title("SwordOL · Map Preview")
        root.geometry("1280x840")
        root.minsize(900, 600)
        root.configure(bg="#10151e")
    style = ttk.Style(root)
    style.theme_use("clam")
    style.configure("TFrame", background="#161d29")
    style.configure("TLabel", background="#161d29", foreground="#e2e8f0")
    style.configure("TCheckbutton", background="#161d29", foreground="#e2e8f0")
    left = ttk.Frame(root, padding=16)
    left.pack(side="left", fill="y")
    ttk.Label(left, text="SWORDOL", font=("Segoe UI", 19, "bold")).pack(anchor="w")
    ttk.Label(left, text="MAP PREVIEW · dữ liệu đã thu").pack(anchor="w", pady=(0, 18))
    search = tk.StringVar()
    ttk.Entry(left, textvariable=search, width=31).pack(fill="x")
    listing = tk.Listbox(
        left,
        width=34,
        bg="#10151e",
        fg="#e2e8f0",
        selectbackground="#2563eb",
        font=("Segoe UI", 10),
        borderwidth=0,
        highlightthickness=0,
        exportselection=False,
    )
    listing.pack(fill="both", expand=True, pady=12)
    right = ttk.Frame(root)
    right.pack(side="left", fill="both", expand=True)
    toolbar = ttk.Frame(right, padding=10)
    toolbar.pack(fill="x")
    grid = tk.BooleanVar()
    blocked = tk.BooleanVar()
    portals = tk.BooleanVar(value=True)
    visible = [tk.BooleanVar(value=True) for _ in range(3)]
    title = tk.StringVar(value="Chọn map bên trái")
    status = tk.StringVar(value="Cuộn để zoom · kéo chuột để di chuyển")
    ttk.Label(right, textvariable=title, font=("Segoe UI", 15, "bold"), padding=12).pack(fill="x")
    canvas = tk.Canvas(right, bg="#0c111a", highlightthickness=0)
    canvas.pack(fill="both", expand=True)
    ttk.Label(right, textvariable=status, padding=10).pack(fill="x")
    hover = tk.StringVar(value="")
    ttk.Label(right, textvariable=hover, padding=(10, 0, 10, 8)).pack(fill="x")
    state = {
        "entries": [],
        "shown": [],
        "preview": None,
        "image": None,
        "zoom": 1.0,
        "photo": None,
        "offset": [0.0, 0.0],
    }

    def repaint():
        if state["image"] is None:
            return
        w = max(1, canvas.winfo_width())
        h = max(1, canvas.winfo_height())
        z = state["zoom"]
        ox, oy = state["offset"]
        # Render just the viewport, so large zoom levels do not allocate a giant resized map.
        view = state["image"].transform(
            (w, h),
            Image.Transform.AFFINE,
            (1 / z, 0, -ox / z, 0, 1 / z, -oy / z),
            resample=Image.Resampling.NEAREST,
            fillcolor="#0c111a",
        )
        state["photo"] = ImageTk.PhotoImage(view)
        canvas.delete("all")
        canvas.create_image(0, 0, image=state["photo"], anchor="nw")

    def fit():
        if state["image"] is None:
            return
        w, h = state["image"].size
        state["zoom"] = min(canvas.winfo_width() / w, canvas.winfo_height() / h) * 0.95
        state["offset"] = [
            (canvas.winfo_width() - w * state["zoom"]) / 2,
            (canvas.winfo_height() - h * state["zoom"]) / 2,
        ]
        repaint()

    def layers():
        if state["preview"] is not None:
            state["image"] = overlay(
                state["preview"],
                grid.get(),
                blocked.get(),
                portals.get(),
                tuple(v.get() for v in visible),
            )
            repaint()

    def choose(event=None):
        selection = listing.curselection()
        if not selection:
            return
        item = state["shown"][selection[0]]
        try:
            folder = (catalog_path.parent / item["path"]).resolve()
            if catalog_path.parent.resolve() not in folder.parents:
                raise ValueError("Invalid map path")
            doc = json.loads((folder / "map.json").read_text("utf-8"))
            preview = render_map(folder, doc, original_images)
            state["preview"] = preview
            layers()
            fit()
            title.set(
                "%s   ·   %s   ·   %d × %d ô"
                % (item["name"], item["mapId"], doc["columns"], doc["rows"])
            )
            detail = "Ảnh tĩnh, khung animation đầu · Không gồm NPC/nhân vật"
            if preview.missing_images:
                detail += " · Thiếu %d ảnh: %s" % (
                    len(preview.missing_images),
                    str(preview.missing_images[:12]),
                )
            if preview.warnings:
                detail += " · %d cảnh báo" % len(preview.warnings)
            status.set(detail)
        except (OSError, ValueError, KeyError, IndexError, struct.error) as error:
            state["preview"] = None
            state["image"] = None
            canvas.delete("all")
            title.set("Không đọc được map")
            messagebox.showerror("Preview map", str(error))

    def filter_list(*args):
        text = search.get().casefold()
        state["shown"] = [
            e for e in state["entries"] if text in (e["name"] + " " + str(e["mapId"])).casefold()
        ]
        listing.delete(0, "end")
        for item in state["shown"]:
            listing.insert("end", "%s  ·  %s" % (item["name"], item["mapId"]))

    def reload():
        try:
            state["entries"] = json.loads(catalog_path.read_text("utf-8"))["maps"]
            filter_list()
        except (OSError, ValueError, KeyError) as error:
            messagebox.showerror("Danh mục map", str(error))

    def save():
        if state["image"] is None:
            return
        path = filedialog.asksaveasfilename(
            title="Lưu ảnh preview", defaultextension=".png", filetypes=[("PNG", "*.png")]
        )
        if path:
            try:
                state["image"].save(path)
            except OSError as error:
                messagebox.showerror("Lưu PNG", str(error))

    def wheel(event):
        if state["image"] is None:
            return
        before = state["zoom"]
        after = max(0.08, min(8, before * (1.2 if event.delta > 0 else 1 / 1.2)))
        ox, oy = state["offset"]
        state["offset"] = [
            event.x - (event.x - ox) * after / before,
            event.y - (event.y - oy) * after / before,
        ]
        state["zoom"] = after
        repaint()

    def start_drag(event):
        state["drag"] = (event.x, event.y, *state["offset"])

    def drag(event):
        if "drag" in state:
            x, y, ox, oy = state["drag"]
            state["offset"] = [ox + event.x - x, oy + event.y - y]
            repaint()

    def inspect_cell(event):
        preview = state["preview"]
        if preview is None:
            return
        ox, oy = state["offset"]
        z = state["zoom"]
        x = int(((event.x - ox) / z - preview.padding) // preview.cell_width)
        y = int(((event.y - oy) / z - preview.padding) // preview.cell_height)
        rows = len(preview.tiles)
        cols = len(preview.tiles[0])
        if 0 <= x < cols and 0 <= y < rows:
            names = [str(p["name"]) for p in preview.portals if p["x"] == x and p["y"] == y]
            hover.set(
                "Ô (%d, %d) · Tile %d · %s%s"
                % (
                    x,
                    y,
                    preview.tiles[y][x],
                    "Bị chặn" if bit(preview.masks[0], y * cols + x) else "Đi được",
                    " · Portal: " + ", ".join(names) if names else "",
                )
            )
        else:
            hover.set("Ngoài vùng ô map")

    for text, var in [("Lưới", grid), ("Cản đường", blocked), ("Portal", portals)]:
        ttk.Checkbutton(toolbar, text=text, variable=var, command=layers).pack(side="left", padx=5)
    ttk.Label(left, text="LỚP HIỂN THỊ").pack(anchor="w", pady=(12, 4))
    for text, var in zip(("Nền", "Trang trí thấp", "Vật thể phía trước"), visible):
        ttk.Checkbutton(left, text=text, variable=var, command=layers).pack(anchor="w", pady=2)
    ttk.Button(toolbar, text="Vừa cửa sổ", command=fit).pack(side="left", padx=8)
    ttk.Button(toolbar, text="Lưu PNG", command=save).pack(side="right")
    ttk.Button(left, text="Nạp lại danh mục", command=reload).pack(fill="x", pady=4)
    ttk.Label(left, text="Đỏ: cản đường\nVàng: portal\nCuộn: zoom · Kéo: di chuyển").pack(
        anchor="w", pady=12
    )
    listing.bind("<<ListboxSelect>>", choose)
    search.trace_add("write", filter_list)
    canvas.bind("<MouseWheel>", wheel)
    canvas.bind("<ButtonPress-1>", start_drag)
    canvas.bind("<B1-Motion>", drag)
    canvas.bind("<Motion>", inspect_cell)
    canvas.bind("<Configure>", lambda event: repaint())
    reload()

    def initial():
        if state["shown"]:
            index = next((i for i, e in enumerate(state["shown"]) if e["mapId"] == 1101400), 0)
            listing.selection_set(index)
            listing.see(index)
            choose()

    root.after(200, initial)
    if own_window:
        root.mainloop()
    return reload


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--catalog", type=Path, default=ROOT / "dev/server-maps/catalog.json"
    )
    parser.add_argument("--images", type=Path, default=ROOT / "dev/legacy-images")
    parser.add_argument("--map", type=int)
    parser.add_argument("--export", type=Path, help="Render PNG without opening the desktop window")
    args = parser.parse_args()
    if args.export:
        catalog = json.loads(args.catalog.read_text("utf-8"))["maps"]
        entry = next((e for e in catalog if e["mapId"] == args.map), None)
        if entry is None:
            raise ValueError("Map ID not found in catalog")
        folder = args.catalog.parent / entry["path"]
        doc = json.loads((folder / "map.json").read_text("utf-8"))
        preview = render_map(folder, doc, args.images)
        args.export.parent.mkdir(parents=True, exist_ok=True)
        preview.image.save(args.export)
        print(
            json.dumps(
                {
                    "mapId": args.map,
                    "png": str(args.export),
                    "size": preview.image.size,
                    "missingImages": preview.missing_images,
                    "warnings": preview.warnings,
                },
                ensure_ascii=True,
            )
        )
    else:
        viewer(args.catalog.resolve(), args.images.resolve())


if __name__ == "__main__":
    main()
