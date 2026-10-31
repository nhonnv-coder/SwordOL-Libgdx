# Thiết kế nền tảng dựng lại SwordOL

## Phạm vi đã chốt

Người dùng yêu cầu lập kế hoạch và triển khai ngay. Client libGDX dùng PureMVC, có Facade, Command, Proxy, Mediator và notification/event hợp lý; mục tiêu Android, Windows, iOS. Server được cấu hình trong JAR dùng làm nguồn đối chiếu response. Server mới chỉ thực hiện nghiệp vụ cơ bản; các luật chưa khôi phục phải có TODO rõ ràng.

## Cách tiếp cận

Chọn một lõi server mới, độc lập giao thức cũ, có đường mở rộng adapter. Hai server với hai bộ nghiệp vụ sẽ làm tăng chi phí duy trì; chỉ hỗ trợ client mới sẽ mất công cụ đối chiếu cũ. Bản đầu không giả định đã có server gốc hoặc có quyền sửa nó. Không tự động gửi tài khoản hoặc thao tác làm thay đổi trạng thái tới endpoint gốc.

## Lát cắt đầu tiên

- Phân tích tĩnh 69 class, trích xuất endpoint/config và định dạng packet, lưu provenance và mức chắc chắn.
- Kiểm kê/trích xuất tài nguyên có SHA-256, xử lý tên trùng hoa/thường trên Windows mà không mất bản gốc.
- Server Java: phiên demo cục bộ, vào bản đồ, di chuyển có kiểm tra giới hạn, đánh quái, nhận thưởng một lần; lỗi rõ ràng cho nghiệp vụ chưa có. Đây là mô hình demo, không tuyên bố công thức gốc.
- Giao thức mới JSON theo dòng qua TCP, tối đa 16 KiB mỗi frame; request có requestId/type/payload; response có requestId/type/ok/payload/error. Bind loopback mặc định. Không dùng cho production public nếu chưa TLS/auth/persistence.
- Client libGDX/PureMVC: màn hình nhập tên demo, kết nối localhost:7777, vào thế giới, di chuyển, đánh quái và hiển thị trạng thái. Network chạy ngoài render thread; cập nhật View trên render thread.
- Windows launcher chạy được. Android/iOS có cấu hình và hướng dẫn; build/ký phải được ghi đúng mức đã kiểm chứng theo SDK/host sẵn có.
- Script build/test, tài liệu chạy, TODO có ưu tiên, cấu hình đóng gói/bảo vệ mã; không hứa mã hóa bất khả dịch ngược.

## Ranh giới

Không suy diễn công thức chiến đấu, kinh tế, quest hay schema server gốc từ tên class. Không nhúng địa chỉ server gốc vào client demo mặc định. Không publish hoặc dùng khóa ký giả. Chưa cam kết tương thích wire protocol cũ trước khi đủ bằng chứng.

## Kiến trúc

`protocol` giữ DTO và codec cho giao thức mới. `server` giữ state và xử lý lệnh, adapter mạng tách khỏi nghiệp vụ. `client-core` giữ libGDX UI và PureMVC; `lwjgl3` giữ launcher. `tools` giữ phân tích và xuất tài nguyên. Tài liệu nêu rõ phần observed/inferred/TODO. Dùng dependency PureMVC chính thức, không tự viết framework rồi gọi là PureMVC.

## Kiểm chứng

Test framing/giới hạn input; test phiên, xác thực phiên, movement và reward chống lặp; test lifecycle mediator và đường notification; smoke test thực qua socket; build desktop và các target có đủ SDK. Mọi tính năng chưa build/chạy phải đánh dấu chưa kiểm chứng.

## Bổ sung 2026-10-25: tạo/chỉnh sửa và quản lý tài nguyên

Yêu cầu bổ sung của người dùng: có công cụ edit/create và quản lý tilemap, UI, sprite, fonts. Exporter hiện tại chỉ trích xuất byte, chưa phải editor. Các task nền tảng đã hoàn tất không bao gồm khả năng biên tập này. Phần dưới là thiết kế đề xuất cho giai đoạn kế tiếp, chưa triển khai hoặc nghiệm thu.

### Các hướng và khuyến nghị

1. Tự viết mọi editor: trải nghiệm thống nhất nhưng tốn nhiều thời gian cho công cụ vẽ map/font/ảnh đã có.
2. Kết hợp editor chuẩn và công cụ quản lý riêng: khuyến nghị. Tiled phục vụ tilemap; TexturePacker/FreeType hoặc công cụ font phù hợp phục vụ build; công cụ dự án giữ registry, import/export, sprite timeline, UI layout và preview runtime.
3. Chỉ ghép các tool bên ngoài: nhanh lúc đầu nhưng thiếu quản lý dependency/ID và đối chiếu định dạng client gốc.

