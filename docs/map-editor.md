# Tilemap và resource editor — Task 6

Người dùng chọn **editor map riêng trong project**, không tích hợp Tiled ở bước này. Một tài liệu map duy nhất chứa cả phần hiển thị và phần nghiệp vụ; client và server nạp đúng cùng bytes từ bundle nên không có đường cho hai bên lệch nhau.

## Chạy

```powershell
# Build các module
powershell -ExecutionPolicy Bypass -File scripts/mvn.ps1 verify

# Sinh lại tileset/map mẫu khi cần (deterministic)
python scripts/make-tileset.py assets/source/demo/images/terrain.png
python scripts/make-demo-maps.py assets/source/demo/maps

# Build bundle và cập nhật bản dùng chung cho client/server
powershell -ExecutionPolicy Bypass -File scripts/build-assets.ps1 -UpdateClient

# Mở editor trên project mẫu
powershell -ExecutionPolicy Bypass -File scripts/run-editor.ps1 -Project assets/source/demo -Map world.demo-field

# Tạo map rỗng bằng CLI
java -jar asset-pipeline/target/swordol-assets.jar new-map assets/source/demo world.cave 24 18 32 32
```

Server đọc bundle trên classpath của chính nó; `--assets path/to/bundle.swb` cho phép chạy với bundle vừa build lại mà không đóng gói lại jar.

## Định dạng map

```json
{
  "schemaVersion": 1,
  "id": "world.demo-field",
  "orientation": "orthogonal",
  "width": 20, "height": 15, "tileWidth": 32, "tileHeight": 32,
  "tilesets": [{"name": "terrain", "asset": "tileset.terrain", "columns": 2, "tileCount": 4}],
  "layers": [{"name": "ground", "tiles": [1, 1, 2]}],
  "collision": [0, 0, 1],
  "spawns": [{"id": "slime-1", "kind": "monster", "template": "slime", "x": 3, "y": 1, "hp": 30}],
  "portals": [{"id": "to-town", "x": 19, "y": 7, "targetMap": "world.town", "targetX": 1, "targetY": 1}]
}
```

- Ô (0,0) ở **góc dưới-trái**, `tiles[y * width + x]`, cùng quy ước với Scene2D của client.
- Tile word: gid ở bit 0–27, `FLIP_H = 1<<28`, `FLIP_V = 1<<29`, `FLIP_D = 1<<30`; bit 31 không dùng nên mọi giá trị là số dương trong JSON. `gid = 0` là ô rỗng. `FLIP_D` là phép chuyển vị (transpose), đã kiểm chứng bằng ảnh: tile có sọc ngang trở thành sọc dọc.
- `firstGid` của tileset suy ra từ thứ tự khai báo (tileset đầu bắt đầu ở 1), không lưu trong file nên không thể mâu thuẫn.
- `id` của tài liệu phải trùng asset ID trong registry. `targetMap` của portal là asset ID của map đích.
- Field lạ ở mọi cấp đều bị từ chối, không bỏ qua im lặng.
- Giới hạn: width/height 1..512 và tối đa 262144 ô, ≤16 layer, ≤8 tileset, ≤4096 spawn, ≤1024 portal, tile 1..1024 px, hp 0..1000000.
- Kind của spawn: `monster`, `npc`, `player-start`.

### Chưa hỗ trợ (báo lỗi rõ, không bỏ qua)

`orientation` khác `orthogonal` (isometric, hexagonal, staggered), map vô hạn (`infinite`), layer dạng chunk, tileset ngoài (`source`), object template, object layer/image layer, animation tile, tile property tự do, và nhiều tile size khác nhau trong một map. Import TMX/TMJ của Tiled **không** được hỗ trợ: định dạng này là của project, không tuyên bố tương thích Tiled.

## Dependency và portal

Registry phân biệt hai quan hệ:

- `dependencies` của map phải khai báo **tileset image** mà map dùng. Đồ thị này phải là DAG.
- **Portal link** không nằm trong `dependencies`: hai map được phép trỏ lẫn nhau (cửa hai chiều) và điều đó sẽ là cycle. Registry theo dõi link riêng; `usages` vẫn báo đúng nơi dùng, nên xóa một map đang được portal trỏ tới vẫn bị phát hiện.

