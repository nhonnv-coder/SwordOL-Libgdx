# Android configuration (not built on this host)

Requires Android SDK 35, JDK 17, Gradle 8.13, and accepted Android SDK licenses. This standalone directory reuses client-core sources; it is not a Maven reactor module. Run `gradle -p android assembleDebug` from the repository root. No wrapper is bundled. AGP 8.13.0 prerequisites: https://developer.android.com/build/releases/agp-8-13-0-release-notes

Run the local demo server on your computer, connect a device/emulator through adb, then run `adb reverse tcp:7777 tcp:7777`. The app intentionally connects to 127.0.0.1:7777, so this reverse tunnel is required. Tap a tile to move; stand beside the monster and tap Attack. `127.0.0.1` on the phone is not the computer.

INTERNET permission is included. Cleartext configuration is restricted to loopback development names; the underlying raw TCP demo protocol is unencrypted and must not be exposed as a public production service. Release authentication, TLS, signing, lifecycle/device testing, and store packaging remain TODO. The build is **not verified** here: no Android SDK build or device test was run.
