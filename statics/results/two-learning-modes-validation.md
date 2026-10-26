# Kiểm tra hai luồng Vào chơi và Ôn tập cá nhân — 26/10/2026

## Thay đổi

- Bỏ Luyện tự chọn khỏi sidebar, tab, thẻ trang chủ và bỏ form tạo PRACTICE. Không thêm mục Luyện thêm. Trang chủ có hai thẻ Chơi theo vòng và Ôn tập cá nhân.
- Giữ bản đồ/khóa vòng. Nút Chơi lại bài và Chơi lại vòng nằm trong lộ trình, không xóa mốc hoàn thành/kết quả cũ.
- Ôn tập giải thích quy trình nộp bài → xác định kiến thức cần ôn → thử câu hỏi cùng kiến thức; mỗi mục tiêu có lý do hoặc tình trạng riêng. Tài khoản chưa có dữ liệu được dẫn về bản đồ. Bỏ hộp gợi ý độ khó vốn dẫn sang chức năng đã bỏ.
- Giữ ba nhóm GUIDED/INDEPENDENT/REVIEW_VARIANT và toàn bộ backend, API, ngân hàng, điểm, XP, lịch sử, journal/receipt. Không migration. PRACTICE cũ vẫn mở từ lịch sử/URL và tiếp tục được; pending cũ vẫn gửi lại cùng key/payload.
- URL tab=practice không kèm attempt được replace về bản đồ; URL có attempt vẫn mở đúng runner. Các màn hình TEACHER/ADMIN giữ cách duyệt học liệu.
- Nút chơi/tiếp tục trên trang chủ dùng dữ liệu đã tải được trong lúc đọc cập nhật nền; không bị disabled giữa pointerdown/up chỉ vì đang cập nhật XP.

## Kết quả

| Kiểm tra | Kết quả |
|---|---|
| TypeScript/Vite và Docker client build cuối | PASS, 75 modules; asset `index-sF4r9wqz.js` |
| Browser toàn bộ | Lượt đầu **101/102**; phát hiện một lỗi Chơi tiếp khi có cập nhật nền |
| Browser sau sửa | **15/15**: learning-journey, round-map, module-five-rewards |
| Tổng ca khác nhau | **102 ca đã qua qua các lượt**, không phải một lượt 102/102 |
| Backend/migration | Không đổi và không chạy lại suite Java cho thay đổi frontend này |

Ca lỗi ban đầu xảy ra khi trang chủ đọc lại dữ liệu và vô hiệu hóa nút ngay trong thao tác bấm. Sau sửa, ca ORDERING chủ động phát sự kiện gamification ở pointerdown, giữ request thống kê chưa trả lời, rồi pointerup; phải điều hướng đúng attempt và giữ nguyên hai từ trong nháp. Ca đó đã qua trên image cuối cùng, cùng các ca phần thưởng/nhận invalidation và mở vòng/chơi lại.

Hồi quy bao gồm M01–M07, câu hỏi/media/authoring, bản đồ và khóa URL, nộp/xem đáp án/xác nhận, câu biến thể theo mục tiêu, ôn/lịch ôn, chơi lại, mobile, mất ACK/timeout, IndexedDB và đổi tài khoản. Các ca tạo lượt M06 hiện dùng hai bài trong cùng vòng đang mở để kiểm tra hai ý định A/B; không cần form PRACTICE để kiểm tra cơ chế này.

Ca tương thích journal PRACTICE tạo một lượt thật qua HTTP, khôi phục bản ghi PREPARED đúng schema như phiên bản cũ có thể để lại sau mất phản hồi, tải lại giao diện mới và gửi lại. Kiểm tra cùng requestId/payload, cùng attemptId và không thêm lượt. Đây là mô phỏng journal cũ trên API thật, không phải đo nâng cấp giữa hai browser bundle đang chạy đồng thời.

Kiểm thử dùng môi trường riêng **15187/18097**, không chạy E2E trên bản người dùng **15183**. Frontend cuối được xem trên điện thoại 390px và desktop; trang ôn hiển thị lý do/tình trạng, trang chủ không còn lối vào Luyện tự chọn. Các số backend/browser ở [biên bản bản đồ](round-map-validation.md) là lịch sử trước lần sửa này.

Log tạm: `.test-state/two-modes-build.log`, `two-modes-stack-final.log`, `two-modes-browser.log`, `two-modes-browser-final.log` trong thư mục kiểm tra. Triển khai chỉ thay client, dùng `--no-deps`; kiểm tra ID/thời điểm khởi động backend giữ nguyên và đối chiếu số tài khoản/lượt/kết quả cùng hash Submission của bài #61 trước/sau bằng truy vấn chỉ đọc.
