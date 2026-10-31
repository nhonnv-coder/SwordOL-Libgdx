"""Decode saved response bytes only; this module has no network operations.

Evidence: TCPHandler.run, HttpPoster.doSend, Engine.receiveMsg,
CNetPacketData constructor, PacketProcess.processDataInputStream (CFR 0.152).
"""

import argparse
import hashlib
import json
from pathlib import Path


class Reader:
    def __init__(self, data):
        self.data, self.pos = data, 0

    def take(self, size):
        if size < 0 or self.pos + size > len(self.data):
            raise ValueError(f"Truncated response at byte {self.pos}, need {size}")
        result = self.data[self.pos : self.pos + size]
        self.pos += size
        return result

    def integer(self, size, signed=False):
        return int.from_bytes(self.take(size), "big", signed=signed)


def packet(data):
    reader = Reader(data)
    result = {"opcode": reader.integer(2, True), "fields": []}
    while reader.pos < len(data):
        kind = reader.integer(1)
        if kind in (1, 2, 3, 4, 5):
            # The shipped reader consumes FOUR bytes even for TYPE_LONG (5).
            field = {
                "type": kind,
                "value": reader.integer({1: 2, 2: 1, 3: 2, 4: 4, 5: 4}[kind], True),
            }
        elif kind in (6, 7):
            raw = reader.take(reader.integer(2))
            field = {"type": kind}
            if kind == 6:
                field["value"] = raw.decode("utf-8", errors="replace")
            else:
                field["value_hex"] = raw.hex()
        else:
            # Original silently ignores unsupported tags. Refuse ambiguity in evidence exports.
            raise ValueError(f"Unsupported type {kind} at byte {reader.pos - 1}")
        result["fields"].append(field)
    return result


def frames(reader, count=None):
    result = []
    while reader.pos < len(reader.data) and (count is None or len(result) < count):
        length = reader.integer(2, True)
        if length < 4:
            raise ValueError(f"Invalid signed frame length {length}")
        result.append(packet(reader.take(length - 2)))
    if count is not None and len(result) != count:
        raise ValueError("Batch packet count exceeds available frames")
    if reader.pos != len(reader.data):
        raise ValueError("Trailing bytes after batch")
    return result


def decode_capture(data, mode):
    reader = Reader(data)
    result = {"mode": mode, "sha256": hashlib.sha256(data).hexdigest()}
    if mode == "tcp":
        result["packets"] = frames(reader)
    elif mode in ("batch", "http"):
        if mode == "http":
            # HttpPoster reads len, then 8 session bytes, then len-8 payload bytes.
            length = reader.integer(2, True)
            if length < 9 or length + 2 != len(data):
                raise ValueError("HTTP body length mismatch")
            result["session"] = {"id": reader.integer(4, True), "random": reader.integer(4, True)}
        count = reader.integer(1)
        result["packets"] = frames(reader, count)
    else:
        raise ValueError(f"Unknown response mode {mode}")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", type=Path, help="Saved raw response bytes; no HTTP headers")
    parser.add_argument("--mode", required=True, choices=["tcp", "batch", "http"])
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    rendered = json.dumps(
        decode_capture(args.capture.read_bytes(), args.mode), ensure_ascii=False, indent=2
    )
    if args.output:
        with args.output.open("x", encoding="utf-8") as output:
            output.write(rendered + "\n")
    else:
        print(rendered)
