# Offline evidence and response comparison

Update 2026-10-31: this page records the initial static investigation. Later received captures are summarized in `dev/server-data/summary.md`. Current client/server capability and evidence limits are assessed in [server-reconstruction-assessment.md](server-reconstruction-assessment.md); the original statements below about absence of recordings describe the initial input, not the current workspace.

Target: `docs/files/VoLamHoiTu.jar`, SHA-256 `16024c3ed0536b29217a67f657f110abc78549eae75ef6ac0bfdd0dc205b0ccd`. Analysis date: 2026-10-25. Original artifact inventory is in `docs/client-inventory.md`; retained REA inventory Evidence ID is `ev_88080f477490a7dc5a91c6f36d300c4645b12f7fd80f7a44d8b388871d459ded` (full JSON: `docs/rea-artifact-inspection.json`).

The findings below are static observations, not observed execution. CFR 0.152 decompiled 69 class files using JDK 26. Generated pseudocode is reproducible with:

```powershell
& 'C:/Program Files/Java/jdk-26/bin/java.exe' -jar .tools/cfr.jar docs/files/VoLamHoiTu.jar --outputdir .tools/decompiled --silent true
```

Method references in the companion reports refer to these generated `.java` files. CFR warns about missing Java ME APIs and reconstructed synchronization. Original bytecode remains authoritative when pseudocode is ambiguous. No MIDlet execution, login, socket request, or live server comparison occurred. This investigation does not recover server source or establish server-side rules.

## Reproducible tools

Python 3.12 stdlib only. Replace `python` with a working interpreter on Windows if the app execution alias is inactive.

```powershell
python -m unittest discover -s tools/analysis -v
python tools/analysis/export_assets.py docs/files/VoLamHoiTu.jar dev/assets
python tools/analysis/decode_responses.py path/to/response.bin --mode tcp --output path/to/decoded.json
```

Exporter refuses an existing destination. The original export contains 351 nonclass members. `dev/assets/manifest.json` maps original ZIP member names and archive ordinals to unique portable output paths, source hash, member hash, and exact size. It never uses archive names as destination paths, so `res/LOGO.png` and `res/logo.png` both survive Windows extraction. The entire archive is checked for absolute paths, traversal, backslashes, and drive prefixes before output creation. Class files are excluded; the manifest and all nonclass bytes are preserved without format conversion.

Decoder modes:

- `tcp`: concatenated server-to-client TCP application bytes, beginning at a frame boundary. Packet capture headers, TCP segments, retransmissions and stream reassembly must be handled separately.
- `http`: one saved binary HTTP response body, with HTTP headers and transfer/content encodings already removed. Includes the legacy session header.
- `batch`: data passed directly to `Engine.receiveMsg` in account/HTTP mode: packet-count byte followed by length-prefixed packets.

Decoder writes hashes and typed fields to JSON. Binary buffers become hexadecimal. It does not infer field names or execute payloads. It rejects truncation, invalid signed lengths, packet-count mismatches, trailing bytes, and unknown types rather than silently producing misleading evidence. Original Java ignores unsupported type tags; this stricter behavior is intentional. Incoming tag 5 consumes four bytes to match the shipped reader; do not replace that with an assumed 64-bit integer. UTF-8 decoding uses replacement for invalid sequences. Decoded files may contain account/session data from the supplied capture; use synthetic or redacted fixtures when sharing.

## Comparison workflow

1. Obtain an existing authorized response capture and retain its original bytes and acquisition notes. These tools never connect to the historical endpoints.
2. Reassemble server-to-client application bytes and select the matching mode above. Do not feed outbound FEFE envelopes to the response decoder.
3. Decode locally and retain the capture SHA-256 with the resulting JSON.
4. Compare opcodes, field types and state transitions to the new server's fixtures. Record disagreements instead of treating client-side names as proof of server behavior.
5. Keep legacy endpoint metadata in `legacy-endpoints.json` separate from new-server defaults. Unknown dynamic game addresses remain unknown until a response establishes them.

## Verification

Exporter behavior was exercised RED then GREEN against real ZIP fixtures; decoder likewise failed before implementation, then passed. `python -m unittest discover -s tools/analysis -v` currently passes 7 tests (including subcases for malformed inputs). This verifies tooling with synthetic fixtures; there are no authentic captured response fixtures in the supplied JAR. Build/runtime verification of the reconstruction is separate.

See `protocol.md`, `flows-and-resources.md`, and `TODO.md` for observed details and remaining work.
