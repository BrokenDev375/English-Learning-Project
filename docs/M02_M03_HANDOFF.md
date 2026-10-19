# Bàn giao M02 và M03 — API và database

Chốt ngày 16/10/2026, hợp đồng `1.0.0`. Đây là đặc tả nghiệp vụ và tích hợp. Cập nhật 19/10: M02 đã có V3, học liệu phiên bản và seed 6 bài/90 câu; M03 đã có V4, API lượt làm/nháp/feedback/nộp/chữa/tiến độ và giao diện. Trạng thái và bằng chứng xem [tiến độ M02](M02_PROGRESS.md), [tiến độ M03](M03_PROGRESS.md), [nghiệm thu M03](../statics/results/module-3-validation.md). Đọc các mục phân công bên dưới như hợp đồng, không phải danh sách công việc còn thiếu.

## 1. Giao việc ngay cho hai người

| Người phụ trách | Việc phải hoàn thành | Database | API |
|---|---|---|---|
| M02 — học liệu | Topic, mục tiêu học, Unit, bài học, tài liệu đọc/nghe/ảnh, ngân hàng câu hỏi; nháp/xem trước/xuất bản/ngừng cấp; upload và seed | V3: 13 bảng | Danh mục học, toàn bộ `/api/author/**`, upload/đọc `/api/assets` |
| M03 — làm bài | Cấp lượt học/luyện, giữ nguyên bộ câu, nhận đáp án, lưu nháp, gợi ý/check câu hướng dẫn, nộp/chấm/chữa, tiến độ hoàn thành bài | V4: 7 bảng | `/api/attempts/**`, `/api/me/learning-progress` |

**M02 làm nền trước; M03 có thể làm song song DTO, bộ chấm và màn làm bài bằng fixture đúng hợp đồng.** M03 tích hợp migration và service học liệu sau khi PR nền V3 của M02 được merge. Không viết một bản bảng câu hỏi khác ở M03.

Đọc theo thứ tự:

1. Tài liệu này: phạm vi, công việc và điều kiện nghiệm thu.
2. [Quy tắc API](../source/common/M02_M03_API.md): luồng và xử lý trạng thái.
3. [OpenAPI 3.1](../source/common/openapi-m02-m03.json): tên trường, enum, request/response, phân trang.
4. [23 ví dụ JSON](../source/common/examples-m02-m03.json): mỗi ví dụ có tên schema để đối chiếu.
5. [V3 học liệu](database/planned/V3__learning_content.sql), [V4 bài làm](database/planned/V4__learning_attempts.sql): DDL dự kiến.
6. [Kiểm tra đặc tả](contracts/M02_M03_VALIDATION.md): DDL/JSON đã kiểm tra và giới hạn kiểm tra.

Tên trường/kiểu dữ liệu lấy theo OpenAPI; quy tắc nghiệp vụ lấy theo mục 2–7 dưới đây và tài liệu API. Các ví dụ là dữ liệu minh họa độc lập, không phải toàn bộ seed hoặc tập INSERT liên kết sẵn.

## 2. Quyết định chung đã chốt cho M02–M03

