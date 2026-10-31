# Map Editor Implementation Plan — Task 6

> **For agentic workers:** Use executing-plans và test-driven-development. Mỗi task chạy RED trước GREEN; không có Git repository nên ledger là `docs/implementation-progress.md`.

**Goal:** Hoàn thành Task6 của `2026-10-25-reconstruction.md`: định dạng map nguồn có schema, editor desktop tạo/mở/sửa/lưu có undo/redo, và một map được client + server dùng chung cùng version.

**Architecture:** Một tài liệu map duy nhất trong `assets/source` chứa cả phần hiển thị (tilesets/layers) và phần nghiệp vụ (collision/spawns/portals). `asset-pipeline` validate và là model chỉnh sửa; `asset-runtime` đọc đúng bytes trong bundle cho cả client và server; `resource-editor` là ứng dụng desktop libGDX dùng renderer chung với client. Không có định dạng biên dịch thứ hai, nên không có đường cho client và server lệch nhau.

**Tech Stack:** Java 8 target, Maven, Gson 2.13.2, JUnit 4, libGDX 1.14.2 Scene2D, Python 3 stdlib cho sinh tileset mẫu.

**Spec:** `docs/superpowers/specs/2026-10-25-reconstruction-design.md`, phần Tilemap.

## Hướng công cụ đã chốt

Người dùng chọn **editor map riêng trong project**, không tích hợp Tiled ở Task6. Hệ quả: schema JSON do project kiểm soát; không tuyên bố tương thích TMX/TMJ; `.tmj` import là việc của giai đoạn sau nếu cần.

## Global Constraints

- Không sửa `dev/assets` hoặc JAR gốc; không nâng Java target; không thêm dependency ngoài libGDX/Gson/JUnit đã có.
- Map hữu hạn orthogonal. `orientation` khác `orthogonal`, `infinite: true`, layer dạng chunk, external tileset và object template phải bị **từ chối với thông báo rõ**, không bỏ qua im lặng.
- Tile word: `gid` ở bit 0–27, `FLIP_H = 1<<28`, `FLIP_V = 1<<29`, `FLIP_D = 1<<30`; bit 31 không dùng để mọi giá trị là số dương trong JSON. `gid = 0` là ô rỗng.
- Giới hạn map: width/height 1..512, tổng ô ≤ 262144, ≤ 16 layer, ≤ 8 tileset, ≤ 4096 spawn, ≤ 1024 portal, tile ≥ 1 px.
- Tọa độ gốc ở **góc dưới-trái**, cùng quy ước với Scene2D và với client hiện tại; `tiles[y * width + x]`.
- Tài liệu map là dữ liệu, không chứa mã thực thi hoặc script.
- Editor save bằng file tạm cùng thư mục rồi atomic replace; không atomic move thì báo lỗi và giữ bản cũ.
- `map.version` = SHA-256 của đúng bytes tài liệu trong bundle. Client và server đều tính từ bytes mình nạp; lệch nhau phải báo lỗi rõ, không tự chạy tiếp.
- Server vẫn là demo: map cung cấp bounds/collision/spawn/portal, không biến số liệu demo thành công thức gốc.

## Review Focus

- Tile word có bit lạ, gid vượt tileset, độ dài `tiles` sai so với width*height.
- Portal trỏ map không tồn tại, trỏ ô ngoài map đích, hoặc đặt trên ô blocked.
- Undo/redo sau khi save/reopen không được làm lệch tài liệu so với file trên đĩa.
- Save thất bại giữa đường không được để lại map rỗng hoặc mất bản cũ.
- Bundle bị sửa tay, version lệch giữa client và server, map asset thiếu trong bundle.
- Editor dispose texture/stage khi đóng; callback sau khi đóng cửa sổ không cập nhật view đã dispose.
- Server bounds phải theo map, không còn 20x15 hardcode; ô blocked không được đi vào.

## Task A: Định dạng map và validation trong pipeline

Files: `asset-pipeline/src/main/java/vn/swordol/assets/pipeline/Map*.java`, test tương ứng, `AssetRegistry.java`. Map code nằm cùng package `vn.swordol.assets.pipeline` để dùng `PipelineIO` (strict JSON, hash, canonical writer) mà không phải mở rộng visibility hoặc nhân bản parser.

Interfaces:
- `MapDocument.create(String id, int width, int height, int tileWidth, int tileHeight)`
- `MapDocument.parse(byte[] utf8Json)`, `byte[] MapDocument.toJsonBytes()` (canonical, deterministic)
- `MapDocument` accessors: `tile(layer, x, y)`, `setTile(layer, x, y, int tileWord)`, `addLayer(name)`, `removeLayer(index)`, `moveLayer(from, to)`, `blocked(x, y)`, `setBlocked(x, y, boolean)`, `spawns()`, `portals()`, `addSpawn(...)`, `addPortal(...)`
- `MapValidation.check(MapDocument doc, Set<String> knownMapIds, Map<String,Integer> tilesetTileCounts)`
- `AssetRegistry` validate asset `type == "map"` bằng `MapDocument.parse`, kiểm tra portal tham chiếu map id đã đăng ký và tileset asset tồn tại.

