# Kiểm chứng bản nền tảng

Ngày 2026-10-25, Asia/Saigon. Host Windows x64, Oracle JDK 26, Maven 3.9.11, Python 3.12 bundled. Đây là nghiệm thu bản nền tảng/demo theo thiết kế; không phải nghiệm thu full game hoặc public production.

Phần Task6 là mới nhất; các phần dưới giữ nguyên kết quả của những bước trước.

## Giải mã tài nguyên gốc

- Python suite **50/50 PASS** (export 4, decode_responses 3, decode_images 16, decode_sprites 13, decode_ui 7, decode_config 7).
- `.p`: 1321/1322 record dựng lại thành PNG; **mọi CRC lưu sẵn (IHDR/PLTE/tRNS/IDAT) đều khớp**, nên palette và chunk là byte-identical với bản client dựng. Record còn lại có tRNS CRC sai sẵn trong dữ liệu gốc, vẫn ghi ra kèm cờ.
- Đã xem trực tiếp hai ảnh giải mã (`dev/legacy-images/310000.png`, `8100000.png`): nội dung và màu đúng, alpha đúng.
- `.dat`: **61/61 sprite**. 60 file dùng placement 4 byte; `d8060000.dat` (sprite thú cưỡi) dùng biến thể 6 byte với offset 16-bit. Nhận diện bằng độ dài vùng frame do chính file khai: chỉ cách đọc 6 byte mới đi đúng 8544 byte, và khi đó 1299/1299 placement có module/transform hợp lệ với offset x∈[−58,39], y∈[−79,1]. Client gốc không vẽ được file này vì chỉ có một nhánh đọc 4 byte.
- `.ui`: 72/72 layout, tiêu thụ **đúng đến byte cuối**; chuỗi ra tiếng Việt đọc được. Mutation probe (đọc `mode` 1 byte thay vì 2) làm cả test lẫn dữ liệu thật RED rồi GREEN sau revert.
- `.o`: 4/4. `start.o` → `JT_N5800F`, 210; `connect_b.o` → 180.169.17.16:13012, khớp `docs/analysis/legacy-endpoints.json` đã ghi từ phân tích tĩnh trước đó.
- Bundle `assets/source/OL.swb`: **1521 asset, 8.486.607 byte**, CLI validate/build xanh; kiểm tra độc lập bằng Python đọc ZIP: 0 sai hash/size, mọi dependency khai báo đều tồn tại.
- Chưa kiểm chứng: bundle này chưa được client hay server nạp; sprite và layout mới ở dạng dữ liệu, chưa có renderer hay binding trong client mới.

## Cập nhật Task6 — tilemap và resource editor

- Maven full reactor sau fix pass của review: **9 module BUILD SUCCESS, 129 test Java**, 0 failure/error. protocol6, asset-runtime23, server19, asset-pipeline49, client-core10, resource-editor22. Log `.tools/final-task6-build.log`.
- Python analysis 50/50 PASS; tổng 179 test trong hai suite.
- Test mới của Task6: MapDocument12, MapValidation8, MapRegistry9, MapInterop1 (pipeline); MapData11 (runtime); DemoMapRules8 (server); MapCatalog4 (client); EditorCore11, EditorController8, DemoProjectEdit1 (editor).
- `MapInteropTest` được kiểm chứng bằng mutation tạm: thêm một field lạ vào writer của `MapDocument` làm test RED, revert xong GREEN. Test thật sự bắt được lệch schema giữa producer và consumer.
- Editor chạy thật trên `assets/source/demo`: mở project, chọn map, canvas vẽ bằng renderer dùng chung, exit0. Screenshot `screenshots/editor-demo-field.png` đã xem trực tiếp: browser2 map, collision overlay, marker spawn/portal, inspector đủ tool/tile/layer.
- Flip kiểm chứng riêng bằng map phóng to `screenshots/editor-flipcheck.png`: FLIP_H và FLIP_V đúng; FLIP_D là transpose thật (tile sọc ngang thành sọc dọc), không phải xoay180 độ.
- Client + server thật trên map mới: server nạp bundle, client auto-connect và vẽ `world.demo-field`, exit0. Screenshot `screenshots/client-map.png` đã xem trực tiếp: đường đi, hồ nước và tường hiện đúng vị trí blocked, player (1,1), quái (3,1) HP30.
- Sửa map thật qua editor được nghiệm thu bằng `DemoProjectEditTest`: copy project mẫu, paint + collision + thêm spawn, save, mở lại thấy đúng; hoàn tác lại các thay đổi rồi save cho bytes **giống hệt** bản đầu.
- Build lại bundle mẫu deterministic: `dev/demo.swb` và bản trong `demo-bundle` cùng SHA256 `9b8708ba2a309c25adf170782794c18d0065b10d48f433bb2e4f0ea829bf9731`.
- Chưa kiểm chứng ở bước này: đóng gói Windows cho editor, bản protected/app-image chưa rebuild cho Task6, Android/iOS vẫn chưa build, và chưa đo hiệu năng preview trên map lớn (map mẫu 20x15 và 10x8).

