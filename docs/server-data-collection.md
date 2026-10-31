# Thu dữ liệu map, quái/NPC và item

Mở giả lập bằng `scripts/run-legacy-pc.ps1` hoặc bản portable có bộ ghi. Bạn xử lý đăng nhập; dữ liệu mới được ghi khi client nhận phản hồi. Trong lúc dùng game, đi qua map, gặp NPC/quái, mở túi hoặc chi tiết item để server gửi dữ liệu tương ứng. Không cần bật Wireshark.

Chat đã có lịch giám sát mỗi15 phút. Mỗi lần kiểm tra đọc snapshot capture cục bộ và cập nhật báo cáo; chỉ thông báo khi có dữ liệu mới đáng kể, lỗi hoặc cần bạn xử lý. Công cụ không tự chơi, gửi opcode dò ID, mua/bán/xóa đồ hay thử endpoint. Dữ liệu không server gửi sẽ chưa thu được. Nếu màn hình giả lập không đọc được bằng công cụ điều khiển, người dùng vẫn có thể chơi để bộ ghi thu và lịch giám sát tổng hợp.

Chạy thủ công ngay bằng `Thu-Du-Lieu.cmd`, hoặc:

```powershell
python tools/analysis/collect_server_data.py
```

Kết quả ở `dev/server-data/`:

- `summary.md`: tổng hợp riêng map, quái/NPC, item và phần chưa có.
- `latest.json`: đường dẫn phiên báo cáo mới nhất và số lượng từng nhóm.
- `runs/<hash>/maps.json`: danh sách map và phiên bản tài nguyên để preview.
- `runs/<hash>/npcs.json`: dạng hiển thị và từng lần quan sát, vị trí, HP/MP, cấp, look ID, mã loại. Template cache được tách theo từng kết nối TCP.
- `runs/<hash>/items.json`: item type đã thấy từ gói thông tin hoặc chi tiết, tên/icon khi xác định được và các quan sát thuộc tính. Không xuất ID chủ đồ.
- `runs/<hash>/sources.json`, `manifest.json`: hash snapshot nguồn và hash từng file báo cáo.
- `runs/<hash>/diagnostics.json`: schema chưa xử lý hoặc bản ghi lỗi; frame cuối đang ghi dở được giữ cho lần đọc sau.

Mỗi phiên báo cáo được giữ nguyên; không ghi đè capture. Không xuất account/password/session/chat/player packets trong báo cáo. Capture thô vẫn có thể chứa bí mật nên giữ riêng trên máy.

Giới hạn: chưa phân loại hoàn chỉnh mọi mã NPC thành quái hay NPC chức năng; “dạng hiển thị” chỉ là tổ hợp look ID, tên, mã loại và cấp đã quan sát. Vị trí hiện tại không chứng minh định nghĩa spawn hay thời gian hồi sinh. Item trong túi không chứng minh nơi rơi hoặc bảng giá. Gói chi tiết dạng script1032/action99 được ghi chẩn đoán, chưa giả định schema. Không suy ra công thức damage/drop/AI từ các gói này.
