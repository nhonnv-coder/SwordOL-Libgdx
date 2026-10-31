# Kế hoạch dựng lại client bằng libGDX + PureMVC

> Theo đúng cấu trúc đang có: **một Maven project `client-core`**, module là **package Java**, PureMVC là cơ chế điều phối duy nhất, libGDX lo cửa sổ/GL/input/Scene2D, `protocol.legacy` lo giao thức gốc, `asset-runtime` lo tài nguyên. Không tạo Maven artifact mới, không thêm event bus hay service locator thứ hai, không tạo module rỗng rồi coi là xong.

## 1. Luật kiến trúc áp dụng cho mọi giai đoạn

| Vai | Thuộc về | Không được làm |
|---|---|---|
| **Facade** | một facade cho mỗi *phiên có lifecycle riêng*: `modules.login.LoginFacade` giữ cả login và world vì chung một kết nối. PureMVC Facade là singleton theo tiến trình nên không thể có facade thứ hai cùng lúc; feature đăng ký Proxy/Command/Mediator của mình vào facade đang có | không tạo facade cho từng feature nhỏ |
| **Proxy** | giữ state và bọc IO: `GameSession` (mạng), `WorldProxy` (state thế giới), `BagProxy`, `SkillProxy`… | không gọi libGDX, không vẽ |
| **Command** | nhận intent từ Mediator, gọi Proxy, không giữ state | không chạy IO chặn |
| **Mediator** | nối Scene2D view với intent/state, cập nhật trên render thread | không mở socket, không parse packet |
| **View port** | interface thuần Java (`LoginView` kiểu) để test không cần GL | không rò rỉ kiểu libGDX vào test logic |
| `render` | `MapRenderer`, `TileSheets`, `LegacyMapRenderer`, sprite renderer | không biết tài khoản/mạng/PureMVC |
| `protocol.legacy` | framing, CRC/XOR, codec packet, schema | không giữ socket, không luật server |
| `asset-runtime` | đọc bundle, kiểm tra toàn vẹn | không game loop |

Quy tắc chung mọi giai đoạn:

1. **Packet vào → notification ra.** Một Proxy đọc socket trên thread riêng, đưa kết quả về render thread bằng dispatcher (`Gdx.app.postRunnable`). Generation của phiên làm mất hiệu lực callback cũ sau disconnect.
2. **Schema chỉ lấy từ reader gốc hoặc byte đã bắt được thật.** Không gán nghĩa cho field mà client gốc không đọc. Mỗi codec có test với shape thật.
3. **Server là nguồn chân lý về vị trí/giá trị.** Client chỉ dự đoán cục bộ rồi nhận correction; không tự ý giữ state lệch.
4. **Mỗi giai đoạn phải chạy được thật** (gateway local hoặc server thật khi người dùng cho phép) kèm ảnh GL, không dừng ở unit test.
5. **TDD**: viết test đỏ trước, implement, xanh; log RED/GREEN lưu trong `.tools/`.

## 2. Nền đã xong (không làm lại)

- `protocol.legacy`: framing TCP/HTTP, CRC/XOR, `WireWriter/TcpReader`, login/role schema, và **mới xong hôm nay**: `MapResource`, `MapReferences`, `MapResourceAssembly`, `PackedImages`, `WorldPackets` (35/35 test).
- `modules.login` + `ui.NativeLoginGame`: server list →1051 →1052 →1080 →1081 →1110, PureMVC đầy đủ, UI tiếng Việt.
- `server.legacy`: gateway local chạy đúng luồng binary để nghiệm thu.
- `asset-pipeline`/`asset-runtime`/`render`: bundle, map document, renderer dùng chung với editor.
- Pipeline giải mã tài nguyên gốc: 1321 ảnh,61 sprite,72 layout UI,4 file config → `OL.swb`.

## 3. Các giai đoạn

### Giai đoạn 1 — Thế giới chơi được — **xong 2026-10-31**

`modules.world` + `render.WorldMap/MapPlan/WorldRenderer`. Nhận1407 →1110 →1126 →1006 action2, dựng grid, vẽ3 lớp tile đúng thứ tự gốc, đặt nhân vật theo vị trí server, đi lại bằng1005 có kiểm tra ô chặn và nhận correction. Gateway local phát lại map1101400 đã bắt được thật.
*Nghiệm thu đã đạt*: probe GL `NATIVE_WORLD_PROBE_PASS draws=6138 cell=22,30`, ảnh `screenshots/native-world-*.png`, 187 test xanh. Xem `docs/native-world.md`.
*Chưa gồm*: animation nhân vật (hiện vẽ marker), combat, item.

