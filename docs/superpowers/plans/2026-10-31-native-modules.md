# Native package implementation plan

> Execute inline with executing-plans. User delegates decisions and explicitly requires Java packages, not new Maven projects. Workspace has no Git repository.

**Goal:** runnable native client organized by package and evidence-based server assessment.
**Architecture:** existing client-core owns application/network/render packages; existing protocol owns protocol.legacy. Preserve desktop and mobile composition entry.
**Tech Stack:** Java8, libGDX1.14.2, PureMVC2.2, Maven/JUnit4; no new dependencies.
**Spec:** docs/superpowers/specs/2026-10-31-native-modules.md

## Constraints / review focus

No new Maven modules. No JavaME dependency in native client. Callback dispatcher and stale generation preserved. Strict inbound length/tag/truncation checks and immutable binary fields. Server rules kept distinct from observations. No live auth/probe or changes to running game.

- [x] A: preserve existing flow test, extract JsonConnection and PureMVC Proxy adapter; move facade/mediator/view tests into application and map code/tests into render. Update editor and core imports. Original module-split RED/GREEN logs retained as development evidence; user correction supersedes artifact design.
- [x] B: codec JUnit first RED, then LegacyPacket/LegacyTcpReader GREEN; final location in existing protocol package. Add dispatcher/stale callback regression test.
- [x] C: package graph, pattern roles, feature status and server assessment with evidence/acceptance gates; native launcher and README.
- [x] D: clean full reactor, inspect native jar/package boundaries, native screenshot, independent review and fix confirmed issues.
