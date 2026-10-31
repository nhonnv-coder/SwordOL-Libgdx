# TODO và ranh giới bản đầu

## Giai đoạn kế tiếp — bộ công cụ tài nguyên

- [x] Asset registry: ID ổn định, dependency/find-usages, provenance/hash, schemaVersion, migration có backup và bundle build có validation/atomic publication.
- [x] Công cụ desktop: tạo/mở/sửa/lưu, undo/redo, project browser, inspector và preview dùng renderer chung với game (map; các loại khác chưa).
- [x] Tilemap: tileset/layer/collision/spawn/portal; client và server đọc cùng bundle và so `map.version`. Trigger và tile property chưa có.
- [ ] UI: layout/hierarchy/anchor/style/localization/action binding qua Mediator; Skin là phần style riêng.
- [ ] Sprite: atlas/frame/pivot/offset/duration/direction/hitbox/marker và timeline preview; import `.p/.dat` có fixture.
- [ ] Font: TTF/OTF/bitmap profile, bake atlas, kerning/fallback, kiểm tra đủ glyph tiếng Việt và DPI.
- [ ] Nghiệm thu create/edit/save/reopen/build/load thật cho cả4 loại; đã đạt cho map, còn UI/sprite/font.
- [ ] Editor: đóng gói Windows, xác nhận khi đóng lúc chưa lưu, chọn tile bằng ảnh, zoom/pan, xóa tileset khỏi map.

Task5–6 đã triển khai, xem `docs/asset-pipeline.md` và `docs/map-editor.md`; Task7–10 (UI, sprite, font, liên kết/đóng gói) vẫn chưa triển khai. Editor hiện chỉ sửa map: UI, sprite và font vẫn chưa có trình biên tập.

## P0 — trước khi mở server public

- [ ] Xác thực tài khoản thật, hash mật khẩu phù hợp, giới hạn login, token hết hạn và thu hồi.
- [ ] TLS cho kết nối mới; không đưa account/password vào log hoặc fixture.
- [ ] PostgreSQL và schema migration; lưu nhân vật/hành trang, transaction thưởng/mua bán; backup và thử restore.
- [ ] Thế giới dùng chung nhiều người chơi, quyền sở hữu entity, tick loop và AOI. State demo theo phiên không phải MMO world.
- [ ] Chặn gian lận tốc độ theo thời gian, collision thật và tính hợp lệ của input; rate limit theo kết nối/tài khoản/IP.
- [ ] Quan sát vận hành: log có request/session ID đã che dữ liệu nhạy cảm, metrics, cảnh báo, lưu state khi shutdown.
- [ ] Kiểm thử tải, bộ nhớ, timeout, reconnect, worker saturation và vận hành lâu dài.

## P1 — tương thích client và nghiệp vụ gốc

- [ ] Capture response server cũ trong phiên được phép, lưu raw bytes + SHA-256 + timestamp + phiên bản client; loại bỏ bí mật trước khi chia sẻ.
- [ ] Đối chiếu decoder packet với response thực; chưa suy diễn mã hóa/nén từ mỗi tên hàm.
- [ ] Hoàn thiện adapter HTTP/TCP cũ, opcode/schema và trình tự phiên; không dùng JSON demo cho JAR gốc.
- [ ] Danh sách nhân vật/tạo nhân vật, lưu và chọn nhân vật thật.
- [x] Pipeline giải mã `.p/.ui/.dat/.o` sang PNG/JSON, xem `docs/legacy-assets.md`.
- [x] Map gốc trong client native: tài nguyên1407, lưới/collision, vẽ đúng lớp, nhân vật và di chuyển1005, xem `docs/native-world.md`. Còn lại: pathfinding, chuyển map qua portal và renderer sprite nhân vật/quái.
- [ ] Item/hành trang/trang bị, skill/cooldown/mana, quest/NPC/shop, chat/party/guild/trade.
- [ ] Công thức damage, drop, exp, hồi sinh và kinh tế: cần bằng chứng hoặc quyết định thiết kế riêng; không gán số demo thành số gốc.
- [ ] Fixture đối chiếu server cũ và mới theo state nghiệp vụ, tách khác biệt transport và thời gian.

## P1 — Android/iOS và phát hành

- [ ] Build/chạy Android trên thiết bị thật; test lifecycle pause/resume, mất mạng, bàn phím và tỷ lệ màn hình.
- [ ] Build/chạy iOS trên macOS/Xcode, simulator và thiết bị thật; signing/provisioning.
- [ ] CI đa nền tảng; kiểm tra dependency và giữ license thư viện.
- [ ] Ký Windows installer, Android AAB và iOS IPA bằng khóa của dự án; giữ khóa ngoài repository.
- [ ] Kiểm tra lại bản đã làm rối mã, lưu mapping riêng theo phiên bản để giải crash.
- [ ] Asset bundle có phiên bản và kiểm tra toàn vẹn; khi cần mã hóa tài nguyên, thiết kế phân phối khóa và giới hạn rõ rằng client vẫn phải giải mã để hiển thị.

## P2 — khi có số liệu thực

- [ ] Chuyển gateway sang Netty nếu cần nhiều kết nối; đo trước khi thay cơ chế IO.
- [ ] Tách world/map instance sang tiến trình riêng, routing/shard, Redis chỉ khi cần phối hợp liên tiến trình.
- [ ] Hot update dữ liệu game có validation/rollback, LiveOps và dashboard vận hành.