## Cập nhật Task5 — registry và asset pipeline

- Maven full reactor lúc15:49:21 +07:00: cả7 module BUILD SUCCESS, **44 test Java**, 0 failure/error. Runtime12; pipeline13; client2; protocol6; server11. Log `.tools/asset-final-build.log`.
- Python analysis7/7 PASS, chạy lại cùng lượt; tổng51 test trong hai suite.
- Importer nhận toàn bộ351 nonclass resources từ manifest exporter, validate và build bundle2,982,809bytes; runtime loader độc lập đọc/kiểm tra đủ351 asset.
- Test `BundledAssetsTest` đã FAIL vì thiếu bundle trước khi tích hợp, rồi PASS sau khi CLI build resource thật. Client hiện tải `brand.logo` từ `bundles/demo.swb`; không dùng PNG trực tiếp cho logo hiển thị.
- Desktop jar mới chạy cùng server loopback, tự kết nối/enter-world, exit0. Screenshot `screenshots/asset-bundle.png` đã được xem trực tiếp, logo và state hiển thị đúng.
- Review phát hiện timestamp ZIP phụ thuộc locale th_TH. Regression test đã RED→GREEN với GregorianCalendar tường minh. CLI build en_US/th_TH có cùng SHA256 `706d18f34cdc5cdd30ae0e17cacb7ade448e594c6e66537a220899f33f8cb0d2`, khớp bundle trong client resources; timezone regression cũng PASS.
- Các bản Windows protected/app-image kiểm chứng ở giai đoạn trước chưa được rebuild cho Task5; để chạy code Task5 dùng `scripts/run-client.ps1` với jar mới. Android/iOS đã thêm source loader vào cấu hình nhưng vẫn chưa build/chạy.
- Ba quan sát nhỏ chưa xử lý: parser registry UTF-8 thay thế byte lỗi, runtime ID grammar rộng hơn registry, đọc/hash nguồn lặp khi nhiều alias. Chi tiết trong `docs/asset-pipeline.md`.

## Kết quả nền tảng trước Task5

| Hạng mục | Kết quả thực tế |
|---|---|
| Maven reactor | `scripts/mvn.ps1 -o verify`: BUILD SUCCESS, cả 5 module |
| Protocol | 6 test, 0 failure/error |
| Server qua TCP thật | 11 test, 0 failure/error; phiên, movement bounds, cooldown/range/reward, replay, input lỗi và control characters |
| Client/PureMVC | 1 test tích hợp nhiều bước, 0 failure/error; login/world/move, heartbeat, lỗi mạng/response, mediator detach và facade reopen |
| Công cụ phân tích Python | 7 test, 0 failure/error; exporter và decoder với fixture tổng hợp |
| Desktop thường | Chạy cùng server loopback, auto-login/enter-world, exit 0; đã kiểm tra hình |
| Desktop đã làm rối | ProGuard 7.9.1 thành công; chạy cùng server loopback, exit 0; screenshot `screenshots/desktop-protected.png` có state người chơi và quái |
| Gói Windows có runtime | `dist/windows-protected/SwordOL/SwordOL.exe` chạy với runtime đi kèm, auto-connect, exit 0 |
| Docker Compose | Cấu hình hợp lệ; Docker engine chưa chạy nên chưa build/run image |
| Android | Có launcher, cấu hình SDK/native extraction và release R8; chưa build/chạy vì host chưa có Android SDK |
| iOS | Có launcher/RoboVM/config; chưa build/chạy/ký trên macOS |
| Server cũ | Phân tích tĩnh và decoder offline; chưa có capture thực, chưa kiểm tra endpoint còn hoạt động |

## Bằng chứng và giới hạn

