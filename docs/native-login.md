# Đăng nhập và chọn nhân vật native

`SwordOL-Native.cmd` hiện mở màn hình libGDX native, không mở JSON demo hoặc JAR game gốc. Có thể tải danh sách máy chủ/tuyến, đăng nhập HTTP account bằng packet cũ, nối TCP game từ response1052, đọc danh sách1080 theo slot và gửi chọn nhân vật1081. Đã kiểm chứng luồng này bằng gateway binary local; **chưa nghiệm thu bằng tài khoản/server game thật**.

## Chạy thử trên máy

1. Mở `Server-Native.cmd`, đặt tài khoản và mật khẩu **local riêng** trong cửa sổ đó. Server chỉ nghe127.0.0.1, account port7780, game port7781. Nhân vật local mẫu là Kiếm Khách; không nhập tài khoản cũ vào server thử nghiệm này.
2. Mở `SwordOL-Native.cmd`. Giữ `http://127.0.0.1:7780/account`, bấm **Tải danh sách máy chủ**.
3. Chọn máy chủ/tuyến, nhập tài khoản local vừa đặt, bấm **Đăng nhập**. Ô mật khẩu được xóa sau thao tác và không lưu xuống hồ sơ.
4. Chọn nhân vật, bấm **Vào game**. Client nhận tài nguyên bản đồ, dựng lưới và cho đi lại từng ô; xem [native-world.md](native-world.md). Combat, vật phẩm, NPC và animation nhân vật vẫn chưa có.

Nếu dùng dịch vụ account khác do người dùng chọn, nhập URL trực tiếp. Native hiện hỗ trợ HTTP(S) account → TCP gameplay theo connection mode1; chưa hỗ trợ WAP/proxy hoặc HTTP gameplay. Redirect HTTP không được tự theo. Không có tự dò endpoint, tự đăng nhập, xóa nhân vật hay thao tác vật phẩm.

## Đăng ký và tạo nhân vật

Hai bước này đã có, theo đúng packet của client gốc (xem `docs/analysis/protocol.md`):

- **Đăng ký** dùng opcode `1055` trên kênh HTTP account, thân gói theo `Business.initRegisterPacket`. Ba ô là "Tài khoản:", "Mật khẩu:" và "Trùng lặp:" (nhập lại mật khẩu) theo layout gốc của cửa sổ114; mỗi ô6~13 ký tự, đúng chú ý in trong chính cửa sổ đó. Client gửi ô thứ ba đi chứ không tự so. Đăng ký xong **không** tự đăng nhập; màn hình báo kết quả và bạn đăng nhập bằng tài khoản mới. Server local chỉ nhận `1055` khi có `Registrar`; `GatewayMain` cấp một bộ nhớ tạm trong tiến trình, băm bằng salt của phiên, tối đa32 tài khoản và mất khi tắt server.
- **Tạo nhân vật** dùng opcode `1001` trên kênh TCP game, thân gói theo `Business.NewRoleWnd_EditOK`. Tên tối đa10 ký tự,3 ô nhân vật. Môn phái và giới tính lấy từ bảng `newRoleLookFaceDatID`: môn phái9 và10 không tạo được, và môn phái nào thiếu một giới thì giới đó không hiện. Client **không** tự thêm nhân vật vào danh sách; nó vẽ lại theo gói `1080` server trả về. Look face do server quyết theo bảng, không lấy từ request.

Tên môn phái hiển thị là số thứ tự. Client gốc không hard-code tên: `NewRoleWnd_Flush` hỏi server bằng gói `1602` rồi hiện chữ nhận được; phần đó chưa làm.

Nhân vật tạo trên server local vào cùng bản đồ mà launcher đang phục vụ, vì gateway không có bản đồ khởi đầu riêng theo môn phái.

JSON demo cũ chỉ mở khi chạy Java với `-Dswordol.demo=true`; launcher desktop mặc định không dùng nó nữa. Android/iOS chưa chuyển sang màn hình mới.

## Code theo package

| Nơi | Vai trò |
|---|---|
| `client-core/.../modules/login` | LegacySession state machine; LoginFacade/Proxy/Command/Mediator; LoginView |
| `client-core/.../ui/NativeLoginGame` | Scene2D form, server/tuyến và nhân vật; dispatcher render |
| `protocol/.../legacy` | LegacyWriter, LegacyWire, LoginPackets; giới hạn frame/CRC và schema |
| `server/.../legacy` | Gateway local HTTP/TCP, verifier được inject, ticket ngắn hạn dùng một lần, role ownership |

Không thêm Maven module. IO chạy ngoài render thread, terminal failure/disconnect làm mất hiệu lực generation; HTTP cancel ở worker riêng. Watchdog áp dụng cho bước handshake, không coi người dùng chờ chọn nhân vật là handshake lỗi. Server tách executor account và game để phiên game không chiếm hết dịch vụ đăng nhập.

Server local không có DB hay world/inventory persistence; tài khoản đăng ký và nhân vật tạo ra chỉ sống trong tiến trình. Verifier của launcher dùng PBKDF2 cho mật khẩu local của phiên; account/role mẫu chỉ tồn tại trong process. Đây là vertical slice thực thi giao thức, không phải server cũ hoàn chỉnh hay server production.

## Nguồn đối chiếu và kiểm chứng

- JAR gốc giữ nguyên SHA-256 `16024c3ed0536b29217a67f657f110abc78549eae75ef6ac0bfdd0dc205b0ccd`; REA đã mở kiểm tra artifact. Kiểm thử gọi riêng lớp Packet của JAR để so bytes writer, không chạy MIDlet hoặc login thật.
- Business.longinGame/sendServerListCmdPacket_A/ServerListOK và CmdProcessor.Connect/processLoginBackCmd/processLoginRoleCmd là nguồn field ordering. Client version250615 và identifier JT_N5800F lấy từ Const/start.o. Server index và tuyến là hai khái niệm riêng; native mở mỗi row thành các tuyến theo field3 như menu gốc.
- TCP outbound có FEFE/key/XOR/CRC/AAAA; TCP inbound chỉ length+logical packet. HTTP account có session/random/count và batch packet riêng. Không dùng decoder chiều nhận để gán semantics cho long outbound.
- `LegacyWireTest`: byte parity original writer và malformed envelope; `LoginPacketsTest`: slot, handoff, channel; `LegacySessionTest`: login sai/đúng, role ownership tại cổng client, clear password array, cancellation, timeout race và bốn phiên chờ không chặn account.
- Probe GL `vn.swordol.desktop.NativeLoginProbe` bấm các nút UI thật qua event, chỉ dùng danh tính giả và server loopback tự tạo. Ảnh: `screenshots/native-login-roles.png`, `screenshots/native-login-map-announcement.png`.
- Font tiếng Việt là atlas raster, tái tạo bằng `java tools/native/FontAtlas.java client-core/src/main/resources/fonts`; không đóng gói file font hệ thống. Tên hiển thị được chuẩn hóa NFC, dữ liệu raw vẫn giữ nguyên trong packet.

Phần tiếp sau bản đồ và di chuyển (xem [native-world.md](native-world.md)): sprite nhân vật/quái và animation, nội suy bước đi, chuyển map qua portal, rồi chiến đấu, vật phẩm, NPC và nhiệm vụ. Chưa có nghiệm thu với server thật.
