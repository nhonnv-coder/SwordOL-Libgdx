"""Extract the original assets and build bundles from them, one at a time or in groups.

Extraction reuses the decoders in this directory; bundling always goes through the Java asset
pipeline CLI, so a bundle this tool writes is byte for byte the bundle the build writes. Nothing
here re-implements the bundle format, and no asset file is modified.
"""

import argparse
import json
import subprocess
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAR = ROOT / "asset-pipeline/target/swordol-assets.jar"
ORIGINAL_JAR = ROOT / "docs/files/VoLamHoiTu.jar"
EXPORT = ROOT / "dev/assets"
DECODED = ROOT / "dev"
PROJECT = ROOT / "dev/OL"
GROUPINGS = ("asset", "type", "prefix")


def search_key(text):
    """Accent and case insensitive key, so 'tuyen' finds 'Tuyến'."""
    folded = str(text).casefold().replace("đ", "d")
    return "".join(c for c in unicodedata.normalize("NFD", folded) if not unicodedata.combining(c))


def extraction_steps(jar=ORIGINAL_JAR, export=EXPORT, decoded=DECODED, project=PROJECT):
    """The documented decode chain, as (label, argv, output) steps in the order they must run."""
    python = sys.executable
    here = Path(__file__).resolve().parent
    steps = [
        (
            "Xuất tài nguyên từ JAR",
            [python, str(here / "export_assets.py"), str(jar), str(export)],
            export,
        ),
        (
            "Giải mã ảnh .p",
            [python, str(here / "decode_images.py"), str(export), str(decoded / "legacy-images")],
            decoded / "legacy-images",
        ),
        (
            "Giải mã sprite .dat",
            [python, str(here / "decode_sprites.py"), str(export), str(decoded / "legacy-sprites")],
            decoded / "legacy-sprites",
        ),
        (
            "Giải mã giao diện .ui",
            [python, str(here / "decode_ui.py"), str(export), str(decoded / "legacy-ui")],
            decoded / "legacy-ui",
        ),
        (
            "Giải mã cấu hình .o",
            [python, str(here / "decode_config.py"), str(export), str(decoded / "legacy-config")],
            decoded / "legacy-config",
        ),
        (
            "Gom thành asset project",
            [
                python,
                str(here / "build_legacy_project.py"),
                str(export),
                str(decoded),
                str(project),
            ],
            project,
        ),
    ]
    return steps


def pending_steps(steps):
    """Steps whose output is missing. The decoders refuse to overwrite, so finished ones are kept."""
    return [step for step in steps if not step[2].exists()]


def parse_list(text):
    """Reads the `list` output of the asset CLI: id, type, decoded size and dependency ids."""
    rows = []
    for line in str(text).splitlines():
        if not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) < 3:
            raise ValueError("Dòng danh sách tài nguyên không hợp lệ: %r" % line[:80])
        dependencies = [item for item in (parts[3].split(",") if len(parts) > 3 else []) if item]
        rows.append(
            {
                "id": parts[0],
                "type": parts[1],
                "size": int(parts[2]),
                "dependencies": dependencies,
            }
        )
    return rows


def filter_rows(rows, query="", types=()):
    """Case and accent insensitive id search, optionally limited to some types."""
    key = search_key(query).strip()
    chosen = set(types or ())
    result = []
    for row in rows:
        if chosen and row["type"] not in chosen:
            continue
        if key and key not in search_key(row["id"]):
            continue
        result.append(row)
    return result


def summarize(rows):
    """Count and decoded size per type, for the status line."""
    totals = {}
    for row in rows:
        count, size = totals.get(row["type"], (0, 0))
        totals[row["type"]] = (count + 1, size + row["size"])
    return dict(sorted(totals.items()))


def build_argv(project, output, ids=(), jar=JAR, java="java"):
    """One bundle. With ids it holds those assets and their dependencies, otherwise the project."""
    argv = [str(java), "-jar", str(jar), "build", str(project), str(output)]
    for identifier in ids:
        check_name(identifier)
        argv.append(identifier)
    return argv