- [x] RED: round-trip create→toJsonBytes→parse giữ nguyên nội dung và bytes; tile flip giữ bit; layer add/remove/move; `tiles` sai độ dài, gid vượt tileset, bit lạ, orientation/infinite/chunk/external-tileset không hỗ trợ; portal trỏ map lạ, ô ngoài map đích, portal trùng ID; spawn ngoài map; schemaVersion lạ; width/height ngoài giới hạn.
- [x] GREEN: `MapDocument`, `MapCodec` canonical writer, `MapValidation`; nối vào `AssetRegistry` cho asset type `map` kèm kiểm tra tham chiếu chéo trong registry.
- [x] CLI: `validate PROJECT` báo lỗi map hỏng với exit nonzero; thêm lệnh `new-map PROJECT ID W H TW TH` tạo map rỗng và đăng ký asset.
- [x] Toàn bộ test asset-pipeline GREEN, bundle mẫu cũ build lại không đổi hash.

## Task B: Đọc map trong runtime dùng chung

Files: `asset-runtime/src/main/java/vn/swordol/assets/runtime/MapData.java` và `RuntimeJson.java`, test tương ứng. Trích các helper JSON strict đang là private trong `AssetBundle` ra `RuntimeJson` (refactor giữ 12 test cũ GREEN) rồi `MapData` dùng lại, thay vì nhân bản parser thứ hai trong cùng module.

Interfaces: `MapData.parse(byte[] bytes)`, `width()`, `height()`, `tileWidth()`, `tileHeight()`, `layerCount()`, `layerName(i)`, `tileWord(layer, x, y)`, `gid(int tileWord)`, `flipH/flipV/flipD(int tileWord)`, `isBlocked(x, y)`, `spawns()`, `portals()`, `portalAt(x, y)`, `version()`, `tilesets()`. Dữ liệu trả về immutable; `parse` không ghi filesystem.

- [x] RED: fixture map hợp lệ; thiếu field, sai kiểu, `tiles` sai độ dài, gid âm, bit 31, orientation lạ, vượt giới hạn, JSON lồng sâu, UTF-8 lỗi, portal/spawn ngoài map; `version()` khác nhau khi bytes khác nhau và bằng nhau khi bytes bằng nhau.
- [x] GREEN: `MapData` bounded parser dùng lại kiểu kiểm tra của `AssetBundle` (strict JSON, depth limit, integer grammar), accessors immutable và `version()` = SHA-256 bytes đầu vào.
- [x] Test đọc map thật từ bundle do CLI build, không chỉ fixture trong test.

## Task C: Resource editor desktop

Files: `resource-editor/**` (module Maven mới), `client-core/src/main/java/vn/swordol/client/map/MapRenderer.java`, root `pom.xml`, `scripts/run-editor.ps1`.

Interfaces:
- `EditorProject.open(Path projectRoot)`, `assets(String typeFilter)`, `openMap(String assetId)`, `saveMap(MapAssetHandle)` (temp + atomic replace), `createMap(...)`
- `MapEditSession`: `apply(EditorCommand)`, `undo()`, `redo()`, `canUndo()`, `canRedo()`, `isDirty()`, `document()`
- `EditorCommand` implementations: `PaintTile`, `FlipTile`, `SetBlocked`, `AddLayer`, `RemoveLayer`, `MoveLayer`, `AddSpawn`, `RemoveSpawn`, `AddPortal`, `RemovePortal`
- `MapRenderer.draw(Batch, MapData, TilesetTextures, float x, float y, float scale)` dùng chung cho client và preview editor.

- [x] RED: create→save→reopen bằng `EditorProject` thật trên thư mục tạm giữ đúng nội dung; undo/redo từng loại command khôi phục đúng trạng thái trước; undo quá đầu/redo quá cuối không làm gì; dirty flag; save khi validation lỗi phải giữ file cũ; save hai lần liên tiếp cho bytes giống nhau; project browser lọc theo type và báo ID không tồn tại.
- [x] GREEN: core editor headless (không phụ thuộc libGDX) đạt các test trên.
- [x] UI Scene2D: project browser bên trái, canvas chính vẽ qua `MapRenderer`, inspector bên phải (layer, tile đang chọn, flip, collision, spawn/portal), nút New/Open/Save/Undo/Redo và báo lỗi validation trên màn hình. Launcher `resource-editor` có chế độ smoke `-Dswordol.editor.smokeFrames` + screenshot như client.
- [x] Chạy editor thật trên desktop: mở project mẫu, sửa một map, save, mở lại, xem screenshot và exit 0.

## Task D: Tích hợp client/server và nghiệm thu

Files: `assets/source/demo/**`, `scripts/make-tileset.py`, `scripts/build-assets.ps1`, `client-core/**`, `server/**`, `protocol/**` nếu cần field version, `docs/**`.

- [x] RED: client test nạp map `world.demo-field` từ bundle và kiểm tra width/height/collision; server test MOVE vào ô blocked bị từ chối, MOVE ngoài bounds theo map bị từ chối, spawn monster lấy từ map, portal trả thông tin đích; test version lệch giữa client và server báo lỗi rõ.
- [x] GREEN: sinh tileset mẫu bằng `scripts/make-tileset.py` (deterministic, provenance ghi rõ do project sinh), thêm map `world.demo-field` + `world.town` vào project mẫu, build bundle; `DemoSession` lấy bounds/collision/spawn/portal từ `MapData`; state trả thêm `map.version` và `map.blocked` cần cho render; client vẽ map bằng `MapRenderer` và so `map.version` với bundle của mình.
- [x] Full suite Java + Python; CLI validate/build trên project mẫu; desktop client smoke screenshot trên map mới; editor smoke screenshot.
- [x] Cập nhật `docs/asset-pipeline.md` (mục map), `docs/verification.md`, `docs/TODO.md`, `docs/implementation-progress.md`, plan Task6 checkbox; ghi rõ map type chưa hỗ trợ và phần chưa kiểm chứng.
