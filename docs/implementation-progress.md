# Ledger — docs/superpowers/plans/2026-10-25-reconstruction.md

## Typed data manager — 2026-10-31

User requests management/render by data type. Added Quan-Ly-Data.cmd, scripts/run-data-manager.ps1 and data_manager.py: embedded existing map preview in single Tk root, typed NPC/item/image/sprite tabs, Vietnamese search, properties with source/sample bounds, action/frame playback, zoom and PNG export. Item atlas equation and NPC look mapping checked against ItemEx/MapObject, not guessed from look ID filenames. Unresolved NPCs keep explicit original-JAR fallback1000000 label; not claimed recovered appearances.
Added server_sprites.py passive extraction of1407/actions30..43 PNG/DAT/index bundles from existing captures, with complete-part grouping, CRC checks and source hashes; recovered7 server sprite assets and manager prioritizes them. No network requests or capture mutation.
Review fixed: manager initially omitted1032 detail packets from item properties; pure entry_properties + detail-only regression RED→GREEN. Also preserve numeric observation counts and label sampleLimit8. Sprite recovery split-parts/index-first test RED→GREEN. Python69/69 pass; actual87/87 item icons,48/61 original sprite frame0,7/7 server frame0 drawable;3 server sprites have missing11001000 image and remain warnings.315NPC forms currently include7 with server sprite mapping, remaining mappings unresolved/fallback. See docs/data-manager.md.
GUI starts without error, window SwordOL · Data Manager observed; native screenshot/click geometry unavailable, so screenshots are render artifacts, not UI interaction verification. Data manager opened with latest code, read-only asset handling preserved.

## Passive map/NPC/item monitor — 2026-10-30

User requests opening emulator and autonomous monitoring/collecting available data, summarized per section. Opened source launcher with recorder; current capture captures/20261030-233857-b29c6630 receiving TCP data (>66KiB at observation). Native emulator screenshot read times out; no autonomous UI/gameplay claim.
Implemented collect_server_data.py and Thu-Du-Lieu.cmd: reuses map extraction, resolves2030 templates per stream exactly as original, exports selected NPC/HP/MP/position fields and1008 item fields excluding keeper IDs, ordinary1032 details; scripted1032/action99 remains explicit diagnostic. Immutable generation reports, file manifests, source snapshot hashes/offsets and separate maps/npcs/items JSON plus summary.md.
Review fixed: details-only1032 item types were omitted from catalog/count; regression RED→GREEN unions their confirmed type/icon IDs without inferring names. Python64/64 GREEN. Latest verified snapshot25maps/274NPC display forms/1348NPC observations/87item types/319item observations/2ordinary detail packets/2script-schema diagnostics;7 report files hash/size verified.
Heartbeat automation thu-d-li-u-swordol ACTIVE every15min, quiet on unchanged unique counts/old errors, source data treated as data not instructions. Baseline monitor-state.json initialized; no endpoint probing, credentials, injected opcodes or inventory transactions. Docs/server-data-collection.md documents operation and limits.

## Desktop map preview — 2026-10-30

User requested tool preview map, then identified incorrect layer order. Added tools/analysis/preview_maps.py with bounded map-ref parser, original/downloaded PNG resolver, first-frame rendering, Tk map selector/search, zoom/pan, grid/collision/portal overlays, hover info and PNG export. Double-click Preview-Map.cmd; scripts/run-map-preview.ps1 uses existing Python/Pillow/Tkinter, no dependency installation.
Fixed after user feedback: initial single row-order pass let later background cover foreground. Original MapEx.drawCellA + draw + Entity.compareMapPos establish three passes: nonanimated0x4000 immediate, remaining0x0800 low-decoration queue, remaining static entities; each sorted(y,x). Two overlapping-row regression subcases RED→GREEN; separate layer toggles added. Independent reviewer confirmed correction and acknowledged earlier scope interpretation missed static map queue ordering.
Verification: full Python61/61 pass; all25 catalog maps render with no missing images/warnings; Trung Nguyen/Ngoc Long PNGs visually inspected, corrected Trung Nguyen inspected again. Tk window launches, no startup log errors. Native screenshot capture timed out, so UI interaction verification remains limited; no claim of animated timing/NPC simulation. User guide docs/map-preview.md.

