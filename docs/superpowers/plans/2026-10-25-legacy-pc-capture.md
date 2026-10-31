# Legacy PC capture implementation plan

Goal: Resume the existing MicroEmulator experiment and provide a Windows launcher of the original client that records inbound server bytes for later decoding.

Architecture: Keep both supplied JARs unchanged. A Java agent wraps Java ME Connector network connections and input streams, recording only inbound bytes. Use the existing Maven/ASM/JUnit dependencies. Store emulator home and captures inside the project. No automatic login or remote probing.

User ruling: The user explicitly requests immediate continuation without questions. Reuse the supplied emulator instead of completing the independent UI/sprite/font editor tasks. This is a temporary PC client, not completion of the libGDX reconstruction.

- [x] A. Add capture agent, test exact input bytes/hash, skip/read semantics, exception delegation, interface preservation, truncation and instrumentation against a local HTTP/TCP server.
- [x] B. Add Windows build/run/package scripts and double-click launcher, isolate emulator config, retain original artifact hashes and session metadata.
- [x] C. Run full Java/Python suites and actual desktop smoke; document controls, capture decoding, and remaining server interoperability limits. Native screenshot capture timed out; original Resources/MyGameCanvas/TCPHandler threads and real inbound 99-byte HTTP capture provide runtime evidence, not visual parity evidence.

Constraints: Java release 8; inbound data only; never log request bodies or account/password fields; no new external dependency; per-stream capture limit 64 MiB with explicit truncation; preserve network behavior and exception causes. Captures can contain server-side session data and stay local.
