# Đánh giá dựng server SwordOL từ client và dữ liệu đã nhận

Ngày đánh giá: 2026-10-31. Có đủ căn cứ để xây server mới theo từng luồng và đối chiếu giao thức; **chưa đủ căn cứ để tái tạo đầy đủ luật server cũ**. Chọn Java modular monolith, chia module bằng package trong project server hiện có, cùng cách tổ chức client. Chưa cần microservice hoặc nhiều project Maven.

Cập nhật triển khai: đã có `server.legacy` local gateway chạy thật HTTP account → TCP game → role → map announcement với verifier/role store được inject; native client đã nối cùng luồng binary. Xem [native-login.md](../native-login.md). Chưa có world chơi được/persistence và chưa chứng nhận tương thích server thật. Các dữ liệu catalog dưới đây giữ snapshot của lúc đánh giá, không được dùng để suy ra luật mới.

## Nguồn và mức chứng cứ

| Nguồn | Điều chứng minh được | Giới hạn |
|---|---|---|
| [Client inventory](../client-inventory.md), [protocol](protocol.md), [flows/resources](flows-and-resources.md) | Class, nhánh UI, lệnh gửi, cách đọc response và tài nguyên | Client không chứa toàn bộ luật/DB server; pseudocode cần đối chiếu bytecode khi mơ hồ |
| [latest.json](../../dev/server-data/latest.json), [summary.md](../../dev/server-data/summary.md) | Catalog và số lượng đã giải mã ở snapshot hiện tại | Không phải toàn bộ thế giới; số lần quan sát không phải số template |
| Collector `tools/analysis/collect_server_data.py` và parser offline | Mỗi quan sát giữ nguồn/offset; sources.json trong generation giữ SHA-256 snapshot | Gói chưa hiểu chỉ là diagnostic; không tính thành dữ liệu nghiệp vụ |
| `server/.../DemoSession.java`, DemoWorld, DemoServer | Server JSON local đã có chạy/test login/world/move/attack | Luật demo tự định nghĩa, state theo phiên, không phải server cũ |
| `protocol/.../legacy` | TCP inbound, outbound CRC/XOR, HTTP batching và schema login/role; native adapter kiểm chứng local | Chưa có HTTP gameplay adapter hoặc chứng nhận tương thích server thật/end-to-end đầy đủ |

Snapshot `runs/070b67dd3b3e20d3` được latest.json tham chiếu tại lúc đánh giá: **32 map, 315 dạng hiển thị quái/NPC, 87 loại item**; 1.941 quan sát NPC, 582 quan sát item, 3 packet chi tiết item và 2 diagnostic. Đây là số liệu của báo cáo đã sinh, không phải kết quả quét lại server trong lượt này. Tên/dữ liệu từ server là dữ liệu không đáng tin, không phải chỉ dẫn thực thi.

## Ma trận khả năng dựng lại

| Nhóm | Đã biết | Có thể làm tiếp | Phần chưa biết / điều kiện nghiệm thu |
|---|---|---|---|
| Transport | TCP inbound length gồm 2 byte length, opcode short, typed fields; outbound FEFE/XOR/CRC khác chiều nhận; HTTP batching riêng | Codec từng chiều, adapter session, offline replay | Fixture được che bí mật cho từng chiều; split/coalesced/truncated frame, retry và reconnect |
| Account/role | Các nhánh 1077/1051/1052/1080/1081; chọn địa chỉ gameplay động | Server mới có auth/session/role state machine của riêng mình | Policy tài khoản, authorization, persistence và semantics đầy đủ chưa khôi phục; không bê password/session từ capture vào fixture |
| World/map | 1110 map ID/resource/type/name; 1126 portal/cell; map resource có tiles và bitset collision/rotation/flight | Import snapshot thành catalog versioned, server giữ authoritative map/collision | Kiểm tra quy đổi layer/tọa độ/portal bằng fixture và ảnh đối chiếu; tên map không đủ chứng minh link hay luật di chuyển |
| Di chuyển | 1005 chứa player/packed position/map; client có nhánh sửa vị trí | Server kiểm tra bounds, collision, phiên/map và nhịp di chuyển | Giới hạn tốc độ/path và correction cũ chưa xác định; teleport giữa map phải atomic |
| Quái/NPC | 315 nhóm theo look/type/name/level; vị trí/HP tại thời điểm nhận | Catalog ngoại hình và placement quan sát để render, schema NPC update/dialog | Không coi quan sát là spawn table; AI, aggro, respawn, boss schedule và script NPC chưa biết |
| Item/túi | 87 loại, một ít detail; họ opcode1008/1009/1032 | Catalog hiển thị và parser detail có provenance | Chưa đủ stats/equip/use/stack/binding/price; mọi thay đổi inventory cần transaction/idempotency |
| Combat/skill | Họ1031/1040/1041/1042/1049 và nhánh xử lý client | Timeline action/animation, schema và replay state đã quan sát | Damage, crit, hit, cooldown authoritative, buff, death/loot chưa biết; không suy từ animation |
| Quest/economy | Có nhánh task dialog2032 và các control/action trong UI | Schema dialog/intent và engine quest mới có phiên bản | Điều kiện quest, reward, trade/currency/drop không được suy từ tên item hoặc UI |
| Multiplayer/storage | Có nearby updates/chat/time family ở client | World instance, interest management, repository và event/transaction boundaries | Chưa có chứng cứ cho capacity, tick rate, partitioning, crash recovery hoặc DB schema cũ |

