# Kiểm kê client gốc

Ngày: 2026-10-25. Mục tiêu phát hành người dùng đã chọn: Android, Windows, iOS.

## Bằng chứng quan sát trực tiếp

- Đầu vào: `docs/files/VoLamHoiTu.jar`, SHA-256 `16024C3ED0536B29217A67F657F110ABC78549EAE75EF6AC0BFDD0DC205B0CCD`.
- Manifest khai báo MIDP-2.0, CLDC-1.0, phiên bản 1.05; điểm vào `work.main.MyMidlet`.
- ZIP inventory: 69 `.class`, 61 `.dat`, 4 `.o`, 150 `.p`, 63 `.png`, 72 `.ui`, 1 `.mf`.
- Có class `base/math/AStar`, `base/math/ZipMe`, `base/utils/Packet`, `base/utils/TCPBase`, `base/utils/TCPHandler`, `base/utils/HttpPoster`, `base/utils/RMS`, `base/utils/Resources`, `work/api/ImagePointer`, `work/api/Const`.
- Đây là kiểm kê bằng thư viện ZIP của hệ thống, chưa phải bằng chứng thực thi hoặc kết quả giải mã nghiệp vụ của REA.

## Suy luận cần kiểm chứng

REA `inspect_artifact` đã hoàn tất kiểm kê tĩnh và lưu bằng chứng đầy đủ tại `docs/rea-artifact-inspection.json`, Evidence ID `ev_88080f477490a7dc5a91c6f36d300c4645b12f7fd80f7a44d8b388871d459ded`. REA cảnh báo tên mục trùng khi không phân biệt hoa/thường: khi trích xuất trên Windows phải bảo toàn danh tính tài nguyên, tránh ghi đè. Kiểm kê này không thực thi client hoặc xác nhận nghiệp vụ.

- Manifest cho thấy đây là client Java ME; libGDX sẽ cần thay thế các API MIDP thay vì chỉ đổi launcher.
- Tên class gợi ý có đường đi, mạng TCP/HTTP, lưu cục bộ và định dạng tài nguyên riêng. Chưa xác nhận cách sử dụng hay luồng gọi.
- Phần mở rộng `.p`, `.ui`, `.o`, `.dat` chưa đủ xác định nội dung; cần đọc bộ nạp và đối chiếu dữ liệu gốc.

## Chưa xác định

- Luồng khởi động, đăng nhập, chọn nhân vật, vào bản đồ và gameplay thực tế.
- Framing packet, opcode, encoding, nén/mã hóa, trạng thái phiên và endpoint được sử dụng.
- Luật server, dữ liệu thế giới, tỷ lệ rơi đồ, công thức chiến đấu và các tác vụ không nằm trong client.
- Yêu cầu tương thích server cũ hay được thay giao thức khi dựng server mới.
- Toolchain decompile Java và môi trường chạy MIDlet phù hợp.

## Phạm vi cần phân tích tiếp

1. Lập sơ đồ gọi từ `MyMidlet` tới giao diện, trạng thái và xử lý gói tin; dẫn nguồn từng kết luận.
2. Lập hợp đồng giao thức và bộ mẫu request/response; đánh dấu thông tin suy luận và thông tin chưa biết.
3. Kiểm kê từng tài nguyên, hash, định dạng, bộ giải mã và bản xuất; giữ file gốc bất biến.
4. Chọn mô hình tương thích rồi thiết kế client libGDX/PureMVC và server dựa trên hợp đồng đã xác minh.
5. Xây một lát cắt chạy được trước, sau đó mở rộng theo luồng đã phân tích.
6. Thiết kế build Android/Windows/iOS, ký phát hành, làm rối mã, quản lý mapping và bảo vệ tài nguyên. Không coi mã hóa client là biện pháp giấu bí mật tuyệt đối.

## Tình trạng công cụ

REA 6.7.0 có CLI cục bộ. Phiên chat hiện tại chưa quảng bá công cụ REA trực tiếp; có thể tiếp tục qua CLI. `doctor --client codex` kiểm tra cấu hình cá nhân, báo thiếu đăng ký tại đó; kết quả này không phủ định cấu hình cấp project đã được kiểm tra ở bước cài đặt. Kiểm tra native của REA bị giới hạn quyền trong lần chạy doctor; chưa dùng kết quả đó để kết luận thiếu Java hay thiếu engine.
