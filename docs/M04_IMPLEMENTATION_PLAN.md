# M04 — Ôn tập cá nhân

Phạm vi chốt ngày 21/10/2026: chọn nội dung từ kết quả đã nộp, ưu tiên mục tiêu còn sai hoặc lâu chưa ôn, dùng lịch nhắc đơn giản và ngân hàng M02. Không dùng AI sinh câu tự động; không thêm thi đấu, XP hoặc bộ nhớ offline bền vững.

## 1. Nguồn câu và trách nhiệm

- Topic → Unit → Lesson. Khi biên soạn bài, giảng viên gắn QuestionVersion vào nhóm GUIDED, INDEPENDENT hoặc REVIEW_VARIANT.
- LEARN vẫn dùng GUIDED + INDEPENDENT; câu REVIEW_VARIANT dành cho ôn.
- M04 chỉ chọn câu INDEPENDENT/REVIEW_VARIANT trong bài hiện đang công bố, cùng mục tiêu đã có kết quả của chính học viên.
- Ưu tiên câu REVIEW_VARIANT chưa gặp, rồi INDEPENDENT chưa gặp, rồi câu đã gặp ít/lâu nhất. Khử trùng bằng originKey; đổi phiên bản/copy không tạo bằng chứng mới.
- Giữ nguyên MaterialVersion đi kèm; các câu chung đoạn đọc/audio được xếp cùng nhóm. Không tự cắt hay tạo file mới.
- Kho thiếu câu thì cấp số câu thực có, tối đa 10. Nếu không có câu, trả NO_QUESTIONS_AVAILABLE và hướng dẫn bổ sung học liệu.
- Seed hiện có 90 câu ở nhiều độ khó. Năm câu ôn của một bài không có nghĩa là có năm câu ở từng mức khó.

## 2. Phân tích từ Submission

Nguồn chính thức là Result schema 1 bất biến của M03, dùng FIRST. Mỗi Submission/mục tiêu được tổng hợp một lần:

| Dữ liệu | Xử lý |
|---|---|
| Có đáp án, FIRST sai | Ghi nhận cần luyện, giữ cờ lỗi sai tới một lượt tự làm tốt |
| GUIDED hoặc FIRST dùng hỗ trợ | Tính riêng; không chứng minh tự làm độc lập |
| Bỏ trống | Ghi số bỏ trống; không coi là hiểu bài hay làm sai có bằng chứng |
| Tự làm đúng câu từng gặp | Có thể giảm ưu tiên và đặt mốc ôn, không chứng minh tăng độ khó |
| Tự làm câu chưa gặp, không hỗ trợ | Dữ liệu hợp lệ để đánh giá gợi ý mức luyện |
| CORRECTION sau nộp | Không sửa FIRST/Submission hoặc cộng thêm bằng chứng |

Độ khó ưu tiên ôn là mức thấp nhất còn sai trong lượt gần nhất có câu trả lời; nếu không có sai thì lấy mức cao nhất trong các câu tự làm đã trả lời. Đây là mức chọn bài nội bộ, không phải chứng nhận trình độ.

## 3. Lịch và thứ tự ưu tiên P0

- Một hàng tiến độ cho mỗi user + objective; không tối ưu một lịch riêng cho từng câu.
- Sai: stage về 0, ưu tiên ôn ngay.
- Có câu tự làm và tất cả câu đã trả lời lần đầu đều đúng: bỏ cờ cần luyện, tăng tối đa một stage theo ngày Việt Nam.
- Khoảng nhắc sau các stage: 1 → 3 → 7 ngày, tính từ submittedAt; stage tối đa 3.
- Đúng chỉ ở câu hướng dẫn/hỗ trợ: giữ cần luyện; chưa tăng stage.
- Nộp toàn bộ bỏ trống: giữ mốc/cờ đang có; mục tiêu mới hiển thị chưa đủ dữ liệu.
- DUE gồm mục tiêu cần luyện hoặc đã tới mốc; MISTAKES gồm mục tiêu còn cờ lỗi sai.
- Ưu tiên mục tiêu còn sai → cần tự làm thêm → đã tới mốc; cùng nhóm lấy mốc cũ trước.
- Chọn luân phiên giữa các mục tiêu để lượt ngắn có thể ôn nhiều phần yếu.
- Lịch là quy tắc prototype có cấu hình, chưa có đánh giá hiệu quả học tập hoặc chứng minh tối ưu.