- Log Maven cuối: `.tools/final-build.log`, 2026-10-25 15:28:30 +07:00. Report JUnit tại `*/target/surefire-reports/`.
- Bản Windows protected chứa đúng jar có SHA-256 `01311a738b778f5ff52b6e74b4f60613ef81548e6a3481a164cab3d0d47cd267`; jar trong app bundle khớp jar protected.
- Mapping xác nhận `GameFacade`, `GameMediator`, `NetworkProxy`, `SwordGame` đã đổi tên. Thư viện/JNI entrypoint được giữ. Mapping nằm trong `dist/protected/mapping.txt`, không phân phối cho người chơi.
- Desktop screenshot được xem trực tiếp: logo gốc, màn hình đúng chiều, map demo, player HP100/gold0 và monster HP30 sau kết nối local.
- Review độc lập tìm thấy một lỗi P2 ở kiểm tra tên có nhiều xuống dòng. Hai regression test đã FAIL trước sửa rồi PASS sau sửa; thêm test tên tiếng Việt hợp lệ. Không có finding quan trọng khác được xác nhận trong lượt review.
- Maven/JDK và LWJGL có cảnh báo deprecation Unsafe/Java8 target; shade có cảnh báo metadata/resource trùng giữa dependency. Không có failure/error của test. Bản đóng gói vẫn cần kiểm thử thiết bị/máy khác trước phát hành.
- ProGuard cảnh báo tham chiếu động tới backend ANGLE/OpenGLES tùy chọn; bản Windows đang kiểm chứng dùng OpenGL desktop và đã chạy thực sau obfuscation. Không suy rộng kết quả đó sang mobile hoặc backend khác.
- Không có Git repository trong workspace; không có commit/PR/publish nào được tạo.

## Tái chạy

```powershell
powershell -ExecutionPolicy Bypass -File scripts/mvn.ps1 verify
python -m unittest discover -s tools/analysis -v
powershell -ExecutionPolicy Bypass -File scripts/obfuscate-desktop.ps1
```

Nếu alias `python` không hoạt động, dùng đường dẫn một Python 3 đã cài. Trong phiên này dùng runtime bundled của Codex; công cụ phân tích không cần package Python bên ngoài.
# Temporary original-client PC capture — 2026-10-26

- Full Maven reactor: 131 Java tests, zero failures/errors; `.tools/legacy-pc-final-verify.log`.
- Analysis tools: 50 Python tests pass; `.tools/legacy-pc-python.log`.
- Real supplied MicroEmulator Connector with loopback HTTP/TCP: inbound bytes unchanged, captures byte-identical, EOF metadata finalized. Reproduce with `scripts/verify-legacy-capture.ps1`; `.tools/legacy-capture-probe-final.log`.
- ASM conflict: original emulator ClassAdapter failed to link before relocation and loads after relocation. Private ASM/Gson namespace avoids the legacy enhancer collision.
- Portable runtime launched original game threads (Resources, MyGameCanvas, TCPHandler). A real 99-byte HTTP body was saved and decoded locally. TCP stream had no received bytes at observation. No authenticated gameplay success claim.
- Original and packaged client SHA-256 both `16024c3ed0536b29217a67f657f110abc78549eae75ef6ac0bfdd0dc205b0ccd`; emulator hashes both `dbd5f3eb8365d3e839d6a203149e0e3776fc1a0585e16ac1fc23f76c9fcae1c6`.
- Native UI screenshot capture timed out, so visual parity has not been verified. Portable game left running for user interaction. Details: `docs/legacy-pc.md`.


# Original-compatible libGDX desktop — 2026-10-31

- Full offline Maven reactor: 135 tests, zero failures/errors; `.tools/classic-full-verify.log`.
- Python analysis: 69 tests pass; `.tools/classic-python-verification.log`.
- Synthetic MIDlet verifies framebuffer, viewport, key/pointer/Unicode input and bounded shutdown. Actual original JAR startup rendered and visually checked: `screenshots/classic-libgdx-startup.png`.
- Portable bundled Java with capture agent and paths containing spaces pass; `.tools/classic-portable-package.log`, `.tools/classic-spaces-smoke.log`, `screenshots/classic-portable.png`.
- Original/package client, Emulator and host hashes match. Independent review: no critical/important findings.
- Live authenticated gameplay remains unverified. This is an original-compatible desktop host, not a completed pure-native rewrite.

# Native client packages and legacy inbound codec — 2026-10-31

