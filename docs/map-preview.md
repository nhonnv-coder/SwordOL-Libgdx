# Preview map đã thu từ server

Mở `Preview-Map.cmd` ở thư mục gốc dự án. Tool dùng Python, Tkinter và Pillow đã có trên máy, không kết nối mạng hoặc sửa tài nguyên gốc.

- Chọn map trong danh sách bên trái; ô tìm kiếm lọc theo tên hoặc ID.
- Cuộn chuột để zoom, giữ chuột trái và kéo để di chuyển. “Vừa cửa sổ” đưa map về giữa.
- Bật “Lưới”, “Cản đường” (đỏ), “Portal” (vàng).
- Bật/tắt riêng ba lớp “Nền”, “Trang trí thấp”, “Vật thể phía trước”. Thứ tự ba lượt vẽ và thứ tự hàng/cột trong mỗi lượt theo MapEx/Entity của client gốc.
- Đưa chuột lên ô map để xem tọa độ, tile ID, trạng thái cản đường và tên portal.
- “Lưu PNG” xuất toàn map với các lớp đang bật.
- Sau khi chạy công cụ tách map để cập nhật `catalog.json`, bấm “Nạp lại danh mục”.

Tool đọc `dev/server-maps/catalog.json`, tài nguyên map đã tách và ảnh từ `dev/legacy-images`. Ảnh tải riêng theo map được ưu tiên so với ảnh chung. Có thể truyền `--catalog` và `--images` khi chạy `tools/analysis/preview_maps.py` để chọn bộ dữ liệu khác.

Đây là preview tĩnh: dùng khung đầu animation, không hiển thị nhân vật/NPC. Có áp dụng ba lớp vẽ tĩnh của map và các gói cập nhật ô map, cản đường, portal đã thu. Thiếu ảnh hoặc tham chiếu lỗi sẽ hiện trên thanh trạng thái; không tự tạo ảnh thay thế. Vùng đệm quanh map giúp xem phần cây/nhà nhô ra khỏi lưới.

Xuất ảnh không mở cửa sổ:

```powershell
python tools/analysis/preview_maps.py --map 1101400 --export screenshots/preview-trung-nguyen.png
```
