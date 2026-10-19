# Kế hoạch triển khai M03 — Học bài, luyện tập và bài nộp

Ngày lập: 19/10/2026. Nhánh triển khai: `feature/m03-learning-attempts`.

Trạng thái khi lập kế hoạch: M01/M02 đã có; V4/API/giao diện M03 chưa triển khai. Trạng thái hiện tại và bằng chứng tại [M03_PROGRESS](M03_PROGRESS.md), [nghiệm thu toàn module](../statics/results/module-3-validation.md); chỉ đánh dấu checkbox khi đã có kết quả kiểm tra tương ứng.

## 1. Mục tiêu và nguồn đặc tả

M03 đưa học liệu M02 vào luồng học thực tế: học viên bắt đầu hoặc tiếp tục một lượt, trả lời, lưu nháp, nhận hỗ trợ cho câu hướng dẫn, nộp bài, xem kết quả, chữa bài và theo dõi hoàn thành bài học.

Luồng hoàn chỉnh cần bàn giao:

```text
Khám phá bài học / chọn điều kiện luyện tập
  → tạo hoặc tiếp tục Attempt
  → nhận bộ câu và phiên bản cố định
  → nhập đáp án, nghe/đọc tài liệu, lưu nháp
  → hint/check câu GUIDED
  → nộp kèm toàn bộ đáp án cuối
  → kết quả bất biến và chữa từng câu
  → xem lịch sử, tiến độ và tiếp tục lượt còn dở
```

Tài liệu này chia công việc và thứ tự triển khai; các nguồn dưới đây quy định hành vi và dữ liệu:

| Nguồn | Cách sử dụng |
|---|---|
| [Bàn giao M02–M03](M02_M03_HANDOFF.md), mục 2, 4–7 | Phạm vi, nghiệp vụ, tích hợp và tiêu chí nghiệm thu |
| [Quy tắc API](../source/common/M02_M03_API.md), mục 1–3, 5–8 | Quyền, transaction, revision, chống gửi trùng, chấm và trạng thái |
| [OpenAPI](../source/common/openapi-m02-m03.json) | Tên trường, kiểu, enum, required/null, request/response và phân trang |
| [Ví dụ JSON](../source/common/examples-m02-m03.json) | Fixture theo schema; không coi là toàn bộ dataset |
| [DDL V4 dự kiến](database/planned/V4__learning_attempts.sql) | Cột, FK, unique, CHECK và index |
| [Plan tổng](../PLAN_ENGLISH_LEARNING_SYSTEM.md), mục M03 | Mục tiêu module và quan hệ với M04–M07 |

Nếu phát hiện nguồn mâu thuẫn, ghi lại điểm cụ thể và thống nhất hợp đồng trước khi code phần phụ thuộc. Không tự đổi nghiệp vụ chỉ để thuận tiện triển khai.

## 2. Phạm vi bàn giao

### 2.1. Bắt buộc trong M03

- Chỉ STUDENT có tài khoản ACTIVE dùng attempt/progress API; lấy danh tính từ session.
- Ba loại câu: `SINGLE_CHOICE`, `FILL_BLANK`, `MATCHING`.
- LEARN lấy toàn bộ GUIDED rồi INDEPENDENT của bản bài học được chốt; bộ seed là 10 câu/lượt. REVIEW_VARIANT dành cho ôn.
- PRACTICE chọn Topic + difficulty + count 1–10; lấy câu INDEPENDENT từ bài đang công bố, không trùng origin, ưu tiên chưa gặp rồi ít gặp/lâu gặp.
- REVIEW có interface tích hợp M04; chưa có provider thật thì trả `409 REVIEW_NOT_READY`. Test runner bằng provider giả lập, không bật provider giả trong ứng dụng demo.
- Snapshot cố định, exposure theo origin, lưu/tải nháp, playerState và quyền media theo Attempt.
- Hint/check GUIDED, submit nguyên tử, kết quả bất biến, chữa sau nộp và tiến độ bài.
- Giao diện đầy đủ cho học/luyện, tiếp tục lượt, kết quả/chữa và tiến độ cơ bản.
- Kiểm thử bộ chấm, API với MySQL thật/Testcontainers và E2E trên môi trường riêng.

