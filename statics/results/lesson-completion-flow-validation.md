# Kiểm tra luồng nộp bài và xác nhận hoàn thành — 25/10/2026

## Hành vi được kiểm tra

- LEARN nộp đủ đáp án được ghi nhận hoàn thành, không bắt buộc CHECK từng câu GUIDED hoặc trả lời đúng hết.
- Sau nộp, hiển thị đáp án và giải thích của tất cả câu, sau đó có nút **Xác nhận hoàn thành bài học** trở về đúng vòng.
- Bài nộp theo luật cũ, đủ đáp án nhưng chưa hoàn thành vì thiếu CHECK, có thể xác nhận bằng POST `/api/attempts/{id}/complete` mà không làm lại.
- Xác nhận không sửa Submission, FIRST, điểm, câu trả lời khi nộp hoặc thưởng cũ. Bài nộp thiếu đáp án không được dùng bài chữa để hoàn thành.
- Dùng receipt và pending journal hiện có để gửi lại cùng requestId/payload khi mất phản hồi hoặc tải lại trang. Không thêm migration.

## Kết quả

| Kiểm tra | Kết quả |
|---|---|
| Backend: AttemptApiTest, AnswerGraderTest, AttemptRequestsTest, GamificationApiTest | 73 ca khác nhau đã qua qua hai lượt: lượt đầu 72/73; sửa phép so sánh JSON trong fixture mới, chạy lại đúng ca đó 1/1 |
| Browser: lesson-completion, module-three-learning, module-five-rewards, module-six-recovery, learning-journey, module-seven-statistics | 52 ca khác nhau đã qua qua các lượt: lượt đầu 51/52, ca kéo thẻ được sửa cách di chuyển chuột trong kiểm thử và chạy lại 1/1 |
| Ba ca browser riêng của completion | 3/3, chạy lại 3/3; gồm đủ 10 câu không CHECK, bài nộp thiếu, và kết quả cũ chưa có cờ hoàn thành |
| Connection manager | 9/9 |
| Recovery storage | 6/6 |
| Frontend production build | PASS, 74 modules |
| Backend package | BUILD SUCCESS |
| Hợp đồng M03 và tài liệu | 25/25 fixtures, toàn bộ OpenAPI schemas, 645 local references và 142 liên kết tài liệu hợp lệ |

Ca kéo thẻ trước đó chỉ phát sinh mousedown mà không có dragstart khi di chuyển thẳng bằng một bước; kiểm thử cuối dùng chuột qua các điểm trung gian và kiểm tra ghép đủ hai cặp, lưu nháp, tải lại và bỏ ghép. Không đổi component kéo thẻ trong lần sửa luồng hoàn thành này.

Backend kiểm tra quyền owner/role/CSRF, chặn mode PRACTICE/lượt chưa nộp/bài thiếu đáp án, giữ tiến độ của lượt đang học mới hơn, replay/lặp xác nhận, rollback khi ghi receipt lỗi và gửi lại cùng key. Việc chuyển progress cũ được kiểm tra bằng fixture thực trong integration test. Browser mô phỏng cờ lessonCompleted cũ để kiểm tra phần hiển thị; không dùng mock đó thay bằng chứng backend.

Browser kiểm tra cả mất ACK sau khi server đã commit, tải lại và gửi lại đúng payload; GET result vẫn bất biến. Giao diện mobile 390px không tràn ngang. Các lượt browser chạy trên môi trường riêng 15185, tách dữ liệu khỏi bản người dùng đang dùng ở 15183.

Log tạm được giữ tại `.test-state/completion-backend.log`, `completion-backend-recheck.log`, `completion-browser.log`, `completion-browser-final.log`, `completion-matching-final.log`, `completion-storage.log` và `completion-client-final.log` trong thư mục kiểm tra. Các số kiểm thử của vòng học trước tại [biên bản vòng học](learning-journey-validation.md) là bằng chứng lịch sử riêng.