## PC capture continuation — 2026-10-26

Map continuation: added tools/analysis/extract_maps.py, supports complete resource groups preceding 1110 identity, growing TCP snapshots, portal packets, row-major geometry/LSB bitsets, raw+ZIP resources, downloaded PNG reconstruction with existing decoder and CRC verification, immutable versions and catalog.json. Source and packaged captures yielded 14 distinct maps and 17 downloaded images at verification snapshot; remaining images refer to packaged assets, full-map rendering not yet implemented.
Review fixed: initial ad-hoc JSON inferred column-major incorrectly from copy loops; actual MapEx render/getHeight use y*cols+x. Non-square literal regression RED→GREEN; raw captured map bytes never changed. Older derived exports marked superseded; use catalog.json. Also fixed 13/14 image indexes to replace per complete ZIP, matching updateArrayBoosImages; two-index regression RED→GREEN. Full Python55/55 pass. Final snapshot grew to17 maps while user continued playing; independently verified108 file hashes/sizes and each tileRows flattened equals original bytes;17 downloaded PNGs. Marked13 incorrect older derived exports superseded without changing raw data.

User steering: tạm dựng bản PC để người dùng lấy dữ liệu server, nối tiếp tiến độ và không hỏi lại.
Ruling: Use supplied original client + supplied MicroEmulator for temporary PC use; keep libGDX demo and editor scope separate. Original JARs unchanged. A Java agent wraps only inbound streams of Connector.open, with dynamic endpoints retained and URL userinfo/query stripped from recorder metadata. Client uses Connector.open; convenience Connector.openInputStream APIs remain outside agent coverage.
Complete: legacy-capture Maven module, two unit tests (missing implementation RED → GREEN), real original-emulator loopback HTTP/TCP probe, portable Java runtime and Chay-Game.cmd/Mo-Du-Lieu.cmd launchers. Verification script: scripts/verify-legacy-capture.ps1.
Fixed: original emulator bundled ASM is incompatible with ASM9 on the shared classpath; ClassAdapter load regression reproduced RED, shaded private ASM/Gson packages → GREEN. Isolated emulator home fixes earlier C:\.microemulator config error. Use javaw for visible game without console.
Review: fresh reviewer found no Critical/Important issue for the supplied original client. Python 50/50 pass; full Java reactor passes. Full final verification recorded in .tools/legacy-pc-final-verify.log.
Runtime: packaged process runs original Resources/MyGameCanvas and TCPHandler with Capture.Recording in stack; real 99-byte HTTP response recorded and offline decode succeeds. No automated credential entry. Native screenshots repeatedly timed out, so no claim of visual parity. Game left open for user; captures remain local under dist/SwordOL-PC-Capture/captures.
Limit: 64MiB per stream, only bytes client actually reads, no total-session size cap. Graceful exit finalizes SHA256; forced termination can leave completion=open. Captures/legacy logs can contain session data. Socket currently had no inbound bytes at observation; no claim of successful authenticated gameplay.

Ruling: Người dùng yêu cầu lập plan và triển khai ngay; thực hiện trong phiên này, không thêm vòng xin duyệt plan.
Ruling: Workspace chưa có Git repository; làm tại thư mục dự án được chỉ định, không tạo worktree hoặc commit giả.
Ruling: NDJSON là giao thức demo mới, không được quảng bá tương thích JAR gốc; adapter cũ chờ phân tích.
Ruling: Bản đầu dùng state trong bộ nhớ; persistence, xác thực production, TLS và scale là TODO bắt buộc trước public deployment.

Task 1: complete — decompile 69 class; báo cáo endpoint/protocol/flow/resource; 351 tài nguyên bảo toàn; decoder offline; Python7/7 GREEN.
Task 2: complete — protocol và server demo; TCP thật; protocol6/server11 GREEN; session/replay/bounds/cooldown/reward và input validation.
Task 3: complete within foundation scope — libGDX/PureMVC chính thức; mediator lifecycle, proxy network, command/facade; client integration1/1 GREEN; desktop chạy; mobile launchers/config cung cấp nhưng chưa build.
Task 4: complete within local packaging scope — full reactor18/18 GREEN, desktop và Windows protected smoke exit0, Docker Compose validate; public deployment/signing để TODO.