### 2.2. Ranh giới module

M04 sở hữu lịch ôn và thuật toán chọn câu ôn. M05 sở hữu XP, streak, huy hiệu và Giải đấu tuần. M06 sở hữu IndexedDB, outbox, presence và phục hồi offline đầy đủ. M07 sở hữu dashboard/thống kê nâng cao.

M03 vẫn phải giữ đáp án trong state khi lỗi mạng, cho retry đúng requestId/payload, lưu nháp server và xử lý revision. Không cam kết F5 lúc offline giữ được đáp án chưa đồng bộ khi chưa có IndexedDB. Không bổ sung bảng XP/lịch ôn, thi IELTS, solo, AI chấm, thời hạn thi, cancel hoặc trạng thái EXPIRED vào M03.

## 3. Nền tảng và điểm tích hợp hiện có

Giữ kiến trúc Java 17/Spring Boot, JPA/Hibernate, MySQL 8.4, Flyway, React/TypeScript/Vite và Docker Compose. Backend đi theo controller → service interface → service.impl → repository interface/implementation → Spring Data JPA; không serialize entity ra API.

| Thành phần hiện có | Cách M03 sử dụng |
|---|---|
| `PublishedContentReader` | Đọc bài đang công bố để cấp lượt mới; đọc đúng Version ID để phục hồi lượt cũ. DTO nội bộ chứa keys/transcript nên phải ánh xạ sang DTO học viên riêng |
| `RequestReceiptService` | Mutation và receipt cùng transaction; replay được xử lý trước kiểm tra revision/state |
| `AccountGuard` | Khóa/kiểm tra actor trước khóa dữ liệu nghiệp vụ, bảo vệ request đồng thời với thao tác khóa tài khoản |
| `AssetReadGrant` | Thêm `AttemptAssetReadGrant` cho file thuộc lượt của chính học viên, cả trước và sau nộp |
| `api.ts` | Giữ cookie, CSRF, timeout và cơ chế báo hết phiên; bổ sung adapter M03 trong feature riêng |
| `navigation.ts`, `App.tsx`, `LearningCatalog.tsx` | Nối nút bắt đầu/tiếp tục và điều hướng lượt làm; URL chỉ chứa view/ID, không chứa đáp án |
| Seed M02 | Dùng 6 bài/90 câu để demo; tạo fixture riêng cho ca biên và kiểm thử |

Điểm cần giải quyết ở bước đầu: `RequestReceiptService.execute` hiện nhận status cố định, còn POST tạo Attempt cần 201/200 tùy tạo mới hay tiếp tục. Bổ sung overload/cơ chế trả status động từ action trong cùng transaction, hoặc giải pháp tương đương giữ nguyên hợp đồng. Giữ API cũ cho M02 và test replay đúng status/body gốc; không chọn status bên ngoài transaction bằng một truy vấn có thể bị race.

## 4. Nguyên tắc dữ liệu và giao dịch

### 4.1. V4 và tính bất biến

Chuyển V4 dự kiến vào `source/server/src/main/resources/db/migration/V4__learning_attempts.sql` khi bắt đầu triển khai dữ liệu, giữ `ddl-auto=validate`. Không sửa V1–V3 đã áp dụng. Nếu V4 đã được áp dụng ở môi trường dùng chung thì thay đổi schema tiếp theo cần migration mới.

| Bảng | Trách nhiệm M03 |
|---|---|
| `attempts` | Chủ sở hữu, mode, state, selection, phiên bản bài, playerState và revision |
| `attempt_questions` | Phiên bản câu, slot nguồn, origin, nhóm, thứ tự/permutation và dấu hỗ trợ/đã gặp |
| `attempt_answers` | Đáp án nháp/cuối; không cập nhật sau submit |
| `answer_trials` | FIRST/GUIDED_RETRY/CORRECTION bất biến và thứ tự thử |
| `submissions` | Một kết quả duy nhất mỗi lượt; số đếm và result_json version 1 |
| `question_exposures` | Lịch sử gặp theo user + origin; tăng đúng một lần khi cấp lượt |
| `lesson_progress` | STARTED/COMPLETED theo user + Lesson root; NOT_STARTED tổng hợp khi chưa có hàng |

