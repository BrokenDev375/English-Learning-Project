# Tiến độ M03

Cập nhật 19/10/2026. Theo [kế hoạch triển khai](M03_IMPLEMENTATION_PLAN.md), M03 đã có API tạo/tiếp tục lượt, snapshot/media, nháp, hint/check, nộp/chấm, chữa và tiến độ. Giao diện runner thuộc bước 5.

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

Tại mốc bước 1, toàn bộ backend đạt 100/100 test, không failure/error/skipped. Kết quả và lệnh tái hiện tại [nghiệm thu bước 1](../statics/results/module-3-step1-validation.md). Người triển khai đã commit bước 1 tại `d37ba0b`.

## Bước 2–3 — đã hoàn thành API lượt làm và nháp

- `AttemptController` → `AttemptService/impl`: tạo LEARN/PRACTICE/REVIEW, tiếp tục cùng lựa chọn và danh sách theo owner/mode/state/phân trang. REVIEW chưa có M04 trả REVIEW_NOT_READY; provider chỉ được giả lập trong test.
- `AttemptSelector`: LEARN lấy GUIDED rồi INDEPENDENT theo biên soạn; PRACTICE loại trùng origin, ưu tiên chưa/ít/lâu gặp, thiếu câu không lặp. Actor lock trước Lesson root theo ID tăng dần; đọc lại sau khi chờ publication/retirement.
- Cấp lượt pin Version ID/slot/origin/permutation và dấu seen trước khi tăng exposure. Tạo null answers, STARTED progress cho LEARN và receipt nguyên tử. Resume không chọn lại hoặc tăng exposure.
- `AttemptSnapshotReader`: đọc bản đã pin, ánh xạ DTO an toàn và khôi phục đáp án/playerState. Không trả keys/giải thích/transcript/hint hoặc storageKey, kể cả snapshot SUBMITTED.
- `AttemptAssetReadGrant`: STUDENT đọc đúng file của lượt mình, cả lịch sử/retire/sau nộp; giữ HTTP Range hiện có. Thêm repository truy vấn slot và grant, không thêm schema.
- `AttemptDraftController` → `AttemptDraftService/impl`: GET snapshot và PUT full draft; kiểm tra membership, response theo QuestionVersion, current item/audio positions; lưu/revision/ACK/receipt cùng transaction. Không gọi grader hoặc tạo trial khi lưu.
- Replay trước revision/state; key khác với revision cũ báo xung đột. Actor lock ngăn request ghi tiếp khi tài khoản bị khóa. Snapshot GET read-only dùng REPEATABLE_READ để không trộn các phần của hai lần lưu khác nhau.

Nhóm tập trung đạt 35/35 test; toàn bộ backend bản cuối đạt 119/119, không failure/error/skipped, gồm 19 test HTTP bước 2–3. Xem [nghiệm thu bước 2–3](../statics/results/module-3-step23-validation.md). [Hướng dẫn thử API](M03_STEP23_API_SMOKE.md) có luồng đăng nhập, tạo lượt, lưu và GET khôi phục. Bước 2–3 đã commit tại `759ccb3`.

## Bước 4 — API feedback, nộp/chấm, chữa và tiến độ

- `AttemptCompletionController` → `AttemptCompletionService/impl`: hint/check chỉ cho GUIDED đang làm; hint mở lại không tăng revision. Check yêu cầu response đầy đủ, lưu đáp án, thêm FIRST/GUIDED_RETRY, ghi dấu hỗ trợ trước khi mở feedback và không trả transcript.
- Submit nhận full answers/playerState, dùng validator chung với draft; chấm theo phiên bản đã pin. Cùng một transaction lưu final, FIRST còn thiếu kể cả UNANSWERED, Submission/result schema 1 với ID thật, state/revision/progress và receipt. Nộp sớm được phép; điểm dựa trên số câu thực cấp, làm tròn HALF_UP 2 chữ số.
- GET result đọc bản đã lưu, không chấm lại. CORRECTION sau nộp chỉ thêm trial và tăng revision; không thay final, FIRST, điểm hoặc tiến độ. Thời điểm trong Result lưu dạng chuỗi ISO để tránh mất nanosecond khi MySQL lưu số JSON.
- LEARN hoàn thành khi mọi response đầy đủ và mọi GUIDED đã CHECK, không đòi đúng hết. FIRST của INDEPENDENT/REVIEW_VARIANT chưa từng gặp, không dùng hỗ trợ và đã nhập đáp án được đếm riêng; partial vẫn là đã trả lời.
- `LearningProgressController` → `LearningProgressService/impl`: tổng hợp NOT_STARTED bằng truy vấn có phân trang trên bài đang công bố và lịch sử của chính học viên. Không lộ bài nháp; giữ lịch sử bài nghỉ, currentPublishedVersionId=null. updateAvailable so với bản hoàn thành gần nhất, hoặc bản lượt gần nhất nếu chưa hoàn thành. Học lại/nộp dở không hạ COMPLETED.
- Không thêm migration, XP, lịch ôn hoặc runner UI. Xem [hướng dẫn thử bước 4](M03_STEP4_API_SMOKE.md) và [kết quả kiểm tra](../statics/results/module-3-step4-validation.md). Các thay đổi bước 4 chưa commit.

Nhóm tập trung đạt **54/54**; toàn bộ backend đạt **136/136**, không failure/error/skipped. Có 17 ca HTTP mới cho bước 4 trong tổng 36 AttemptApiTest, bao gồm concurrent submit/save/check, replay sau bỏ body phản hồi và rollback khi lỗi progress/receipt sau insert Submission. Không chạy browser E2E M03 trong bước này.

## Phần chưa triển khai

Runner UI và E2E nghiệp vụ ở bước 5–6; REVIEW provider thật của M04. Chưa có XP, lịch ôn hoặc đồng bộ offline bền vững. Test bước 4 nộp qua API thật; fixture SUBMITTED cũ chỉ phục vụ các ca riêng của bước 2–3.

V4 mới được kiểm thử trong container riêng; không nâng DB demo trong lượt triển khai này. Khi build/chạy backend từ mã nguồn mới, Flyway sẽ tự áp dụng V4 lên database được cấu hình. Không xóa volume để nâng schema.
