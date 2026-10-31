# Original-compatible libGDX desktop implementation plan

> Execute inline using executing-plans. User explicitly delegates technical choices and asks to continue without confirmation. Workspace has no Git repository; work in place, no worktree or fake commits.

Goal: ship the original client running under a libGDX desktop window with a clear compatibility boundary and portable package.
Architecture: new classic-desktop module depends on installed libGDX backend and the supplied Emulator.jar API; original client artifact is loaded at runtime unchanged. No source/behavior claims beyond observed evidence.
Spec: docs/superpowers/specs/2026-10-31-classic-libgdx-desktop.md
Tech: Java8, libGDX1.14.2/LWJGL3.4.1, supplied MicroEmulator, existing capture agent, JDK26 packaging.

- [x] A. RED→GREEN tests and implementation: pixel mailbox (ARGB→RGBA, bounded dimensions/concurrency), fixed logical viewport with bars excluded from pointer mapping, keyboard mapping.
- [x] B. Headless original runtime: custom display/context, isolated file RMS, dynamic JAR load, keyboard/text/pointer, clean lifecycle; synthetic MIDlet frame/input integration check.
- [x] C. libGDX application and launcher: texture upload, nearest filtering, native window/input/resize/focus, F10 fullscreen/F11 screenshot, truthful compatibility status; smoke actual original startup and GL screenshot.
- [x] D. Build/run/portable Windows packaging scripts, capture sessions and hashes, docs/native migration ledger, full verification and fresh review.

Risk focus: differing classloader visibility; old ASM vs agent shading; texture Y orientation and channel order; keyUp/focus loss leaving stuck inputs; concurrent display callbacks; lifecycle keeping original threads alive; credentials must stay out of new logs; package with spaces in paths.
