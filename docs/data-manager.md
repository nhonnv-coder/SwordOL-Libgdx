# Quản lý và render dữ liệu SwordOL

Mở `Quan-Ly-Data.cmd` ở thư mục gốc. Công cụ dùng Python/Tkinter/Pillow đã có trên máy. Năm tab hoạt động với dữ liệu cục bộ:

- **Map:** dùng lại renderer ba lớp của client: nền, trang trí thấp, vật thể phía trước; zoom/pan, lưới, cản đường, portal và xuất PNG.
- **Quái/NPC:** tìm theo tên/look ID/cấp, xem thuộc tính, map đã thấy và tối đa8 quan sát mẫu có nguồn. Các gói sprite tải riêng đã thu được ưu tiên. Chưa có sprite thì hiển thị nhãn rõ trước khi dùng sprite dự phòng1000000 theo fallback của client; hình đó không được coi là ngoại hình đã xác nhận. Không tự suy luận body/skin/equipment.
- **Item:** cắt icon từ atlas bằng công thức ItemEx, xem thuộc tính quan sát và các gói mô tả1032 cùng nguồn. Giữ type ID signed như packet gốc và hiển thị thêm giá trị unsigned32 để tra cứu. Không suy ra giá/drop/thuộc tính chưa có.
- **Ảnh:** xem các PNG gốc đã giải mã, kích thước, ID và metadata.
- **Sprite/Animation:** xem cả sprite gốc và bộ sprite server khôi phục từ capture; chọn action/frame, bật playback10FPS, zoom và xuất PNG. Không diễn giải byte flag thành thời lượng animation.

Tìm kiếm hỗ trợ tên tiếng Việt có/không dấu. **Nạp lại báo cáo** đọc `server-data/latest.json` và danh mục map mới nhất; đây không phải thao tác dò dữ liệu server. Báo cáo được cập nhật bằng `Thu-Du-Lieu.cmd` hoặc lịch giám sát đã thiết lập. Ảnh/sprite thiếu mảnh được báo trong vùng preview, không tự thay bằng ảnh khác.

Khi nạp dữ liệu, tool dựng thêm cache phái sinh `dev/server-sprites/<sprite-id>/<hash>/` từ các gói1407/actions30..43 đã có trong capture. Pack ảnh/index được kiểm CRC, `.dat` dùng decoder hiện có. Không sửa capture hay tài nguyên nguồn. `source.json` ghi file capture, SHA-256 snapshot, offset và hash nội dung để truy nguồn.

Giới hạn đã kiểm chứng ở bộ dữ liệu hiện tại:87/87 icon item render được;61 sprite gốc có48 frame0 render được, những frame khác rỗng/thiếu image slot hoặc ảnh; khôi phục7 bộ sprite server, cả7 có frame0 render,3 bộ còn báo thiếu mảnh ảnh. Nhiều NPC nhóm look3 chưa có file sprite được chỉ định trong dữ liệu đã thu, nên còn dùng preview dự phòng có nhãn. Manager không phải editor thay đổi dữ liệu game hay trình điều khiển nhân vật.

Entry nguồn: `tools/analysis/data_manager.py`; cache sprite server: `tools/analysis/server_sprites.py`.