Final: fixed name-control bypass — hai input CRLF/multiple LF RED→GREEN; tên Unicode vẫn được chấp nhận; full suite18/18.
Final: fixed Windows PowerShell native-stderr Java-home detection — command tái hiện NativeCommandError trước sửa; helper resolve JDK/jpackage thành công sau sửa.
Final: fixed Maven wrapper argument forwarding — `-o` lỗi ambiguous trước sửa; cùng lệnh qua được parameter binding và full reactor sau sửa.
Ruling: Dùng ProGuard7.9.1 thay7.8.2 vì bản cũ không đọc được classfile JDK26; bản protected đã chạy thật. Nếu đổi JDK/backend, cần kiểm tra lại.
Ruling: Để socket Java có giới hạn trong bản demo thay vì thêm Netty ngay; đổi gateway khi đo tải cho thấy cần. Giới hạn hiện tại8 workers/16 queued sockets không phải sức chứa MMO.
Final: no independently confirmed minor code findings left deferred; production/mobile/legacy-capture limitations được ghi trong docs/TODO.md và docs/verification.md.

Scope update 2026-10-25: Người dùng yêu cầu thêm công cụ tạo/chỉnh sửa tilemap, UI, sprite, fonts. Bổ sung Spec và Task5–10, tất cả pending. Task1–4 đã hoàn thành chỉ là nền tảng và trích xuất, không bao gồm editor; hướng kết hợp công cụ ngoài và editor quản lý riêng đang là đề xuất.

## Task5 — docs/superpowers/plans/2026-10-25-asset-registry.md

User steering: “next action” — triển khai nền registry/pipeline và nối vào client, chưa thực hiện editor Task6–10.
Task A: complete — registry/validation/find-usages, deterministic atomic builder, legacy importer và migration có backup;13 pipeline tests GREEN.
Task B: complete — bounded ZIP/JSON/integrity loader, defensive copies và stream ownership;12 runtime tests GREEN.
Task C: complete — sample registry/bundle, client logo tải theo assetId, mobile source config cập nhật, full Java44/44 + Python7/7; desktop smoke exit0; độc lập runtime load351 legacy assets.
Final: fixed locale-dependent ZIP timestamp — reviewer tái hiện en_US/th_TH khác bytes; regression RED→GREEN, explicit GregorianCalendar; full suite44/44.
Ruling: Giữ bundle mẫu nhỏ trong client resources và build-assets tường minh để bootstrap checkout không cần tự sửa source khi chạy test. Chi phí: khi sửa source assets cần chạy build-assets -UpdateClient trước build game.
Ruling: Runtime v1 nạp bundle vào bộ nhớ với limit64MiB để kiểm chứng và đơn giản hóa API. World asset lớn sẽ cần streaming/chunk/cache ở giai đoạn tiếp, không tăng limit tùy tiện trên mobile.
Final: minor (deferred): registry parser replaces malformed UTF-8 while runtime reports it; harden before untrusted editor imports.
Final: minor (deferred): runtime ID grammar is wider than producer registry; unify in shared schema before editor interchange.
Final: minor (deferred): aliases can repeat disk reads/hash work; add source snapshot cache when asset projects grow.

## Task6 — docs/superpowers/plans/2026-10-25-map-editor.md