- Giữ React/TypeScript/Vite, Java 17/Spring Boot, Spring Data JPA/Hibernate, MySQL 8.4, Flyway và Docker hiện có. Controller → service interface → service.impl → repository interface/implementation → Spring Data JPA. Không chuyển sang JDBC.
- Ngân hàng học liệu đã xuất bản dùng chung cho mọi người học; không cần vào lớp mới được học. Lịch sử/đáp án/tiến độ thuộc từng tài khoản.
- TEACHER quản lý nội dung mình sở hữu; ADMIN quản lý được mọi nội dung. TEACHER được đọc/tái sử dụng phiên bản đã xuất bản của người khác, không được sửa hoặc đọc nháp của người đó. Muốn sửa câu của người khác thì sao chép thành câu của mình.
- Chỉ STUDENT tạo lượt học thật. TEACHER/ADMIN dùng xem trước, không sinh lịch sử, điểm hoặc XP.
- P0 có đúng ba loại: `SINGLE_CHOICE`, `FILL_BLANK`, `MATCHING`. Chọn nhiều/sắp xếp từ là P1, không nhận payload kiểu này ở P0.
- Độ khó: `EASY/MEDIUM/HARD`. Nhóm sử dụng: `GUIDED/INDEPENDENT/REVIEW_VARIANT`. Hai khái niệm độc lập.
- Một câu có một Topic, một LearningObjective chính. Objective phải thuộc Topic; câu phải cùng Topic với Unit và mục tiêu phải nằm trong mục tiêu bài.
- Server cấp phiên bản, kiểm tra đáp án và ghi kết quả chính thức. Client dựng tương tác, giữ đáp án đang nhập, phát audio, hiển thị trạng thái lưu và kết quả server trả.
- M02/M03 chưa làm lịch ôn thích nghi, XP/huy hiệu/streak, Giải đấu tuần, rank solo, bài thi IELTS hoặc AI chấm. Những phần này không được tự thêm vào V3/V4.
- Socket/reconnect/IndexedDB đầy đủ thuộc M06. M03 vẫn bắt buộc có API lưu/tải nháp và xử lý gửi lại; không đợi M06 mới có lưu bài.

## 3. Công việc M02

### 3.1. Database V3

| Bảng | Dữ liệu chính và trách nhiệm |
|---|---|
| `request_receipts` | Dùng chung M02/M03: actor, requestId, operation, hash payload, HTTP status/body đã commit; unique `(actor_id, request_id)` |
| `topics` | Chủ đề, code duy nhất, tên/mô tả, owner, revision |
| `learning_objectives` | Mục tiêu cụ thể, code duy nhất, Topic, owner, revision |
| `units` | Topic, chặng `stage`, thứ tự `sort_order`, tên/mô tả, owner, revision, seedKey |
| `assets` | Metadata file: storageKey, MIME thực, tên gốc, bytes, SHA-256, owner, state |
| `materials` | Root của tài liệu; owner, con trỏ draft/published, revision, retiredAt, seedKey |
| `material_versions` | Phiên bản READING/AUDIO/IMAGE, text hoặc asset, transcript/đoạn thời gian, nguồn |
| `questions` | Root câu hỏi; owner, originKey, con trỏ phiên bản, revision, retiredAt, seedKey |
| `question_versions` | Type, Topic, Objective, difficulty, prompt, content/answer JSON, hint/explanation, materialVersionId, nguồn |
| `lessons` | Root bài học, Unit, owner, thứ tự, con trỏ phiên bản, revision, retiredAt, seedKey |
| `lesson_versions` | Tên/mô tả, khối hướng dẫn, state/revision/versionNo |
| `lesson_objectives` | Các mục tiêu của một LessonVersion |
| `lesson_questions` | QuestionVersion của một LessonVersion, practiceRole, thứ tự trong nhóm |

Quan hệ: Topic → Unit → Lesson → LessonVersion → LessonQuestion → QuestionVersion; QuestionVersion → LearningObjective và MaterialVersion → Asset. Không nhân bản một file nghe cho từng câu dùng chung.

Chỉ JSON có cấu trúc thay đổi theo loại mới để JSON: `content_json`, `answer_json`, `source_json`, `instructions_json`. Topic/Objective/type/difficulty/owner/state/FK vẫn là cột để lọc và kiểm tra. JPA ánh xạ JSON bằng DTO/kiểu có cấu trúc; không serialize entity trực tiếp ra API.

V3 đã có FK, unique, index và CHECK cơ bản. **Service còn phải kiểm tra** quan hệ Topic–Objective, ownership, loại asset, state của con trỏ, cấu trúc đáp án, một origin không lặp trong bài và điều kiện xuất bản. FK/CHECK không thay thế các kiểm tra này.

Unit không có bảng mục tiêu riêng: `objectiveIds` suy ra từ các bài của Unit. API học viên chỉ tính bài đã xuất bản; API tác giả tính các bản mình được phép xem, ưu tiên nháp của bài mình sở hữu.

