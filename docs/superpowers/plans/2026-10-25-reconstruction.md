# SwordOL Reconstruction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or superpowers:subagent-driven-development to implement task-by-task. Steps use checkbox syntax.

**Goal:** Dựng nền tảng client libGDX/PureMVC và server demo mới, giữ bằng chứng client gốc và TODO rõ ràng.

**Architecture:** Một lõi nghiệp vụ mới và gateway riêng. Server gốc chỉ là nguồn đối chiếu, không được coi là backend do dự án quản lý.

**Tech Stack:** Java, Maven multi-module, libGDX 1.14.2, PureMVC chính thức, Gson JSON, JUnit; cấu hình mobile theo toolchain tương ứng.

**Spec:** `docs/superpowers/specs/2026-10-25-reconstruction-design.md`

## Global Constraints

- Android, Windows, iOS; không tuyên bố build iOS trên Windows.
- Không gửi account hoặc gameplay lên endpoint gốc tự động.
- Giao thức mới TCP NDJSON, frame tối đa 16 KiB; loopback:7777 mặc định.
- Request: requestId/type/payload. Response: requestId/type/ok/payload/error.
- Luật chưa có bằng chứng là demo/TODO, không phải nghiệp vụ gốc.

## Review Focus

- Frame quá dài/JSON lỗi không được làm chết server hoặc tăng bộ nhớ không giới hạn.
- Session của kết nối khác không được điều khiển nhân vật.
- Request lặp không nhận thưởng nhiều lần.
- Callback sau đóng màn hình không được cập nhật view đã dispose.
- Tài nguyên khác hoa/thường không được ghi đè nhau khi xuất.

## Task 1: Phân tích và tài nguyên

Files: `tools/analysis/*`, `docs/analysis/*`, `dev/assets/*` (generated, ignored).

- [x] Kiểm kê/decompile offline JAR; ghi endpoint, packet và luồng có dẫn class/method.
- [x] Test trích xuất tên trùng, path traversal và hash; chạy RED rồi GREEN.
- [x] Xuất manifest tài nguyên, mapping tên nguyên bản sang tên lưu an toàn; báo cáo giới hạn.

## Task 2: Protocol và server cơ bản

Files: `protocol/**`, `server/**`; JUnit trong từng module.

Interfaces: request `{requestId:string,type:string,payload:object}`, response `{requestId,type,ok,payload,error}`. Types `LOGIN`, `ENTER_WORLD`, `MOVE`, `ATTACK`, `PING`. Login payload `{name}`; MOVE `{x,y}`; ATTACK `{targetId}`. Login trả sessionId; state trả `{player:{id,name,x,y,hp,gold},monster:{id,x,y,hp},map:{id,width,height}}`. ENTER_WORLD/MOVE/ATTACK trả state qua `payload`. Mỗi socket giữ riêng session; không nhận session do client khai để chiếm phiên.

- [x] Viết/runs test RED cho framing, lỗi input, movement, session, lệnh lặp.
- [x] Cài codec, domain service và socket adapter, giới hạn worker/timeout/frame.
- [x] Chạy GREEN và smoke socket; tạo jar server chạy được.

## Task 3: Client PureMVC/libGDX

Files: `client-core/**`, `lwjgl3/**`, mobile launchers/config.

Consumes Task 2 contract. Facade đăng ký proxy/command/mediator; View chỉ quản lý Scene2D.

- [x] Test RED command/notification và cleanup mediator.
- [x] Cài network proxy, login/world commands, mediator, UI và launcher.
- [x] Test GREEN; compile/run desktop nếu host cho phép; cấu hình mobile và chỉ rõ prerequisite.

## Task 4: Tích hợp và phát hành

Files: root build, `README.md`, `docs/TODO.md`, `deploy/**`, `scripts/**`.

- [x] Build reactor, chạy toàn bộ test và smoke qua socket.
- [x] Rà soát độc lập, sửa lỗi quan trọng với regression test.
- [x] Hướng dẫn chế độ đối chiếu, chạy local, đóng gói/ký/bảo vệ mã; ma trận đã/chưa kiểm chứng.
- [x] Cập nhật ledger, đánh dấu từng task và báo đúng phần còn thiếu.

## Giai đoạn tiếp theo: Asset Pipeline và Resource Editor

**Trạng thái:** Task5 theo plan `2026-10-25-asset-registry.md` và Task6 theo plan `2026-10-25-map-editor.md` đã triển khai. Task7–10 (UI, sprite, font, liên kết/đóng gói) còn chưa triển khai.

