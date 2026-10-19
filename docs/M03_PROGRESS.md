# Tiến độ M03

Cập nhật 19/10/2026. Theo [kế hoạch triển khai](M03_IMPLEMENTATION_PLAN.md), M03 hiện có nền dữ liệu/DTO/bộ chấm; chưa có API hoặc giao diện làm bài.

## Bước 0 — đã hoàn thành

Commit `bd913be`: đối chiếu hợp đồng, fixture dương/âm, baseline và overload receipt chọn status/body trong transaction. Xem [đối chiếu](M03_CONTRACT_REVIEW.md), [kết quả kiểm tra](../statics/results/module-3-step0-validation.md).

## Bước 1 — đã hoàn thành nền V4, model và bộ chấm

Đã bổ sung:

- Flyway `V4__learning_attempts.sql`, giữ DDL hợp đồng và không sửa V1–V3.
- Entity cho 7 bảng, hai khóa ghép user/origin và user/lesson; enum lưu VARCHAR, JSON có kiểu cho Answer, PlayerState, Display và Result.
- `AttemptRepository`/implementation cùng 7 Spring Data JPA repository: đọc theo owner, phân trang/lọc lượt, khóa Attempt, truy vấn items/answers/trials, exposures và progress.
- `AttemptRequests` parse nghiêm theo OpenAPI; `AttemptViews` tách DTO học viên khỏi DTO nội bộ M02. Kiểm tra full bộ item/quyền/state/revision sẽ nằm trong service ở bước 2–4.
- `AnswerValidator` kiểm tra response theo câu hỏi; `AnswerGrader`/implementation thuần cho ba dạng, không tự tạo trial hoặc cộng điểm/XP vào DB.
- Test DTO/fixture, grader, nâng V3→V4 có dữ liệu, DB trống lên V4, Hibernate validate, JSON round-trip và ràng buộc database.

Lưu ý cho các bước tiếp theo:

1. Mọi ghi dữ liệu qua repository chạy trong transaction nghiệp vụ sau actor lock. Gọi `AttemptRepository.attempt(..., true)` để đọc/refresh Attempt dưới khóa trước kiểm tra revision/state.
2. `AttemptEntity` dùng revision nghiệp vụ, không dùng JPA `@Version`. Service gọi `changed()` đúng một lần cho mutation thực sự; GET không thay revision.
3. `AnswerTrialEntity` bất biến; không có API cập nhật trial. `SubmissionEntity` được tạo với Result có submissionId=0 trong nội bộ transaction; repository INSERT lấy ID, gắn ID vào result_json rồi flush trước khi trả. Không trả Result chưa gắn ID, không gọi JPA repository trực tiếp để tạo Submission. Overload save Submission bắt buộc có transaction hiện hành và từ chối lưu lại Submission đã có ID.
4. `LessonProgressEntity` chỉ lưu STARTED/COMPLETED. Khi đọc, service tổng hợp NOT_STARTED cho bài chưa có hàng. Model giữ COMPLETED khi nhận kết quả học lại chưa hoàn thành.
5. Những kiểm tra hiện có chưa chứng minh được các luồng create/resume/submit HTTP, cấp quyền media theo Attempt hoặc phục hồi lỗi mạng. Những phần đó thuộc bước 2–6.

Toàn bộ backend đạt 100/100 test, không failure/error/skipped. Kết quả thực tế và lệnh tái hiện tại [nghiệm thu bước 1](../statics/results/module-3-step1-validation.md). Thay đổi bước 1 chưa stage/commit để người triển khai tự commit.

## Phần chưa triển khai

Selector LEARN/PRACTICE, REVIEW provider, snapshot API, AttemptAssetReadGrant, lưu nháp HTTP, hint/check/submit/result/retry, API tiến độ, runner UI và E2E nghiệp vụ. Chưa có XP, lịch ôn hoặc đồng bộ offline bền vững.

V4 mới được kiểm thử trong container riêng; không nâng DB demo trong lượt triển khai này. Khi build/chạy backend từ mã nguồn mới, Flyway sẽ tự áp dụng V4 lên database được cấu hình. Không xóa volume để nâng schema.
