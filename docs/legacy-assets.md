# Giải mã tài nguyên gốc về định dạng dùng được

Toàn bộ 351 member nonclass của `docs/files/VoLamHoiTu.jar` đã được chuyển từ định dạng riêng của client Java ME về PNG và JSON, rồi gom thành một bundle. Công cụ là Python 3 stdlib trong `tools/analysis`, đọc đúng theo bytecode đã decompile; mọi tham chiếu method tái tạo được bằng lệnh CFR trong [docs/analysis/README.md](analysis/README.md).

## Chạy

```powershell
# 1. Xuất bytes gốc (đã có sẵn trong dev/assets)
python tools/analysis/export_assets.py docs/files/VoLamHoiTu.jar dev/assets

# 2. Giải mã từng họ định dạng
python tools/analysis/decode_images.py dev/assets dev/legacy-images
python tools/analysis/decode_sprites.py dev/assets dev/legacy-sprites dev/legacy-images/images.json
python tools/analysis/decode_ui.py dev/assets dev/legacy-ui
python tools/analysis/decode_config.py dev/assets dev/legacy-config

# 3. Gom thành asset project rồi build bundle
python tools/analysis/build_legacy_project.py dev/assets dev dev/OL
java -jar asset-pipeline/target/swordol-assets.jar validate dev/OL
java -jar asset-pipeline/target/swordol-assets.jar build dev/OL assets/source/OL.swb
```

## Kết quả

| Nguồn | Số file | Ra | Kết quả |
|---|---|---|---|
| `.p` + `mimg.o`/`rimg.o` | 150 pack, 1322 record | PNG | **1321 ảnh**, 0 CRC lệch; 1 record có tRNS CRC sai sẵn trong dữ liệu gốc |
| `.dat` | 61 | JSON sprite | **61/61 sprite**; 60 file dùng placement 4 byte, 1 file dùng biến thể 6 byte |
| `.ui` | 72 | JSON layout | **72/72**, 605 control (322 button, 157 grid, 52 text, 41 edit, 33 list) |
| `.o` | 4 | JSON | **4/4**: 2 file cấu hình + 2 bảng index ảnh |
| `.png` gốc | 63 | giữ nguyên bytes | đưa thẳng vào project |

Bundle `assets/source/OL.swb`: **1521 asset, 8.486.607 byte** (ảnh trùng nội dung dùng chung blob). Kiểm tra độc lập bằng Python: 0 sai hash/size, mọi dependency khai báo đều tồn tại.

## Ảnh `.p`

`work/api/ImagePointer.NewCreatePng` nạp ảnh theo bảng index: `mimg.o` cho image ID < 1000 (pack `mp<N>.p`), `rimg.o` cho phần còn lại (pack `rp<N>.p`). Mỗi record 7 byte: image ID (int BE), số pack (byte), offset trong pack (short BE).

Tại offset là một head 22 byte: width/height (u16), bit depth, chỉ số màu trong suốt (byte có dấu, -1 là không có), CRC dựng sẵn của PLTE/tRNS/IHDR (int), độ dài palette (u16), tổng độ dài dữ liệu (u16). Sau đó là palette RGB565 rồi dữ liệu nén và CRC của IDAT. Client dựng lại PNG trong bộ nhớ; công cụ này dựng y hệt rồi ghi ra đĩa.

**CRC lưu sẵn là oracle độc lập**: nếu palette dựng sai một byte thì CRC không khớp. Cả 1321 ảnh đều khớp, nên palette là **byte-identical** với bản client hiển thị.

Vì vậy decoder tái tạo đúng cả một chỗ bất thường của client: nó đọc màu RGB565 bằng kiểu short có dấu rồi dịch số học, nên kênh đỏ từ 16 trở lên bị lệch (đỏ 31 ra 248 thay vì 255). "Sửa" cho đẹp sẽ tạo ra ảnh mà game gốc chưa bao giờ hiển thị, và mọi CRC sẽ sai.

## Sprite `.dat`

Payload sau header độ dài (u16 little-endian): số ảnh (byte), số module (u16 LE), số frame (u16 LE), số action (byte), độ dài vùng frame (u16 LE), rồi bảng image ID (4 byte BE), module (5 byte: image index, x, y, w, h), frame (1 byte số mảnh + mỗi mảnh 4 byte: word LE với module ở bit 0–11 và transform ở bit 12–15, rồi 2 byte offset có dấu), action (1 byte số frame, 4 byte action ID BE, rồi mỗi frame 3 byte: chỉ số frame u16 BE + 1 byte).