User steering: “LÀM TIẾP TASK 6” sau khi chốt hướng công cụ.
Ruling: Hướng tilemap là editor map riêng trong project, không tích hợp Tiled ở Task6 — người dùng chọn trực tiếp từ ba phương án. Chi phí nếu sai: map vẽ sẵn dạng TMX phải nhập qua importer viết thêm ở giai đoạn sau.
Ruling: Một tài liệu map duy nhất chứa cả visual và collision/spawn/portal, client và server nạp cùng bytes từ bundle; không sinh định dạng biên dịch thứ hai. Chi phí nếu sai: khi server cần dữ liệu nghiệp vụ mà client không nên thấy, phải tách tài liệu và thêm bước build.
Ruling: Map code đặt trong package `vn.swordol.assets.pipeline` thay vì subpackage `.map` để dùng `PipelineIO` sẵn có; runtime trích helper JSON strict từ `AssetBundle` ra `RuntimeJson`. Chi phí nếu sai: hai module có thêm lớp nội bộ phải đổi tên nếu sau này tách subpackage.
Pre-flight: Task A sinh `MapDocument`/canonical bytes mà Task B đọc lại, và Task D dùng cả hai; kiểm tra: tile word, giới hạn và quy ước gốc dưới-trái phải khai báo một lần trong Global Constraints và được cả hai bên test theo đúng cùng fixture.
Task A: complete — MapDocument/MapValidation/registry map checks và CLI new-map; asset-pipeline 41/41 GREEN (từ 13); bundle mẫu rebuild vẫn SHA256 706d18f34cdc5cdd30ae0e17cacb7ade448e594c6e66537a220899f33f8cb0d2.
Task B: complete — RuntimeJson trích từ AssetBundle (12 test cũ vẫn GREEN), MapData bounded reader 11 test, interop pipeline→runtime 1 test; runtime 23/23 và pipeline 42/42 GREEN.
Final: interop test được kiểm chứng bằng mutation tạm (thêm field lạ vào writer) — MapInteropTest và MapDocumentTest RED, sau revert GREEN; test thực sự bắt lệch schema giữa hai module.
Ruling: Portal link giữa hai map không được coi là `dependencies` của registry — cửa hai chiều là bình thường nhưng sẽ tạo cycle; registry theo dõi link riêng và `findUsages` gộp cả hai nên vẫn báo đúng nơi dùng. Chi phí nếu sai: công cụ nào suy ra thứ tự build từ `dependencies` sẽ không thấy quan hệ map–map.
Ruling: Bundle mẫu chuyển sang module `demo-bundle` để client và server cùng đọc một file thay vì nhân bản resource. Chi phí nếu sai: thêm một module nhỏ trong reactor và mọi target muốn bundle phải khai báo phụ thuộc.
Ruling: Nội dung mẫu (tileset PNG, hai map) sinh bằng script Python deterministic trong Task C thay vì chờ Task D, vì nghiệm thu editor cần project thật để mở. Chi phí nếu sai: nếu định dạng map đổi, phải chạy lại script trước khi build bundle.
Task C: complete — module resource-editor (EditorProject/MapEditSession/EditorCommands/EditorController + Scene2D app, launcher, scripts/run-editor.ps1), MapRenderer/TileSheets dùng chung trong client-core; 19 test editor GREEN; editor chạy thật trên project mẫu, screenshot screenshots/editor-demo-field.png đã xem trực tiếp.
Final: flip rendering kiểm chứng riêng bằng map 4x2 phóng to (screenshots/editor-flipcheck.png): FLIP_H/FLIP_V đúng và FLIP_D là transpose thật (sọc ngang thành sọc dọc), không phải xoay 180°.
Task D: complete — demo-bundle dùng chung; DemoSession lấy bounds/collision/spawn/portal từ MapData và trả map.id/map.version; MapCatalog của client từ chối version lệch; SwordGame vẽ map bằng renderer dùng chung; client+server chạy thật, screenshot screenshots/client-map.png đã xem trực tiếp.
Final review: reviewer độc lập (Opus) chạy trên toàn bộ Task6, tái hiện từng finding bằng chương trình riêng; 1 Critical, 5 Important, 7 Minor.
Final: fixed Critical — save chỉ validate map đang sửa nên chặn một ô mà map khác có portal trỏ tới sẽ ghi đè file cũ rồi mới lỗi, project không mở lại được. `saveIsRefusedWhenItWouldBreakAnotherMapsPortal` RED→GREEN; `MapValidation.checkProject` validate mọi map trước khi thay file; `markSaved` chuyển xuống sau `refresh`.
Final: fixed Important — spawn đứng trên ô blocked vẫn build được, server trả WORLD_UNAVAILABLE cho mọi ENTER_WORLD. `rejectsASpawnStandingOnABlockedCell` + `saveIsRefusedWhenASpawnWouldStandOnABlockedCell` RED→GREEN.
Final: fixed Important — không ai kiểm ảnh tileset đủ lớn cho columns/tileCount; lỗi chỉ lộ lúc TileSheets chạy. `rejectsATilesetImageTooSmallForItsDeclaredTiles`, `rejectsATilesetImageThatIsNotAReadablePng`, `rejectsAMapWhoseTilesetImageIsTooSmall` RED→GREEN; thêm MapValidation.ImageSource và kiểm IHDR của PNG.
Final: fixed Important — producer nhận literal `2.0`/`-0`/`2e0` mà runtime từ chối, bundle build xanh nhưng client/server không đọc được. `rejectsNumbersTheRuntimeReaderWouldRefuse` RED→GREEN; PipelineIO giữ nguyên literal và MapDocument dùng đúng grammar của runtime.
Final: fixed Important — thông báo lệch map.version bị status kế tiếp ghi đè và client vẫn chơi trên lưới trống. `WorldSyncTest` 4 test RED→GREEN; thêm `WorldSync`, client giữ thông báo, đổi lưới sang nền đỏ, chặn click và không vẽ nhân vật khi chưa tin được map. Kiểm chứng ảnh `screenshots/client-version-mismatch.png`.
Final: fixed Important — preview editor lỗi để canvas hiển thị map cũ trong khi click sửa map mới. `reloadPreview` xóa preview/sheets trước khi nạp; kiểm bằng đường dẫn lỗi ảnh tileset.
Final: suite sau fix pass 129/129 Java GREEN (9 module), client thường và client lệch version đều chụp ảnh xem trực tiếp.
Final: minor (deferred): MOVE ghi x/y trước khi chuyển portal nên portal lỗi để client và server lệch vị trí.
Final: minor (deferred): ảnh `image` hỏng làm Pixmap ném GdxRuntimeException không bắt ở loadLogo, client chết thay vì báo lỗi.
Final: minor (deferred): palette tile trong editor tạo nút theo từng gid, map khai nhiều tileset lớn sẽ treo UI.
Final: minor (deferred): saveMap không kiểm file có bị sửa bởi tiến trình khác, ghi đè âm thầm.
Final: minor (deferred): `layerName`/`layerCount` ném IndexOutOfBounds trong khi API láng giềng ném IOException.
Final: minor (deferred): spawn monster hp 0 được chấp nhận, quái chết sẵn và không bao giờ thưởng.
Final: minor (deferred): MapRenderer vẽ mọi ô mọi layer, chưa cull, map ở giới hạn schema sẽ không chạy nổi.

