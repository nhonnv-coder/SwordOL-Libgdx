"""Tests for the packed-image decoder, with fixtures built from the documented record layout."""

import os
import struct
import sys
import tempfile
import unittest
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import decode_images


def rgb565(red5, green6, blue5):
    return (red5 << 11) | (green6 << 5) | blue5


def indexed_png_parts(width, height, pixels, bit_depth=8):
    """Returns (idat_payload, idat_crc_bytes) for a stored indexed image."""
    raw = b"".join(b"\x00" + bytes(pixels[row]) for row in range(height))
    payload = zlib.compress(raw, 9)
    crc = zlib.crc32(b"IDAT" + payload) & 0xFFFFFFFF
    return payload, struct.pack(">I", crc)


def chunk_crc(kind, data):
    return zlib.crc32(kind + data) & 0xFFFFFFFF


def build_record(width, height, colors, pixels, trns_index=-1, bit_depth=8, broken_crc=False):
    """Builds the 22 byte head plus data block exactly as the original packer stores it."""
    palette = b"".join(struct.pack(">H", value) for value in colors)
    idat_payload, idat_crc = indexed_png_parts(width, height, pixels, bit_depth)
    data = palette + idat_payload + idat_crc
    ihdr = struct.pack(">II", width, height) + bytes([bit_depth, 3, 0, 0, 0])
    expanded = decode_images.expand_palette(palette)
    plte_crc = chunk_crc(b"PLTE", expanded)
    if broken_crc:
        plte_crc ^= 0xFFFF
    trns_crc = 0
    if trns_index != -1:
        trns = bytes(0 if index == trns_index else 255 for index in range(len(colors)))
        trns_crc = chunk_crc(b"tRNS", trns)
    head = struct.pack(">HHBb", width, height, bit_depth, trns_index)
    head += struct.pack(">III", plte_crc, trns_crc, chunk_crc(b"IHDR", ihdr))
    head += struct.pack(">HH", len(palette), len(data))
    return head + data


class PaletteTest(unittest.TestCase):
    def test_expands_rgb565_with_the_shipped_signed_shift(self):
        # The client reads the colour as a signed short and shifts arithmetically, so red values
        # of 16 and above wrap. These expectations are computed from that arithmetic by hand.
        self.assertEqual(b"\x00\x00\x00", decode_images.expand_palette(struct.pack(">H", 0)))
        self.assertEqual(
            b"\x00\x00\xff", decode_images.expand_palette(struct.pack(">H", rgb565(0, 0, 31)))
        )
        self.assertEqual(
            b"\x00\xff\x00", decode_images.expand_palette(struct.pack(">H", rgb565(0, 63, 0)))
        )
        self.assertEqual(
            b"\xf8\x00\x00", decode_images.expand_palette(struct.pack(">H", rgb565(31, 0, 0)))
        )
        self.assertEqual(
            b"\x7d\x00\x00", decode_images.expand_palette(struct.pack(">H", rgb565(16, 0, 0)))
        )
        self.assertEqual(
            b"\x7b\x00\x00", decode_images.expand_palette(struct.pack(">H", rgb565(15, 0, 0)))
        )

    def test_rejects_odd_palette_length(self):
        with self.assertRaises(ValueError):
            decode_images.expand_palette(b"\x00")


class IndexTableTest(unittest.TestCase):
    def test_parses_declared_records(self):
        body = struct.pack(">IBh", 1234, 7, 900) + struct.pack(">IBh", 5678, 0, 12)
        table = decode_images.parse_index(struct.pack(">h", len(body)) + body)
        self.assertEqual([(1234, 7, 900), (5678, 0, 12)], table)

    def test_rejects_truncated_or_misdeclared_tables(self):
        body = struct.pack(">IBh", 1, 2, 3)
        with self.assertRaises(ValueError):
            decode_images.parse_index(struct.pack(">h", len(body) + 7) + body)
        with self.assertRaises(ValueError):
            decode_images.parse_index(struct.pack(">h", 5) + b"12345")
        with self.assertRaises(ValueError):
            decode_images.parse_index(b"\x00")

    def test_reads_pack_offset_as_unsigned(self):
        # Observed in a 41191 byte map pack: image 703 sits at 36857, which is negative as a short.
        body = struct.pack(">IBH", 703, 0, 36857)
        table = decode_images.parse_index(struct.pack(">h", len(body)) + body)
        self.assertEqual([(703, 0, 36857)], table)


