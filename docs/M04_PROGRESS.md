# Tiến độ M04 — Ôn tập cá nhân

Ngày 21/10/2026. Bản bàn giao P0 được phát triển trên nhánh feature/m04-personal-review, xuất phát từ origin/main tại be92240 (đã gồm M03 và bản sửa audio). Trạng thái commit/merge/push hiện tại tra cứu bằng Git.

## Phần đã triển khai

- [x] Chốt P0: chọn theo mục tiêu còn sai/tới mốc, lịch cấu hình 1–3–7 ngày; không tối ưu lịch từng câu.
- [x] Flyway V5 và ORM cho tiến độ mục tiêu, bằng chứng độ khó, ledger Submission.
- [x] POST đồng bộ kết quả cũ/mới theo batch, receipt/actor lock và transaction chống trùng.
- [x] GET dashboard riêng từng học viên, lọc chủ đề/kind, phân trang và giải thích.
- [x] Provider REVIEW nối M03: câu đang công bố, cùng mục tiêu/mức, ưu tiên biến thể mới, dedup origin, dùng chung runner.
- [x] Gợi ý mức cần tối thiểu hai bộ mới đủ câu; tách gợi ý/hỗ trợ/câu cũ/bỏ trống; báo thiếu nội dung.
- [x] Giao diện Ôn tập cá nhân và liên kết từ trang kết quả.
- [x] Viết API contract, kế hoạch chi tiết, ca thao tác và kiểm thử tự động.
- [x] Kiểm tra: 157 backend; 50 E2E hồi quy cũ và 5 E2E M04 đã qua; restart test giữ lịch/progress/receipt/ledger. Chi tiết và phạm vi trong biên bản.
- [x] Đã build và mở local tại localhost:5173 trong lượt kiểm tra M04; nâng V4 → V5, giữ dữ liệu cũ và xác nhận đúng bundle. Đây là bằng chứng tại thời điểm kiểm tra, không xác định image hiện đang chạy.

## Giới hạn và phần tiếp theo

- Kho câu vẫn do giảng viên chuẩn bị tại M02; không có AI tự sinh học liệu.
- Lịch là quy tắc prototype; chưa có nghiên cứu/đo hiệu quả, điều chỉnh sâu thuộc P1.
- Chưa có API/giao diện cho giảng viên cấu hình lịch riêng theo Unit. Đây là phần bổ sung đã trao đổi, chưa triển khai trong bản P0 này; lịch hiện cấu hình chung ở backend.
- Chưa có bài kiểm tra ngắn riêng để gợi ý điểm bắt đầu, email/thông báo đẩy hoặc nghiệm thu toàn bộ checklist thủ công.
- Đồng bộ projection khi vào trang/cập nhật; dashboard có pendingSubmissions. Tạo lượt mới yêu cầu không còn kết quả chưa xử lý.
- Stage theo mục tiêu; nếu cần trạng thái chi tiết từng cấu trúc nhỏ hơn thì giảng viên phải gắn Objective đủ cụ thể.
- Gợi ý mức chỉ có tính chất luyện tập. Seed hiện mỏng theo từng mục tiêu/mức, nên có thể chưa đủ bằng chứng tăng mức.
- XP/streak/giải đấu tuần thuộc M05; IndexedDB/outbox/autosave và phục hồi bền vững thuộc M06.
- Không đổi hợp đồng lưu/nộp/chữa M03; không sửa V1–V4; không xóa volume dữ liệu.

Xem [kế hoạch](M04_IMPLEMENTATION_PLAN.md), [API](../source/common/M04_API.md), [ca thao tác](M04_MANUAL_TEST_CASES.md), [validation](../statics/results/module-4-validation.md).
