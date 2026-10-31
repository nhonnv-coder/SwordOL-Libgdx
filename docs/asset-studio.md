# Asset Studio

Tool desktop xem, **thay part** và **export** sprite/animation gốc theo naming chuẩn ([project-structure.md](project-structure.md)). Mở bằng `Asset-Studio.cmd` (cần Python + Pillow + Tkinter).

## Bố cục (3 khu)

- **Trái — danh sách sprite**: tìm theo id/tên, chọn để mở.
- **Giữa — preview + playback**: canvas vẽ khung hình hiện tại; chọn **action**, kéo thanh **frame**, bật **Play** để chạy animation (10 FPS xem thử; nhịp gốc 50 ms ghi trong `.ani`).
- **Phải — PARTS + swap**: danh sách slot ảnh (`[slot] image_id`). Chọn một slot, nhập **image id** mới, bấm **Swap** → mọi khung dùng slot đó đổi part ngay (đúng cơ chế `ISpriteImageInit` gốc).

## Export

Nút **Export project** ghi sprite đang mở ra layout chuẩn vào `--out` (mặc định `dev/OL`):

```
sprites/data/<id>_<frame>.sprite   # mỗi khung 1 file  { parts:[{image,src,dst,transform}] }
animation/data/<id>.ani            # { frames:[{sprite,ms}], actions:[{id,frames}] }
```

## Chạy / tùy chọn

```
Asset-Studio.cmd
# hoặc: python tools/analysis/asset_studio.py --sprites dev/OL/sprites --images dev/legacy-images --out dev/OL
```

## Tab Map

- **Trái**: danh sách map — **map `.map`** (demo, `--maps`) + **map gốc `★`** đọc từ catalog đã giải mã (`--catalog`, mặc định `dev/server-maps/catalog.json`, 32 map).
- **Giữa**: render tile toàn map. Map `.map` dựng từ tileset (resolve qua `registry.json` của `--map-project`); map gốc dựng bằng `preview_maps` với ảnh gốc (`--orig-images`).
- **Phải**: thuộc tính (id, size, layer/tileset, portal/spawn, ô cản) + 2 nút Tiled:
  - **Export → Tiled**: ghi `.tmj` + **copy ảnh tileset** (dims thật). *Chỉ cho map `.map`.*
  - **Import Tiled →**: nạp `.tmj` đã sửa về `.map` (giữ id + asset tileset gốc).

Map gốc là định dạng raw (`tileRows`) nên hiện **chỉ xem**, chưa xuất Tiled — muốn sửa trong Tiled thì chuyển qua `.map` trước (bước raw→`.map` chưa có). Map gốc đã giải mã sẵn trong `dev/server-maps` (chạy `Thu-Du-Lieu.cmd` nếu chưa có).

Chi tiết chuyển đổi `.map` ↔ Tiled: [map-editor.md](map-editor.md#sửa-map-trong-tiled).

## Giới hạn

- Logic có test (`test_asset_model`, `test_tiled_map`, `test_map_render` — 12/12 xanh). Phần GUI chưa auto-verify được (cần màn hình) — chạy thử bằng mắt; log ở `.tools/asset-studio.err`.
- Part-swap: hiện sửa image slot; chưa sửa module/offset. UI (`.ui`) chưa vào Studio.
- Đọc dữ liệu đã giải mã trong `dev/` (gitignore). Chưa có JAR gốc giải mã thì danh sách sprite rỗng; map demo luôn có sẵn.