## Giải mã tài nguyên gốc (ngoài phạm vi Task6, theo yêu cầu người dùng)

User steering: “chuyển về ảnh gốc trước rồi phân tích làm bundle về lại như thế”, sau đó “tất cả các file”.
Ruling: Decoder tái tạo đúng số học của client, kể cả lỗi dịch số học kênh đỏ RGB565, vì CRC lưu sẵn trong pack là oracle: sửa cho “đẹp” sẽ làm sai toàn bộ CRC và tạo ảnh game gốc chưa từng hiển thị. Chi phí nếu sai: nếu sau này muốn màu đúng chuẩn, cần cờ riêng và phải bỏ kiểm tra CRC.
Ruling: Chỉ số ngoài bảng trong sprite là lỗi *nội dung*, báo warning; chỉ vi phạm *cấu trúc* mới là lỗi cứng. Chi phí nếu sai: dữ liệu hỏng có thể lọt vào bundle và chỉ lộ khi render.
Ruling: `d8060000.dat` không được diễn giải theo giả thuyết 6 byte/mảnh dù vừa khít, vì client chỉ có một nhánh đọc 4 byte. Chi phí nếu sai: mất một sprite cho tới khi có bằng chứng khác.
Final: 1321/1322 ảnh `.p` dựng lại thành PNG, toàn bộ CRC lưu sẵn khớp; 60/61 sprite; 72/72 layout tiêu thụ đúng đến byte cuối và ra chuỗi tiếng Việt đọc được; 4/4 file `.o`, trong đó `connect_b.o` khớp đúng endpoint đã ghi trong tài liệu.
Final: project `dev/OL` 1520 asset build thành bundle 8.362.399 byte; kiểm tra độc lập bằng Python: 0 sai hash/size, dependency đầy đủ.
Final: Python suite 48/48 GREEN (thêm decode_images 16, decode_sprites 11, decode_ui 7, decode_config 7).
Final: decoder UI và config viết test trước nhưng chỉ chạy sau khi có implementation, không có RED được quan sát; bù lại bằng mutation probe (đổi `mode` sang 1 byte làm 5 test và dữ liệu thật RED, revert xong GREEN) và bằng dữ liệu thật.
Final: fixed — `d8060000.dat` giải mã được sau gợi ý của người dùng (project Godot gọi 8060000 là `AniChar_RiderDeDiem`, sprite thú cưỡi). Hai test biến thể RED→GREEN; file dùng placement 6 byte với offset 16-bit LE, nhận diện bằng độ dài vùng frame tự khai. Sprite 61/61, Python 50/50.
Ruling: Chọn biến thể placement bằng chính độ dài vùng frame file khai (thử 4 byte trước, chỉ nhận 6 byte khi 4 không khớp mà 6 khớp chính xác), thay vì theo tên file hay heuristic nội dung. Chi phí nếu sai: một file 4 byte vô tình khớp cả ở 6 byte sẽ bị đọc sai; chưa gặp trong 61 file.
Final: hai bản port C# trong RiderProjects/SwordOL (Sword.Client, Sword.Gaudot) dùng cùng layout 4 byte nên xác nhận format chung nhưng không xử lý biến thể này; `Sword.Tools/PFileExtractor.cs` giải nén lại pixel và chuyển palette theo công thức đã sửa (mask 5 bit) nên màu khác bản gốc và không dùng được CRC lưu sẵn làm oracle.