Pin LessonVersion, QuestionVersion, MaterialVersion và thứ tự hiển thị khi tạo lượt. F5, retry, cập nhật audio đồng loạt hoặc retire nội dung không thay phiên bản của lượt cũ. Snapshot SUBMITTED vẫn dùng DTO an toàn; đáp án/giải thích/transcript lấy qua result.

### 4.2. Quy trình mutation chung

1. Xác thực vai trò; vào transaction và khóa actor bằng nền M01/receipt hiện có.
2. Kiểm tra receipt theo actor + requestId; cùng thao tác/payload thì trả đúng status/body gốc. Khác payload/thao tác trả `409 REQUEST_ID_REUSED`.
3. Với key mới: khóa Attempt, kiểm tra quyền sở hữu, state và baseRevision trước ghi. Tạo lượt mới kiểm tra lựa chọn đang IN_PROGRESS sau khi đã khóa actor.
4. Validate, thực hiện nghiệp vụ và ghi receipt trong cùng transaction. Chỉ phản hồi thành công sau commit.

Không giữ khóa Attempt rồi quay lại khóa actor. Thứ tự khóa item/exposure/progress phải thống nhất, ghi rõ trong implementation và test cạnh tranh. Khi đọc lại bài đang công bố sau chờ lock phải dùng dữ liệu mới, không dựa vào entity cache trước lock. GET không đổi revision hoặc exposures.

### 4.3. Bộ chấm và dữ kiện kết quả

- Validate riêng cấu trúc response: ID lạ, sai type, blank trùng hoặc matching lặp một đầu là `400 INVALID_ANSWER`, không ghi thành câu sai hợp lệ.
- Draft cho phép partial/null. Null hoặc toàn bộ giá trị trống là UNANSWERED; có nhập nhưng thiếu/sai là INCORRECT; đúng toàn bộ là CORRECT. `responseComplete` độc lập với đúng/sai.
- Fill chuẩn hóa NFC; gộp/trim ASCII whitespace khi được cấu hình; lowercase bằng Locale.ROOT khi không phân biệt hoa thường. Không fuzzy, bỏ dấu hoặc bỏ dấu câu.
- Matching chấm bằng ID ổn định, không theo vị trí đã xáo. Mỗi câu tối đa một điểm.
- FIRST là lần được đánh giá đầu tiên, không phải mỗi lần sửa nháp. Check sai rồi sửa đúng giữ FIRST sai.
- `supportUsed` của FIRST chụp trước khi mở feedback check lần đầu; hint đã mở làm FIRST có hỗ trợ. GUIDED_RETRY/CORRECTION sau thấy đáp án có hỗ trợ.
- Submit chấm đáp án cuối; `scorePercent` dùng số câu thực cấp, BigDecimal và HALF_UP hai chữ số.
- Hai số đếm independentFresh chỉ lấy FIRST của INDEPENDENT/REVIEW_VARIANT chưa gặp origin trước, chưa hỗ trợ và có đáp án không rỗng.
- LEARN hoàn thành khi mọi câu điền đủ và mọi GUIDED có CHECK trước nộp; không bắt tất cả đúng. Nộp sớm được nhưng chưa hoàn thành.
- Retry sau nộp chỉ thêm CORRECTION và tăng Attempt revision; giữ nguyên đáp án cuối, Submission, FIRST, điểm và tiến độ hoàn thành.

## 5. Các bước triển khai và mốc commit

Làm tuần tự theo các cổng nghiệm thu dưới đây. Tên class/file mới là đề xuất tổ chức; tên API và DTO phải theo OpenAPI. Có thể chia một bước thành nhiều commit nhỏ, mỗi commit phải build được.

### Bước 0 — Chốt nền tích hợp

- [x] Xác nhận đang ở nhánh M03, working tree rõ ràng và đã có thay đổi M02 mới nhất cần tích hợp.
- [x] Đối chiếu DTO/schema cho create, mutation, hint, check/retry, snapshot, feedback, result và progress; ghi fixture hợp lệ/không hợp lệ.
- [x] Ghi nhận kết quả build/test nền thực tế, không sao chép số test cũ thành kết quả của nhánh M03.
- [x] Giải quyết status 201/200 của receipt; kiểm thử hồi quy các mutation M02 đang dùng service này.

