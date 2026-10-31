# Cấu trúc dự án

Chia theo ba nhóm: **client**, **server**, **tools**. Đây là một reactor Maven; "nhóm" là cách đọc, không phải thư mục cha riêng — ranh giới giữ bằng dependency và review.

## Client (chạy game)

| Module | Vai trò |
|---|---|
| `client-core` | Lõi client: package `modules.login`/`modules.world`, `ui`, `network`, `application` (PureMVC), `render` |
| `lwjgl3` | Launcher desktop Windows + shaded JAR |
| `android`, `ios` | Launcher mobile dùng chung `client-core` |
| `classic-desktop` | Bản tương thích gốc để đối chiếu |

Lệnh: `SwordOL-Native.cmd` (client native), `SwordOL-libGDX.cmd` (bản tương thích).

## Server

| Module | Vai trò |
|---|---|
| `server` | `DemoServer`/`DemoSession`/`DemoWorld` (JSON demo) và `server.gateway` (gateway binary local để nghiệm thu) |
| `protocol` | *(dùng chung)* hợp đồng JSON (`Request`/`Response`/`FrameCodec`) và `protocol.wire` (framing/CRC/XOR, codec packet gốc) |
| `asset-runtime` | *(dùng chung)* đọc bundle, kiểm tra toàn vẹn, model map — client/server/editor cùng dùng |

Lệnh: `Server-Native.cmd` (gateway local).

## Tools (hỗ trợ)

| Thành phần | Vai trò |
|---|---|
| `asset-pipeline` | CLI registry/validate/build/split và **`atlas`** (xuất TextureAtlas libGDX) |
| `resource-editor` | Editor map desktop |
| `demo-bundle` | Bundle demo nhỏ đã build, commit sẵn (`bundles/demo.swb`) cho client/server/test |
| `tools/analysis/*.py` | Giải mã `.p`/`.dat`/`.ui`/`.o`, xuất tài nguyên, thu dữ liệu; `asset_bundler.py` là GUI gọi lại CLI Java |
| `tools/verification` | `BundleProbe`, `CaptureProbe` (kiểm chứng ngoài Maven) |
| `tools/native/FontAtlas.java` | Dựng atlas font |
| `legacy-capture` | Agent bắt response HTTP/TCP từ client gốc |

Lệnh: `Tao-Bundle.cmd` (tạo bundle), `Quan-Ly-Data.cmd` (quản lý dữ liệu), `Preview-Map.cmd` (xem map), `Thu-Du-Lieu.cmd` (thu dữ liệu).

## Assets: nguyên mẫu → bundle

Một **project nguồn** = `registry.json` + file thô xếp theo thư mục con. Build qua `asset-pipeline` ra **một `.swb`**. Bundle là ZIP có manifest + hash, nạp bằng `asset-runtime`.

```
dev/                   # TẤT CẢ tài nguyên gốc + trung gian — GITIGNORE (bản quyền)
  assets/              #   file thô extract từ JAR, kèm mapping + hash
  OL/                  #   project game đã giải mã (layout domain bên dưới)
  bundles-*/ server-*/ legacy-*/         # output trung gian của tool decode/collect
assets/                # CHỈ chứa thứ của "project thật"
  source/
    demo/              #   project mẫu tự tạo — COMMIT (test/checkout)
    OL.swb             #   bundle game build ra — GITIGNORE
  live/                #   png loose để chạy nhanh khi dev — GITIGNORE (tùy chọn)
```

### Layout project nguồn (domain)

```
<project>/
  registry.json
  maps/       images/0001.png   data/0001.map       # ảnh tile  +  map
  animation/  images/0001.png   data/0001.ani       # ảnh       +  animation (chuỗi khung hình)
  sprites/    images/0001.png   data/0001.sprite    # ảnh part  +  sprite = 1 khung hình
  ui/         images/0001.png   data/0001.ui        # ảnh       +  screen (control)
```

Mẫu đồng nhất mọi domain: **`<domain>/images/` + `<domain>/data/`**.

### Format (naming chuẩn)

| Đuôi | Là gì | Nội dung |
|---|---|---|
| `.sprite` | **1 khung hình** ghép từ part | `{ "parts": [ { "image", "src":[x,y,w,h], "dst":[x,y], "transform":0–7 } ] }` |
| `.ani` | **animation** = nhiều khung | `{ "frames":[{"sprite","ms":50}], "actions":[{"id","frames":[idx…]}] }` — `id = action*100+dir`, đông vẽ gương tây |
| `.map` | map | grid, layers, tilesets, portals, blocked |
| `.ui` | screen | danh sách control (id, type, x/y/w/h…) |

Registry `type`: `image, sprite, animation, map, ui` (+ `font, binary`). **Part-swap = đổi `image` của một part trong `.sprite`** (vd slot tóc dùng ảnh khác); nhiều `.sprite` chung bố cục thì swap theo lô. Cơ chế gốc: [analysis/animation-map-ui.md](analysis/animation-map-ui.md).

Luồng:

```
dev/assets  --(tools/analysis: export_assets → decode_* → build_legacy_project)-->  dev/OL/ (maps/ animation/ sprites/ ui/)
dev/OL/  --(asset-pipeline build)-->  assets/source/OL.swb
assets/source/OL.swb  --(asset-pipeline atlas)-->  <dir>/<name>.atlas + <name>N.png   # cho AssetManager/Scene2D
```

Quy ước đặt tên: **`OL`** = tài nguyên game thật; **`demo`** = project mẫu commit sẵn (dùng cho test và checkout đầu). CLI:

```powershell
$jar = 'asset-pipeline/target/swordol-assets.jar'
java -jar $jar build  dev/OL  assets/source/OL.swb      # project -> bundle
java -jar $jar atlas  assets/source/OL.swb  thu-muc-ra  OL           # bundle  -> TextureAtlas libGDX
```

**Tự build khi chạy:** `SwordOL-Native.cmd` (qua `run-native-desktop.ps1`) tự dựng `assets/source/OL.swb` từ `dev/OL` nếu bundle thiếu hoặc cũ hơn project, rồi mới chạy — không phải build tay. Maven `verify` **không** auto-build (giữ test deterministic/offline); build assets luôn là bước tường minh hoặc do launcher lo.

**Bản quyền:** mọi thứ trong `dev/` (thô + giải mã) và `assets/source/OL.swb` **không commit**. Chỉ `assets/source/demo/` (dữ liệu mẫu tự tạo) được commit. Không chỉnh bundle bằng tay — luôn build lại từ project nguồn.
