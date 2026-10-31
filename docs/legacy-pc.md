# Bản PC tạm để thu dữ liệu server

Mở `Chay-Game.cmd` trong thư mục bản PC. Java runtime đã đi kèm, không cần cài Java. Đây là client JAR gốc chạy bằng MicroEmulator, không phải client libGDX demo. JAR gốc và giả lập được giữ nguyên bytes.

Màn hình ban đầu 480×640, có thể kéo đổi kích thước. Dùng chuột, phím mũi tên và Enter; F1/F2 tương ứng phím mềm. Nếu giả lập hiện danh sách ứng dụng, chọn Võ Lâm Hội Tụ và nhấn Start. Bạn tự nhập tài khoản trong game.

`Mo-Du-Lieu.cmd` mở thư mục dữ liệu. Mỗi lần chạy tạo `captures/<thời-gian>-<id>/` gồm:

- `session.json`: thời điểm và SHA-256 của client, giả lập, bộ ghi.
- `*-http.bin`: body phản hồi HTTP sau khi runtime đã bỏ HTTP headers.
- `*-tcp.bin`: luồng bytes server→client theo từng kết nối TCP.
- JSON cùng tên: endpoint không chứa query/userinfo, thời gian, số bytes, SHA-256 khi hoàn tất, trạng thái cắt ngắn.
- `emulator.log` và `emulator.err`: chẩn đoán của giả lập. Bộ ghi không ghi request, nhưng log của client gốc chưa được bảo đảm loại bỏ mọi bí mật.

Đóng cửa sổ game bình thường để hoàn tất metadata và hash. Trong lúc đang chạy, `.bin` được cập nhật liên tục, metadata có `completion: open`. Nếu tắt cưỡng bức, hash có thể chưa được chốt. Bộ ghi giới hạn 64 MiB mỗi luồng; `truncated: true` báo bản ghi không đầy đủ, kết nối game vẫn đọc tiếp bình thường. Phản hồi có thể chứa mã phiên/tài khoản: giữ captures trên máy, kiểm tra và che bí mật trước khi chia sẻ.

Trong workspace, giải mã bằng công cụ hiện có:

```powershell
python tools/analysis/decode_responses.py captures/SESSION/000001-http.bin --mode http --output response.json
python tools/analysis/decode_responses.py captures/SESSION/000002-tcp.bin --mode tcp --output response.json
```

Chỉ dùng `--mode http` với body giao thức account/game. Phản hồi tải tài nguyên, HTML lỗi, hoặc batch chưa hoàn chỉnh không phải packet hợp lệ. Bộ ghi lưu các bytes thực sự được client đọc; body không được client đọc hết sẽ chỉ có phần đã đọc. Không suy ra dữ liệu server không gửi hay mã nguồn server từ captures.

## Tách map đã thu

```powershell
python tools/analysis/extract_maps.py captures dist/SwordOL-PC-Capture/captures --output dev/server-maps
```

Đọc danh mục `dev/server-maps/catalog.json` để tìm bản xuất mới nhất cho từng map. Mỗi map có `map.json` (ô map theo hàng, bitset cản đường/xoay/cản bay, các gói portal/cập nhật), dữ liệu ZIP/thô nguyên bản, và manifest SHA-256. Map tải thêm pack ảnh riêng có thư mục `images/` chứa PNG được kiểm CRC. Ảnh còn lại dùng tài nguyên trong client gốc; chưa tạo ảnh ghép toàn map hoặc chuyển sang định dạng editor mới.

Công cụ đọc một snapshot của bản ghi đang mở, chỉ nhận chunk đã đủ và bỏ chờ frame TCP cuối nếu đang ghi dở. Chạy lại để lấy phần vừa tải thêm; các phiên bản cũ được giữ nguyên. Gói tài nguyên map đến trước gói tên map, nên công cụ gán tài nguyên cho gói tên đi sau, không dùng tên map trước đó. Không xuất thông tin nhân vật, chat hay tài khoản.

Từ source: `powershell -ExecutionPolicy Bypass -File scripts/package-legacy-pc.ps1`. Sau khi build agent, có thể chạy trực tiếp bằng `scripts/run-legacy-pc.ps1`. Hồ sơ giả lập nằm trong `data/emulator`, không dùng chung cấu hình giả lập cũ của máy.

Endpoint là cấu hình lịch sử trong JAR; khả năng kết nối/đăng nhập server thật phải được xác nhận bằng phiên sử dụng thực tế. Bản này cung cấp môi trường chạy và bộ ghi, không tự đăng nhập hoặc tự kiểm tra server từ xa.
