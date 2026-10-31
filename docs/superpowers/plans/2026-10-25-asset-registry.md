# Asset Registry Implementation Plan

> **For agentic workers:** Use executing-plans and TDD; independent pipeline/runtime work may run concurrently under dispatching-parallel-agents.

**Goal:** Hoàn thành Task5: registry, validation, legacy import, migration và build bundle được client tải thật.

**Architecture:** `asset-pipeline` là CLI desktop/headless; `asset-runtime` đọc stream bundle không phụ thuộc editor; client sử dụng cùng loader. Output là một ZIP `.swb`, publish bằng atomic move để giữ bundle cũ khi lỗi.

**Tech Stack:** Java8-compatible, Maven, Gson2.13.2, JUnit4; formatter hiện có google-java-format.

**Spec:** `docs/superpowers/specs/2026-10-25-reconstruction-design.md`, phần tài nguyên bổ sung. Người dùng yêu cầu “next action” sau đề xuất, triển khai nền registry; editor Task6–10 vẫn riêng.

## Global Constraints

- Không sửa `dev/assets` hoặc JAR gốc. Không nâng Java target hoặc thêm tool UI ngoài.
- Registry `registry.json`: `{schemaVersion:1, assets:[{id,type,source,dependencies:[],provenance:{...}}]}`; relative source dùng slash nằm trong project root, không symlink ra ngoài. Types `image,map,ui,sprite,font,binary`.
- Bundle manifest `bundle.json`: `{schemaVersion:1, assets:[{id,type,path,sha256,size,dependencies:[],provenance:{...}}]}`; blobs `files/<lowercase SHA256>`. Entries/assets sorted, timestamp ZIP cố định, không nhúng absolute path hoặc thời gian build.
- Giới hạn: 10000 assets, manifest4MiB, blob16MiB, total decoded64MiB. Kiểm tra dữ liệu thực tế, không chỉ tin ZIP header.
- Schemas nội dung map/UI/sprite/font thuộc task sau; không nhận định file binary gốc là đã giải mã.

## Review Focus

- Input traversal, symlink và case collision phải bị từ chối.
- Dependency thiếu/cycle và ID trùng không được build.
- ZIP manifest giả, trùng entry, sai hash/size/version phải bị từ chối khi load.
- Build/migration lỗi phải giữ file trước; không hỗ trợ atomic move thì báo lỗi, không fallback unsafe.
- Dữ liệu nguồn thay đổi sau validate phải được tính hash từ đúng bytes đã ghi bundle.

## Task A: Pipeline và CLI

Files `asset-pipeline/**`. `AssetRegistry.load(Path projectRoot)`, `validate(String assetId)`, `findUsages(String assetId)`; `AssetBuilder.build(Path projectRoot, Path outputFile)`; CLI `validate`, `build`, `usages`, `import-legacy`, `migrate`.

- [x] JUnit RED: duplicate ID, missing/cycle dependency, path escape/case collision, unsupported version, deterministic build và old-output preservation.
- [x] Registry validation, snapshot/hash builder + atomic publication; CLI exit nonzero on invalid input.
- [x] Import manifest exporter tới project đích mới, giữ source/member hash và archive ordinal; không overwrite project đã có.
- [x] Explicit migrate schema0 (`path` -> `source`) sang1 qua atomic save và backup trước sửa; không tự migrate khi load.
- [x] GREEN và package CLI jar; hướng dẫn sample.

## Task B: Runtime bundle loader

Files `asset-runtime/**`. Public `AssetBundle.load(InputStream) throws IOException`, `byte[] read(String assetId)`, `String type(String assetId)`, `List<String> ids()`; loaded data immutable; caller owns input stream.

- [x] RED fixtures đúng, sai hash/size, ID trùng, thiếu/cycle deps, path/trùng ZIP, unsupported version, decoded limits.
- [x] Loader bounded, integrity validation và defensive copies; không ghi filesystem.
- [x] GREEN độc lập, sau đó test bundle thật từ pipeline bằng runtime.

## Task C: Sample, client và nghiệm thu

Files `assets/source/demo/**`, `client-core/**`, root Maven, mobile configurations, scripts, docs.

- [x] Sample registry trỏ logo gốc đã có provenance; build bundle vào client resources và kiểm tra deterministic rebuild.
- [x] Test runtime đọc artifact pipeline; chuyển client logo sang đọc assetId từ bundle, dependency runtime được khai báo cho Maven/mobile.
- [x] Full Java/Python suite; CLI validate/build/import/migrate trên fixture và project thật; desktop smoke screenshot.
- [x] Review độc lập, sửa regression có test, cập nhật progress/Task5 và TODO; không đánh dấu Task6–10 hoàn thành.

