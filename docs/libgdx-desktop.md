# SwordOL desktop trên libGDX

Mở `SwordOL-libGDX.cmd` ở thư mục gốc. Bản portable ở `dist/SwordOL-libGDX/SwordOL-libGDX.cmd` có Java runtime đi kèm.

## Kiến trúc và phạm vi

Cửa sổ hiển thị là libGDX/LWJGL3 thật; game được tải từ JAR gốc qua lớp tương thích JavaME không mở cửa sổ MicroEmulator. Bộ nghiệp vụ, màn hình, tài nguyên và giao thức cũ vẫn do client gốc thực thi. Lớp mới sở hữu cửa sổ, framebuffer/GL texture, đổi kích thước, phím/chuột/text, xuất PNG, đóng phiên và tích hợp bộ ghi inbound.

Đây là **bản desktop tương thích gốc**, không phải bản69 class đã được viết lại thuần libGDX. Mọi nhãn trong app và tài liệu giữ ranh giới này. Bản JSON demo cũ ở client-core/lwjgl3 vẫn độc lập; không dùng server demo để giả lập khả năng tương thích server cũ.

Luồng đăng nhập/chọn nhân vật/map/chiến đấu/túi đồ/kỹ năng/chat/NPC trong JAR được giữ nguyên. Điều đó không có nghĩa tất cả luồng đã được nghiệm thu trên server thật, hoặc dữ liệu server đã đầy đủ. Chỉ tài khoản/phiên người dùng được phép sử dụng mới dùng để nghiệm thu; không tự đăng nhập, mua bán, xóa nhân vật hoặc thao tác vật phẩm trong smoke test.

## Điều khiển

- Chuột trái: thao tác game theo tọa độ sau khi quy đổi đúng vùng hiển thị.
- Mũi tên + Enter; numpad tương ứng phím số JavaME.
- F1/F2: hai phím mềm; Esc tương đương F2.
- Gõ chữ trực tiếp vào ô nhập; không ghi nội dung nhập vào log của lớp desktop.
- F10: toàn màn hình; F12: đổi giữa fit và pixel scale nguyên; F11: xuất ảnh toàn cửa sổ vào screenshots.
- Mất focus/đổi kích thước sẽ nhả các phím và con trỏ đang giữ để tránh kẹt di chuyển.

Độ phân giải logic640×480; có thể kéo cửa sổ mà không đổi layout game gốc. Vùng viền/thanh hướng dẫn không được gửi như thao tác vào game. Xử lý ảnh trên CPU trong lớp JavaME; libGDX tải framebuffer vào GL texture với nearest filtering. Chưa thay renderer từng tile/sprite bằng draw call native.

## Dữ liệu và capture

Hồ sơ/RMS riêng: `data/libgdx-desktop/`, không sửa hồ sơ giả lập trước đó. Bộ ghi phản hồi bật mặc định, tạo `captures/classic-<thời gian>-<id>/`; metadata giữ SHA-256 JAR gốc, runtime và host. Các công cụ tổng hợp/giám sát hiện có đọc các phiên này như capture trước.

Bản portable nằm trong workspace tự dùng captures/data của workspace. Nếu mang ra thư mục khác, dữ liệu nằm cạnh bản portable và cần chỉ đường dẫn cho collector khi nhập về. Không gửi mật khẩu/mã phiên lên công cụ tổng hợp hoặc chia sẻ capture chưa che bí mật. JAR gốc và Emulator.jar vẫn giữ nguyên bytes.

## Build và kiểm chứng

```powershell
powershell -ExecutionPolicy Bypass -File scripts/mvn.ps1 -o verify
powershell -ExecutionPolicy Bypass -File scripts/package-libgdx-desktop.ps1
# Tự đóng sau240 frame và xuất ảnh, không nhập tài khoản
powershell -ExecutionPolicy Bypass -File scripts/run-libgdx-desktop.ps1 -SmokeFrames 240 -Screenshot screenshots/classic-smoke.png
```

Nguồn host: `classic-desktop/src/main/java/vn/swordol/classic/`. Module khai báo API của Emulator.jar có sẵn theo system scope; file này được copy riêng vào package để giữ nguyên runtime cũ và tránh trộn class.

## Chuyển tiếp sang client native

1. Giữ compatibility desktop làm mốc so sánh chạy được cho màn hình/input/network.
2. Chuyển packet codec, session state và world state sang Java source độc lập, đối chiếu fixture capture đã che bí mật.
3. Dùng renderer tile/sprite đã xác minh ba lớp, rồi nối UI và state native. Mỗi luồng chuyển phải có ảnh/state/packet đối chiếu với mốc gốc.
4. Thay dần các dịch vụ JavaME; chỉ bỏ compatibility runtime khi toàn bộ luồng đã nghiệm thu. PureMVC của demo có thể dùng cho luồng native, không gán nó cho kiến trúc client gốc.

Phần chưa hoàn tất: port native nghiệp vụ/UI/network, renderer native đầy đủ, nghiệm thu end-to-end các chức năng bằng tài khoản thật, mobile và thay server production. Các phần này không được coi hoàn thành chỉ vì JAR gốc chạy trong host mới.
