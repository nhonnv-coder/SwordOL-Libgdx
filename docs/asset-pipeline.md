# Registry và pipeline tài nguyên — Task 5

Công cụ CLI và loader dùng chung cho game và editor. Map đã có schema, validation và editor riêng từ Task6: xem `docs/map-editor.md`. UI, sprite và font hiện mới là loại đăng ký; schema và trình biên tập của chúng thuộc Task7–9.

## Dữ liệu và lệnh chạy

Project mẫu tại `assets/source/demo/registry.json` chứa asset `brand.logo`. Bản gốc JAR và `dev/assets` giữ nguyên. Nguồn chỉnh sửa được đặt trong `assets/source`; bundle tái tạo được đặt ở `dev`.

```powershell
# Build các module CLI/runtime
powershell -ExecutionPolicy Bypass -File scripts/mvn.ps1 -pl asset-pipeline -am package

# Kiểm tra và hỏi asset nào tham chiếu ID này
java -jar asset-pipeline/target/swordol-assets.jar validate assets/source/demo
java -jar asset-pipeline/target/swordol-assets.jar usages assets/source/demo brand.logo

# Build bundle mẫu và cập nhật resource mà client dùng
powershell -ExecutionPolicy Bypass -File scripts/build-assets.ps1 -UpdateClient

# Kiểm tra lại client và đóng gói
powershell -ExecutionPolicy Bypass -File scripts/mvn.ps1 verify
```

Bundle mẫu nhỏ được giữ trong `demo-bundle/src/main/resources/bundles/demo.swb` (module client và server cùng phụ thuộc) để build checkout ban đầu không cần chạy CLI riêng trước. Khi sửa project mẫu, phải chạy build-assets với `-UpdateClient` rồi build game. Không chỉnh bundle bằng tay. Chưa có file watcher hoặc build-assets tự động trong Maven; đây là thao tác tường minh để tránh thay đổi source khi chạy test.

## Hợp đồng registry

```json
{
  "schemaVersion": 1,
  "assets": [
    {
      "id": "brand.logo",
      "type": "image",
      "source": "images/logo.png",
      "dependencies": [],
      "provenance": {"originalMember": "res/logo.png"}
    }
  ]
}
```

ID dùng chữ/số ASCII, dấu chấm, gạch dưới hoặc gạch nối; bắt đầu bằng chữ/số, tối đa256 ký tự. Đổi tên file chỉ cần sửa source, giữ ID. `dependencies` chứa ID đã tồn tại, không trùng/vòng lặp. `usages` trả các tham chiếu ngược trực tiếp. `provenance` là object bắt buộc; asset tạo mới có thể dùng object rỗng rồi bổ sung tác giả/nguồn theo nghiệp vụ editor sau.

Types: `image`, `map`, `ui`, `sprite`, `font`, `binary`. Asset type `map` được parse và validate đầy đủ theo `docs/map-editor.md` (gồm tham chiếu tileset và portal). Các type còn lại chỉ kiểm tra registry và byte toàn vẹn; khai type `sprite` chưa chứng minh file là sprite hợp lệ. Định dạng `.p/.dat/.ui` chưa có decoder hoàn chỉnh vẫn nhập dưới type `binary`.

## Bundle và loader

Một ZIP `.swb` có `bundle.json` và blob `files/<sha256>`. Asset cùng nội dung dùng chung blob. Assets, dependencies và ZIP entries được sắp xếp; thời gian ZIP cố định bằng lịch Gregorian để không phụ thuộc locale/timezone. Không ghi absolute path hoặc thời gian build vào bundle. Hash được tính từ đúng bytes ghi vào ZIP. Build thành công mới publish bằng atomic move; không có atomic move thì báo lỗi và giữ bản cũ.

Runtime `vn.swordol.assets.runtime.AssetBundle.load(InputStream)` kiểm tra ZIP entry, version, field types, hash/size, ID trùng, dependency, giới hạn và dữ liệu UTF-8 trước khi trả kết quả. Không extract filesystem. Caller giữ quyền đóng stream; buffer trả bởi `read(id)` là bản sao. ID không tồn tại trả null, nên consumer phải báo lỗi theo ngữ cảnh; game đã kiểm tra `brand.logo` và loại image.

Giới hạn: 10000 asset, manifest4MiB, mỗi blob16MiB, tổng bytes decoded64MiB gồm manifest và blob duy nhất. Nguồn phải là file trong project; chặn traversal, symlink và tên phân biệt chỉ bằng hoa/thường. Builder không được ghi đè registry hoặc file source đã đăng ký.

## Import gốc và migration

```powershell
java -jar asset-pipeline/target/swordol-assets.jar import-legacy dev/assets/manifest.json dev/legacy-project
java -jar asset-pipeline/target/swordol-assets.jar build dev/legacy-project dev/legacy.swb
java -jar asset-pipeline/target/swordol-assets.jar migrate PROJECT
```

Import chỉ tạo project mới, từ chối đích đã tồn tại. Kiểm tra member size/hash và copy vào tên theo archive ordinal; lưu source hash, member hash, path gốc và ordinal trong provenance. PNG là image, phần còn lại binary. Provenance giữ khai báo từ manifest đầu vào; hash JAR không tự chứng minh manifest đó là nguồn đáng tin nếu người dùng cung cấp manifest khác. Công cụ không kết nối server cũ.

Migration chỉ hỗ trợ schema0 với field `path` sang schema1 `source`; phải gọi tường minh. Kiểm tra cấu hình trước, tạo backup `registry.json.schema0.bak` không ghi đè, rồi atomic replace. Load thường không tự migrate.

## Kiểm chứng và bước tiếp

- Test tích hợp client đã FAIL khi chưa có bundle, rồi PASS sau khi CLI tạo bundle thật.
- Toàn bộ351 tài nguyên từ exporter đã import/validate/build và được loader runtime kiểm tra đủ351 ID.
- Cùng project mẫu build en_US và th_TH có SHA256 `706d18f34cdc5cdd30ae0e17cacb7ade448e594c6e66537a220899f33f8cb0d2`; cũng khớp bundle mẫu trong client resources.
- Task6 đã xong: project browser/inspector, create/save/reopen/undo/redo và map dùng chung client/server; chi tiết trong `docs/map-editor.md`. Không tích hợp Tiled theo lựa chọn của người dùng.

Các hạn chế nhỏ ghi nhận trong review: parser registry thay thế byte UTF-8 lỗi thay vì báo lỗi như runtime; runtime cho phép ID rộng hơn grammar của registry; các alias nguồn có thể gây đọc/hash lặp. Các điểm này chưa làm hỏng pipeline mẫu hoặc bundle sinh bởi CLI đã kiểm thử, nhưng cần xử lý trước khi nhận project lớn/không tin cậy qua giao diện editor.

## Tạo bundle theo lựa chọn

`AssetBuilder.build(project, output, ids)` đóng gói đúng những tài nguyên được chọn cùng toàn bộ phụ thuộc của chúng, và `AssetBuilder.buildEach(project, dir, grouping, names)` tách thành nhiều bundle theo từng tài nguyên, từng loại hoặc từng tiền tố id, kèm `bundles.json` ghi tên/file/kích thước/SHA-256/danh sách tài nguyên. CLI: `build PROJECT OUTPUT [ID...]`, `split PROJECT DIR asset|type|prefix [NAME...]`, `list PROJECT`. Công cụ người dùng và kết quả chạy thật: [asset-bundles.md](asset-bundles.md).