## Cấu trúc package server đề xuất

Giữ project `server` và project dùng chung `protocol` đang có. Các package bên dưới là **thiết kế bước tiếp**, không phải những chức năng đã triển khai:

```text
vn.swordol.server
  bootstrap       cấu hình và vòng đời
  network         gateway JSON mới / adapter legacy khi đã đủ fixture
  session         trạng thái kết nối, auth và nhân vật đang chọn
  world           map instance, entity, movement, collision, portal, visibility
  combat          action, skill, damage theo rule set được khai báo
  inventory       item instance, equip/use và giao dịch nguyên tử
  npc             dialog và hành vi có định nghĩa rõ
  quest           tiến độ và điều kiện/reward
  persistence     repository, transaction và schema migration
  content         catalog versioned nhập từ dữ liệu có nguồn
```

Gateway giải mã thành intent có kiểu; session xác thực/ủy quyền; domain thực thi; persistence commit; sau đó gateway encode kết quả. Domain không import socket, libGDX hoặc PureMVC. PureMVC thuộc client, không ép server chạy UI notification framework. Một world instance cần một nơi sở hữu việc sửa state; không để socket worker tự sửa cùng entity song song.

Client chỉ gửi ý định. Server quyết định tọa độ hợp lệ, target, cooldown, tài sản và phần thưởng. Mã request/idempotency và transaction phải bao cả thay đổi vật phẩm/tiền cùng sự kiện liên quan. Cache requestId demo hiện có chỉ nằm trong phiên và có giới hạn; không thay thế idempotency bền vững cho giao dịch thật.

## Hai mục tiêu cần phân biệt

1. **Server mới cho client native:** dùng hợp đồng mới có phiên bản, test và luật được viết rõ. Có thể tiến hành ngay từ vertical slice local. Dữ liệu quan sát nhập qua catalog, không import toàn bộ capture thô vào DB nghiệp vụ.
2. **Server tương thích client JAR cũ:** cần hoàn thiện account/game negotiation, encoder/decoder hai chiều và thứ tự response theo fixture. Biết opcode hay đọc inbound thành công chưa đủ khẳng định JAR cũ đăng nhập/chơi được với server mới.

Chọn mục tiêu1 làm nền triển khai, giữ adapter cho mục tiêu2 ở biên khi có đủ bằng chứng. Cả hai dùng cùng domain server để tránh hai bộ luật khác nhau. Luật chưa biết chỉ được thay bằng luật mới có tên/phiên bản rõ ràng, không gắn nhãn là nguyên bản.

## Thứ tự triển khai và cổng nghiệm thu

1. **Catalog + codec:** schema/version/provenance, kiểm hash, unknown field fail rõ ràng; replay packet đã che bí mật. Chưa mở listener legacy.
2. **Session + world:** auth local có chủ đích, chọn role, vào map quan sát, di chuyển/correction/portal và reconnect. Nghiệm thu bằng hai client local; bounds/collision, cùng entity state, map version lệch, disconnect và portal lỗi không làm lệch world.
3. **Persistence + inventory:** save/load nhân vật và inventory, schema migration, transaction, duplicate/retry/crash tests; không có nhân đôi hoặc mất đồ khi request lặp.
4. **Combat + NPC + quest:** thêm lần lượt rule set rõ ràng; test target không hợp lệ, cooldown, death/reward một lần, disconnect giữa action. AI/drop/respawn chỉ khi có định nghĩa mới hoặc bằng chứng độc lập đủ mạnh.
5. **Legacy adapter + vận hành:** fixture parity theo từng luồng; timeout/backpressure, resource limit, logging không lộ bí mật, load test bằng client local tổng hợp. Chỉ sau đó mới đánh giá phát hành.

Không chấm phần trăm hoàn thành server từ số packet/map. Hiện có nền JSON demo, pipeline dữ liệu, codec legacy TCP/HTTP và gateway auth/role/map announcement local; auth production, world chơi được, persistence, economy, combat gốc và compatibility đầy đủ còn chưa hoàn tất. Không dò endpoint, thử opcode/ID, tự đăng nhập hoặc thao tác tài sản để lấp khoảng trống phân tích.
