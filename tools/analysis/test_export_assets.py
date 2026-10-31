import hashlib
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from export_assets import export_assets


class ExportTests(unittest.TestCase):
    def test_preserves_case_collisions_and_hashes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            jar = root / "input.jar"
            with zipfile.ZipFile(jar, "w") as z:
                z.writestr("a/A.png", b"first")
                z.writestr("a/a.png", b"second")
                z.writestr("Main.class", b"class")
            manifest = export_assets(jar, root / "out")
            self.assertEqual(len(manifest["members"]), 2)
            paths = [m["exported_path"] for m in manifest["members"]]
            self.assertEqual(len(set(p.casefold() for p in paths)), 2)
            self.assertEqual(
                [(root / "out" / p).read_bytes() for p in paths], [b"first", b"second"]
            )
            self.assertEqual(
                manifest["source_sha256"], hashlib.sha256(jar.read_bytes()).hexdigest()
            )
            self.assertEqual(manifest["members"][0]["sha256"], hashlib.sha256(b"first").hexdigest())
            self.assertEqual(json.loads((root / "out/manifest.json").read_text()), manifest)

    def test_rejects_traversal_before_creating_output(self):
        for name in ["../escape", "/escape", "C:/escape", "a\\escape", "a/../../escape"]:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                jar = root / "input.jar"
                with zipfile.ZipFile(jar, "w") as z:
                    z.writestr(name.replace("\\", "/"), b"bad")
                if "\\" in name:
                    jar.write_bytes(
                        jar.read_bytes().replace(name.replace("\\", "/").encode(), name.encode())
                    )
                with self.assertRaises(ValueError):
                    export_assets(jar, root / "out")
                self.assertFalse((root / "out").exists())

    def test_refuses_existing_destination(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            jar = root / "input.jar"
            with zipfile.ZipFile(jar, "w") as z:
                z.writestr("image.png", b"new")
            out = root / "out"
            out.mkdir()
            (out / "keep").write_bytes(b"old")
            with self.assertRaises(FileExistsError):
                export_assets(jar, out)
            self.assertEqual((out / "keep").read_bytes(), b"old")


if __name__ == "__main__":
    unittest.main()
