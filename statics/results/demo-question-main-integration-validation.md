# Tích hợp main vào nhánh dữ liệu câu hỏi — 31/10/2026

Nhánh đích `addquestionsystem-integration`, nguồn dữ liệu `c06ee8a`, main `cfadff3` (đã gồm PR #11 giám sát phiên và bản giải quyết conflict `bc9127b`). Nhánh cũ `addquestionsystem` tại `9d3b743` không phải nhánh chứa bộ dữ liệu mới và không được thay đổi trong lượt này.

- Merge mô phỏng chỉ báo một conflict: phần giới thiệu đầu README. Giữ nguyên cả đoạn hướng dẫn bộ dữ liệu A1–A2 và đoạn tích hợp giám sát phiên/V1–V18, không chọn bỏ một bên.
- Source frontend/backend/test/migration trong `source/` giống main sau merge. Không có conflict nghiệp vụ hoặc thêm migration. Các file JSON, script, test script, tài liệu bộ dữ liệu và biên bản nạp gốc giữ nguyên theo commit nguồn; cập nhật AGENTS để chỉ tới đúng nhánh và báo cáo này.
- `node scripts/seed-demo-questions.mjs` PASS, mặc định chỉ kiểm tra JSON và không gọi API: 3 chủ đề, 60 câu học + 12 câu ôn, 3 bộ đề mỗi bộ dùng 20 câu chính.
- `node --test --test-isolation=none scripts/seed-demo-questions.test.mjs`: **5/5 PASS**, 0 fail/skip/cancel, khoảng 1.9s. Gồm đáp án/tham chiếu bốn dạng; mô phỏng nạp đủ và chạy lại không ghi; mất ACK publish/replay cùng request; nạp từng phần/quyền ADMIN; origin local, cookie/CSRF và không theo redirect. Fixture chỉ tạo/xóa thư mục test vừa tạo dưới `.test-state`, có kiểm tra đường dẫn trước khi xóa.
- Kiểm tra diff/whitespace, không còn marker conflict và liên kết tài liệu PASS. Không chạy lại backend/browser hồi quy vì code runtime giữ nguyên main. Số 132 backend/42 browser trong báo cáo tích hợp giám sát phiên là bằng chứng lịch sử của source runtime đó, không phải chạy lại trong lượt ghép dữ liệu này.
- Không chạy `--apply`, không sao chép `.env`, không khởi động Docker hoặc nạp/thay database của người dùng. Các kiểm tra API thật trên nền `fe3ab0c` của người biên soạn là lịch sử riêng tại [biên bản gốc](demo-question-data-validation.md).

Hướng dẫn dữ liệu và cách nạp khi người dùng yêu cầu nằm ở [DEMO_QUESTION_DATA](../../docs/DEMO_QUESTION_DATA.md). Merge mã nguồn không tự nhập 72 câu vào database đang chạy.
