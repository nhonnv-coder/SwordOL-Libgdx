# Native world implementation plan

> Execute inline (executing-plans); user asked to continue the unfinished work without another approval round. No Git repository.

**Goal:** the native client renders the real captured map, places the server-supplied player entity on it and moves with the original walk packet, collision and correction.
**Architecture:** `protocol.legacy` gains resource/world codecs; `client-core` gains package `modules.world` plus legacy render support; `server.legacy` gateway replays one captured map's resources. Existing Maven projects only.
**Tech:** Java8/libGDX/PureMVC/JUnit; no new external libraries.
**Spec:** docs/superpowers/specs/2026-10-31-native-world.md

## Global constraints

- Map cell index is row-major `y*columns+x`; bitsets store one bit per cell, least significant bit first; both sides use the same captured fixture.
- Walk field packing is `action*10000000 + direction*1000000 + x*1000 + y`; map id travels as a separate field.
- Every parser is bounded and fails closed; no packet field is given a meaning that the original client reader does not give it.
- Resource bytes are never mutated and captures are read-only.

## Review focus

Bounds/overflow in resource assembly and reference offsets, ZIP inflate limits, CRC handling, strict schema counts, no personal capture data committed, render thread never does IO, local position never silently diverging from the server, no claim of full world/gameplay.

- [x] A: resource and world codec tests RED; implement `MapResource`, `MapReferences`, `MapResourceAssembly`, `WorldPackets` with captured-map parity and original `Packet` byte parity GREEN.
- [x] B: world state and draw-plan tests RED; implement `WorldState` and legacy map render plan/renderer GREEN.
- [x] C: gateway serves a captured map and walk; native world screen; GL probe screenshot, full verification, docs and ledger.
