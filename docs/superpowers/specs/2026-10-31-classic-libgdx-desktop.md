# Original-compatible libGDX desktop

User goal: use the old client as the behavioral baseline for a complete desktop-first libGDX client. Existing native client-core/lwjgl3 is a synthetic JSON demo and cannot honestly stand in for original gameplay. Original JAR contains69 business/UI/render classes and all legacy request/session flows.

Chosen implementation: a separate classic-desktop libGDX host, with an explicit desktop-only JavaME compatibility boundary backed by the already supplied MicroEmulator runtime. The host owns the visible window, GL texture/pixel upload, desktop viewport, input forwarding, screenshot export and lifecycle. The original bytecode remains the reference application and implements all its existing screens and protocol behavior. No MicroEmulator Swing window is opened. Record incoming data with the existing capture agent. Keep the independent demo intact.

Alternatives considered: a full native rewrite of69 classes now would leave substantial original behaviors missing and cannot be claimed complete without protocol/UI acceptance fixtures; continuing the current demo does not preserve original flow. This compatibility desktop makes the full original program executable through libGDX while isolating the boundary for subsequent source migration. It is explicitly NOT a completed native rewrite, new server implementation, or proof of all server-side features.

Deliverable: buildable/tested Java8 source module, real libGDX window, original assets rendered without recoloring, keyboard/mouse/text input, isolated RMS, original network behavior plus inbound recorder, portable Windows runtime, run/build scripts and migration/status documentation. Original JAR bytes unchanged. No credentials embedded or logged by new code.

Verification: unit tests for RGBA conversion/viewport/key mapping; a synthetic MIDlet integration test for startup/frame/input/close independent of the live service; packaged real-client smoke and exported native GL screenshot; full reactor and Python suites. Account login/live gameplay remain user-controlled and unverified unless observed in authorized use.

Migration boundary: future native rendering/network/UI code replaces compatibility services incrementally behind the desktop host; native data/editor modules continue to own decoded assets. Remaining work and verified coverage must be named accurately, not labeled complete by association.
