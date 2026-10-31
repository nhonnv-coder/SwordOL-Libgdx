# SwordOL reconstruction

Nền tảng dựng lại client Java ME bằng **libGDX + PureMVC** và server Java demo. Bản này dùng để kiểm chứng kiến trúc và luồng client–server; chưa phải bản phục dựng đầy đủ hoặc server MMO production.

## Client libGDX native chia module bằng package

Mở `SwordOL-Native.cmd`: màn hình đăng nhập/nhân vật native dùng packet binary cũ đã thay demo làm desktop entry. Mở `Server-Native.cmd` để tự đặt tài khoản local riêng rồi kiểm thử HTTP account → TCP game → chọn nhân vật → nhận thông tin map. Chưa có world chơi được trong luồng native mới, chưa nghiệm thu server thật. [Hướng dẫn cụ thể](docs/native-login.md).

Client dùng PureMVC; module là các package `modules.login`, `ui`, `application`, `network`, `render` bên trong cùng `client-core`, không thêm project Maven cho từng phần. JSON demo cũ chỉ mở bằng `-Dswordol.demo=true`.

Xem [cấu trúc client và trạng thái tính năng](docs/native-client-modules.md), [đánh giá server cũ và hướng xây server mới](docs/analysis/server-reconstruction-assessment.md). Codec TCP/HTTP legacy và schema login nằm trong package `vn.swordol.protocol.wire` của project protocol có sẵn.

## Client gốc trên desktop libGDX

Mở `dist/SwordOL-libGDX/SwordOL-libGDX.cmd` (đã kèm Java), hoặc `SwordOL-libGDX.cmd` sau khi build. Cửa sổ, hiển thị và điều khiển dùng libGDX; nghiệp vụ/giao thức chạy từ JAR gốc qua lớp tương thích JavaME. Đây chưa phải bản viết lại hoàn toàn bằng libGDX. Bộ ghi dữ liệu nhận từ server bật mặc định.

Hướng dẫn: [docs/libgdx-desktop.md](docs/libgdx-desktop.md). Các lệnh client/server bên dưới dành cho bản demo JSON độc lập.

## Bắt đầu trên Windows

Cần JDK (đã kiểm thử bằng JDK 26 trên máy hiện tại), Python 3 cho công cụ phân tích. Mã Java ứng dụng biên dịch với `--release 8` để hỗ trợ core đa nền tảng.

```powershell
# Chỉ cần khi chưa có Maven cục bộ; tải từ Maven Central và kiểm tra SHA-512
powershell -ExecutionPolicy Bypass -File scripts/bootstrap.ps1

# Build và chạy toàn bộ kiểm thử Java
powershell -ExecutionPolicy Bypass -File scripts/mvn.ps1 verify

# Hai terminal riêng
powershell -ExecutionPolicy Bypass -File scripts/run-server.ps1
powershell -ExecutionPolicy Bypass -File scripts/run-client.ps1
```

Server mới mặc định `127.0.0.1:7777` và nạp map từ bundle tài nguyên (`--assets` để chỉ bundle khác). Nhập tên demo trong client rồi kết nối. Phiên demo chỉ tồn tại trong bộ nhớ; không dùng mật khẩu hoặc tài khoản thật. Xem nút điều khiển trong client để vào map, di chuyển và đánh quái. Công thức chiến đấu là dữ liệu demo, chưa khôi phục từ server cũ.

## Cấu trúc

| Thư mục | Vai trò |
|---|---|
| `protocol` | Hợp đồng request/response của server mới, framing có giới hạn |
| `server` | Xử lý lệnh demo, state của phiên, TCP gateway |
| `client-core` | Client native: package application (PureMVC), network, render và Scene2D UI |
| `lwjgl3` | Launcher Windows/desktop và jar có dependency |
| `asset-pipeline` | CLI registry, validation, legacy import, migration và bundle builder |
| `asset-runtime` | Loader kiểm tra toàn vẹn bundle và đọc map, dùng chung cho client/server/editor |
| `demo-bundle` | Bundle mẫu đã build, client và server cùng đọc một file |
| `resource-editor` | Editor map desktop: project browser, canvas, inspector, undo/redo |
| `android`, `ios` | Launcher/cấu hình mobile; xem hướng dẫn riêng và giới hạn kiểm chứng |
| `tools/analysis` | Xuất tài nguyên, giải mã `.p`/`.dat`/`.ui`/`.o`, đọc response cũ offline |
| `docs/analysis` | Kết quả phân tích có nguồn và mức chắc chắn |
| `dev/assets` | Tài nguyên xuất có mapping/hash, có thể tái tạo, không commit |
| `deploy` | Cấu hình container local |

## Server cũ và dữ liệu gốc

Quản lý/render theo loại: mở `Quan-Ly-Data.cmd` — các tab Map, Quái/NPC, Item, Ảnh, Sprite/Animation. Hướng dẫn và giới hạn ngoại hình: `docs/data-manager.md`.