Bản P0 hiện cấu hình lịch chung qua backend và hiển thị mốc trong ứng dụng. Phần giảng viên cấu hình quy tắc riêng theo Unit đã được trao đổi thêm nhưng chưa có API/giao diện và chưa triển khai. Không có email/thông báo đẩy trong bản này.

## 4. Gợi ý mức luyện

- Theo user + objective + difficulty, một bộ hợp lệ là phần câu mới của cùng mức trong một Submission.
- Mặc định cần ít nhất 3 câu mới/bộ, đạt ít nhất 80%, tối thiểu 2 bộ tốt liên tiếp đủ số câu.
- Chỉ lấy FIRST của INDEPENDENT/REVIEW_VARIANT, origin chưa thấy trước lượt, có trả lời và không hỗ trợ.
- Câu cũ không tăng số bộ mới. Sai câu cũ làm mất chuỗi bộ tốt; lượt câu mới quá ít và có sai cũng mất chuỗi.
- Chỉ đề xuất tăng khi hiện không còn cờ cần luyện và mức kế tiếp có ít nhất 3 câu mới đang công bố.
- Thiếu bằng chứng hoặc thiếu nội dung hiển thị lý do riêng. Học viên vẫn tự chọn độ khó trong Luyện tập theo chủ đề.
- Không dùng số lần luyện lại, tổng XP hoặc tỷ lệ đúng khi nộp sau sửa để nâng mức.

Cấu hình Spring: app.review.intervals-days, minimum-fresh-questions, good-percent, minimum-good-sets và zone; xem [hợp đồng API](../source/common/M04_API.md).

## 5. Dữ liệu và đồng bộ

Flyway V5 thêm review_objective_progress, review_difficulty_evidence và review_processed_submissions; không sửa migration cũ hoặc dữ liệu M03.

Mở trang Ôn tập cá nhân → POST sync để đọc tối đa 100 Submission chưa xử lý theo ID tăng dần → ghi progress/evidence/ledger/receipt cùng transaction → GET danh sách. UI tiếp tục tối đa 10 batch mỗi lần; còn tồn thì báo để người dùng cập nhật tiếp.

Ledger trên MySQL cho phép đọc lại kết quả cũ sau nâng cấp hoặc restart, chống xử lý trùng. Sync giữ đúng requestId khi mất ACK. Tạo lượt REVIEW khi còn Submission chưa đồng bộ trả REVIEW_SYNC_REQUIRED; lượt đã tạo vẫn tiếp tục được theo snapshot cũ.

GET chỉ đọc, không tự tạo lịch sử/mutation. Không dùng callback RAM hoặc cron làm nguồn duy nhất.

## 6. Đầu ra và kiểm tra

- Backend giữ Controller → Service interface/impl → Repository interface/impl → Spring Data JPA.
- Giao diện thêm tab Ôn tập cá nhân, lý do cần ôn, mốc, số câu có/chưa gặp, gợi ý mức và link từ kết quả.
- Dùng chung Attempt runner, lưu nháp, submit/result, media grant, receipt/revision và conflict M03.
- Kiểm tra hai học viên yếu khác mục tiêu, FIRST/hỗ trợ/bỏ trống, dữ liệu mới/cũ, thiếu học liệu, retire, rollback, đồng thời, mất ACK và dữ liệu sau tải lại.
- Xem [tiến độ](M04_PROGRESS.md), [ca thao tác](M04_MANUAL_TEST_CASES.md) và [kết quả kiểm tra](../statics/results/module-4-validation.md).
