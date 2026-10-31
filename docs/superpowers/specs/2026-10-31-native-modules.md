# Client native chia module bằng package và căn cứ dựng server

User yêu cầu client libGDX/PureMVC mới và đánh giá server cũ. User đã sửa rõ: module nằm trong từng Java package, không tách Maven project. Giữ client-core/lwjgl3 và protocol/server hiện có; không thêm Maven module. Tự thực hiện theo ủy quyền trước, không hỏi lại.

## Thiết kế

- client-core: package application chứa Facade/Command/Proxy/Mediator/GameView; network chứa JsonConnection không import libGDX/PureMVC; render chứa MapCatalog/WorldSync/MapRenderer/TileSheets; SwordGame là composition và Scene2D UI.
- PureMVC điều phối intent/state; adapter tách IO khỏi framework. Callback qua dispatcher render, generation loại response cũ; resource theo lifecycle.
- protocol hiện có: package vn.swordol.protocol.legacy chứa immutable packet và bounded inbound TCP decoder, không mở socket, không dùng outbound envelope cho response.
- Java release8, dependency có sẵn. Không đưa JavaME runtime/JAR game vào native client. classic-desktop giữ riêng làm đối chứng.

Chọn package thay vì nhiều artifact theo yêu cầu user; không thêm event bus hay registry trùng PureMVC. Chức năng native hiện có là local demo login/world/move/attack, chưa toàn bộ client cũ. Không tạo package feature rỗng rồi báo hoàn thành.

## Server

Báo cáo phân biệt static client, packet đã giải mã, catalog map/NPC/item và luật chưa biết. Các số32map/315dạng/87item là snapshot quan sát, không phải database đầy đủ. Đề xuất server modular monolith cũng chia package; chưa bịa combat/AI/drop/respawn. Server JSON demo hiện có không đổi thành server cũ bằng cách đổi tên.

## Nghiệm thu

Build sạch toàn reactor, flow loopback không cần GL, transport dispatcher/stale response test. Inbound codec test signed values, UTF-8, tag5 bốn byte, binary bất biến, unknown tags, invalid/truncated lengths, nhiều frame và EOF. Chạy native desktop xuất ảnh. Không tự login game thật hoặc sửa phiên đang hoạt động.
