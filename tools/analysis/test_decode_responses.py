import unittest
from decode_responses import decode_capture


class ResponseTests(unittest.TestCase):
    def test_tcp_split_frames_and_typed_signed_values(self):
        data = bytes.fromhex("0013043802ff03fffe04000000050600026869 000903ed0500000007")
        result = decode_capture(data, "tcp")
        self.assertEqual([p["opcode"] for p in result["packets"]], [1080, 1005])
        self.assertEqual([f["value"] for f in result["packets"][0]["fields"]], [-1, -2, 5, "hi"])
        self.assertEqual(result["packets"][1]["fields"], [{"type": 5, "value": 7}])

    def test_http_session_and_batched_binary_data(self):
        data = bytes.fromhex("0012 00000001 00000002 01 000903e807000200ff")
        result = decode_capture(data, "http")
        self.assertEqual(result["session"], {"id": 1, "random": 2})
        self.assertEqual(result["packets"][0]["opcode"], 1000)
        self.assertEqual(result["packets"][0]["fields"], [{"type": 7, "value_hex": "00ff"}])

    def test_rejects_invalid_frames_and_unknown_types(self):
        for data in ["0001", "000603e8", "000503e808", "000903e806000261", "8000"]:
            with self.subTest(data=data), self.assertRaises(ValueError):
                decode_capture(bytes.fromhex(data), "tcp")

    def test_batch_rejects_trailing_bytes(self):
        with self.assertRaises(ValueError):
            decode_capture(bytes.fromhex("01000403e800"), "batch")


if __name__ == "__main__":
    unittest.main()