Đã nghiệm thu 19/10/2026: [đối chiếu hợp đồng](M03_CONTRACT_REVIEW.md), [kết quả bước 0](../statics/results/module-3-step0-validation.md).

Đầu ra: nền tích hợp sẵn sàng, hợp đồng không thay đổi âm thầm. Commit đề xuất: `refactor(receipts): support transactional response status for attempts`.

### Bước 1 — V4, model và bộ chấm

Phụ thuộc: bước 0.

- [x] Thêm Flyway V4; ánh xạ 7 bảng, khóa ghép, enum và JSON có cấu trúc. Giữ nguyên FK/unique/CHECK của hợp đồng.
- [x] Thêm repository interface/implementation và JPA repository cho truy vấn/khóa lượt, exposures, trials, submission và progress.
- [x] Tạo `AttemptRequests`, `AttemptViews`/DTO tương đương, từ chối trường lạ và validate giới hạn ID/UUID/null theo schema.
- [x] Tách validator response và grader thuần cho ba dạng; cùng kết quả chấm dùng cho check/submit/retry.
- [x] Test unit ca đúng/sai/trống/partial, Unicode/whitespace/case, matching đã xáo và ID không hợp lệ.
- [x] Test MySQL nâng V3 → V4 và khởi tạo V1 → V4, Hibernate validate, unique Submission và trial sequence. Không đụng DB demo.

Kết quả: [nghiệm thu bước 1](../statics/results/module-3-step1-validation.md). Thay đổi bước 1 để chưa commit để người triển khai tự commit.

Cổng nghiệm thu: schema ánh xạ đúng, bộ chấm độc lập với HTTP và có fixture phân biệt chưa trả lời/điền thiếu/điền đủ nhưng sai.

Commit đề xuất: `feat(m03): add attempt schema and persistence`; `feat(m03): add answer validation and grading`.

### Bước 2 — Cấp lượt, snapshot và quyền media

Phụ thuộc: bước 1.

- [x] Thêm `AttemptService`, selector và controller cho POST/GET `/api/attempts`, GET `/api/attempts/{id}`.
- [x] Chuẩn hóa selection. Cùng user/cùng lựa chọn IN_PROGRESS trả lượt cũ; LEARN so Lesson root, PRACTICE/REVIEW so toàn bộ tham số chọn.
- [x] Xử lý resume trước khi bốc nội dung mới; lượt đang làm giữ bản cũ dù bài mới được publish/retire. Học lại sau SUBMITTED tạo lượt mới nếu nội dung còn đủ điều kiện cấp.
- [x] LEARN cấp đúng nhóm/thứ tự. PRACTICE lọc đúng difficulty/Topic, loại trùng origin kể cả câu copy/version, cấp ít hơn count khi thiếu; không có câu trả `NO_QUESTIONS_AVAILABLE`.
- [x] Lưu permutation một lần, tạo đáp án null, ghi dấu seen trước khi tăng exposure và lưu mọi dữ liệu nguyên tử.
- [x] Thêm `ReviewSelectionProvider` và fallback REVIEW_NOT_READY; provider giả chỉ ở test. Validate Version ID/role/Topic/state và loại trùng trước khi pin kết quả provider.
- [x] Dựng snapshot riêng, danh sách material duy nhất theo Version ID; không lộ answerKey/explanation/transcript/storageKey/hint chưa xin.
- [x] Thêm `AttemptAssetReadGrant`; học viên đọc đúng asset của lượt mình, kể cả sau nộp/retire/cập nhật audio, tài khoản khác nhận 404.
- [x] Phân trang/lọc danh sách lượt của chính mình theo OpenAPI; thêm kiểm tra role, no-store và lỗi đúng nền M01.

Cổng nghiệm thu: hai request tạo đồng thời chỉ tạo một lượt cùng lựa chọn; GET/F5/retry không đổi bộ câu hoặc tăng exposures; media Range chạy với quyền học viên.

Commit đề xuất: `feat(m03): create and resume learning attempts`; `feat(m03): expose safe snapshots and attempt media access`.