### 3.2. Danh mục và bài học

- CRUD cần làm ở P0: tạo/đọc/cập nhật Topic, Objective, Unit; không cần API xóa danh mục.
- Code Topic/Objective bất biến sau tạo. Đổi nghĩa mục tiêu thì tạo mục tiêu mới; không đổi Topic của Objective. Unit có bài rồi không được đổi Topic.
- Sắp xếp bài bằng toàn bộ danh sách Lesson ID của Unit; không trùng/thiếu ID; position bắt đầu 0. Kiểm tra Unit revision để tránh hai người ghi đè.
- Tạo bài phải kèm `unitBaseRevision`, tự nối cuối, tăng Unit revision. Owner bài bằng owner Unit, kể cả ADMIN thao tác.
- Hướng dẫn bài gồm khối TEXT/EXAMPLE. Không đưa HTML tùy ý vào nội dung.
- Một bài tối đa 100 câu; khi xuất bản phải có ít nhất một câu ở mỗi nhóm GUIDED, INDEPENDENT, REVIEW_VARIANT. Có tên, hướng dẫn và mục tiêu hợp lệ.
- 5/5/5 chỉ là quy mô seed, không ép mọi bài tương lai có đúng 15 câu.

### 3.3. Nháp, phiên bản và xuất bản

Mỗi root tối đa một nháp hiện hành, có thể đồng thời giữ một phiên bản đang xuất bản. Nháp được lưu khi nội dung chưa hoàn chỉnh nhưng vẫn phải đúng type/schema/FK. Ví dụ câu chưa có đủ lựa chọn vẫn lưu được; upload asset trước khi tạo tài liệu AUDIO/IMAGE.

Bản `PUBLISHED` bất biến. Sửa nội dung bằng bản nháp phiên bản mới, không UPDATE bản đã xuất bản. `versionNo` là thứ tự phiên bản của một root; `revision` là số lần sửa để phát hiện ghi đè.

Luồng bắt buộc:

1. Tạo/chọn Topic và Unit; tạo Lesson draft.
2. Tạo/tái sử dụng Material và Question, gắn vào Lesson draft bằng Version ID.
3. Lưu nháp toàn bộ; xem trước trả danh sách lỗi/cảnh báo và `previewToken`.
4. Publish kiểm tra lại token, quyền, revision và mọi phụ thuộc trong transaction.
5. Khi publish bài, tự publish các nháp Question/Material liên quan mà người gọi có quyền sửa trong cùng transaction. Không dùng nháp của tác giả khác. Nếu một phụ thuộc lỗi thì không công bố phần còn lại.
6. Ngừng cấp nội dung bằng retire. Không cấp nội dung retired cho lượt mới; lượt đã tạo vẫn dùng phiên bản cũ.
7. Không retire câu/tài liệu còn được bài đang xuất bản sử dụng, kể cả tham chiếu qua phiên bản cũ. Trả `409 CONTENT_IN_USE`; thay tham chiếu/ngừng bài trước.
8. Bỏ nháp chỉ khi chưa được nháp khác tham chiếu; không xóa root khác hoặc lịch sử đã xuất bản. Xuất bản lại nội dung hợp lệ có thể gỡ retiredAt.

Copy câu giữ nguyên `originKey`. Chỉnh bản nháp, sửa phiên bản, đảo lựa chọn hoặc copy không biến câu cũ thành câu “chưa từng gặp”. Câu biến thể có ngữ cảnh/nội dung mới thật sự được tạo mới với originKey mới; hệ thống không tự phát hiện mọi bản sao ngữ nghĩa.

### 3.4. Upload và quyền đọc file