Hai quan sát được ghi nguyên trạng, không diễn giải:

- **Byte thứ ba của mỗi entry action không được client đọc.** Frame chạy theo nhịp cố định 50 ms trong `nextActionFrame`, nên không có bằng chứng nó là duration. Trường giữ tên `flag`.
- **Mọi file khai độ dài thiếu đúng 1 byte** so với cấu trúc thật. Byte thiếu luôn là `flag` của entry cuối, thứ client không đọc, nên bản gốc không bao giờ lộ.

**Hai biến thể placement.** 60 file dùng 4 byte (word + 2 byte offset có dấu) — đúng thứ `ISpriteEx.drawFrame` đọc. Riêng `d8060000.dat` dùng **6 byte**: word + hai số nguyên có dấu 16-bit little-endian.

Bằng chứng cho biến thể này, không phải suy đoán:

- Chỉ cách đọc 6 byte mới đi hết vùng frame **đúng bằng** độ dài mà chính header file khai (8544).
- Với cách đọc đó, 1299/1299 placement có module nằm trong bảng và transform trong 0–7; cách đọc 4 byte cho chỉ số rác ngay từ frame 0.
- Offset ra dải hợp lý: x ∈ [−58, 39], y ∈ [−79, 1], so với module cỡ 36×36 px. Đọc big-endian cho ra x ≈ −14593, tức sai.

Decoder đi thử 4 byte trước, chỉ chuyển sang 6 byte khi độ dài vùng frame khai báo nói vậy, và ghi lại `placement_size` đã dùng. File này là sprite thú cưỡi (project Godot của bạn đặt tên `AniChar_RiderDeDiem`); client gốc **không vẽ được nó** vì chỉ có một nhánh đọc 4 byte — đó là nhận định về file, không phải lý do để đọc lại các file khác.

## Layout `.ui`

`CtrlManager.loadData` đọc: main screen (int id, byte class, khối chung, byte focus, 2 byte soft key), rồi byte số control, rồi từng control (int id, byte class, khối chung, phần riêng theo loại).

Khối chung 23 byte: 4 byte neighbor (-1 là không có), 4 short x/y/width/height, int màu chữ, int position index, short mode, byte cờ tối ưu.

Chỉ có 5 loại: 2 button, 4 list, 7 edit, 11 text, 12 grid. Class khác làm reader gốc không tiêu thụ byte nào nên phần còn lại của file lệch — decoder từ chối file như vậy thay vì đoán.

72/72 file tiêu thụ **đúng đến byte cuối**, và chuỗi ra tiếng Việt đọc được ("Xác nhận", "Quay lại", "Nhập tài khoản：", "Câu hỏi 1: Tên ở nhà của bạn?"), nên cả căn chỉnh trường lẫn encoding đều đúng.

## Cấu hình `.o`

`.o` không phải một format. `connect_b.o` và `start.o` là file dòng nén của `OFileReader`; `mimg.o`/`rimg.o` là bảng index ảnh. Loader chọn cách đọc, không phải phần mở rộng, nên decoder cũng định tuyến theo tên file đúng như client.

Kết quả đối chiếu được với tài liệu có sẵn: `start.o` → `["JT_N5800F"], [210]`; `connect_b.o` → `[180], [169], [17], [16], [13012]`, khớp `http://180.169.17.16:13012` trong [docs/analysis/legacy-endpoints.json](analysis/legacy-endpoints.json).

## Giới hạn

- Đây là **địa chỉ lịch sử trong file**, không phải xác nhận server còn hoạt động. Công cụ không kết nối bất kỳ endpoint nào.
- Map gốc **không nằm trong JAR**: tile, collision và ảnh map do server gửi runtime (`MapEx.updateArray...`, `ZipMe`). Không có capture thì không xuất được map gốc.
- Sprite mới ở dạng dữ liệu: chưa có renderer trong client mới, chưa nối vào editor, chưa dựng atlas. Đó là Task 8.
- Layout mới ở dạng dữ liệu: chưa ánh xạ control ID sang action/Mediator, chưa có UI editor. Đó là Task 7.
- 65 tham chiếu ảnh trong sprite và một số trong UI trỏ tới ID không có trong JAR; chúng được giữ trong `provenance.unresolved_images` chứ không bị bỏ im lặng.
- Bundle `OL` chưa được client hoặc server nạp; nó mới được CLI validate/build và kiểm tra độc lập bằng Python.