### Bước 3 — Lưu và khôi phục nháp

Phụ thuộc: bước 2.

- [x] PUT `/api/attempts/{id}/draft` nhận full snapshot: mỗi AttemptQuestion đúng một entry, dùng response null để bỏ đáp án.
- [x] Từ chối entry thiếu/trùng/lạ, sai dạng; kiểm tra current item và audio material thuộc lượt, audioPositions không trùng, seconds đúng giới hạn hợp đồng.
- [x] Lưu đáp án/playerState, cập nhật revision và ACK theo schema; không chấm hoặc tạo trial.
- [x] Bảo đảm key retry cũ vẫn replay trước kiểm tra revision; key mới dùng revision cũ bị 409, không ghi đè.
- [x] Test load lại partial/null và vị trí nghe; hai tab ghi đồng thời; DB lỗi rollback cả nháp/revision/receipt; khóa tài khoản đồng thời chặn ghi theo AccountGuard.

Cổng nghiệm thu: GET khôi phục đúng nháp; retry không tạo hiệu ứng lần hai; xung đột không làm mất dữ liệu đã commit.

Commit đề xuất: `feat(m03): save attempt drafts with revision checks`.

### Bước 4 — Hint, check, submit, chữa và tiến độ

Phụ thuộc: bước 3.

- [x] Hint/check chỉ cho GUIDED khi IN_PROGRESS; hint đã mở trả lại mà không tăng revision thêm. Check/retry yêu cầu đáp án đầy đủ.
- [x] Check lưu đáp án, tạo FIRST hoặc GUIDED_RETRY đúng thứ tự và dấu hỗ trợ; feedback không có transcript.
- [x] Submit nhận đáp án cuối/playerState, validate trước ghi và không lệ thuộc autosave đã chạy hay chưa.
- [x] Trong cùng transaction: lưu final answers, thêm FIRST còn thiếu kể cả UNANSWERED, chấm, lưu result_json và Submission, chuyển SUBMITTED, cập nhật progress/revision, ghi receipt.
- [x] Lưu `factsSchemaVersion=1`, Submission ID và các số đếm khớp result_json để M04/M05/M07 đọc bù sau này.
- [x] GET result trước nộp trả RESULT_NOT_READY; sau nộp trả kết quả đã lưu, không tính lại theo nội dung mới hoặc trial chữa mới.
- [x] Retry sau nộp ghi CORRECTION, giữ kết quả nộp bất biến; trạng thái không cho phép trả mã lỗi theo hợp đồng.
- [x] GET `/api/me/learning-progress` tổng hợp NOT_STARTED, giữ COMPLETED khi học lại dở, báo updateAvailable khi có bản bài mới và trả đúng tài khoản/Unit/phân trang.
- [x] Test concurrent submit cùng key/khác key, submit cạnh tranh save/check, lỗi sau insert Submission và cập nhật progress để chứng minh rollback toàn bộ.

Cổng nghiệm thu: mỗi lượt đúng một Submission; kết quả đầu/final/chữa phân biệt được; submit đủ/thiếu câu cho ra lessonCompleted đúng luật.

Commit đề xuất: `feat(m03): add guided feedback`; `feat(m03): submit and correct attempts atomically`; `feat(m03): track lesson completion`.

### Bước 5 — Giao diện học và luyện tập

Phụ thuộc: bước 2 cho skeleton; hoàn tất tích hợp sau bước 4.