- M02 bước 2 đã bổ sung API upload/đọc file; M01 chỉ chuẩn bị volume `asset_data`.
- File bytes lưu trong volume backend; database lưu metadata. Chấp nhận PNG/JPEG và MP3/WAV/OGG, tối đa **5 MiB = 5.242.880 bytes/file**.
- Kiểm tra nội dung/MIME thực và kích thước phía server; không tin tên file, extension hoặc Content-Type client gửi. Storage key UUID do server sinh, không nhận đường dẫn từ client.
- File đã READY không ghi đè; upload mới tạo Asset mới. Viết file tạm rồi atomic move; rollback DB thì dọn file vừa tạo, không ảnh hưởng file dùng chung.
- `GET /api/assets/{id}` cần đăng nhập và quyền đọc. TEACHER đọc file mình sở hữu hoặc file của Material đã xuất bản được phép tái sử dụng; ADMIN đọc mọi file. STUDENT chỉ đọc file thuộc lượt làm của chính mình, kể cả sau nộp.
- M02 cung cấp interface `AssetReadGrant`; M03 bổ sung implementation `AttemptAssetReadGrant`. Không để M02 phụ thuộc bảng/service của M03 trước khi V4 có mặt.
- Hỗ trợ HTTP Range cho audio bằng cơ chế Resource của Spring, có 200/206/416. Không trả storageKey cho học viên.
- File đã READY bị mất trên ổ đĩa: `503 ASSET_UNAVAILABLE`; không trả ACK upload giả hoặc audio rỗng. Không có quyền: 404.

### 3.5. Seed và bàn giao của M02

Seed profile phát triển/demo, chạy lại không nhân bản nhờ seedKey; không bật seed demo tùy tiện trên production.

- 3 Topic ban đầu: Daily routines, Food and shopping, Travel and directions.
- Mỗi Topic một Unit chặng đầu, mỗi Unit hai bài; tổng 6 bài × 15 câu = 90 Question root.
- Mỗi bài 5 GUIDED + 5 INDEPENDENT + 5 REVIEW_VARIANT. Các biến thể cùng mục tiêu nhưng khác nội dung thật.
- Toàn bộ seed có đủ ba loại tương tác P0, câu text độc lập, nhóm câu chung đoạn đọc và nhóm câu chung audio.
- Tối thiểu một đoạn đọc và một audio cho mỗi Unit; audio có transcript, nguồn và quyền sử dụng. Có ít nhất một ảnh để demo upload/đọc ảnh.
- Nội dung/đáp án/giải thích phải được người biên soạn kiểm tra; không tải audio/trích đoạn không rõ quyền chỉ để đủ số.
- Tài khoản demo dùng cơ chế M01; không hard-code tài khoản thật hoặc reset tiến độ người học khi chạy seed lại.
- Bàn giao manifest seedKey → chủ đề/mục tiêu/loại/nhóm/Material; ghi nguồn học liệu trong `statics/dataset/README.md`.

M02 phải bàn giao: V3 + entity/repository/service/controller/DTO, API upload và danh mục/biên soạn, ba validator câu hỏi, service đọc phiên bản cho M03, fixture API và seed 90 câu. Nếu nhận cả frontend: thêm màn quản lý Unit/bài/tài liệu/câu, editor theo ba type, preview/publish/retire.

## 4. Công việc M03

### 4.1. Database V4

| Bảng | Dữ liệu chính và trách nhiệm |
|---|---|
| `attempts` | User, mode, state, LessonVersion nếu học bài, selection JSON, playerState, revision và thời điểm |
| `attempt_questions` | Bộ câu cố định của lượt: QuestionVersion, slot gốc, origin, nhóm, thứ tự, thứ tự hiển thị đã xáo, đã gặp trước hay chưa |
| `attempt_answers` | Đáp án hiện tại/nháp của từng AttemptQuestion; thay đổi khi lưu/check/nộp trước khi chốt |
| `answer_trials` | Lần được đánh giá bất biến: FIRST/GUIDED_RETRY/CORRECTION, response, outcome, supportUsed, responseComplete |
| `submissions` | Một kết quả cuối duy nhất/Attempt; điểm, số câu, hoàn thành bài, JSON sự kiện/kết quả version 1 |
| `question_exposures` | Mỗi user/origin đã thấy bao nhiêu lần và ở lượt nào; không dựa trên ID phiên bản |
| `lesson_progress` | Mỗi user/Lesson: STARTED/COMPLETED, lượt gần nhất, phiên bản hoàn thành, điểm gần nhất |

