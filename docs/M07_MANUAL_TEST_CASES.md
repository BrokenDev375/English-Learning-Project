# Kiểm tra thủ công M07

STUDENT mở Hồ sơ học tập; ADMIN mở Thống kê học tập; TEACHER mở Thống kê học liệu (bổ sung 24/10). Không chạy E2E ghi dữ liệu vào demo. Các ca dưới đây là hướng dẫn, chỉ đánh dấu PASS nếu thực sự đã thao tác.

| Ca | Thao tác | Kỳ vọng |
|---|---|---|
| 01 | Học viên mới mở hồ sơ | Tỷ lệ chưa có dữ liệu, không tự xuất XP/huy hiệu giả |
| 02 | Nộp bài có đúng/sai/bỏ trống | Tổng, tỷ lệ và số bỏ trống khớp; nội dung chưa nộp không tính |
| 03 | Dùng gợi ý/check rồi sửa đúng | FIRST và hỗ trợ giữ riêng; không coi là tự làm mới |
| 04 | Chữa sau nộp, tải lại | Số lần chữa tăng; FIRST không bị sửa |
| 05 | Đổi chủ đề/mục tiêu, trang và khoảng kết quả | Đúng nhóm, không trùng trang; giá trị rỗng rõ ràng |
| 06 | Hoàn thành rồi học lại/nộp dở; retire bài | Bài vẫn COMPLETED, đếm một root; tiêu đề/điểm có nhãn đúng |
| 07 | Xem nội dung cần ôn | Có pending thì báo; bấm chuyển M04 sync rồi tải lại hồ sơ |
| 08 | Mở lịch sử, tiếp tục hoặc xem kết quả | Dùng đúng Attempt cũ, không tạo lượt mới |
| 09 | F5/deep link, đổi tài khoản cùng trình duyệt | Đúng màn hình; B không thấy hồ sơ/lịch sử A |
| 10 | Mất mạng khi cập nhật | Hiện lỗi và nút thử lại; không biến số liệu thành 0 |
| 11 | ADMIN lọc câu thường sai/độ khó | FIRST sai theo phiên bản, kèm cỡ mẫu và số người; không lộ đáp án/PII |
| 12 | Mở hai tab cùng tài khoản | Tổng kết nối tăng, connectedAccounts không tăng gấp đôi; số này chỉ ước lượng kết nối mở |
| 13 | STUDENT/TEACHER gọi API admin; khóa tài khoản đang đăng nhập | 403/401 đúng, không trả dữ liệu |
| 14 | Màn hình 390×844 | Nội dung trang không tràn ngang; bảng cuộn trong khung |
| 15 | Giảng viên chọn tất cả Unit của tôi rồi từng Unit | Chỉ Unit mình làm chủ, số lượt/người/kết quả đúng phạm vi; không dùng chủ câu hỏi để quyết định quyền |
| 16 | Cùng QuestionVersion được dùng trong Unit A/B của hai giảng viên | Mỗi bên chỉ tính LEARN trong Unit của mình; Unit ngoại và ID không tồn tại cùng 404 |
| 17 | Tạo PRACTICE/REVIEW dùng cùng câu, hoặc LEARN chưa nộp | PRACTICE/REVIEW không tính vào Unit; bài dở chỉ tính hoạt động, FIRST chưa nộp không tính kết quả |
| 18 | Giảng viên chưa có Unit; Unit chưa có bài hoặc chưa ai học | Trạng thái rỗng/0 đếm đúng, tỷ lệ null; không tạo dữ liệu mẫu hoặc truy cập học viên |
| 19 | Lọc 7D/30D/90D; retire bài, sửa phiên bản câu/bài; học lại | Số nộp/kết quả theo khoảng, hoàn thành vẫn toàn thời gian và không đếm học lại thành mới; câu tách từng phiên bản |
| 20 | Mở gợi ý/làm lại/chữa rồi nộp | Câu mở gợi ý đếm tối đa một/item; FIRST, retry, correction riêng; không thay điểm gốc |
| 21 | F5/Back/Forward với Unit/khoảng thời gian; deep link Unit ngoại | Giữ lựa chọn hợp lệ, từ chối Unit ngoại, không lộ tên/số liệu Unit đó |
| 22 | Mất mạng khi tải thống kê giảng viên, sau đó thử lại | Thông báo lỗi, không thay bằng số 0; tải lại đúng bộ lọc và không ghi DB |

XP/streak/huy hiệu/Giải đấu tuần cần provider M05; hiện ghi chưa có dữ liệu. Xuất báo cáo, thống kê tải và presence theo heartbeat M06 chưa nghiệm thu ở bản này.
