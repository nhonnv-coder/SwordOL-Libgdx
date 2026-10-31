"""Tests for the UI layout (.ui) decoder, with fixtures built from the documented record layout."""

import os
import struct
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import decode_ui


def common(
    left=-1,
    right=-1,
    up=-1,
    down=-1,
    x=10,
    y=20,
    width=100,
    height=40,
    color=0xFFFFFF,
    pos_index=3,
    mode=0,
    flags=0,
):
    return (
        struct.pack("bbbb", left, right, up, down)
        + struct.pack(">hhhh", x, y, width, height)
        + struct.pack(">ii", color, pos_index)
        + struct.pack(">h", mode)
        + struct.pack("b", flags)
    )


def utf(text):
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def screen(screen_id=1000, controls=b"", count=0, focused=0, soft_left=1, soft_right=2):
    data = struct.pack(">i", screen_id) + bytes([0]) + common()
    data += struct.pack("bbb", focused, soft_left, soft_right)
    data += bytes([count]) + controls
    return data


def control(control_id, classflag, tail):
    return struct.pack(">i", control_id) + bytes([classflag]) + common() + tail


BUTTON = control(1001, 2, utf("Dong y") + struct.pack(">i", 5010000) + bytes([1, 0, 3]))
TEXT = control(1002, 11, utf("Xin chao_dong hai"))
EDIT = control(1003, 7, bytes([16]) + utf("") + bytes([8]))
LIST = control(1004, 4, bytes([1]) + utf("mot_hai") + bytes([2, 30, 40]) + struct.pack(">i", 0))
GRID = control(1005, 12, bytes([2, 10, 12, 3, 5, 6, 7, 1, 9]))


class ScreenTest(unittest.TestCase):
    def test_decodes_a_screen_with_every_control_type(self):
        data = screen(controls=BUTTON + TEXT + EDIT + LIST + GRID, count=5, focused=4)
        layout = decode_ui.decode(data, 1000)
        self.assertEqual(1000, layout["screen_id"])
        self.assertEqual(4, layout["focus_id"])
        self.assertEqual(1, layout["soft_left_id"])
        self.assertEqual(2, layout["soft_right_id"])
        self.assertEqual(5, len(layout["controls"]))
        button = layout["controls"][0]
        self.assertEqual("button", button["type"])
        self.assertEqual(1001, button["id"])
        self.assertEqual("Dong y", button["text"])
        self.assertEqual(5010000, button["image_id"])
        self.assertEqual(1, button["image_index"])
        self.assertEqual(10, button["x"])
        self.assertEqual(100, button["width"])
        self.assertEqual(16777215, button["text_color"])
        self.assertEqual("text", layout["controls"][1]["type"])
        self.assertEqual("Xin chao_dong hai", layout["controls"][1]["text"])
        self.assertEqual("edit", layout["controls"][2]["type"])
        self.assertEqual(16, layout["controls"][2]["char_limit"])
        self.assertEqual(8, layout["controls"][2]["input_flags"])
        self.assertEqual("list", layout["controls"][3]["type"])
        self.assertEqual([30, 40], layout["controls"][3]["item_sizes"])
        self.assertEqual("mot_hai", layout["controls"][3]["text"])
        grid = layout["controls"][4]
        self.assertEqual("grid", grid["type"])
        self.assertEqual([10, 12], grid["rows"])
        self.assertEqual([5, 6, 7], grid["columns"])
        self.assertEqual([9], grid["data_ids"])
        self.assertEqual([], layout["warnings"])

    def test_keeps_vietnamese_text_intact(self):
        data = screen(controls=control(1, 11, utf("Võ Lâm Hội Tụ")), count=1)
        layout = decode_ui.decode(data, 1)
        self.assertEqual("Võ Lâm Hội Tụ", layout["controls"][0]["text"])

    def test_records_neighbour_ids_only_when_they_are_set(self):
        data = screen(controls=control(1, 11, utf("")), count=1)
        layout = decode_ui.decode(data, 1)
        self.assertIsNone(layout["controls"][0]["left_id"])
        data = struct.pack(">i", 2) + bytes([11]) + common(left=5, down=7) + utf("")
        layout = decode_ui.decode(screen(controls=data, count=1), 1)
        self.assertEqual(5, layout["controls"][0]["left_id"])
        self.assertEqual(7, layout["controls"][0]["down_id"])
        self.assertIsNone(layout["controls"][0]["up_id"])

    def test_rejects_an_unknown_control_class(self):
        data = screen(controls=control(1, 9, b""), count=1)
        with self.assertRaises(ValueError) as failure:
            decode_ui.decode(data, 1)
        self.assertIn("control class 9", str(failure.exception))

    def test_rejects_truncated_files_and_leftover_bytes(self):
        data = screen(controls=TEXT, count=1)
        with self.assertRaises(ValueError):
            decode_ui.decode(data[:-3], 1)
        with self.assertRaises(ValueError):
            decode_ui.decode(data + b"\x01\x02", 1)
        with self.assertRaises(ValueError):
            decode_ui.decode(b"", 1)

    def test_rejects_a_control_count_the_file_cannot_hold(self):
        with self.assertRaises(ValueError):
            decode_ui.decode(screen(controls=TEXT, count=4), 1)


class ExtractionTest(unittest.TestCase):
    def test_writes_one_document_per_screen_and_reports_failures(self):
        import tempfile

        with tempfile.TemporaryDirectory() as root:
            files = {
                "res/ui/u7.ui": screen(screen_id=7000, controls=TEXT, count=1),
                "res/ui/u8.ui": b"\x00\x01",
            }
            written = decode_ui.extract(files, os.path.join(root, "out"))
            self.assertEqual(1, len(written))
            self.assertEqual(7, written[0]["window_id"])
            self.assertEqual(1, written[0]["controls"])
            self.assertTrue(os.path.isfile(os.path.join(root, "out", written[0]["path"])))
            self.assertEqual(1, len(decode_ui.extract.failures))


if __name__ == "__main__":
    unittest.main()
