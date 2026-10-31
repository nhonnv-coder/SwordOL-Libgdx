"""Tests for the `.o` decoder: compact line files and the packed image index tables."""

import os
import struct
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import decode_config


def integer(value):
    """Encodes an integer the way the original writer must have, by size."""
    if -32 <= value <= 31:
        return bytes([value & 0x3F if value >= 0 else (value & 0x3F) | 0x20])
    if -8192 <= value <= 8191:
        high = (value >> 8) & 0x3F
        if value < 0:
            high |= 0x20
        return bytes([0x40 | high, value & 0xFF])
    high = (value >> 24) & 0x3F
    if value < 0:
        high |= 0x20
    return bytes([0x80 | high]) + struct.pack(">I", value & 0xFFFFFF)[1:]


def text(value):
    raw = value.encode("utf-8")
    return bytes([0xC0 | (len(raw) >> 8), len(raw) & 0xFF]) + raw


def line(*values):
    body = b"".join(values)
    return integer(len(body)) + body


class LineFileTest(unittest.TestCase):
    def test_decodes_strings_and_integers_line_by_line(self):
        data = integer(2) + line(text("JT_N5800F"), integer(210)) + line(integer(-5), text("ok"))
        result = decode_config.decode_lines(data)
        self.assertEqual([["JT_N5800F", 210], [-5, "ok"]], result["lines"])
        self.assertEqual(2, result["line_count"])

    def test_decodes_the_three_integer_widths(self):
        data = integer(1) + line(
            integer(7), integer(-7), integer(1000), integer(-1000), integer(70000)
        )
        values = decode_config.decode_lines(data)["lines"][0]
        self.assertEqual([7, -7, 1000, -1000, 70000], values)

    def test_rejects_a_line_that_runs_past_the_file(self):
        data = integer(1) + integer(40) + b"\x01\x02"
        with self.assertRaises(ValueError):
            decode_config.decode_lines(data)

    def test_rejects_trailing_bytes_and_a_wrong_line_count(self):
        data = integer(1) + line(integer(1)) + b"\x7f"
        with self.assertRaises(ValueError):
            decode_config.decode_lines(data)
        data = integer(3) + line(integer(1))
        with self.assertRaises(ValueError):
            decode_config.decode_lines(data)

    def test_rejects_a_string_longer_than_its_line(self):
        body = text("abcdef")
        data = integer(1) + integer(3) + body
        with self.assertRaises(ValueError):
            decode_config.decode_lines(data)


class IndexTableTest(unittest.TestCase):
    def test_decodes_an_image_index_table(self):
        body = struct.pack(">IBh", 1234, 7, 900)
        result = decode_config.decode_index(struct.pack(">h", len(body)) + body)
        self.assertEqual([{"image_id": 1234, "pack": 7, "offset": 900}], result["records"])


class ExtractionTest(unittest.TestCase):
    def test_routes_each_file_to_the_reader_its_loader_uses(self):
        import tempfile

        with tempfile.TemporaryDirectory() as root:
            body = struct.pack(">IBh", 1, 2, 3)
            files = {
                "res/start.o": integer(1) + line(text("JT_N5800F"), integer(210)),
                "res/mimg.o": struct.pack(">h", len(body)) + body,
                "res/broken.o": b"\x40",
            }
            written = decode_config.extract(files, os.path.join(root, "out"))
            self.assertEqual(
                {"res/mimg.o": "index", "res/start.o": "lines"},
                {entry["source"]: entry["kind"] for entry in written},
            )
            self.assertEqual(1, len(decode_config.extract.failures))
            self.assertEqual("res/broken.o", decode_config.extract.failures[0]["source"])


if __name__ == "__main__":
    unittest.main()
