# Đóng gói, phát hành và bảo vệ mã

## Windows

Build reactor trước. `scripts/package-windows.ps1` dùng JDK `jpackage --type app-image` tạo ứng dụng portable có runtime; không đòi WiX. Output `dist/windows/SwordOL/SwordOL.exe`. App-image không đồng nghĩa installer đã ký. Muốn MSI/EXE installer cần toolchain tương ứng, khóa ký và smoke test lại bản phân phối.

```powershell
powershell -ExecutionPolicy Bypass -File scripts/obfuscate-desktop.ps1
powershell -ExecutionPolicy Bypass -File scripts/package-windows.ps1 -Protected
```

Pipeline bảo vệ desktop dùng ProGuard 7.9.1 (hỗ trợ thư viện JDK 26 trên máy hiện tại), đổi tên phần code ứng dụng và giữ thư viện bên ngoài/native entrypoint. Chưa bật shrink/optimize cho desktop để giảm rủi ro phá reflection. Output jar `dist/protected/swordol-desktop-protected.jar`; app có runtime `dist/windows-protected/SwordOL/SwordOL.exe`. Giữ kín `dist/protected/mapping.txt`. Các bản gói đã tồn tại sẽ được giữ nguyên; script không xóa đè thư mục app.

## Android

Xem `android/` để build launcher. Release cần shrink/obfuscation R8 và keep rule cho các field protocol được Gson đọc/ghi, framework callbacks và thư viện native; sau đó kiểm tra end-to-end bản đã làm rối. Release phải dùng keystore riêng qua biến môi trường/local secret, không tự sinh một khóa rồi coi là khóa phát hành chính thức. Cấu hình SDK và signing chưa đủ thì không tuyên bố đã có AAB publish được.

## iOS

Launcher dùng backend RoboVM. Build IPA trên macOS với Xcode, SDK và signing/provisioning của dự án. Windows có thể sửa core Java nhưng không chứng minh được bản iOS phát hành. Bản native vẫn cần kiểm tra symbol, dữ liệu debug và bí mật nhúng.

## Server

`docker compose -f deploy/compose.yaml up --build` dùng jar đã build, tiến trình không chạy root, filesystem read-only và bind cổng host loopback. Container không cung cấp database hay production auth/TLS. Khi deploy thật phải có persistence, migration, backup/restore, metrics, giới hạn tài nguyên và kiểm thử rollback trước.

## Bảo vệ mã

Làm rối tên, thu gọn và bỏ debug info giúp tăng chi phí dịch ngược; đây không phải mã hóa không thể giải. Không phân phối Java source trong app. Bí mật dịch vụ và các quyết định thưởng/tiền phải ở server. Mã hóa asset nếu bổ sung phải có định dạng/version, kiểm tra toàn vẹn, quản lý khóa và test bộ giải mã; không đổi đuôi file rồi gọi là mã hóa.

Mapping làm rối và symbols thuộc artifact riêng, không phát cho người chơi. Mỗi release cần ghi version, commit/source snapshot, dependency lock/version, checksum, mapping, test report và chứng chỉ đã dùng. Chưa có Git trong workspace nên không tạo commit ID giả cho bản đầu.

Tham khảo chính thức: https://libgdx.com/wiki/deployment/deploying-your-application ; https://developer.android.com/topic/performance/app-optimization/enable-app-optimization
