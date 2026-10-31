"""Tests for the sprite (.dat) decoder, with fixtures built from the documented record layout."""

import os
import struct
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import decode_sprites


def build_payload(images, modules, frames, actions, frame_section_length=None, placement=4):
    body = bytearray()
    for image_id in images:
        body += struct.pack(">I", image_id)
    for module in modules:
        body += bytes(module)
    frame_bytes = bytearray()
    for frame in frames:
        frame_bytes.append(len(frame))
        for module_index, transform, x, y in frame:
            frame_bytes += struct.pack("<H", (transform << 12) | module_index)
            if placement == 6:
                frame_bytes += struct.pack("<hh", x, y)
            else:
                frame_bytes += struct.pack("bb", x, y)
    action_bytes = bytearray()
    for action_id, entries in actions:
        action_bytes.append(len(entries))
        action_bytes += struct.pack(">I", action_id)
        for frame_index, flag in entries:
            action_bytes += struct.pack(">H", frame_index) + bytes([flag])
    section = len(frame_bytes) if frame_section_length is None else frame_section_length
    head = bytes([len(images)])
    head += struct.pack("<H", len(modules))
    head += struct.pack("<H", len(frames))
    head += bytes([len(actions)])
    head += struct.pack("<H", section)
    payload = head + bytes(body) + bytes(frame_bytes) + bytes(action_bytes)
    return payload


def build_file(payload):
    return struct.pack("<H", len(payload)) + payload


SIMPLE = {
    "images": [5010000, 42],
    "modules": [(0, 1, 2, 16, 24), (1, 3, 4, 8, 8)],
    "frames": [[(0, 0, -5, -24)], [(1, 5, 7, 9), (0, 2, -1, -2)]],
    "actions": [(1000, [(0, 0), (1, 3)]), (2000, [(1, 255)])],
}