### Task 5: Registry và build tài nguyên

Files dự kiến: `asset-pipeline/**`, `asset-runtime/**`, `assets/source/**`; output `dev/**`.

Interfaces dự kiến: `AssetRegistry.load(projectPath)`, `validate(assetId)`, `findUsages(assetId)`, `build(projectPath, outputPath)`; tên/signature cụ thể được chốt trong plan thực thi riêng sau khi chọn công cụ.

- [x] Viết test RED cho ID trùng, dependency thiếu/cycle, collision đường dẫn, schemaVersion không hỗ trợ và build lỗi không phá output cũ.
- [x] Cài registry, schema migration, import manifest gốc giữ provenance/hash và builder CLI có output deterministic.
- [x] Chạy GREEN; client tải bundle đã build, báo lỗi version/asset rõ ràng.

### Task 6: Công cụ desktop và tilemap

Files dự kiến: `resource-editor/**`, `asset-pipeline/**/map/**`, `asset-runtime/**/map/**`.

- [x] Chốt hướng công cụ; kiểm thử RED create/save/reopen, undo/redo, tile flip, layer, tọa độ và portal reference lỗi bằng fixture nhỏ có kỳ vọng độc lập.
- [x] Cài project browser/inspector/preview; người dùng chọn editor map riêng, không tích hợp Tiled.
- [x] Build map hữu hạn orthogonal gồm visual/collision/spawn/portal từ một nguồn; client/server kiểm tra cùng version.
- [x] Nghiệm thu tạo mới và sửa một map, đóng/mở editor, chạy client/server trên map đó; ghi rõ unsupported map types.

### Task 7: UI layout và skin

Files dự kiến: `resource-editor/**/ui/**`, `asset-pipeline/**/ui/**`, `asset-runtime/**/ui/**`, `client-core/**`.

- [ ] Test RED hierarchy/ID/action/style lỗi, safe save, round-trip layout và event binding qua Mediator.
- [ ] Cài layout editor tạo/sửa widget, properties, anchor/padding/style/text key; skin tooling quản lý style/atlas/font riêng.
- [ ] Import `.ui` gốc theo các control đã hiểu; lưu thông tin unsupported, không tự mất dữ liệu.
- [ ] Dùng editor sửa UI đăng nhập thật; preview nhiều aspect ratio và chạy thao tác qua Mediator trong client.

### Task 8: Sprite và animation

Files dự kiến: `resource-editor/**/sprite/**`, `asset-pipeline/**/sprite/**`, `asset-runtime/**/sprite/**`.

- [ ] Test RED duration/pivot/trim, frame bị thiếu, stable ID sau repack, marker/loop và malformed legacy record.
- [ ] Cài import spritesheet/atlas, timeline tạo/sửa frame, duration, pivot, hướng, hitbox và preview step/play.
- [ ] Hoàn thiện decoder `.p/.dat` bằng fixture gốc và ảnh đối chiếu, không gán phần chưa hiểu là dữ liệu chuẩn.
- [ ] Build atlas và animation manifest; client dùng cùng renderer với editor và chạy một animation đã chỉnh.

### Task 9: Font và tiếng Việt

Files dự kiến: `resource-editor/**/font/**`, `asset-pipeline/**/font/**`, `assets/source/fonts/**`.

- [ ] Test RED glyph thiếu, fallback, metric/kerning sai, overflow atlas và profile sai.
- [ ] Cài nhập font/profile, glyph preview, bake `.fnt` + PNG/atlas; quản lý metadata nguồn và quyền sử dụng.
- [ ] Preview Unicode tiếng Việt NFC/NFD, dấu ghép, clipping, spacing và nhiều DPI; phát hiện glyph thiếu khi build.
- [ ] Dùng font đã tạo trong UI client thật; kiểm chứng mobile riêng khi có môi trường.

### Task 10: Liên kết công cụ và đóng gói

- [ ] Test end-to-end create/edit/save/reopen/build/load cho cả4 loại; broken references và migration rollback.
- [ ] Đóng gói editor Windows cùng hướng dẫn và project mẫu, không kèm bí mật hoặc mapping release của game.
- [ ] Thêm headless validation/build vào CI; xác nhận output theo platform và giữ artifact/hash/schema version.
- [ ] Review độc lập, kiểm tra UI bằng ảnh và cập nhật bảng đã/chưa kiểm chứng.