## Desktop libGDX tương thích client gốc — 2026-10-31

Hoàn thành classic-desktop và dist/SwordOL-libGDX: cửa sổ/texture/input/lifecycle libGDX, JAR gốc giữ nguyên qua lớp JavaME không mở cửa sổ giả lập; profile riêng, capture inbound, fullscreen, PNG và scale nguyên. Shutdown có giới hạn chờ. 135 Java và 69 Python test pass; bản portable chạy thực, ảnh đúng chiều/màu, đường dẫn có khoảng trắng pass. Review không có finding critical/important.

Chưa nghiệm thu đăng nhập/gameplay server thật; chưa chuyển nghiệp vụ/UI/network/renderer sang source libGDX native. Xem docs/libgdx-desktop.md.

## Client native theo package và đánh giá server — 2026-10-31

User correction: module là Java package trong cùng client-core, không phải nhiều Maven artifact. Đã gộp lại toàn bộ thư mục tạm và parent reactor giữ nguyên danh sách project ban đầu.

application chứa PureMVC Facade/Command/Proxy/Mediator/GameView; network chứa JsonConnection tách IO khỏi framework; render chứa map/tiles/world sync; SwordGame giữ composition/Scene2D. Protocol hiện có thêm package protocol.legacy đọc TCP inbound offline, không gửi request. Native entry SwordOL-Native.cmd. Báo cáo server và feature status ở docs/native-client-modules.md và docs/analysis/server-reconstruction-assessment.md.

Review phát hiện hai lỗi cũ: unchanged map làm mất texture, cùng map ID nhưng version mới bị bỏ qua. Cả hai được tái hiện RED (JUnit và probe GL thực), sửa rồi GREEN. Full reactor140test, failures0/errors0; .tools/native-packages-final-verify.log. GL probe pass và ảnh screenshots/native-packages.png đã xem trực tiếp; launcher smoke exit0; native JAR có0entry JavaME. Review lại không còn finding important.

Giới hạn: native gameplay vẫn là local demo, chưa thay đầy đủ nghiệp vụ/UI/giao thức cũ; server assessment là thiết kế dựa bằng chứng, không phải production server đã phục dựng. Không can thiệp phiên game gốc đang chạy hoặc tự đăng nhập.

## Native login/role binary vertical slice — 2026-10-31

User rejected package-only/demo work. DesktopLauncher now defaults to NativeLoginGame; JSON demo requires explicit swordol.demo=true. New feature packages modules.login and ui remain in client-core, no new Maven project. Protocol typed writer matches original Packet bytes. Native account HTTP batch1077/1051 → handoff1052 → gameplay TCP connect → roles1080 → enter1081 → map1110 is implemented, not routed through JSON demo or JavaME runtime.

