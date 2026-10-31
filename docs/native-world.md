# Thế giới native: bản đồ, nhân vật và di chuyển

Sau khi chọn nhân vật, client native không dừng ở thông báo bản đồ nữa. Nó nhận **tài nguyên bản đồ thật** của server qua packet1407, dựng lưới từ map-data, vẽ tile theo map-ref đúng thứ tự lớp của client gốc, đặt nhân vật vào ô server gửi trong1006 và đi lại bằng packet1005 có kiểm tra ô chặn cùng correction từ server.

Bản đồ dùng để nghiệm thu là map **1101400 Trung nguyên** lấy từ chính phiên chơi đã ghi lại (`captures/`), không phải map tự vẽ.

## Chạy thử trên máy

1. `Server-Native.cmd` → đặt tài khoản/mật khẩu **local riêng**. Gateway mặc định phục vụ bản đồ trong `client-core/src/test/resources/maps`; đổi bằng `scripts/run-native-server.ps1 -Map <thư mục map> -Spawn x,y`. Thư mục map là thư mục do `tools/analysis/extract_maps.py` xuất ra (có `map-data.bin` và `map-ref.bin`).
2. `SwordOL-Native.cmd` → tải danh sách máy chủ → đăng nhập → chọn nhân vật → **Vào game**.
3. Trong thế giới: **mũi tên** hoặc **WASD** để đi từng ô, **Esc** để rời. Thanh trên cùng hiển thị tên/ID bản đồ, kích thước lưới và ô hiện tại.

Ảnh tile lấy từ bộ ảnh gốc đã giải mã. Launcher tự dùng `assets/source/OL.swb` nếu có; nếu chưa chạy pipeline giải mã thì truyền `-LegacyAssets <bundle hoặc thư mục png>` cho `scripts/run-native-desktop.ps1`. Thiếu ảnh nào thì thanh trạng thái báo số lượng, bản đồ vẫn vẽ phần còn lại.

## Những gì đã chạy thật

- `1407` → `1110` → `1126` → `1006` → `1005` chạy trên socket thật với gateway local, dữ liệu bản đồ là bytes đã bắt được từ server gốc.
- Vẽ6138 lệnh ảnh cho map1101400, **khớp từng lệnh** với bộ dựng Python đã kiểm chứng (`tools/analysis/preview_maps.py`), không thiếu ảnh và không cảnh báo.
- Nhân vật xuất phát đúng ô20,30 server gửi; đi đông2 ô rồi **bị ô chặn thật của bản đồ giữ lại** ở22,30 — client tự từ chối bước thứ ba, server không phải sửa.
- Ảnh: `screenshots/native-world-map.png`, `screenshots/native-world-walked.png`.

## Code theo package

| Nơi | Vai trò |
|---|---|
| `protocol/.../legacy/MapResource` | map-data: header, lưới tile row-major `y*cols+x`, ba bitset |
| `protocol/.../legacy/MapReferences` | map-ref: định nghĩa tile, cờ0x2000/0x4000/0x0800, piece2/3/7 byte |
| `protocol/.../legacy/MapResourceAssembly` | gom chunk1407, giải nén ZIP có kiểm size/CRC, giới hạn16MiB |
| `protocol/.../legacy/PackedImages` | dựng lại PNG map tải về theo đúng số học RGB565 của client |
| `protocol/.../legacy/WorldPackets` | schema1110/1006/1082/1014/1005/1126 và writer1005/1014 |
| `client-core/.../render/LegacyMap` | lưới sống: tile, chặn, xoay, chặn bay, cập nhật ô |
| `client-core/.../render/LegacyMapPlan` | thứ tự vẽ3 lớp, offset và transform từng piece |
| `client-core/.../render/LegacyMapView` | tâm nhìn theo `setMapView` và đổi hệ toạ độ sang GL |
| `client-core/.../render/LegacyTileImages`,`LegacyImages` | nạp đúng ảnh map dùng, từ map tải về rồi tới bộ ảnh gốc |
| `client-core/.../render/LegacyWorldRenderer` | vẽ theo plan, cull ngoài khung nhìn |
| `client-core/.../modules/world` | `WorldState` (nhân vật, người khác, portal, bước đi), `WorldListener`, `WorldView` |
| `client-core/.../ui/NativeWorldScreen` | màn hình thế giới, phím đi, HUD |
| `server/.../legacy` | `World`/`Portal`: phát tài nguyên map, `1110`, portal,1006 và kiểm tra walk |

PureMVC: một facade cho cả phiên native (`LoginFacade`), trong đó `WorldProxy` giữ `WorldState`, `WorldMediator` nối `NativeWorldScreen`, command chạy intent trên render thread. Packet được decode ở thread IO rồi đẩy sang render thread dưới dạng giá trị bất biến; không có state dùng chung giữa hai thread.

## Giới hạn còn lại

- Nhân vật là **ô màu có tên**, chưa phải sprite gốc: ghép look/hair/weapon là giai đoạn sau.
- Chưa có animation, nội suy bước đi, tìm đường, chuyển map qua portal; portal chỉ là dấu vị trí.
- Chưa có combat, vật phẩm, NPC, nhiệm vụ, chat.
- Chưa nghiệm thu với **server/tài khoản thật**; mọi kết quả ở trên là gateway local phát lại bytes đã bắt được.
- Server local không có thế giới nhiều người chơi, không lưu trạng thái; walk chỉ kiểm tra "một ô kề và đi được".
- Renderer nạp một texture cho mỗi ảnh tile (chưa gộp atlas); bản đồ ở giới hạn schema sẽ cần atlas.