class SpriteDecodeTest(unittest.TestCase):
    def test_decodes_images_modules_frames_and_actions(self):
        sprite = decode_sprites.decode(build_file(build_payload(**SIMPLE)), 1234)
        self.assertEqual(1234, sprite["sprite_id"])
        self.assertEqual([5010000, 42], sprite["images"])
        self.assertEqual(
            {"image": 0, "x": 1, "y": 2, "width": 16, "height": 24}, sprite["modules"][0]
        )
        self.assertEqual([{"module": 0, "transform": 0, "x": -5, "y": -24}], sprite["frames"][0])
        self.assertEqual(
            [
                {"module": 1, "transform": 5, "x": 7, "y": 9},
                {"module": 0, "transform": 2, "x": -1, "y": -2},
            ],
            sprite["frames"][1],
        )
        self.assertEqual(1000, sprite["actions"][0]["id"])
        self.assertEqual(
            [{"frame": 0, "flag": 0}, {"frame": 1, "flag": 3}], sprite["actions"][0]["frames"]
        )
        self.assertEqual(2000, sprite["actions"][1]["id"])
        self.assertEqual([], sprite["warnings"])

    def test_keeps_the_unread_action_frame_byte_without_naming_it(self):
        sprite = decode_sprites.decode(build_file(build_payload(**SIMPLE)), 1)
        self.assertEqual(255, sprite["actions"][1]["frames"][0]["flag"])
        self.assertIn("flag", sprite["notes"])

    def test_rejects_a_truncated_file_or_length_header(self):
        data = build_file(build_payload(**SIMPLE))
        with self.assertRaises(ValueError):
            decode_sprites.decode(data[:1], 1)
        with self.assertRaises(ValueError):
            decode_sprites.decode(data[:-3], 1)
        with self.assertRaises(ValueError):
            decode_sprites.decode(b"", 1)

    def test_accepts_a_declared_length_one_byte_short_of_the_structure(self):
        # Every shipped sprite declares one byte less than its records occupy. The missing byte is
        # the final action entry flag, which the client never reads, so it never notices.
        payload = build_payload(**SIMPLE)
        data = struct.pack("<H", len(payload) - 1) + payload
        sprite = decode_sprites.decode(data, 5)
        self.assertEqual(2, len(sprite["frames"]))
        self.assertEqual(len(payload) - 1, sprite["declared_payload_length"])
        self.assertEqual(len(payload), sprite["payload_length"])

    def test_rejects_a_frame_section_length_that_disagrees_with_the_frames(self):
        payload = build_payload(frame_section_length=3, **SIMPLE)
        with self.assertRaises(ValueError):
            decode_sprites.decode(build_file(payload), 1)

    def test_reports_indexes_outside_their_tables_without_discarding_the_sprite(self):
        # Structure errors are fatal, but an index the original data itself points nowhere with is
        # content: one shipped sprite has such a frame, and the client only fails if it draws it.
        broken = dict(SIMPLE)
        broken["modules"] = [(9, 1, 2, 16, 24), (1, 3, 4, 8, 8)]
        sprite = decode_sprites.decode(build_file(build_payload(**broken)), 1)
        self.assertEqual(9, sprite["modules"][0]["image"])
        self.assertIn("module 0 uses image index 9 of 2", sprite["warnings"])
        broken = dict(SIMPLE)
        broken["frames"] = [[(7, 0, 0, 0)], [(0, 0, 0, 0)]]
        sprite = decode_sprites.decode(build_file(build_payload(**broken)), 1)
        self.assertIn("frame 0 uses module 7 of 2", sprite["warnings"])
        broken = dict(SIMPLE)
        broken["actions"] = [(1, [(9, 0)])]
        sprite = decode_sprites.decode(build_file(build_payload(**broken)), 1)
        self.assertIn("action 0 uses frame 9 of 2", sprite["warnings"])

    def test_rejects_trailing_bytes_after_the_last_action(self):
        payload = build_payload(**SIMPLE) + b"\x00\x00"
        with self.assertRaises(ValueError):
            decode_sprites.decode(build_file(payload), 1)

    def test_reads_the_six_byte_placement_variant_with_sixteen_bit_offsets(self):
        # One shipped mount sprite stores its placements as a 16 bit word plus two signed 16 bit
        # offsets. The declared frame section length is what tells the two layouts apart.
        frames = [[(0, 0, -58, -79)], [(1, 5, 39, 1)]]
        payload = build_payload(
            SIMPLE["images"], SIMPLE["modules"], frames, SIMPLE["actions"], placement=6
        )
        sprite = decode_sprites.decode(build_file(payload), 8060000)
        self.assertEqual(6, sprite["placement_size"])
        self.assertEqual([{"module": 0, "transform": 0, "x": -58, "y": -79}], sprite["frames"][0])
        self.assertEqual([{"module": 1, "transform": 5, "x": 39, "y": 1}], sprite["frames"][1])
        self.assertIn("placement_size", sprite["notes"])

    def test_reads_the_four_byte_placement_variant_by_default(self):
        sprite = decode_sprites.decode(build_file(build_payload(**SIMPLE)), 1)
        self.assertEqual(4, sprite["placement_size"])

    def test_reports_the_placement_size_that_would_fit_when_four_bytes_do_not(self):
        # One shipped sprite only walks if placements are six bytes, which no shipped code reads.
        # The decoder must say so instead of guessing a new format.
        frames = [[(0, 0, 1, 2)], [(1, 0, 3, 4)]]
        payload = bytearray(
            build_payload(SIMPLE["images"], SIMPLE["modules"], frames, SIMPLE["actions"])
        )
        body = bytearray()
        for frame in frames:
            body.append(len(frame))
            for module_index, transform, x, y in frame:
                body += struct.pack("<H", (transform << 12) | module_index)
                body += struct.pack("bb", x, y) + bytes(2)
        head = bytes([len(SIMPLE["images"])])
        head += struct.pack("<H", len(SIMPLE["modules"]))
        head += struct.pack("<H", len(frames))
        head += bytes([len(SIMPLE["actions"])])
        head += struct.pack("<H", len(body))
        images = b"".join(struct.pack(">I", i) for i in SIMPLE["images"])
        modules = b"".join(bytes(m) for m in SIMPLE["modules"])
        actions = bytearray()
        for action_id, entries in SIMPLE["actions"]:
            actions.append(len(entries))
            actions += struct.pack(">I", action_id)
            for frame_index, flag in entries:
                actions += struct.pack(">H", frame_index) + bytes([flag])
        data = build_file(head + images + modules + bytes(body) + bytes(actions))
        sprite = decode_sprites.decode(data, 1)
        self.assertEqual(6, sprite["placement_size"])

    def test_rejects_an_empty_sprite(self):
        with self.assertRaises(ValueError):
            decode_sprites.decode(build_file(build_payload([], [], [], [])), 1)

    def test_reports_images_the_project_does_not_contain(self):
        sprite = decode_sprites.decode(build_file(build_payload(**SIMPLE)), 1, known_images={42})
        self.assertEqual(["missing image 5010000"], sprite["warnings"])


class SpriteExtractionTest(unittest.TestCase):
    def test_writes_one_document_per_sprite_and_reports_failures(self):
        import tempfile

        with tempfile.TemporaryDirectory() as root:
            files = {
                "res/role/d7.dat": build_file(build_payload(**SIMPLE)),
                "res/role/d8.dat": b"\x02\x00\x01",
            }
            written = decode_sprites.extract(files, os.path.join(root, "out"), set())
            self.assertEqual(1, len(written))
            self.assertEqual(7, written[0]["sprite_id"])
            self.assertTrue(os.path.isfile(os.path.join(root, "out", written[0]["path"])))
            self.assertEqual(1, len(decode_sprites.extract.failures))
            self.assertEqual("res/role/d8.dat", decode_sprites.extract.failures[0]["source"])


if __name__ == "__main__":
    unittest.main()
