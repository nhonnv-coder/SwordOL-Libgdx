# iOS configuration (not built on this Windows host)

Requires macOS, Xcode and matching command-line tools, JDK 17, Gradle, and MobiVM/RoboVM 2.3.26. This standalone directory compiles client-core Java sources with the official libGDX iOS backend. See https://github.com/MobiVM/robovm/releases for host/toolchain compatibility. No Gradle wrapper, signing identity, provisioning profile, or generated Xcode archive is supplied.

On macOS run `gradle -p ios launchIPhoneSimulator -Probovm.arch=arm64` after selecting an installed simulator. The demo's 127.0.0.1:7777 endpoint targets the same Mac through the simulator; run the local server on that Mac. A physical iPhone needs an explicitly designed secure backend/tunnel and networking changes; the current loopback endpoint cannot reach a server on your computer from a physical phone. Do not broaden the server bind address to work around this for production.

Info.plist defines app metadata and landscape orientation. robovm.xml includes Scene2D/Gson reflection classes, frameworks, and launcher. These are a starting configuration and are **not verified** by an iOS compile, simulator, code signing, or device run. Shipping requires real signing credentials, device lifecycle tests, privacy/store review, TLS/authentication and native/linker checks. The current raw TCP transport is local development only; no broad ATS exception is added.