- No new Maven modules: application/network/render are packages inside client-core; legacy decoder is inside existing protocol.
- Full clean reactor passed before review fixes (.tools/native-packages-verify.log); final verify after fixes: 140 tests, failures0/errors0 (.tools/native-packages-final-verify.log).
- Version regression RED: .tools/native-version-red.log; GL texture retention regression RED: .tools/native-render-probe-red.log. Both GREEN after fixes. GL probe .tools/native-render-probe-green.log reports NATIVE_RENDER_PROBE_PASS; screenshot screenshots/native-packages.png visually checked.
- Native user launcher smoke exits0 (.tools/native-launcher-smoke.log), jar inventory has0 javax/microedition or org/microemu entries. Existing original-compatible game session not modified.
- Independent review confirmed both fixes and no remaining important findings. New protocol tests cover signed values, inbound tag5 four-byte width, UTF-8, immutable binary/field list, invalid tags/lengths, truncation, multiple frames and clean EOF. These tests do not establish legacy server interoperability.
- Python tooling was not changed or rerun in this step. Full native game flows, production auth/persistence and legacy server rules remain pending as stated in the assessment.

# Native binary login and role UI — 2026-10-31

Full final reactor:150tests, failures0/errors0 (.tools/native-login-final-verify.log). Typed writer byte parity tested by invoking original Packet only, without MIDlet/login. TCP CRC/XOR and HTTP batch tests plus role slots/handoff/channel tests. Loopback HTTP→TCP tests verify bad/good password, role selection/map response, caller password clearing, client role ownership guard, nonblocking disconnect, stale-generation terminal failure and account availability with four waiting game connections.

Actual libGDX probe drives UI button events with fake local identities: NATIVE_LOGIN_PROBE_PASS (.tools/native-login-probe.log); screenshots/native-login-roles.png and native-login-map-announcement.png viewed directly, including Vietnamese text and empty password. Startup script smoke passes (.tools/native-login-launcher.log), native JAR excludes JavaME/runtime and server implementation classes. Native desktop left open disconnected for user, no automatic live auth.

Independent review findings all addressed; no remaining important findings within auth/role/map-announcement scope. Overall client is still incomplete: no playable native world in this new flow, no live interoperability acceptance, no full server rules/persistence recovery. Original running game and capture recorder unchanged. Python tools unchanged and not rerun.

# Native world: map resources, user entity and movement — 2026-10-31

Full clean reactor:187 tests, failures0/errors0 (.tools/native-world-final-verify.log). New codecs tested against real captured material: map-data and map-ref of map1101400 reproduce the independently verified Python export (tile bytes, three bitsets, 236 tile references), the six images map1105100 downloaded rebuild byte for byte including their stored CRCs, and outbound1005/1014 match the original `Packet` bytes. Malformed sizes, dimensions, ZIP size/CRC, reference offsets, oversized transfers and out-of-grid cells are all rejected.

The headless draw plan reproduces every one of the6138 draw operations the verified Python preview produces for that map, in order, with no missing images and no warnings; layer order, per-piece offsets and the rotation-bit transform are pinned by that digest.

Integration over real sockets against the local gateway serving the captured map: resources →1110 →1126 →1006 →1005. An accepted step gets no answer, as the original client expects; an impossible jump is answered with an action3 correction carrying the position the server accepted; a walk for a role the connection does not own ends the connection. RED logs: .tools/native-world-codec-red.log, .tools/native-world-state-red.log, .tools/native-world-session-red.log, .tools/native-world-lifecycle-red.log; GREEN counterparts beside them.

Real GL probe `vn.swordol.desktop.NativeWorldProbe`: NATIVE_WORLD_PROBE_PASS draws=6138 cell=22,30 (.tools/native-world-probe.log). It walks east three times from the server spawn20,30 and stops at22,30 because cell23,30 is blocked in the captured map, so collision was verified against real map data rather than a fixture. Screenshots screenshots/native-world-map.png and native-world-walked.png viewed directly. Launcher smoke exits0 (.tools/native-world-launcher-smoke.log).

Two defects found by self-review were reproduced RED and fixed: world input arriving before a world existed threw IllegalStateException into the render thread, and a dead session left its world on screen with nothing updating it. client-core now forks a JVM per test class because PureMVC's Facade is a process singleton.

Not verified: any live server or account, character sprites and animation, portals as transitions, combat, items, NPC and quest flows. The gateway is a local vertical slice, not a recovered server. Python tooling unchanged and not rerun in this step.

