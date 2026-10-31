"""Tests for the bundle tool's logic. The Tk window itself is not exercised here."""

import json
import unittest
from pathlib import Path
import tempfile

import asset_bundler


class ParseListTest(unittest.TestCase):
    def test_reads_id_type_size_and_dependencies(self):
        rows = asset_bundler.parse_list(
            "legacy.image.0\timage\t1024\t\n"
            "legacy.sprite.7\tsprite\t2048\tlegacy.image.0,legacy.image.1\n"
        )
        self.assertEqual(2, len(rows))
        self.assertEqual("legacy.image.0", rows[0]["id"])
        self.assertEqual("image", rows[0]["type"])
        self.assertEqual(1024, rows[0]["size"])
        self.assertEqual([], rows[0]["dependencies"])
        self.assertEqual(["legacy.image.0", "legacy.image.1"], rows[1]["dependencies"])

    def test_refuses_a_line_without_the_required_columns(self):
        with self.assertRaises(ValueError):
            asset_bundler.parse_list("legacy.image.0\timage\n")

    def test_ignores_blank_lines(self):
        self.assertEqual([], asset_bundler.parse_list("\n   \n"))


class FilterTest(unittest.TestCase):
    rows = [
        {"id": "legacy.image.0", "type": "image", "size": 1, "dependencies": []},
        {"id": "legacy.sprite.7", "type": "sprite", "size": 2, "dependencies": ["legacy.image.0"]},
        {"id": "legacy.ui.Tuyến", "type": "ui", "size": 3, "dependencies": []},
    ]

    def test_searches_without_case_or_accents(self):
        found = asset_bundler.filter_rows(self.rows, "tuyen")
        self.assertEqual(["legacy.ui.Tuyến"], [row["id"] for row in found])

    def test_limits_to_the_chosen_types(self):
        found = asset_bundler.filter_rows(self.rows, "", ("image", "ui"))
        self.assertEqual(["legacy.image.0", "legacy.ui.Tuyến"], [row["id"] for row in found])

    def test_summarizes_count_and_size_per_type(self):
        self.assertEqual(
            {"image": (1, 1), "sprite": (1, 2), "ui": (1, 3)}, asset_bundler.summarize(self.rows)
        )


class CommandTest(unittest.TestCase):
    def test_builds_the_whole_project_or_a_selection(self):
        whole = asset_bundler.build_argv("proj", "out.swb", jar="p.jar", java="java")
        self.assertEqual(["java", "-jar", "p.jar", "build", "proj", "out.swb"], whole)
        some = asset_bundler.build_argv("proj", "out", ["a", "b"], jar="p.jar", java="java")
        self.assertEqual(["a", "b"], some[-2:])

    def test_split_requires_a_known_grouping_and_prefixes(self):
        argv = asset_bundler.split_argv("proj", "dir", "type", jar="p.jar", java="java")
        self.assertEqual(["java", "-jar", "p.jar", "split", "proj", "dir", "type"], argv)
        with self.assertRaises(ValueError):
            asset_bundler.split_argv("proj", "dir", "everything")
        with self.assertRaises(ValueError):
            asset_bundler.split_argv("proj", "dir", "prefix")

    def test_refuses_names_that_would_look_like_options(self):
        for name in ("", "-x", "a\nb"):
            with self.assertRaises(ValueError):
                asset_bundler.build_argv("proj", "out", [name])


class IndexTest(unittest.TestCase):
    def test_reads_the_index_a_split_wrote(self):
        with tempfile.TemporaryDirectory() as folder:
            Path(folder, "bundles.json").write_text(
                json.dumps(
                    {
                        "schemaVersion": 1,
                        "grouping": "asset",
                        "bundles": [
                            {
                                "name": "legacy.image.0",
                                "file": "legacy.image.0.swb",
                                "size": 1301,
                                "sha256": "a" * 64,
                                "assets": ["legacy.image.0"],
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            rows = asset_bundler.read_index(folder)
            self.assertEqual(1, len(rows))
            self.assertEqual(1301, rows[0]["size"])
            self.assertEqual(["legacy.image.0"], rows[0]["assets"])

    def test_missing_index_is_empty_and_an_unknown_schema_is_refused(self):
        with tempfile.TemporaryDirectory() as folder:
            self.assertEqual([], asset_bundler.read_index(folder))
            Path(folder, "bundles.json").write_text('{"schemaVersion": 9}', encoding="utf-8")
            with self.assertRaises(ValueError):
                asset_bundler.read_index(folder)


class ExtractionTest(unittest.TestCase):
    def test_lists_the_decode_chain_in_order_and_skips_finished_steps(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "export").mkdir()
            steps = asset_bundler.extraction_steps(
                jar=root / "game.jar",
                export=root / "export",
                decoded=root / "decoded",
                project=root / "project",
            )
            self.assertEqual(6, len(steps))
            self.assertIn("JAR", steps[0][0])
            self.assertEqual(5, len(asset_bundler.pending_steps(steps)))
            for _, argv, _ in steps:
                self.assertTrue(argv[1].endswith(".py"), argv)


if __name__ == "__main__":
    unittest.main()