Local server.legacy gateway executes the same binary flow using injected verifier/roles, one-use expiring ticket and ownership validation. Server-Native.cmd lets user create a process-local account; PBKDF2 verifier, no DB or persistent world. UI Vietnamese rasterfont/labels, no password persistence or automatic network action. Actual server gameplay/account not accessed in tests.

Review findings reproduced/fixed: TCP idle timeout at character selection; four game sessions starving account pool (RED native-login-pool-red.log); timed-out HTTP publishing success (deterministic RED native-login-generation-red.log, generation invalidation fix). Channel index vs server row corrected against Business.ServerListOK with RED→GREEN test. Cancellation regression was added after cancellation implementation, not observed RED.

Final full reactor150tests failures0/errors0 (.tools/native-login-final-verify.log); real GL button-event local probe NATIVE_LOGIN_PROBE_PASS (.tools/native-login-probe.log). Screenshots native-login-roles.png and native-login-map-announcement.png visually inspected; launcher smoke exit0. Native JAR has0 JavaME/server implementation entries. Review confirmed no remaining important findings in this narrow scope. Native window opened for user, disconnected by default; original running game/capture unchanged.

Not complete: playable native world, entity/sprite animation, movement/correction, combat, inventory/NPC/quest flows, live server acceptance. Map announcement is explicitly not considered world completion. Guide docs/native-login.md and updated server assessment state these limits.

## Native world: map, user entity and movement — 2026-10-31

Continuation of Codex's unfinished native-login slice, whose own documents listed the world as the next part. Spec/plan: docs/superpowers/specs|plans/2026-10-31-native-world.md.

Task A: complete — protocol.legacy gained MapResource, MapReferences, MapResourceAssembly, PackedImages and WorldPackets. Tests RED first (.tools/native-world-codec-red.log). Parsers reproduce the independently verified Python export of captured map1101400 and rebuild map1105100's six downloaded PNGs byte for byte with their stored CRCs; outbound1005/1014 match original Packet bytes. LegacyWriter gained a tag7 binary field, documented as inbound-shaped for the local gateway because the original client never writes one.

Task B: complete — render gained LegacyMap, LegacyMapPlan, LegacyMapView, LegacyTileImages, LegacyImages, LegacyWorldRenderer; modules.world gained WorldState. The headless draw plan reproduces all6138 draw operations of the verified Python preview in order, so layer order, piece offsets and the rotation-bit transform are pinned across two independent implementations. RED first: .tools/native-world-state-red.log.

Task C: complete — the local gateway serves one captured map's resources (1407 chunks,1110,1126,1006) and checks every1005 against that map, answering an impossible move with an action3 correction and refusing a walk for a role the connection does not own. LegacySession decodes world packets and hands immutable values to the render thread; LoginFacade composes the world as a second proxy/mediator over the same session, since PureMVC's Facade is a process singleton and one session has one lifecycle.

Correction from real evidence: the captured streams send1006 action2 before the1407 resources, so the earlier assumption that world data only counts after a map load was wrong. WorldState now keeps the user across a map load; the test was rewritten around the captured order.

Review fixed: world input arriving before a world existed threw IllegalStateException into the render thread, and a dead session left a stale world on screen. Both reproduced RED (.tools/native-world-lifecycle-red.log) and fixed; client-core forks a JVM per test class because a second PureMVC Facade cannot be constructed in one JVM. The renderer also culls off-screen draws.

Verification: full clean reactor187 tests, failures0/errors0 (.tools/native-world-final-verify.log). GL probe NATIVE_WORLD_PROBE_PASS draws=6138 cell=22,30; the player walks from the server spawn20,30 and is stopped at22,30 by a cell the real captured map blocks, so collision was proven against real data. screenshots/native-world-map.png and native-world-walked.png viewed directly; launcher smoke exit0.

Not complete: character sprites and animation, movement interpolation, pathfinding, portals as transitions, combat, items, NPC/quest/chat, and any acceptance against a live server or account. The character is drawn as a labelled marker and documented as such. Guide docs/native-world.md.

## Kế hoạch dựng lại client bằng libGDX + PureMVC — 2026-10-31