### Giai đoạn 2 — Entity và animation gốc

`render.SpriteLibrary` đọc sprite `.p/.dat` đã giải mã; lắp look/hair/weapon theo `MapObject.resetHair`/`loadGodSprit`; `modules.world` thêm `EntityProxy` cho người khác (1014), NPC/quái (2030/2031/1146), action/frame theo `nextActionFrame`, nội suy di chuyển theo `setTarget`/`setMoveSpeed`.
*Xong khi*: nhân vật và NPC có hình gốc, hướng và nhịp frame đúng, đồng bộ vị trí nhiều entity trên gateway.

### Giai đoạn 3 — HUD và UI gốc

`modules.hud`: layout từ `.ui` đã giải mã nạp vào Scene2D (`CtrlManager`/`CustomScreen` tương đương), font tiếng Việt, minimap (`addToMiniMap`), thanh HP/MP/exp từ1017, chat box1004, nút tắt.
*Xong khi*: HUD dựng từ dữ liệu layout, không hard-code toạ độ; test mapping control id → actor.

### Giai đoạn 4 — Hành trang và vật phẩm

`modules.inventory`: 1008/1032/1313 (thông tin loại và chi tiết),1009 thao tác,1535 mở túi,1101 vật phẩm trên đất. Icon lấy theo công thức atlas đã kiểm trong data manager.
*Xong khi*: mở túi, xem thuộc tính, trang bị/bỏ trang bị qua gateway; không tự giao dịch trên server thật.

### Giai đoạn 5 — NPC, hội thoại, nhiệm vụ, shop

`modules.npc`: 2030 thông tin NPC,2032 dialog,1523/1527 nhiệm vụ,1056 trade/shop. Dialog dựng theo schema gốc, text giữ nguyên.
*Xong khi*: mở được hội thoại NPC và danh sách nhiệm vụ từ gateway.

### Giai đoạn 6 — Chiến đấu và kỹ năng

`modules.combat`: 1010 action,1049/1060 battle effect/ack, cooldown/mana, target chọn bằng input. Công thức damage **không bịa**: client chỉ hiển thị giá trị server gửi.
*Xong khi*: đánh được mục tiêu trên gateway, hiệu ứng/HP đổi theo packet; test quy tắc hiển thị, không test "công thức gốc".

### Giai đoạn 7 — Chuyển map, portal, tìm đường

`modules.world` mở rộng: đi qua portal (1126 action0 + map mới),1141 find road, cache map đã nạp, giải phóng texture map cũ.
*Xong khi*: đổi map không rò texture, vị trí đúng sau khi sang map.

### Giai đoạn 8 — Xã hội

`modules.social`: chat kênh (1004),1107 bang hội, tổ đội (`queryTeamMember`), trade người–người. Lọc dữ liệu nhạy cảm khỏi log.

### Giai đoạn 9 — Nền tảng và phát hành

Android/iOS entry dùng chung `modules.*`, lifecycle pause/resume, mất mạng; đóng gói Windows; kiểm lại bản làm rối mã; CI đa nền tảng.

## 4. Thứ tự phụ thuộc

```
1 world ──> 2 entity ──> 6 combat
   │           │
   │           └──> 5 npc/quest ──> 4 inventory (icon, thưởng)
   └──> 7 portal/map switch
3 hud ──(song song sau 1)──> 4,5,6 dùng lại control
8 social: sau 3
9 platform: sau 3, chạy lại cuối mỗi giai đoạn lớn
```

## 5. Rủi ro và giới hạn phải ghi rõ

- Chưa nghiệm thu bằng **server/tài khoản thật**: mọi giai đoạn nghiệm thu bằng gateway local + byte đã bắt được. Khi người dùng cho phép thử server thật thì mới được nói "đã chạy với server gốc".
- Sprite/animation và layout UI gốc còn phụ thuộc chất lượng pipeline giải mã; file nào chưa giải mã được thì ghi warning, không vẽ bừa.
- Luật nghiệp vụ (damage, drop, exp, giá) **không** suy từ tên hàm; hoặc có bằng chứng, hoặc khai là thiết kế mới có phiên bản riêng.
- Maven không cưỡng chế ranh giới package; ranh giới giữ bằng review và test, chỉ tách artifact khi có lý do build thật.
