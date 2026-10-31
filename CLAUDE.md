# SwordOL — rules

Dự án phục dựng client/server Võ Lâm Hội Tụ từ JAR gốc. Mọi thay đổi phải giữ được ranh giới bằng
chứng: cái gì đọc ra từ client cũ thì ghi nguồn, cái gì tự thiết kế thì nói rõ là thiết kế mới.

## 1. Design pattern — theo pattern đã chỉ định, không tự chọn khác

**Client native dùng PureMVC.** Luật kiến trúc đầy đủ nằm ở §1 của
[`docs/superpowers/plans/2026-10-31-native-client-rebuild.md`](docs/superpowers/plans/2026-10-31-native-client-rebuild.md).
Tóm tắt ràng buộc bắt buộc:

| Vai | Được làm | Cấm |
| --- | --- | --- |
| Facade | một facade cho mỗi phiên có lifecycle riêng; feature đăng ký Proxy/Command/Mediator vào facade đang có | tạo facade cho từng feature nhỏ |
| Proxy | giữ state, bọc IO (socket, file, bundle) | gọi libGDX, vẽ |
| Command | nhận intent từ Mediator, gọi Proxy | giữ state, chạy IO chặn |
| Mediator | nối view với intent/state, cập nhật trên render thread | mở socket, parse packet |
| View port | interface thuần Java để test không cần GL | rò kiểu libGDX vào test logic |

- Packet vào → notification ra. IO chạy thread riêng, trả kết quả về render thread bằng
  `Gdx.app.postRunnable`; generation của phiên làm mất hiệu lực callback cũ sau disconnect.
- **Không thêm event bus hay service locator thứ hai.** PureMVC là cơ chế điều phối duy nhất.
- **Không thêm Maven artifact mới** cho một feature. Module là package Java trong project đang có.
- Nghiệp vụ lấy từ client cũ: đọc `.tools/decompiled/` trước khi thiết kế, không suy từ tên hàm.
  Schema giao thức tra [`docs/analysis/protocol.md`](docs/analysis/protocol.md).
- UI chia widget theo đúng bộ của engine cũ (`work.ui`: `CtrlManager`, `CustomScreen`, `Button`,
  `Edit`, `Grid`, `StringList`, `TextEx`, `Digit`), mỗi widget một trách nhiệm, không nhồi thêm màn
  hình vào class đang có.

## 2. Coding convention — theo chuẩn của ngôn ngữ

**Java** — `maven.compiler.release` là **8**. Không dùng record, `var`, text block, switch
expression, collection factory mới hay virtual thread. Chi tiết còn lại theo skill
`java-coding-conventions`; khi skill và cấu hình build khác nhau thì **build thắng**.

**Python** (`tools/`, `scripts/`) — PEP 8, chỉ thư viện chuẩn trừ khi đã có sẵn trong dự án
(Pillow). Đặt tên và độ dài dòng theo file xung quanh, không đổi style của file đang sửa.

**PowerShell** (`scripts/*.ps1`) — `$ErrorActionPreference = 'Stop'`, `param()` có kiểu, đường dẫn
tuyệt đối dựng từ `$PSScriptRoot`.

Chung: UTF-8, có newline cuối file, không khoảng trắng thừa cuối dòng. Comment chỉ giải thích
**tại sao** khi code không tự nói được, một dòng. Tên và comment trong code dùng tiếng Anh; văn bản
hiển thị cho người chơi và tài liệu dùng tiếng Việt.

## 3. Format — chuẩn và đẹp, có công cụ ép

Toàn reactor dùng **Spotless + google-java-format 2.0.0, style GOOGLE** (khai báo trong `pom.xml`).

```bash
powershell -ExecutionPolicy Bypass -File scripts/mvn.ps1 spotless:apply
```

`spotless:check` phải xanh trước khi coi là xong; không tự chỉnh tay cho khác formatter, không tắt
Spotless cho file riêng. Code mới viết ra phải đã ở dạng formatter chấp nhận, không để `apply` dọn
hộ những thứ vốn nên viết đúng từ đầu.

## 4. SOLID và OOP

- **SRP** — một class một lý do để đổi. `Proxy` giữ state, `Command` điều phối, `Mediator` nối view.
  Class vẽ không biết mạng; class mạng không biết GL. Màn hình mới thì thêm widget/mediator mới,
  không phình class cũ.
- **OCP** — thêm opcode, thêm màn hình, thêm loại entity phải là thêm lớp/đăng ký mới, không phải
  thêm nhánh vào một `switch` khổng lồ đang có.
- **LSP** — class con không được siết tiền điều kiện hay ném lỗi mà hợp đồng cha không cho phép.
- **ISP** — view port tách nhỏ theo thứ màn hình thật sự cần (`LoginView` kiểu), không một interface
  chung cho mọi màn.
- **DIP** — tầng logic phụ thuộc interface, không phụ thuộc libGDX hay socket cụ thể. Đó là điều kiện
  để test chạy không cần cửa sổ.
- Ưu tiên composition hơn kế thừa. Trường `private final` mặc định; chỉ mở `public` khi có người gọi
  thật. Không getter/setter cho state mà lớp khác không được chạm.
- Không abstraction, wrapper, option hay config "để sau" khi chưa có người dùng thứ hai.

## 5. Kiểm chứng

- **TDD**: test đỏ trước, implement, xanh. Log RED/GREEN lưu trong `.tools/`.
- Mỗi giai đoạn phải **chạy thật** (gateway local hoặc server thật khi người dùng cho phép) kèm ảnh
  GL, không dừng ở unit test.
- Chạy test Python từ trong `tools/analysis` (một số test dùng đường dẫn tương đối).
- Không tuyên bố "đã chạy với server gốc" khi mới chỉ nghiệm thu bằng gateway local.