class RecordTest(unittest.TestCase):
    def pack(self, **kwargs):
        colors = [rgb565(0, 0, 0), rgb565(31, 0, 0), rgb565(0, 63, 0), rgb565(0, 0, 31)]
        pixels = [[0, 1, 2, 3], [3, 2, 1, 0]]
        return build_record(4, 2, colors, pixels, **kwargs)

    def test_decodes_a_record_into_a_png_that_parses(self):
        image = decode_images.decode_record(self.pack(), 0)
        self.assertEqual(b"\x89PNG\r\n\x1a\n", image.png[:8])
        self.assertEqual((4, 2), (image.width, image.height))
        self.assertEqual(8, image.bit_depth)
        self.assertTrue(image.crc_ok)
        self.assertEqual(4, image.colors)
        self.assertNotIn(b"tRNS", image.png)
        kinds = [kind for kind, _ in decode_images.iter_chunks(image.png)]
        self.assertEqual([b"IHDR", b"PLTE", b"IDAT", b"IEND"], kinds)
        decode_images.verify_png(image.png)

    def test_adds_transparency_chunk_when_the_record_declares_one(self):
        image = decode_images.decode_record(self.pack(trns_index=2), 0)
        kinds = [kind for kind, _ in decode_images.iter_chunks(image.png)]
        self.assertEqual([b"IHDR", b"PLTE", b"tRNS", b"IDAT", b"IEND"], kinds)
        trns = dict(decode_images.iter_chunks(image.png))[b"tRNS"]
        self.assertEqual(b"\xff\xff\x00\xff", trns)
        self.assertTrue(image.crc_ok)
        decode_images.verify_png(image.png)

    def test_reports_a_stored_crc_that_does_not_match(self):
        image = decode_images.decode_record(self.pack(broken_crc=True), 0)
        self.assertFalse(image.crc_ok)
        self.assertIn("PLTE", image.crc_mismatch)

    def test_rejects_records_that_run_past_the_pack(self):
        record = self.pack()
        with self.assertRaises(ValueError):
            decode_images.decode_record(record[:-4], 0)
        with self.assertRaises(ValueError):
            decode_images.decode_record(record, len(record) - 4)
        head = bytearray(record[:22])
        head[20:22] = struct.pack(">H", 65535)
        with self.assertRaises(ValueError):
            decode_images.decode_record(bytes(head) + record[22:], 0)

    def test_rejects_a_palette_longer_than_the_data_block(self):
        head = bytearray(self.pack()[:22])
        head[18:20] = struct.pack(">H", 65534)
        with self.assertRaises(ValueError):
            decode_images.decode_record(bytes(head) + self.pack()[22:], 0)

    def test_decodes_from_an_offset_inside_the_pack(self):
        record = self.pack()
        image = decode_images.decode_record(b"\x7f" * 13 + record, 13)
        self.assertEqual((4, 2), (image.width, image.height))
        decode_images.verify_png(image.png)


class PackExtractionTest(unittest.TestCase):
    def test_writes_every_indexed_image_and_a_manifest(self):
        colors = [rgb565(0, 0, 0), rgb565(0, 0, 31)]
        pixels = [[0, 1], [1, 0]]
        record = build_record(2, 2, colors, pixels)
        with tempfile.TemporaryDirectory() as root:
            packs = {"rp3.p": b"\x00" * 5 + record}
            table = [(4242, 3, 5)]
            written = decode_images.extract(table, packs, "rp", os.path.join(root, "out"))
            self.assertEqual(1, len(written))
            entry = written[0]
            self.assertEqual(4242, entry["image_id"])
            self.assertTrue(entry["crc_ok"])
            path = os.path.join(root, "out", entry["path"])
            self.assertTrue(os.path.isfile(path))
            with open(path, "rb") as handle:
                decode_images.verify_png(handle.read())

    def test_writes_an_image_whose_stored_crc_is_wrong_but_flags_it(self):
        colors = [rgb565(0, 0, 0), rgb565(0, 0, 31)]
        record = build_record(2, 2, colors, [[0, 1], [1, 0]], broken_crc=True)
        with tempfile.TemporaryDirectory() as root:
            written = decode_images.extract(
                [(7, 1, 0)], {"rp1.p": record}, "rp", os.path.join(root, "out")
            )
            self.assertEqual(1, len(written))
            self.assertFalse(written[0]["crc_ok"])
            self.assertEqual(["PLTE"], written[0]["crc_mismatch"])
            self.assertEqual([], decode_images.extract.failures)
            self.assertTrue(os.path.isfile(os.path.join(root, "out", written[0]["path"])))

    def test_records_a_failure_instead_of_stopping(self):
        with tempfile.TemporaryDirectory() as root:
            table = [(1, 9, 0), (2, 3, 4096)]
            packs = {"rp3.p": b"\x00" * 16}
            written = decode_images.extract(table, packs, "rp", os.path.join(root, "out"))
            self.assertEqual([], written)
            self.assertEqual(2, len(decode_images.extract.failures))
            self.assertIn("missing pack", decode_images.extract.failures[0]["error"])


class VerifyPngTest(unittest.TestCase):
    def test_rejects_a_png_whose_pixels_do_not_fill_the_declared_size(self):
        colors = [rgb565(0, 0, 0), rgb565(0, 0, 31)]
        record = build_record(2, 2, colors, [[0, 1], [1, 0]])
        image = decode_images.decode_record(record, 0)
        broken = image.png.replace(struct.pack(">II", 2, 2), struct.pack(">II", 2, 3), 1)
        with self.assertRaises(ValueError):
            decode_images.verify_png(broken, check_crc=False)

    def test_reports_a_wrong_chunk_crc_only_when_asked(self):
        colors = [rgb565(0, 0, 0), rgb565(0, 0, 31)]
        png = decode_images.decode_record(
            build_record(2, 2, colors, [[0, 1], [1, 0]], broken_crc=True), 0
        ).png
        decode_images.verify_png(png, check_crc=False)
        with self.assertRaises(ValueError):
            decode_images.verify_png(png)


if __name__ == "__main__":
    unittest.main()