Thu và tổng hợp map, quái/NPC, item từ các capture: mở `Thu-Du-Lieu.cmd`. Báo cáo: `dev/server-data/summary.md`; hướng dẫn và giới hạn: `docs/server-data-collection.md`.

Preview các map đã thu: mở `Preview-Map.cmd` để chọn map, zoom/pan, xem lớp cản đường/portal và lưu PNG. Hướng dẫn: `docs/map-preview.md`.

Bản PC tạm để dùng client gốc và thu phản hồi HTTP/TCP: chạy `dist/SwordOL-PC-Capture/Chay-Game.cmd`, mở dữ liệu bằng `Mo-Du-Lieu.cmd`. Java đã đi kèm. Cách dựng lại và giải mã capture: `docs/legacy-pc.md`; script đóng gói: `scripts/package-legacy-pc.ps1`.

**JAR gốc không được commit.** `docs/files/VoLamHoiTu.jar` và `docs/files/Emulator.jar` là binary
có bản quyền của bên khác nên repo không phát hành lại; tự đặt chúng vào `docs/files/` trước khi
chạy `scripts/package-legacy-pc.ps1`, `scripts/package-libgdx-desktop.ps1` hoặc các test đối chiếu
byte với writer gốc. SHA-256 của bản dùng để phân tích là
`16024c3ed0536b29217a67f657f110abc78549eae75ef6ac0bfdd0dc205b0ccd`; kết quả phân tích trong
`docs/analysis/` gắn với đúng bản đó.

`docs/files/VoLamHoiTu.jar` được giữ nguyên, không sửa đổi. Các endpoint trong JAR là **cấu hình lịch sử**, chưa được xác nhận còn hoạt động. Server game còn có thể được cấp động trong response đăng nhập. Không coi URL hardcode là endpoint game cuối cùng.

Ứng dụng mới mặc định chỉ kết nối server demo. Giao thức JSON mới không tương thích wire protocol Java ME. Cần dùng kết quả phân tích và bản ghi response để xây adapter cũ tiếp theo; không giả định có mã nguồn hoặc dữ liệu server gốc.

## Đóng gói

```powershell
# App Windows có runtime đi kèm; cần jpackage trong JDK
powershell -ExecutionPolicy Bypass -File scripts/package-windows.ps1

# Bản làm rối mã và app portable tương ứng
powershell -ExecutionPolicy Bypass -File scripts/obfuscate-desktop.ps1
powershell -ExecutionPolicy Bypass -File scripts/package-windows.ps1 -Protected

# Container server, chỉ expose ra loopback máy host
docker compose -f deploy/compose.yaml up --build
```

Editor tài nguyên:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build-assets.ps1 -UpdateClient
powershell -ExecutionPolicy Bypass -File scripts/run-editor.ps1 -Project assets/source/demo -Map world.demo-field
```

Jar server: `server/target/swordol-server.jar`. Jar desktop: `lwjgl3/target/swordol-desktop.jar`. Container vẫn dùng state trong bộ nhớ và sẽ mất phiên khi restart. Không mở cổng public trước khi hoàn thành các mục P0 trong `docs/TODO.md`.

## Kế hoạch và tình trạng

- Cấu trúc dự án (client/server/tools) và quy ước assets (nguyên mẫu → `OL.swb`): `docs/project-structure.md`
- Cơ chế animation/map/UI gốc: `docs/analysis/animation-map-ui.md`; tool xem/thay-part/export sprite: `docs/asset-studio.md` (`Asset-Studio.cmd`)
- Web app quản lý asset (maps + Quái/NPC mỗi map, sprite/animation, gắn tên, export): `docs/web-studio.md` (`Web-Studio.cmd`)

- Thiết kế: `docs/superpowers/specs/2026-10-25-reconstruction-design.md`
- Kế hoạch: `docs/superpowers/plans/2026-10-25-reconstruction.md`
- Bằng chứng client gốc: `docs/client-inventory.md`, `docs/analysis/`
- Phần còn thiếu: `docs/TODO.md`
- Phát hành và bảo vệ mã: `docs/release.md`
- Kết quả kiểm chứng: `docs/verification.md` (ghi theo kết quả chạy thực tế).
- Pipeline tài nguyên: `docs/asset-pipeline.md`; tạo bundle từng tài nguyên: `docs/asset-bundles.md` (`Tao-Bundle.cmd`)
- Giải mã tài nguyên gốc: `docs/legacy-assets.md`
- Tilemap và editor: `docs/map-editor.md`; Task5–6 đã triển khai, Task7–10 (UI, sprite, font, đóng gói) còn tiếp.
- Client native: `docs/native-login.md` (đăng nhập/nhân vật), `docs/native-world.md` (bản đồ gốc và di chuyển).
- Kế hoạch dựng lại client bằng libGDX + PureMVC: `docs/superpowers/plans/2026-10-31-native-client-rebuild.md`
