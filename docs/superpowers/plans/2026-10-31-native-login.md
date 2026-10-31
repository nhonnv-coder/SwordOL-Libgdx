# Native login implementation plan

> Execute inline (executing-plans), user already asks autonomous implementation without more questions. No Git repository.

**Goal:** native binary-protocol login and role selection, replacing JSON demo as desktop entry.
**Architecture:** protocol.legacy codecs/schema; client.modules.login state/transport/PureMVC + ui; server.legacy minimal local authentication/role gateway. Existing projects only.
**Tech:** Java8/libGDX/PureMVC/JUnit, JDK HTTP server; no new external libraries.
**Spec:** docs/superpowers/specs/2026-10-31-native-login.md

## Review focus

Strict CRC/direction/bounds, credential-free logs, redirection not followed, callbacks stale after disconnect, IO cancellation, strict slot/schema mapping, no arbitrary role IDs, no automatic live auth, no full-world claim.

- [x] A: writer/envelope/schema tests RED; implement codec, original Packet byte parity and malformed tests GREEN.
- [x] B: loopback session tests RED; implement native session and minimal binary server auth→handoff→role→map, cancel/rejection/reconnect tests GREEN.
- [x] C: PureMVC presentation and Scene2D login/role UI, native desktop entry; native GL screenshots and full verification/review. Document remaining world/gameplay and live acceptance explicitly.
