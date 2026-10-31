# Web Studio

Web app quản lý asset chuyên nghiệp, chạy local. Mở bằng `Web-Studio.cmd` (cần Python + Pillow). Server stdlib (`tools/webapp/server.py`, không thêm dependency) phục vụ UP ở `http://127.0.0.1:8777` và API đọc/ghi file project, tái dùng toàn bộ logic Python đã có + test.

## Đã có (v1)

- **Maps**: danh sách map demo (`.map`) + **32 map gốc** từ catalog đã giải mã; tên map bỏ hậu tố kênh `…1Tuyến`. Mỗi dòng có kích thước + số NPC (xanh) / Quái (đỏ).
- **Collect data trong 1 map**: chọn map → **render map gốc** + bảng **Quái**, **NPC** (tên/level/HP/ô, lọc từ `observations` theo `mapId`), **Portal**, **Spawn**.
- **Gắn tên**: ô sửa tên + Lưu cho map và sprite, lưu vào `dev/OL/names.json` (gitignore) — không đụng dữ liệu gốc.
- **Sprites & Animation**: danh sách sprite; chọn → **chạy animation** theo action (preview 10 FPS), đổi action, **Export .ani/.sprite** ra `dev/OL` theo layout chuẩn.

## API chính

`GET /api/maps`, `GET /api/map/<idx>`, `GET /api/map/<idx>.png`, `GET /api/sprites`,
`GET /api/sprite/<id>/<frame>.png`, `POST /api/names` `{id,name}`, `POST /api/export/ani/<spriteId>`.

## Chưa có (kế tiếp)

- **Sửa Tiled map CRUD trong web** (vẽ/xoá tile, layer, thêm/sửa portal·spawn, lưu `.map`). Hiện sửa map trong Tiled qua CLI `tiled_map.py` ([map-editor.md](map-editor.md#sửa-map-trong-tiled)); đưa editor vào web là việc lớn tiếp theo.
- **Trình sửa cấu trúc animation** (thêm/sửa frame·action) trong web — hiện mới xem + export.
- NPC/Quái cho map `.map` demo (id khác hệ, chưa có observation).

## Giới hạn

- Server bind `127.0.0.1` (công cụ dev, không mở ra ngoài). Đóng cửa sổ server để dừng.
- Logic nền có test (`tools/analysis/test_*`); UI đã kiểm bằng ảnh thật (map gốc render, bảng Quái, đổi tên).
- Đọc dữ liệu trong `dev/` (gitignore) — chưa giải mã thì danh sách sprite/map gốc rỗng (map demo vẫn có).
