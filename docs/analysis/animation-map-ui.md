# Cơ chế animation, map, UI (client gốc)

Phân tích suy từ reader đã kiểm thử, không bịa nghĩa. Nguồn: `asset-runtime.SpriteData`/`MapData`, `render.Look`/`SpritePlan`/`SpriteBody`, `tools/analysis/decode_sprites.py`/`decode_ui.py`.

## 1. Animation (sprite `.dat`)

Bốn bảng, mỗi cấp tham chiếu cấp dưới (`SpriteData`):

| Bảng | Là gì |
|---|---|
| `images[]` | id ảnh legacy — **các "part"** (thân, tóc, vũ khí…). Slot = 0 nghĩa là không vẽ |
| `modules[]` | một **Module** = hình chữ nhật `(x,y,w,h)` cắt ra từ `images[imageIndex]` |
| `frames[]` | một frame = danh sách **Placement** `(module, x, y, transform)`; `transform` 0–7 là MIDP `drawRegion` (lật/xoay) |
| `actions{id → [frame]}` | một action phát một dãy frame theo thứ tự |

**Dựng hình một frame** (`SpritePlan`, theo `ISpriteEx.paint`): mỗi placement → module → rect của một ảnh trong `imageTable` → blit tại offset (không gian pixel hướng xuống, gốc tại entity). `SpriteBody` vẽ bằng GL, nhịp **50 ms/frame** (`Look.FRAME_MILLIS`, từ `nextActionFrame`); step wrap để tick liên tục.

**Hướng:** `Look.actionId(action, dir) = action*100 + dir`; frame lưu hướng **tây**, hướng **đông vẽ gương** (`mirrored`, `mirrorTransform`, `mirrorOffsetX`) — không lưu frame riêng cho đông.

### Part swap = ghi lại bảng `images` (`Look.imageTable`)
Đây là cơ chế thay part của bản gốc. Với look người chơi (look 1 hoặc 2), bảng ảnh của sprite **bị ghi đè theo sex/scan** thay vì dùng bảng khai báo:
- Slot `1, 8, ≥11, 20` → 0 (bỏ).
- Slot `9, 10` → giữ nguyên.
- Slot còn lại: `base = id / 10_000_000`; nếu `base < 2` thì `base *= look`; rồi `id = base*10_000_000 + (id/100_000 % 100)*100_000 + scan*1000`.

→ Cùng một **template `.dat`** (module/frame/action giữ nguyên), chỉ đổi `images[]` là ra ngoại hình khác. `Look.spriteId(lookFace)` quyết định template nào được nạp (nghề, mount, crop…).

## 2. Map (`MapData`)

Lưới orthogonal hữu hạn, ô `(0,0)` ở **góc dưới-trái**, tile xếp **row-major**. Dùng chung client↔server để không lệch.
- Kích thước: `width/height` ô, `tileWidth/tileHeight` px.
- **Nhiều lớp** (`layerCount`, `layerName`): bản gốc vẽ 3 lớp tile đúng thứ tự.
- `tileWord(layer,x,y)`: GID kiểu Tiled — `GID_MASK 0x0FFFFFFF` + cờ `FLIP_H/V/D` (bit 28/29/30).
- `tilesets()`: mỗi Tileset có `asset`, `columns`, `tileCount`, `firstGid` → tra GID về ô ảnh.
- `isBlocked(x,y)`: lớp cản đường; `spawns()`, `portals()`: điểm sinh và cổng chuyển map.

## 3. UI (`.ui`, `decode_ui.py`)

Một file = **một screen** chứa danh sách **control**. Mỗi control: `id`, `class_flag`, **common block** rồi field riêng của lớp.
- Lớp: `2 button, 4 list, 7 edit, 11 text, 12 grid`. Lớp lạ → reader gốc trả về không đọc byte → lệch offset, nên decoder **từ chối** file lạ thay vì đoán.
- Common block (23 byte): 4 byte neighbour `left/right/up/down` (-1 = không), 4 short `x/y/w/h`, int `text_color`, int `position_index`, short `mode`, byte `flags` tối ưu.
- Field riêng ví dụ: button có `image_id/image_index`, edit có `char_limit`… (giữ nguyên tên lưu trữ, không đổi nghĩa).

→ HUD dựng từ control id + toạ độ trong file, **không hard-code** — khớp `CtrlManager`/`CustomScreen` gốc.

## Hệ quả cho tool thay part + quản lý

- **Thay part** chỉ cần sửa `images[]` của template `.dat` (và tùy chọn áp `Look.imageTable`), giữ nguyên module/frame/action → preview lại bằng `SpritePlan`.
- **Quản lý**: ba đối tượng (animation theo action/frame, map theo lớp/tile/portal, UI theo control) đều đã có reader + render; tool chỉ cần ghép view + sửa `images[]`/metadata, không tự dựng lại định dạng.