def split_argv(project, directory, grouping, names=(), jar=JAR, java="java"):
    """One bundle per asset, per type or per id prefix, plus a bundles.json index."""
    if grouping not in GROUPINGS:
        raise ValueError("Cách chia phải là asset, type hoặc prefix")
    if grouping == "prefix" and not names:
        raise ValueError("Chia theo prefix cần ít nhất một tiền tố")
    argv = [str(java), "-jar", str(jar), "split", str(project), str(directory), grouping]
    for name in names:
        check_name(name)
        argv.append(name)
    return argv


def check_name(name):
    """The CLI is run without a shell, but an empty or option-looking name is still a mistake."""
    if not name or name.startswith("-") or "\n" in name or "\r" in name:
        raise ValueError("Tên tài nguyên không hợp lệ: %r" % name)
    return name


def read_index(directory):
    """Rows of the bundles.json an earlier split wrote."""
    path = Path(directory) / "bundles.json"
    if not path.is_file():
        return []
    document = json.loads(path.read_text("utf-8"))
    if document.get("schemaVersion") != 1:
        raise ValueError("bundles.json có schemaVersion chưa hỗ trợ")
    rows = []
    for bundle in document.get("bundles", []):
        rows.append(
            {
                "name": bundle["name"],
                "file": bundle["file"],
                "size": int(bundle["size"]),
                "sha256": bundle["sha256"],
                "assets": list(bundle.get("assets", [])),
            }
        )
    return rows