Khai báo portal target trong `dependencies` bị từ chối với thông báo rõ.

Validation chéo khi load registry: tileset phải là asset `image` đã đăng ký; portal phải trỏ tới asset `map` đã đăng ký, trong biên map đích, và cả ô nguồn lẫn ô đích đều không được blocked.

## Editor

`scripts/run-editor.ps1` mở `resource-editor`: project browser bên trái, canvas ở giữa, inspector bên phải.

- Công cụ: PAINT, ERASE, COLLISION, FLIP_H, FLIP_V, FLIP_D; chọn layer và tile trong inspector.
- Layer: thêm, xóa, đổi thứ tự vẽ. Spawn và portal thêm/xóa bằng ô nhập.
- Undo/redo có giới hạn 200 bước; lệnh bị từ chối không đổi tài liệu, không đổi lịch sử, không đặt cờ dirty.
- Save: validate toàn project trước, ghi file tạm cùng thư mục rồi atomic move, sau đó cập nhật `dependencies` nếu đổi. Save bị từ chối giữ nguyên file cũ.
- Preview dùng **đúng** reader runtime và renderer của client (`MapData` + `MapRenderer`), nên canvas không thể lệch so với game.

Chưa có trong bản này: xóa tileset khỏi map, sửa ảnh tileset, chọn tile bằng cách bấm vào bảng ảnh (bảng tile hiện là nút theo gid), kéo-thả nhiều ô, zoom/pan, hộp thoại xác nhận khi đóng lúc còn thay đổi chưa lưu.

## Client và server dùng chung một map

`demo-bundle` là module giữ bundle đã build; client và server đều phụ thuộc module này nên cùng đọc một file.

- Server lấy biên, collision, spawn và portal từ `MapData`; không còn hằng số 20x15 hay quái cố định trong mã. MOVE vào ô blocked trả `BLOCKED`, ra ngoài biên trả `OUT_OF_BOUNDS`, bước lên ô portal chuyển sang map đích tại ô đã khai báo.
- State trả thêm `map.id` và `map.version` (SHA-256 của đúng bytes map). Map không có quái thì state không có trường `monster`.
- Client so `map.version` của server với bản trong bundle của mình; lệch thì báo lỗi nêu cả hai version và không vẽ map sai.

## Sửa map trong Tiled

Thay vì dựng tile editor riêng, dùng **Tiled Map Editor** qua bộ chuyển `tools/analysis/tiled_map.py`:

```powershell
python tools/analysis/tiled_map.py export world.map world.tmj   # .map -> Tiled JSON, mở bằng Tiled
python tools/analysis/tiled_map.py import world.tmj world.map    # Tiled JSON -> .map (sửa xong)
```

Chuyển đổi xử lý khác biệt hai định dạng: tên field (`tileWidth`↔`tilewidth`, `layers[].tiles`↔`data`), **cờ lật GID** (bit 28/29/30 ↔ 31/30/29 của Tiled), và **gốc Y** (`.map` gốc dưới-trái ↔ Tiled trên-trái). `collision` thành tile layer riêng (gid=1 là cản), `portals`/`spawns` thành object group có property sửa được. Round-trip giữ nguyên tile/cản/portal/spawn (test `test_tiled_map.py`, xanh, gồm map mẫu thật).

Lưu ý: ảnh tileset Tiled trỏ `<asset>.png` cạnh file `.tmj` — copy png tileset vào cùng thư mục để Tiled vẽ đúng. Nếu map hiện ngược trên/dưới thì báo để bỏ bước lật Y.

## Giới hạn đã biết

- `MapDocument` (producer) và `MapData` (consumer) lặp lại luật schema vì asset-runtime không phụ thuộc asset-pipeline. `MapInteropTest` build bundle thật rồi đọc lại bằng runtime để bắt lệch; đã kiểm chứng bằng mutation (thêm field lạ vào writer thì test RED).
- Preview dựng lại `MapData` sau mỗi thao tác; đủ nhanh cho map mẫu, cần đo lại trước khi làm map rất lớn.
- Editor chưa đóng gói Windows, chưa chạy trên mobile, chưa có test tự động cho phần widget Scene2D (phần logic đã có test headless; phần vẽ kiểm bằng ảnh).