User asked for a plan to rebuild the client on libGDX with PureMVC following the structure already in place. docs/superpowers/plans/2026-10-31-native-client-rebuild.md records the architecture rules (one facade per screen lifecycle, Proxy owns state and IO, Mediator owns the view, render owns no network, packets decoded off the render thread) and nine staged steps from the world slice through entities, HUD, inventory, NPC/quest, combat, map switching, social and platform work, with the dependency order and the limits that must stay documented.

## Một kiểu format cho cả repo — 2026-10-31

User asked for standard formatting. Java now goes through Spotless + google-java-format2.0.0 in the parent pom (`mvn spotless:apply` rewrites, `spotless:check` fails a drifted build);1.25.2 was tried first and breaks on this JDK26 because javac's Log.DeferredDiagnosticHandler changed. Python tools went through black at line length100. Whole-repo reformat was taken only after copying every .java file to .tools/format-backup, since this workspace has no Git. After reformatting: full reactor193 tests failures0/errors0, `spotless:check` clean, Python81 tests pass. No behaviour change was intended and none was observed.

## Công cụ tạo bundle từng tài nguyên — 2026-10-31

User asked to extract assets and build bundles one at a time. `AssetBuilder` gained a selection build that pulls the dependency closure, and `buildEach` that writes one bundle per asset, per type or per id prefix plus a `bundles.json` index with each bundle's file, size, SHA-256 and real contents. Bundle names are checked before they become file names, so an unusual asset id cannot write outside the output directory. Tests RED first (.tools/asset-selection-red.log), then6 new pipeline tests GREEN; pipeline suite55.

CLI: `list PROJECT`, `build PROJECT OUTPUT [ID...]`, `split PROJECT DIR asset|type|prefix [NAME...]`. User tool `Tao-Bundle.cmd` → `tools/analysis/asset_bundler.py`: Tk list with accent-insensitive search, selection build, per-asset split, per-type split, and a re-extract button that runs the documented decode chain and skips steps whose output exists. The tool never writes bundle bytes itself; it calls the same CLI the build calls.12 Python tests cover the logic, not the Tk window.

Ran on the real decoded project (1520 assets): per type gave image3.673.143 B, sprite4.799.553 B, ui343.137 B, binary87.830 B; per asset for72 UI assets gave72 files totalling353.787 B; one sprite alone gave63.302 B including the13 images it depends on. Runtime load verified with the existing probe: the sprite bundle loads185 assets (61 sprites +124 dependency images, all decoded by libGDX), the single-asset `legacy.ui.1` bundle loads1 asset with8 controls. Guide docs/asset-bundles.md.

## Sprite nhân vật gốc trong client native — 2026-10-31 (đang dở)

Giai đoạn2 của kế hoạch dựng lại client. Đã xong và xanh: `asset-runtime.SpriteData` đọc tài liệu sprite đã giải mã (bảng ảnh, module, frame, action) có giới hạn và từ chối bảng rỗng hoặc chỉ số ngoài bảng (`RuntimeJson.signed` thêm cho offset âm); `render.LegacyLook` là luật gốc dạng số học — MapObject chọn file sprite theo look, ISpriteImageInit ghi lại bảng ảnh theo sex/scan, getIspActionIndex đổi action+hướng thành action id, hướng đông là hướng tây vẽ gương (transformMirror/coordinateTransform); `render.LegacySpritePlan` dựng danh sách blit của một frame; `render.LegacySpriteBody` nạp đúng ảnh sprite cần và vẽ bằng GL, nhịp frame50ms lấy từ nextActionFrame chứ không tự đặt. Test RED trước (.tools/sprite-look-red.log), sau đó xanh; full reactor203 test failures0/errors0 (.tools/sprite-body-build.log).

Màn hình thế giới vẽ nhân vật bằng sprite thật thay cho ô màu: probe GL `NATIVE_WORLD_PROBE_PASS draws=6138 cell=22,30`, ảnh screenshots/native-world-walked.png đã xem trực tiếp — nhân vật đứng đúng ô, có tên phía trên.

Chưa xong trong giai đoạn này: trang bị/vũ khí (Role_Equip) chưa áp vào bảng ảnh; NPC và quái chưa có entity trong world (packet2030 đã đọc được schema từ capture nhưng chưa triển khai); nội suy bước đi giữa hai ô; sprite tải từ server (1407 action30..43) chưa nối vào phiên native.