def run(argv, log=None):
    """Runs a step and returns (exit code, output). Never raises on a failed child process."""
    result = subprocess.run(
        [str(part) for part in argv],
        cwd=str(ROOT),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    output = (result.stdout or "") + (result.stderr or "")
    if log is not None:
        log(output.strip())
    return result.returncode, output


def list_assets(project=PROJECT, jar=JAR, java="java"):
    code, output = run([java, "-jar", str(jar), "list", str(project)])
    if code != 0:
        raise RuntimeError(output.strip()[:400] or "Không đọc được danh sách tài nguyên")
    return parse_list(output)


def viewer(project=PROJECT, jar=JAR):  # pragma: no cover - Tk user interface
    import tkinter as tk
    from tkinter import filedialog, messagebox, ttk

    state = {"rows": [], "project": Path(project), "output": ROOT / "dev/bundles"}

    root = tk.Tk()
    root.title("SwordOL · Tạo bundle tài nguyên")
    root.geometry("1180x760")
    root.minsize(960, 600)
    root.configure(bg="#10151e")
    style = ttk.Style(root)
    style.theme_use("clam")
    style.configure("TFrame", background="#161d29")
    style.configure("TLabel", background="#161d29", foreground="#e2e8f0")
    style.configure("TCheckbutton", background="#161d29", foreground="#e2e8f0")
    style.configure(
        "Treeview", background="#0f1620", fieldbackground="#0f1620", foreground="#e2e8f0"
    )

    left = ttk.Frame(root, padding=12)
    left.pack(side="left", fill="both", expand=True)
    right = ttk.Frame(root, padding=12)
    right.pack(side="right", fill="y")

    header = ttk.Frame(left)
    header.pack(fill="x")
    ttk.Label(header, text="Dự án:").pack(side="left")
    project_label = ttk.Label(header, text=str(state["project"]))
    project_label.pack(side="left", padx=6)

    search = tk.StringVar()
    entry = ttk.Entry(left, textvariable=search)
    entry.pack(fill="x", pady=8)

    columns = ("type", "size", "deps")
    tree = ttk.Treeview(left, columns=columns, selectmode="extended")
    tree.heading("#0", text="Tài nguyên")
    tree.heading("type", text="Loại")
    tree.heading("size", text="Byte")
    tree.heading("deps", text="Phụ thuộc")
    tree.column("#0", width=420)
    tree.column("type", width=90, anchor="center")
    tree.column("size", width=110, anchor="e")
    tree.column("deps", width=90, anchor="center")
    tree.pack(fill="both", expand=True)

    status = ttk.Label(left, text="", wraplength=620, justify="left")
    status.pack(fill="x", pady=8)

    log_box = tk.Text(right, width=52, height=24, bg="#0f1620", fg="#cbd5f5", wrap="word")
    log_box.pack(fill="both", expand=True)

    def log(text):
        if not text:
            return
        log_box.insert("end", text.rstrip() + "\n")
        log_box.see("end")
        root.update_idletasks()

    def refresh():
        rows = filter_rows(state["rows"], search.get())
        tree.delete(*tree.get_children())
        for row in rows[:5000]:
            tree.insert(
                "",
                "end",
                iid=row["id"],
                text=row["id"],
                values=(row["type"], row["size"], len(row["dependencies"])),
            )
        totals = summarize(rows)
        status.configure(
            text="Hiện %d / %d tài nguyên.  " % (len(rows), len(state["rows"]))
            + "  ".join(
                "%s: %d (%d B)" % (name, count, size) for name, (count, size) in totals.items()
            )
        )

    def load():
        try:
            state["rows"] = list_assets(state["project"], jar)
        except (RuntimeError, ValueError) as error:
            messagebox.showerror("Không đọc được dự án", str(error))
            return
        refresh()
        log("Đã nạp %d tài nguyên từ %s" % (len(state["rows"]), state["project"]))

    def choose_project():
        chosen = filedialog.askdirectory(initialdir=str(state["project"].parent))
        if chosen:
            state["project"] = Path(chosen)
            project_label.configure(text=chosen)
            load()

    def choose_output():
        chosen = filedialog.askdirectory(initialdir=str(ROOT / "dev"))
        if chosen:
            state["output"] = Path(chosen)
            log("Thư mục xuất: %s" % chosen)

    def selected_ids():
        return list(tree.selection())

    def extract():
        steps = pending_steps(extraction_steps())
        if not steps:
            log("Tài nguyên đã được trích xuất đầy đủ; không chạy lại bước nào.")
            return
        for label, argv, _ in steps:
            log("▶ " + label)
            code, output = run(argv, log)
            if code != 0:
                messagebox.showerror("Trích xuất thất bại", label)
                return
        log("Đã trích xuất xong.")
        load()

    def build_selected():
        ids = selected_ids()
        if not ids:
            messagebox.showinfo("Chưa chọn", "Chọn ít nhất một tài nguyên.")
            return
        state["output"].mkdir(parents=True, exist_ok=True)
        output = state["output"] / "selection.swb"
        code, _ = run(build_argv(state["project"], output, ids, jar), log)
        log("✔ %s" % output if code == 0 else "✖ Không tạo được bundle")

    def split(grouping):
        names = selected_ids() if grouping != "type" else []
        if grouping == "asset" and not names:
            messagebox.showinfo("Chưa chọn", "Chọn các tài nguyên cần tách riêng từng bundle.")
            return
        state["output"].mkdir(parents=True, exist_ok=True)
        code, _ = run(split_argv(state["project"], state["output"], grouping, names, jar), log)
        if code != 0:
            log("✖ Không tách được bundle")
            return
        for row in read_index(state["output"]):
            log("✔ %s  %d B  %s" % (row["file"], row["size"], row["sha256"][:12]))

    buttons = ttk.Frame(left)
    buttons.pack(fill="x")
    for text, action in (
        ("Chọn dự án", choose_project),
        ("Trích xuất lại", extract),
        ("Thư mục xuất", choose_output),
        ("Gộp 1 bundle", build_selected),
        ("Mỗi cái 1 bundle", lambda: split("asset")),
        ("Theo loại", lambda: split("type")),
    ):
        ttk.Button(buttons, text=text, command=action).pack(side="left", padx=4, pady=6)

    search.trace_add("write", lambda *_: refresh())
    if state["project"].is_dir():
        load()
    root.mainloop()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", default=str(PROJECT))
    parser.add_argument("--jar", default=str(JAR))
    parser.add_argument("--list", action="store_true", help="in danh sách tài nguyên rồi thoát")
    arguments = parser.parse_args(argv)
    if arguments.list:
        for row in list_assets(arguments.project, arguments.jar):
            print(
                "%s\t%s\t%d\t%d" % (row["id"], row["type"], row["size"], len(row["dependencies"]))
            )
        return 0
    viewer(arguments.project, arguments.jar)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