Không tạo `review_schedules`, `xp_ledger`, bảng rank hoặc bảng bài tập khác ở V4. M04/M05 sẽ có migration riêng.

### 4.2. Cấp lượt và giữ snapshot

- LEARN: người học chọn Lesson; server chốt bản đang xuất bản, cấp toàn bộ GUIDED rồi INDEPENDENT theo thứ tự biên soạn. Seed là 10 câu; REVIEW_VARIANT để dành ôn.
- PRACTICE: chọn Topic + difficulty + count từ 1–10. Lấy câu INDEPENDENT trong các bài đang xuất bản, cùng điều kiện, không trùng origin; ưu tiên chưa gặp, sau đó ít gặp/lâu gặp. Chỉ random khi các câu đủ điều kiện có cùng ưu tiên.
- Nếu chỉ có 6 câu phù hợp thì cấp 6; không nhân bản cho đủ 10. Không có câu: `409 NO_QUESTIONS_AVAILABLE`.
- REVIEW: dùng `ReviewSelectionProvider` do M04 triển khai. Trước khi M04 có mặt trả `409 REVIEW_NOT_READY`, không giả lập một thuật toán ôn ngẫu nhiên. M03 viết runner/grader dùng chung và test bằng provider giả lập.
- Cùng user + cùng lựa chọn đang IN_PROGRESS thì trả lại lượt đó, không tạo lượt khác. LEARN so theo Lesson root; PRACTICE/REVIEW so toàn bộ tham số chọn đã chuẩn hóa. Lựa chọn khác được có lượt khác.
- Snapshot giữ Version ID, nhóm/thứ tự câu và permutation lựa chọn/cột phải của matching. F5/GET/retry/mở thiết bị khác không bốc bộ mới.
- Ghi question_exposures một lần khi tạo lượt; không tăng vì F5 hoặc gửi lại request. Lưu `wasSeenBefore/seenCountBefore` tại lúc cấp.
- Lượt cũ vẫn mở được nếu tác giả vừa sửa hoặc retire bài/câu/tài liệu. Không cấp bản sửa thay thế vào lượt đang làm.

### 4.3. Đáp án nháp, lần trả lời và kết quả

| Hành động | Đổi nháp? | Ghi trial? | Được trả khóa đáp án? |
|---|---|---|---|
| PUT draft | Có, full snapshot | Không | Không |
| Mở hint | Không; ghi dấu hỗ trợ | Không | Chỉ hint của GUIDED |
| Check GUIDED | Có | FIRST lần đầu, GUIDED_RETRY các lần sau | Có, câu vừa check |
| Submit | Chốt đáp án cuối trong cùng transaction | Tạo FIRST cho câu chưa có trial | Có, toàn lượt sau commit |
| Retry sau nộp | Không sửa đáp án cuối | CORRECTION | Có, câu vừa chữa |

Chỉ GUIDED được mở hint/check trước nộp. Câu INDEPENDENT/REVIEW_VARIANT không nhận giải thích/khóa đáp án sớm. Transcript chỉ trả sau submit, kể cả tài liệu dùng chung với GUIDED.

**Lưu nháp không tính là lần thử.** Người học đổi lựa chọn khi chưa check/nộp thì không tạo hàng loạt lỗi sai giả.

Mỗi request lưu hoặc nộp gửi toàn bộ danh sách đáp án của lượt, mỗi item xuất hiện đúng một lần; `response:null` để bỏ đáp án. Gửi cả câu đang xem và vị trí audio. Server từ chối ID lạ/trùng/thiếu; không tự xóa câu vì client quên trường.

Submit có thể còn câu bỏ trống: vẫn nộp được, câu đó UNANSWERED. Một Attempt có đúng một Submission. Retry sau nộp chỉ ghi việc chữa, không sửa điểm hoặc kết quả đầu.