- [x] Tạo `src/features/learning/` chứa types, adapter API, runner và result; tái sử dụng thành phần M02 phù hợp sau khi kiểm tra không kéo keys vào UI trước nộp.
- [x] Chỉ STUDENT có nút bắt đầu/tiếp tục. TEACHER/ADMIN giữ luồng khám phá/xem trước, không tạo lượt thật.
- [x] Nối từ LearningCatalog vào LEARN; thêm form PRACTICE theo Topic/difficulty/count và danh sách lượt dở/đã nộp.
- [x] Một runner dùng chung cho các mode, ba renderer dùng ID ổn định, điều hướng câu và hiển thị số câu đã trả lời. Tiến độ trả lời khác trạng thái hoàn thành bài.
- [x] Hiển thị đoạn đọc/ảnh/audio dùng chung; player tôn trọng start/end, khôi phục vị trí giây tuyệt đối và clamp theo duration/segment.
- [x] Save adapter giữ state hiện tại, debounce nếu dùng autosave và tuần tự hóa save/hint/check/submit/retry theo revision mới nhất.
- [x] Tách trạng thái đang sửa, đang lưu, đã lưu server, chưa đồng bộ và đã nộp; chỉ báo đã lưu sau ACK. Không hiển thị đã lưu tại máy khi chưa có cơ chế lưu bền vững.
- [x] Khi timeout giữ nguyên thao tác/payload/requestId để retry. Khóa các mutation tiếp theo khi kết quả thao tác cũ chưa rõ; retry cùng key rồi GET đối soát trước khi ghi tiếp. Không tạo key mới để nộp lại vì mất ACK.
- [x] ACK/replay cũ không ghi đè đáp án local mới. 409 giữ dữ liệu nhập để đối chiếu với snapshot server; không tự tăng baseRevision rồi ghi đè. 401 dừng gửi và yêu cầu đăng nhập lại; 503 giữ state và báo chưa lưu.
- [x] Submit gửi full snapshot cuối sau khi xử lý mutation đang chờ; cảnh báo câu bỏ trống nhưng vẫn cho nộp sớm.
- [x] Trang kết quả hiển thị điểm, FIRST/final, giải thích/transcript, trạng thái hoàn thành và tương tác chữa. GET lại kết quả sau chữa giữ điểm nộp ban đầu.
- [x] Nối progress vào danh sách bài; F5/back/forward/deep link tải đúng Attempt, không gọi create để thay bộ câu. Dọn state khi đổi user/Attempt, không lẫn đáp án giữa tài khoản.
- [x] Kiểm tra desktop/mobile, bàn phím, label và trạng thái tải/lỗi, audio seek và câu có nội dung dài.

Cổng nghiệm thu: học viên chạy được toàn bộ luồng với dữ liệu seed; lỗi mạng/xung đột không hiện thành công giả hoặc mất state trong màn đang mở.

Commit đề xuất: `feat(m03): add shared attempt runner`; `feat(m03): integrate practice results and progress`; `fix(m03): handle uncertain saves and revision conflicts`.

### Bước 6 — Nghiệm thu, tài liệu và Pull Request

Phụ thuộc: bước 1–5 hoàn tất.

- [x] Chạy ma trận mục 7, sửa lỗi và kiểm tra hồi quy M01/M02 sau các thay đổi dùng chung; đối chiếu [biên bản và giới hạn](../statics/results/module-3-validation.md).
- [x] Kiểm tra dữ liệu bền vững qua recreate/restart trong môi trường test riêng: session có thể mất, đăng nhập lại vẫn tải được nháp và kết quả đúng.
- [x] Kiểm tra seed chạy lại không reset tiến độ hoặc nhân bản bài/câu của người học.
- [x] Thêm [M03_MANUAL_TEST_CASES](M03_MANUAL_TEST_CASES.md) với dữ liệu, bước thao tác và kết quả mong đợi.
- [x] Thêm [biên bản nghiệm thu](../statics/results/module-3-validation.md) ghi môi trường, ngày chạy, lệnh, số pass/fail/skip, giới hạn và bằng chứng thật; không ghi PASS cho bước chưa chạy.
- [x] Cập nhật README, DATABASE_DESIGN, BACKEND_STRUCTURE và trạng thái M03 trong tài liệu liên quan theo chức năng thực sự đã xong.
- [x] Chuẩn bị nội dung PR vào main để sao chép từ cuộc trao đổi: luồng trước/sau, migration V4, thay đổi dùng chung, kiểm thử và giới hạn REVIEW/offline; không lưu file mô tả PR riêng.
- [ ] Người dùng tạo PR trên web sau khi commit/push; theo yêu cầu, không tự mở PR trong bước này.

Commit đề xuất: `test(m03): cover concurrency recovery and version history`; `docs(m03): record implementation and validation`.

## 6. Danh mục API cần hoàn thành

