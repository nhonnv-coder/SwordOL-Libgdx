import struct
import unittest
from test_decode_sprites import build_file, build_payload
from test_decode_images import build_record
from test_extract_maps import zipped
from server_sprites import recover_stream


def resource(action, sprite_id, data):
    payload = (
        struct.pack(">hBbBiBhBH", 1407, 2, action, 4, sprite_id, 3, len(data), 7, len(data)) + data
    )
    return struct.pack(">h", len(payload) + 2) + payload


class ServerSpriteTest(unittest.TestCase):
    def test_recovers_interleaved_sprite_payloads_only_after_complete_parts(self):
        image = build_record(1, 1, [0], [[0]])
        data = build_file(
            build_payload([9], [(0, 0, 0, 1, 1)], [[(0, 0, -1, -1)]], [(1, [(0, 0)])])
        )
        index = zipped(struct.pack(">hIBh", 7, 9, 0, 0))
        packed = zipped(data)
        wire = (
            resource(43, 700100, index)
            + resource(38, 700100, image[:12])
            + resource(39, 700100, image[12:])
        )
        self.assertEqual({}, recover_stream(wire)[0])
        found, errors = recover_stream(
            wire + resource(40, 700100, packed[:20]) + resource(41, 700100, packed[20:])
        )
        self.assertEqual([], errors)
        self.assertEqual([9], found[700100]["doc"]["images"])
        self.assertTrue(found[700100]["images"][9].startswith(b"\x89PNG"))


if __name__ == "__main__":
    unittest.main()