### 4.4. Chấm và tiến độ

- Mỗi câu đúng hoàn toàn được 1 điểm, sai/bỏ trống được 0. Matching nhiều cặp hoặc fill nhiều ô vẫn là một câu, không cộng XP/điểm theo từng ô ở P0.
- scorePercent = correctCount / totalQuestions thực cấp × 100, làm tròn HALF_UP hai chữ số. Kết quả cuối dùng đáp án lúc submit.
- Lưu riêng lần đầu được đánh giá; sai ở check rồi sửa đúng vẫn giữ FIRST sai. Dữ kiện cho M04/M07 phải phân biệt tự làm, có hỗ trợ và chữa lại.
- `supportUsed` của FIRST ghi trước khi mở phản hồi check lần đầu; đã mở hint trước đó thì true. Retry sau thấy đáp án luôn có hỗ trợ.
- `independentFreshAnsweredCount/CorrectCount`: FIRST của INDEPENDENT/REVIEW_VARIANT, chưa gặp origin trước, chưa dùng hỗ trợ, có đáp án không rỗng. Không lấy GUIDED hoặc CORRECTION để chứng minh nhớ độc lập.
- Bài LEARN hoàn thành khi tất cả câu được điền đủ và tất cả GUIDED đã có check trước submit. Không bắt mọi câu đúng. Nộp sớm vẫn được nhưng `lessonCompleted:false`.
- lesson_progress không reset COMPLETED khi xuất bản bản mới; trả `updateAvailable` để báo có cập nhật. Chưa có hàng tiến độ thì API tổng hợp NOT_STARTED.
- Không suy band IELTS/CEFR hoặc “thành thạo” chỉ từ một bài hoàn thành.

M03 phải bàn giao: V4 + entity/repository/service/controller/DTO, selector LEARN/PRACTICE, ReviewSelectionProvider, grader ba type, snapshot/save/check/submit/retry/result, tiến độ bài và test tích hợp. Nếu nhận cả frontend: ba renderer, Attempt runner dùng chung, thanh tiến độ, player audio, trạng thái lưu, kết quả/chữa.

## 5. API phải triển khai

Toàn bộ request/response, enum, nullability và query nằm trong OpenAPI. Danh sách dưới đây dùng để chia ticket; `{id}` là root ID, `{versionId}` là version ID, `{itemId}` là **AttemptQuestion ID**.

### M02

| API | Việc |
|---|---|
| GET /api/topics; GET /api/objectives?topicId= | Danh mục học; chỉ trả danh mục có nội dung xuất bản phù hợp |
| GET /api/units; GET /api/units/{id}; GET /api/lessons/{id} | Lộ trình, bài xuất bản và phần giới thiệu; không kèm ngân hàng/đáp án |
| GET, POST /api/author/topics; PUT /api/author/topics/{id} | Quản lý chủ đề |
| GET, POST /api/author/objectives; PUT /api/author/objectives/{id} | Quản lý mục tiêu |
| GET, POST /api/author/units; GET, PUT /api/author/units/{id} | Quản lý Unit |
| PUT /api/author/units/{id}/lesson-order | Toàn bộ thứ tự bài |
| GET, POST /api/author/{materials,questions,lessons} | Danh sách/tạo root + draft |
| GET /api/author/{resource}/{id}; GET .../{id}/versions/{versionId} | Đọc root/đúng phiên bản theo quyền |
| POST, PUT, DELETE /api/author/{resource}/{id}/draft | Mở/lưu/bỏ nháp |
| GET /api/author/{resource}/{id}/preview | Xem trước + token + validation |
| POST /api/author/{resource}/{id}/publish; POST .../{id}/retire | Xuất bản/ngừng cấp |
| POST /api/author/questions/{id}/copy | Sao chép câu có sẵn, giữ origin |
| POST /api/assets; GET /api/assets/{id} | Upload multipart/đọc bytes có quyền |