| API | Bước | Kết quả chính |
|---|---|---|
| POST `/api/attempts` | 2 | 201 tạo mới / 200 tiếp tục; replay giữ status/body gốc |
| GET `/api/attempts` | 2 | Danh sách của chính mình, lọc mode/state và phân trang |
| GET `/api/attempts/{id}` | 2 | Snapshot an toàn và nháp của đúng phiên bản |
| PUT `/api/attempts/{id}/draft` | 3 | ACK revision sau lưu full snapshot |
| POST `/api/attempts/{id}/items/{itemId}/hint` | 4 | Hint GUIDED theo quyền/state |
| POST `/api/attempts/{id}/items/{itemId}/check` | 4 | FIRST/GUIDED_RETRY và feedback câu vừa check |
| POST `/api/attempts/{id}/submit` | 4 | Result bất biến sau giao dịch nộp |
| GET `/api/attempts/{id}/result` | 4 | Result đã lưu, không tính lại |
| POST `/api/attempts/{id}/items/{itemId}/retry` | 4 | CORRECTION, không sửa điểm |
| GET `/api/me/learning-progress` | 4 | Tiến độ của chính mình, lọc Unit và phân trang |

`itemId` là AttemptQuestion ID. Mọi mutation của lượt đã tồn tại dùng baseRevision của Attempt. POST tạo lượt dùng schema AttemptCreate, không tự thêm baseRevision. Request/response chi tiết tra OpenAPI, không định nghĩa DTO khác tại đây.

## 7. Ma trận kiểm thử bắt buộc

| Nhóm | Ca tiêu biểu | Bằng chứng cần có |
|---|---|---|
| Migration/ORM | DB trống lên V4, DB V3 có dữ liệu lên V4, unique/FK/CHECK, JSON mapping | MySQL/Testcontainers và Hibernate validate |
| Quyền | STUDENT/TEACHER/ADMIN, tài khoản khóa, Attempt người khác, asset người khác, CSRF | Status/error đúng; không có ghi trái quyền |
| Selector | LEARN seed 10 câu đúng nhóm; PRACTICE đủ/thiếu/rỗng, trùng origin, ưu tiên exposure; REVIEW fallback/provider test | Bộ Version ID và exposures đúng |
| Snapshot | F5, mở tab/thiết bị khác, retry create, cập nhật audio/publish/retire giữa lượt | Giữ ID/permutation/nội dung và quyền file cũ; không lộ keys/transcript |
| Draft | Partial/null, thiếu/trùng/lạ item, playerState sai, save không có trial | Response lỗi hoặc ACK đúng; DB và GET khớp |
| Chấm | Ba type, nhiều ô/cặp, Unicode/case/whitespace, đáp án giả, FIRST sai rồi final đúng | Outcome/completeness/support và số đếm đúng |
| Submit | Final chưa autosave, bỏ trống, đủ đáp án nhưng thiếu GUIDED CHECK, hai submit khác key/cùng key | Một Submission; điểm và lessonCompleted đúng |
| Chữa/tiến độ | CORRECTION, học lại dở, có version mới, NOT_STARTED | Result/FIRST bất biến, COMPLETED không bị hạ |
| Đồng thời | Hai create, hai save, save/check/submit cạnh tranh, admin khóa lúc ghi | Không nhân bản hoặc ghi đè; lỗi 409/quyền phù hợp |
| Lỗi và replay | Commit xong mất ACK; retry cùng payload; key cũ payload khác; replay revision cũ; DB lỗi giữa giao dịch | Không hiệu ứng lần hai, không receipt/ACK thành công khi rollback |
| Media | 200/206/416, seek/segment, file mất trả 503, tài liệu chung nhiều câu | Bytes/quyền đúng; transcript chỉ có sau submit |
| UI/E2E | LEARN/PRACTICE, resume, hint/check, submit/result/retry, 401/409/503, đổi user, F5/back/forward, mobile | Playwright và checklist thủ công |
| Bền vững/hồi quy | Restart đăng nhập lại, seed lại, các API M01/M02 và cập nhật audio | Dữ liệu học giữ nguyên; chức năng cũ còn chạy |

