# Trích xuất tài nguyên và tạo bundle từng cái

`Tao-Bundle.cmd` mở công cụ chọn tài nguyên và tạo bundle: gộp một bundle cho những gì bạn chọn, hoặc **mỗi tài nguyên một bundle**, hoặc một bundle cho mỗi loại. Công cụ không tự viết định dạng bundle — nó gọi đúng CLI `asset-pipeline` mà build dùng, nên bundle tạo bằng tay và bundle do build tạo giống nhau từng byte.

## Dùng công cụ

1. Build CLI một lần: `powershell -ExecutionPolicy Bypass -File scripts/mvn.ps1 -o -pl asset-pipeline -am package`.
2. Mở `Tao-Bundle.cmd`.
3. **Trích xuất lại** chạy lại chuỗi giải mã từ JAR gốc (`export_assets` → `decode_images/sprites/ui/config` → `build_legacy_project`). Bước nào đã có kết quả thì bỏ qua, vì các decoder từ chối ghi đè.
4. Gõ để tìm (không phân biệt hoa thường và dấu), chọn nhiều dòng, rồi bấm:
   - **Gộp 1 bundle** → `selection.swb` chứa những gì bạn chọn.
   - **Mỗi cái 1 bundle** → mỗi tài nguyên một file riêng.
   - **Theo loại** → một bundle cho mỗi loại (image, sprite, ui, binary, font, map).
5. Thư mục xuất mặc định là `dev/bundles`; đổi bằng **Thư mục xuất**. Mỗi lần tách đều ghi kèm `bundles.json` liệt kê tên, file, kích thước, SHA-256 và danh sách tài nguyên thật sự nằm trong từng bundle.

**Mỗi bundle luôn kéo theo phụ thuộc của nó.** Chọn một sprite thì bundle có cả những ảnh mà bảng ảnh của sprite khai báo, nếu không bundle đó không nạp được một mình.

## Dùng dòng lệnh

```powershell
$jar = 'asset-pipeline/target/swordol-assets.jar'
java -jar $jar list dev/OL                      # id, loại, byte, phụ thuộc
java -jar $jar build dev/OL out.swb legacy.sprite.10000
java -jar $jar split dev/OL thu-muc-ra asset legacy.ui.1 legacy.ui.2
java -jar $jar split dev/OL thu-muc-ra type
java -jar $jar split dev/OL thu-muc-ra prefix legacy.image legacy.sprite
```

`build` không có id nào thì đóng cả dự án như trước. `split` cần `asset`, `type` hoặc `prefix`; với `prefix` phải nêu tiền tố. Tên bundle lấy từ tên nhóm và chỉ chấp nhận chữ, số, `.`, `-`, `_`, nên một id lạ không thể tạo file ngoài thư mục xuất.

## Xuất TextureAtlas cho libGDX

```powershell
java -jar $jar atlas assets/source/OL.swb thu-muc-ra images
```

`atlas` đọc mọi tài nguyên loại `image` trong một bundle `.swb`, gộp (shelf packing, đệm 2px chống lem mép) thành vài trang `images<N>.png` và ghi `images.atlas` theo đúng định dạng libGDX. Client nạp bằng `AssetManager`/`TextureAtlas` nên vài texture trang thay vì một texture cho mỗi ảnh. Tên region là id tài nguyên gốc. Ảnh lớn hơn một trang 2048px, hoặc bundle không có ảnh nào, thì báo lỗi và không ghi gì. *shortcut:* shelf packing phí ít diện tích trang; đổi sang libGDX TexturePacker (gdx-tools) nếu số/kích thước trang thành vấn đề.

## Đã chạy thật trên dự án gốc

Trên `dev/OL` (1520 tài nguyên giải mã từ JAR gốc):

| Lệnh | Kết quả |
|---|---|
| `split ... type` | `image` 3.673.143 B · `sprite` 4.799.553 B · `ui` 343.137 B · `binary` 87.830 B + `bundles.json` |
| `split ... asset` cho 72 tài nguyên UI | 72 file, tổng 353.787 B |
| `build ... legacy.sprite.10000` | 63.302 B, gồm sprite và 13 ảnh nó phụ thuộc |

Kiểm chứng nạp lại bằng `scripts/verify-legacy-bundle.ps1`: bundle `sprite` nạp 185 tài nguyên (61 sprite + 124 ảnh phụ thuộc), 124 ảnh giải mã được bằng libGDX, 61 tài liệu JSON strict hợp lệ; bundle một tài nguyên `legacy.ui.1` nạp 1 tài nguyên với 8 control. Báo cáo: `.tools/bundle-sprite-verify.json`, `.tools/bundle-ui-verify.json`.

## Giới hạn

- Công cụ chỉ đọc dự án và ghi vào thư mục xuất; không sửa registry, không sửa file nguồn.
- Chia theo `asset` cho toàn bộ dự án sẽ nhân bản phụ thuộc ở nhiều bundle (một ảnh dùng chung nằm trong mọi bundle cần nó), nên tổng dung lượng lớn hơn một bundle gộp. Đó là cái giá của việc mỗi bundle tự nạp được.
- Giao diện là Tk; đã chạy lên không lỗi, nhưng chưa có kiểm thử thao tác giao diện tự động — phần logic (đọc danh sách, lọc, dựng lệnh, đọc index, chuỗi trích xuất) có 12 test trong `tools/analysis/test_asset_bundler.py`.