Lựa chọn kết hợp là đề xuất của người triển khai, chưa coi là lựa chọn công cụ đã được người dùng phê duyệt. Không cài đặt công cụ bên ngoài trong lần cập nhật kế hoạch này.

### Mô hình dữ liệu chung

- `dev/assets/`: bản gốc bất biến cùng manifest/hash, không sửa trực tiếp.
- `assets/source/`: dữ liệu làm việc có schemaVersion và assetId ổn định; lưu vào Git khi repository được tạo.
- `dev/`: đầu ra tái tạo được, không sửa thủ công.
- `asset-pipeline/`: schema, importer, validator, builder và CLI headless.
- `resource-editor/`: ứng dụng desktop tạo/mở/sửa/lưu/preview tài nguyên, undo/redo và hộp thuộc tính.
- `asset-runtime/`: loader dùng chung với game và preview; không tạo hai cách render khác nhau.
- Registry theo dõi assetId, loại, source, dependencies, nguồn gốc, hash và output. Đổi tên file không đổi ID; xóa tài nguyên đang được tham chiếu phải báo nơi dùng, không âm thầm xóa dây chuyền.
- Save dùng file tạm cùng thư mục rồi thay thế an toàn; giữ bản trước khi import migration, không ghi đè output khi build lỗi. Đường dẫn phải nằm trong project; kiểm tra collision hoa/thường và giới hạn kích thước.

### Tilemap

Tạo/sửa map và tileset, layer nền/vật thể/trang trí, collision, spawn, NPC, portal và trigger. Bản đầu ưu tiên map hữu hạn orthogonal, tile flip/rotation và thuộc tính có schema; các kiểu map/chunk chưa hỗ trợ phải báo lỗi rõ. Hỗ trợ import/export subset Tiled đã công bố và preview cùng loader game. Build ra phần hiển thị cho client và collision/spawn/portal cho server từ cùng nguồn; checksum/version world phải khớp. Tài nguyên map tải từ server cũ chỉ nhập khi có dữ liệu thực.

### UI

Tạo/sửa cây widget và hierarchy, anchor/alignment, padding, kích thước, thứ tự vẽ, style, văn bản/localization key và controlId/actionId. Phân biệt Scene2D Skin (style/font/drawable) với layout và liên kết thao tác. Binding actionId qua Mediator/Command; tài nguyên không chứa mã Java hoặc script thực thi tùy ý. Preview nhiều tỷ lệ màn hình, touch target và font tiếng Việt; kiểm tra ID trùng, style/asset/action thiếu. Import `.ui` gốc phải theo từng loại control, giữ metadata chưa hiểu và báo unsupported thay vì bỏ qua.

### Sprite

Tạo/sửa danh sách frame, nguồn atlas/region, trim/offset, pivot, frame duration, loop, hướng nhìn, hitbox và marker sự kiện. Preview timeline, pause/step/speed; ID logic giữ ổn định khi repack atlas. Nhập spritesheet/atlas chuẩn và chuyển `.p/.dat` khi decoder đã được kiểm chứng. Lần đầu giữ nguyên pixel; chỉnh ảnh/pixel nâng cao có thể mở bằng editor ảnh bên ngoài rồi reimport. Hitbox gameplay do server xác minh; marker client không tự trao thưởng hoặc quyết định damage.

### Font

Nhập font TTF/OTF được phép sử dụng hoặc bitmap font; tạo profile size, glyph set, outline/shadow, spacing và texture filtering. Xuất bitmap `.fnt` + PNG/atlas hoặc cấu hình FreeType đã kiểm tra trên target. Quản lý fallback, glyph thiếu, kerning và dấu tiếng Việt; preview chuỗi NFC/NFD, chữ/đường dấu có thể bị cắt, nhiều kích cỡ/DPI. Bản đầu ưu tiên font được bake lúc build để phát hiện thiếu glyph sớm. Không giả định font trong client gốc là TTF, hoặc font PNG chưa có bảng glyph đã đủ dùng.

### Nghiệm thu

Mỗi loại tài nguyên phải tạo mới, sửa, undo/redo, save, đóng/mở lại và preview được; không chỉ xem danh sách file. Có ít nhất một map, UI đăng nhập, sprite animation và font tiếng Việt do pipeline build rồi được client tải thật. Map server dùng cùng version collision/spawn. Build phải chặn tham chiếu hỏng và giữ bản build thành công trước; có fixture round-trip và hình đối chiếu cho định dạng cũ đã hỗ trợ. Import/export về binary cũ không được tuyên bố byte-identical hoặc chạy được với JAR nếu chưa có fixture và test tương ứng.

Tài liệu tham khảo: https://doc.mapeditor.org/en/stable/manual/layers/ ; https://libgdx.com/wiki/tools/texture-packer ; https://libgdx.com/wiki/tools/skin-composer ; https://libgdx.com/wiki/extensions/gdx-freetype