Test cạnh tranh dùng barrier/latch để ép các thao tác giao nhau; test mất ACK để server commit thật rồi ngắt phản hồi ở client. Đối chiếu số hàng, revision và kết quả sau retry, không chỉ kiểm tra HTTP 200. Log test không chứa keys, transcript hoặc đáp án người học.

## 8. Cách kiểm tra trong quá trình làm

Backend, từ `source/server`:

```powershell
.\mvnw.cmd test
```

Test tích hợp cần Docker Desktop, MySQL 8.4 qua Testcontainers và ffprobe/ffmpeg theo README. Có thể dùng test target Docker hiện có khi máy chưa có toolchain; ghi lại lệnh thực tế trong biên bản nghiệm thu. Chạy nhóm test liên quan sau từng bước; chạy toàn bộ khi tích hợp xong hoặc thay đổi nền dùng chung.

Frontend, từ `source/client` khi đã cài Node phù hợp với package.json:

```powershell
npm ci
npm run build
npx playwright install chromium
npm run test:e2e
```

Giữ runner E2E hiện có với project `english-learning-e2e`, cổng mặc định 5174/8081 và volume riêng. Không đặt E2E_BASE_URL trỏ vào app demo 5173 để chạy test có ghi. Khi thêm fixture cần dọn dữ liệu theo FK và không làm hỏng các test M02 hiện có.

Kiểm tra diff từ thư mục gốc:

```powershell
git diff --check
git status --short
```

Không dùng `docker compose down -v` trên môi trường demo để làm sạch test. Chỉ cập nhật trạng thái hoàn thành khi kiểm tra đã chạy và có kết quả.

## 9. Kịch bản demo nghiệm thu

1. STUDENT mở bài seed, bắt đầu LEARN và nhận 10 câu; thử ba dạng tương tác, đọc/nghe và mở hint/check một câu GUIDED.
2. Nhập đáp án, lưu nháp, F5 và mở tab khác để thấy bộ câu/vị trí/nháp được khôi phục. Tạo xung đột hai tab và đối chiếu dữ liệu.
3. Mô phỏng lỗi mạng/mất ACK lúc lưu; retry cùng thao tác, kiểm tra revision và dữ liệu không nhân đôi. Trong phần này chỉ cam kết giữ state của màn đang mở.
4. Nộp với đáp án vừa sửa chưa autosave; kiểm tra FIRST khác final, điểm và lessonCompleted. Chữa câu sai, tải lại result và kiểm tra điểm không đổi.
5. Tạo PRACTICE có điều kiện, kiểm tra thiếu câu không lặp. REVIEW báo chưa sẵn sàng khi chưa có M04.
6. Giáo viên xuất bản bản mới/cập nhật audio hoặc ngừng cấp nội dung theo đúng phụ thuộc; học viên tiếp tục lượt cũ bằng phiên bản và media đã pin.
7. Restart môi trường test, đăng nhập lại và mở nháp/kết quả. Đăng nhập học viên khác để chứng minh cách ly dữ liệu.

## 10. Điều kiện hoàn thành M03

- [x] 7 bảng V4 và 10 operation API mục 6 đã triển khai đúng hợp đồng.
- [x] LEARN/PRACTICE chạy đầy đủ; REVIEW có điểm tích hợp và fallback đúng phạm vi.
- [x] Snapshot/keys/media, FIRST/final/CORRECTION và progress tuân các quy tắc đã chốt.
- [x] Chống gửi trùng, revision, concurrent submit, rollback và phiên bản lịch sử có kiểm thử thật.
- [x] Giao diện học/luyện, lưu/tiếp tục, kết quả/chữa và tiến độ cơ bản dùng được trên desktop/mobile.
- [x] Backend/frontend/E2E và hồi quy liên quan đạt; mọi mục chưa kiểm tra được ghi rõ, không tính là đã đạt.
- [x] Có checklist, kết quả kiểm thử thực tế và tài liệu chạy/demo cập nhật để review PR.

M03 bàn giao kết quả nghiệp vụ bền vững theo Submission ID cho module sau. Thực nghiệm tải hệ thống và đánh giá hiệu quả học tập cần kế hoạch đo riêng; kiểm thử chức năng ở đây không thay thế các thực nghiệm đó.