`resource` là một trong `materials/questions/lessons`; không tạo endpoint tên nguyên văn dấu ngoặc. Không có API cho STUDENT tải toàn bộ câu hỏi kèm answerKey.

### M03

| API | Việc |
|---|---|
| POST /api/attempts | Tạo 201 hoặc lấy lại lượt đang làm 200 |
| GET /api/attempts?mode=&state=&page=&size= | Các lượt của chính mình |
| GET /api/attempts/{id} | Snapshot và nháp, không trả answerKey |
| PUT /api/attempts/{id}/draft | Lưu toàn bộ nháp, trả ACK revision |
| POST /api/attempts/{id}/items/{itemId}/hint | Gợi ý GUIDED |
| POST /api/attempts/{id}/items/{itemId}/check | Kiểm tra GUIDED |
| POST /api/attempts/{id}/submit | Lưu đáp án cuối + nộp/chấm nguyên tử |
| GET /api/attempts/{id}/result | Kết quả bất biến đã nộp |
| POST /api/attempts/{id}/items/{itemId}/retry | Ghi lần chữa sau nộp |
| GET /api/me/learning-progress?unitId=&page=&size= | Tiến độ hoàn thành bài của chính mình |

## 6. Chỗ dùng chung và thứ tự PR

| Ticket | Chủ trì | Sản phẩm cụ thể |
|---|---|---|
| M02-01 nền V3 | M02 | Migration/entity + DTO ba type + validator + request_receipts/idempotency service |
| M02-02 danh mục | M02 | Topic/Objective/Unit, thứ tự bài, learner catalog |
| M02-03 file/tài liệu | M02 | Upload, AssetReadGrant, Material/version |
| M02-04 câu/bài | M02 | Question/Lesson/version, preview/publish/retire/copy |
| M02-05 seed | M02 | Seed 90 câu + manifest/nguồn + test không nhân bản |
| M03-01 nền V4/grader | M03 | Migration/entity, grader độc lập, fixtures theo schema |
| M03-02 lượt làm | M03 | Selector, snapshot, exposures, load/save nháp, AttemptAssetReadGrant |
| M03-03 nộp/chữa | M03 | Hint/check/submit/result/retry, first-trial facts, lesson_progress |
| M03-04 tích hợp UI | M03 hoặc người frontend | Runner/renderers/audio, trạng thái ACK, trang kết quả |
| Tích hợp M06 | M06 phối hợp M03 | IndexedDB/outbox client, reconnect/đối soát; dùng API M03 đã có |

Các interface nội bộ cần giao trước:

- `PublishedContentReader` do M02 sở hữu: lấy LessonVersion với slot đã công bố, QuestionVersion/MaterialVersion theo ID; chỉ service server dùng, không biến thành API công khai.
- `RequestReceiptService` do M02 sở hữu: M03 gọi cùng transaction nghiệp vụ, không tự tạo bảng chống trùng riêng.
- `AssetReadGrant` do M02 sở hữu: nhiều provider; M03 thêm grant theo Attempt. Không tạo vòng phụ thuộc M02 ↔ M03.
- `ReviewSelectionProvider` do M03 sở hữu: M04 trả tối đa count Version ID hợp lệ theo user/mục tiêu/lịch; M03 vẫn kiểm tra quyền/state và chốt snapshot.
- M03 cung cấp Submission ID + factsSchemaVersion=1 để M04/M05 xử lý sau. Không cộng XP hoặc cập nhật lịch ôn bằng callback trong RAM rồi coi là đã bảo đảm giao sự kiện. Module sau cần lưu ledger đã xử lý Submission để có thể đọc bù.

M02 merge V3 trước, M03 merge V4 sau. Khi triển khai mới chuyển DDL từ `docs/database/planned/` vào `source/server/src/main/resources/db/migration/`. Không thay V1/V2, không sửa migration đã chạy, không cho Hibernate tự create/update schema. Giữ `ddl-auto=validate`.

Các file dễ đụng: `source/client/src/api.ts`, router/App, security/config, seed/bootstrap, cấu hình upload. M02 bước 2 đã thêm FormData vào api.ts, giữ CSRF/cookie và để trình duyệt đặt multipart boundary. M03/M06 dùng adapter này, không sửa cùng file trên hai PR không trao đổi.

## 7. Điều kiện nghiệm thu bắt buộc

Bổ sung M02 ngày 19/10: [mục 9 đặc tả API](../source/common/M02_M03_API.md) có GET/POST cập nhật audio đồng loạt. Thao tác tạo các phiên bản Question/Lesson mới; không đổi lịch sử. M03 vẫn đọc đúng LessonVersion/QuestionVersion/MaterialVersion đã pin khi tạo Attempt. Lesson do ADMIN tạo trong Unit thuộc giảng viên vẫn giữ chủ sở hữu là chủ Unit. Xem [kết quả kiểm tra](../statics/results/module-2-audio-update-validation.md).

### M02

- Migration V1→V2→V3 chạy trên MySQL 8.4 trống; JPA validate. Không ảnh hưởng dữ liệu M01.
- TEACHER không đọc/sửa nháp người khác; ADMIN can thiệp được; STUDENT không gọi author API.
- Lưu được nháp chưa đủ nội dung; publish sai đáp án/thiếu nhóm/sai Topic–Objective/FK/private dependency bị từ chối.
- Publish bài/phụ thuộc nguyên tử; một lỗi không để lại nửa bài công bố.
- Hai tab sửa cùng revision: một lần thành công, lần còn lại 409. Preview cũ sau sửa câu/tài liệu không được publish âm thầm.
- Sửa sau công bố tạo version mới; GET bản cũ vẫn giữ nguyên nội dung.
- Upload sai định dạng/quá 5 MiB/path giả bị chặn; upload gửi lại cùng requestId không sinh asset thứ hai; audio seek được qua Range.
- Retire có tham chiếu bị 409; không xóa file/phiên bản đang phục vụ lượt cũ.
- Seed đủ 90 câu, 5/5/5 từng bài, đủ tương tác/đọc/nghe/ảnh; chạy lại không nhân bản.

### M03

- LEARN seed cấp 10 câu đúng nhóm/thứ tự; PRACTICE lọc đúng, thiếu câu không lặp, REVIEW chưa nối M04 báo rõ chưa sẵn sàng.
- F5, retry tạo lượt, mở tab khác không đổi bộ/xáo thứ tự lại hoặc tăng exposures lần hai.
- Snapshot/nháp/log client không lộ answerKey, explanation, transcript hoặc storageKey. Hint/check không dùng được cho INDEPENDENT trước nộp.
- PUT draft không tạo trial; đáp án nháp đầy đủ/partial/null được khôi phục đúng. ID lạ/trùng/thiếu bị 400.
- FIRST sai rồi sửa đúng vẫn giữ FIRST sai; chấm fill chuẩn hóa đúng quy tắc; matching không chấm theo vị trí xáo.
- Submit gửi đáp án cuối chưa autosave vẫn không mất dữ liệu. Hai request submit cùng/khác requestId chỉ có một Submission.
- Server commit nhưng ACK mất: gửi lại cùng requestId nhận cùng kết quả; cùng key payload khác bị 409. Revision cũ không ghi đè nháp mới.
- Chữa sau nộp không sửa điểm, FIRST hoặc Submission. Truy cập Attempt người khác bị 404.
- Sau tác giả xuất bản bản mới/retire, lượt cũ giữ câu/đáp án và đọc được file theo quyền.
- DB lỗi rollback, không trả ACK thành công. Khóa tài khoản cùng lúc ghi bài vẫn tuân AccountGuard của M01.
- Tiến độ hoàn thành không reset vì cập nhật nội dung; dữ liệu hai tài khoản không lẫn nhau.

Test cần dùng MySQL thực/Testcontainers như M01, không chỉ H2 hoặc mock repository. Đây là tiêu chí cho **code sắp triển khai**; kiểm tra DDL/JSON trong lần bàn giao tài liệu không chứng minh các hành vi runtime đã hoàn thành.

