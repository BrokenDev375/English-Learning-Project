# Quy tắc API M02–M03

Hợp đồng 1.0.0 — 16/10/2026. Cập nhật 17/10: đã triển khai API M02 danh mục tác giả, asset/material, questions/lessons, copy câu, chi tiết Unit/thứ tự bài và learner catalog. PublishedContentReader đã có cho M03. Bộ seed 6 bài/90 câu đã hoàn thành, kèm 13 tài liệu và 7 file thật. API attempt/progress M03 chưa triển khai. Xem [tiến độ M02](../../docs/M02_PROGRESS.md).

Đọc cùng [bàn giao](../../docs/M02_M03_HANDOFF.md), [OpenAPI](openapi-m02-m03.json), [ví dụ](examples-m02-m03.json), [API M01](API.md). OpenAPI định nghĩa DTO chính xác; tài liệu này định nghĩa giao dịch, quyền và luồng.

Cập nhật M03 ngày 19/10: đã có API và runner UI cho học/luyện tập, nháp, hint/check, submit/result/retry và progress. Xem [tiến độ](../../docs/M03_PROGRESS.md), [giao diện bước 5](../../docs/M03_UI_SMOKE.md), [API M03](../../docs/M03_API_SMOKE.md). Trạng thái “M03 chưa triển khai” trong mốc 17/10 ở trên là ghi nhận lịch sử, không phải trạng thái code hiện tại.

## 1. Quy ước HTTP

- Cùng origin, cookie JSESSIONID và CSRF của M01. Mọi POST/PUT/DELETE gửi header token nhận từ `GET /api/auth/csrf`. Không bổ sung JWT hoặc nhận userId/ownerId do client khai.
- Tất cả API cần tài khoản còn ACTIVE; author API cần TEACHER/ADMIN; attempt/progress cần STUDENT.
- ID là số nguyên dương không vượt 9.007.199.254.740.991; version/revision là số nguyên, không phải timestamp. UTC ISO-8601 cho thời điểm. UUID requestId dạng chữ thường.
- DTO từ chối trường lạ. Các trường nullable vẫn phải có mặt nếu schema ghi required; dùng null, không dùng chuỗi rỗng thay ID.
- Trang: `{items,totalElements,page,size,totalPages}`, page bắt đầu 0, size 1–50 (mặc định 20), q tối đa 100 ký tự tại endpoint có q.
- Danh sách author mặc định scope=OWN. Bank Material/Question/Lesson thêm scope=PUBLISHED để dùng lại nội dung đang công bố và scope=ALL chỉ cho ADMIN. Unit scope=ALL cũng chỉ ADMIN; Topic/Objective scope=ALL cho TEACHER/ADMIN vì là danh mục chung, quyền sửa vẫn own/ADMIN. Scope không có trong enum endpoint bị 400; TEACHER xin ALL trái quyền bị 403.
- Thứ tự: Unit theo stage/sortOrder/id; Lesson theo sortOrder/id; author bank theo id giảm; Attempt theo updatedAt/id giảm. Mục tiêu và nguồn không có HTML tùy ý.
- Public topics/objectives chỉ hiện mục được nội dung đang công bố sử dụng; author lists thấy danh mục để biên soạn. Unit public có ít nhất một bài active published.
- Danh sách chỉ trả summary; chi tiết qua endpoint ID. Với root người khác, chỉ trả phiên bản công bố; draftVersion/draftVersionId bị ẩn thành null.
- Response cá nhân/author dùng Cache-Control private, no-store. Không log payload có answerKey/transcript/đáp án học viên.

Lỗi luôn cùng M01: `{code,message,fieldErrors:{}}`. fieldErrors dùng đường dẫn như `questions[2].questionVersionId` hoặc `answers[1].response`.

| HTTP | Code chủ yếu |
|---|---|
| 400 | VALIDATION_FAILED, PUBLISH_VALIDATION_FAILED, INVALID_ANSWER |
| 401 | AUTHENTICATION_REQUIRED / session hết hoặc bị thu hồi |
| 403 | ACCESS_DENIED / CSRF |
| 404 | NOT_FOUND, HINT_NOT_AVAILABLE; tài nguyên riêng của người khác cũng 404 |
| 409 | REVISION_CONFLICT, REQUEST_ID_REUSED, DRAFT_MISMATCH, PREVIEW_STALE, CONTENT_IN_USE, ATTEMPT_FINISHED, RESULT_NOT_READY, FEEDBACK_NOT_AVAILABLE, NO_QUESTIONS_AVAILABLE, REVIEW_NOT_READY |
| 413 | FILE_TOO_LARGE / giới hạn request |
| 503 | DATABASE_UNAVAILABLE, ASSET_UNAVAILABLE |

## 2. requestId và revision: áp dụng cho cả hai module

Mỗi mutation gửi requestId UUID mới cho **một thao tác logic**. Retry vì mất ACK phải giữ nguyên method/path/body/requestId. Thao tác mới sinh key mới. Upload đặt requestId trong multipart form; DELETE draft đặt requestId/draftVersionId/baseRevision trong query.

`request_receipts` unique actor+requestId. Quy trình trong một transaction:

1. Gọi `AccountGuard.lockCurrent(UserPrincipal)` trước các lock dữ liệu nghiệp vụ, đúng quy tắc M01.
2. Tìm receipt của actor+requestId. Nếu operation và payload_hash trùng, trả lại đúng status/body đã commit, header `Idempotent-Replayed:true`; không check revision cũ rồi làm hỏng retry.
3. Nếu cùng key nhưng khác thao tác hoặc payload, trả 409 REQUEST_ID_REUSED.
4. Nếu key mới, kiểm tra quyền, state, revision, thực hiện mutation, ghi receipt cùng transaction.
5. Chỉ ACK 2xx sau commit. Transaction thất bại không để lại receipt thành công.

operation = method viết hoa + path đã resolve ID; với DELETE không đưa requestId vào operation. Hash trên DTO đã chuẩn hóa, bỏ requestId; sort object keys đệ quy, giữ thứ tự array, null/default nhất quán, số thập phân stripTrailingZeros không scientific notation. SHA-256 UTF-8 chữ thường. Upload hash gồm SHA-256 file bytes + tên gốc/MIME đã chuẩn hóa; không hash boundary multipart. Các tham số DELETE ảnh hưởng nghiệp vụ nằm trong payload hash.

Giữ receipt suốt P0, chưa tự đặt TTL. Với 204, response_json lưu giá trị JSON null và HTTP không có body. Replayed response có thể chứa revision cũ nếu sau đó đã có thao tác khác; client tải lại GET snapshot để đối soát.

| Lệnh | baseRevision so với |
|---|---|
| PUT Topic/Objective/Unit, POST mở draft, POST retire | revision của root/đối tượng |
| Tạo Lesson | unitBaseRevision của Unit |
| PUT draft, POST publish | revision của draftVersion, kèm đúng draftVersionId |
| Mọi mutation Attempt | revision của Attempt |

Mọi thay đổi thật làm tăng revision của đối tượng tương ứng. Lưu nháp Question/Material/Lesson tăng revision **cả version lẫn root**; thay con trỏ/state/retire cũng tăng root. GET/preview không làm tăng revision. Mở draft đã tồn tại hoặc mở hint đã mở trả lại dữ liệu hiện tại, không tạo phiên bản/lần hỗ trợ mới.

Nếu 409 REVISION_CONFLICT: tải lại snapshot; không tự thay baseRevision rồi gửi đè đáp án của thiết bị khác. M03 UI hiển thị “Bài làm đã thay đổi ở nơi khác” và cho người học lựa chọn áp dụng thay đổi sau đối chiếu.

## 3. Đặc tả ba loại câu

API `content/answerKey/source` ánh xạ lần lượt `content_json/answer_json/source_json`. schemaVersion=1.

| Type | content | answerKey của tác giả | response của người học |
|---|---|---|---|
| SINGLE_CHOICE | options: [{id,text}] | {correctOptionId} | {type:"SINGLE_CHOICE",optionId:"opt-a"}; optionId=null khi chưa chọn |
| FILL_BLANK | parts gồm TEXT{text} hoặc BLANK{blankId} | blanks:[{blankId,acceptedAnswers:[]}], caseSensitive, normalizeWhitespace | {type:"FILL_BLANK",values:[{blankId,value}]} |
| MATCHING | leftItems/rightItems: [{id,text}] | pairs:[{leftId,rightId}] | {type:"MATCHING",pairs:[{leftId,rightId}]} |

Quy tắc publish:

- SINGLE_CHOICE: 2–6 lựa chọn, ID/text không rỗng, ID duy nhất; đúng một correctOptionId thuộc options.
- FILL_BLANK: 1–5 blank ID duy nhất; mỗi blank có 1–10 đáp án chấp nhận không rỗng, không thiếu/thừa blank. parts phải có nội dung có nghĩa.
- MATCHING: mỗi cột 2–6 phần tử, hai cột bằng số lượng; ID/text hợp lệ; answerKey là song ánh, không ghép trùng một đầu.
- Prompt/explanation/nguồn hợp lệ; material đúng phiên bản/quyền. GUIDED phải có hint không rỗng; các loại khác hint có thể null.
- Draft được thiếu nội dung/đáp án nhưng JSON vẫn phải đúng hình dạng theo OpenAPI.

Chấm FILL_BLANK: NFC; nếu normalizeWhitespace=true thì gộp chuỗi ASCII space/tab/CR/LF thành một space và trim ASCII; nếu caseSensitive=false thì lowercase Locale.ROOT. So sánh bằng một acceptedAnswer chuẩn hóa theo cùng quy tắc. Không bỏ dấu, bỏ punctuation, so khớp một phần hoặc fuzzy ở P0.

Đáp án nháp có thể partial. Null/toàn bộ giá trị trống là UNANSWERED. Có nhập nhưng thiếu/sai phần là INCORRECT; đúng tất cả mới CORRECT. `responseComplete` phân biệt đã điền đủ với đúng/sai. Chọn ID lạ, sai type, blank ID trùng, matching lặp trái/phải trả 400 INVALID_ANSWER; không ghi như một câu sai hợp lệ.

Permutation chỉ thay thứ tự hiển thị; so đáp án bằng ID ổn định, không dùng chỉ số hoặc chữ A/B/C hiện tại.

## 4. Quy trình biên soạn/xuất bản

`POST /author/{resource}` tạo root + versionNo=1 DRAFT. POST draft tạo bản mới từ phiên bản công bố mới nhất, hoặc trả nháp hiện có. PUT draft thay **toàn bộ** nội dung draft, kèm draftVersionId và baseRevision.

Root status: retiredAt có giá trị → RETIRED; nếu chưa retired và có publishedVersion → PUBLISHED; còn lại DRAFT. Có cả published và draft thì status vẫn PUBLISHED. Root rỗng sau bỏ nháp vẫn giữ ID/lastVersionNo, không tái sử dụng số version.

GET preview trả errors/warnings và dữ liệu tác giả để dựng thử. Preview không sinh Attempt, exposure hoặc tiến độ. UI preview phải mô phỏng ẩn khóa đáp án/hint/transcript như luồng học viên, dù DTO tác giả chứa dữ liệu đó.

previewToken là SHA-256 chữ thường của chuỗi UTF-8:

```text
v1|LESSON|rootId|draftVersionId|draftRevision
Q|questionVersionId|revision
...
M|materialVersionId|revision
...
```

Dòng Q/M duy nhất, sort tăng theo numeric version ID, Q trước M, nối bằng LF, không LF cuối. Với QUESTION/MATERIAL thay kind dòng đầu; bỏ các nhóm phụ thuộc không có. QUESTION token chứa material của câu; LESSON token chứa toàn bộ câu và material được tham chiếu. Dùng revision của phiên bản hiện tại, kể cả bản đã công bố.

Publish gửi `{requestId,draftVersionId,baseRevision,previewToken}`. Server khóa closure phụ thuộc, tính lại token rồi validate/publish cùng transaction. Token sai 409 PREVIEW_STALE; dữ liệu chưa hợp lệ 400 PUBLISH_VALIDATION_FAILED với fieldErrors. Không tin errors[] do client gửi.

Để tránh deadlock: sau AccountGuard, publish khóa root Material → Question → Lesson, ID tăng dần trong từng nhóm; đọc lại phụ thuộc/token sau lock. Không giữ lock Lesson rồi đi khóa Question/Material. Sửa nháp chỉ khóa root của nháp; publish kiểm tra lại tham chiếu. Tạo/sắp xếp bài khóa Unit trước các Lesson ID, publish không sửa Unit.

Foreign published dependency được dùng lại. Foreign draft bị từ chối; ADMIN có quyền đối với mọi draft. Trường hợp publish bài tự công bố draft phụ thuộc thì trả root Lesson mới; client refresh bank để lấy revision mới của Question/Material.

DELETE draft: gỡ con trỏ root trước khi xóa version/join, không xóa Question/Material độc lập. Đang bị nháp khác tham chiếu thì 409. Published version/history không xóa qua API này.

## 5. File và tài liệu

POST /api/assets multipart có đúng file và requestId; header CSRF vẫn bắt buộc. Backend đặt giới hạn request lớn hơn 5 MiB một chút để chứa overhead multipart nhưng giới hạn bytes file luôn 5 MiB.

Asset chỉ chứa file metadata. MaterialVersion xác định cách sử dụng:

- READING: readingText; có thể kèm ảnh/altText.
- AUDIO: asset audio, transcript riêng, startSeconds và endSeconds nullable. start >=0; end nếu có phải >start và nằm trong audio. Không cắt/nhân file khi vài câu dùng cùng đoạn.
- IMAGE: asset ảnh, altText/caption.
- P0 không dùng YouTube player/transcript scraping; cần file demo có quyền sử dụng.

PublicMaterial không có transcript/storageKey. Khi audio có giới hạn đoạn, client phát từ start đến end, lưu vị trí giây tuyệt đối trong file. Vị trí nghe phục vụ tiếp tục học, không dùng xác nhận đã nghe đủ hoặc chấm điểm.

Spring MVC có cơ chế [Resource/ResponseEntity](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responseentity.html) để triển khai phản hồi file; người làm phải test Range thật, không chỉ thêm Accept-Ranges.

## 6. Luồng làm bài và lưu nháp

```text
POST /attempts → GET snapshot → người học nhập → PUT draft → ACK
                   ↓
         hint/check GUIDED nếu cần
                   ↓
POST submit (kèm đáp án cuối) → GET result → retry chữa từng câu
```

Attempt chỉ có IN_PROGRESS → SUBMITTED, không có thời gian thi/EXPIRED/cancel ở P0. Submit sớm được phép; sau đó muốn học lại tạo lượt mới.

POST tạo lượt ghi selection đã chuẩn hóa, bộ Version ID, origin, practiceRole, order/permutation, dấu đã gặp và row đáp án null. Khóa actor trước khi kiểm tra lượt đang làm để hai request không tạo đôi. GET snapshot không sửa hay chọn lại câu.

GET snapshot có lesson của đúng LessonVersion đã chốt (null với PRACTICE/REVIEW), PublicQuestion và PublicMaterial, không có answerKey, explanation, transcript hoặc nội dung hint chưa yêu cầu. Kể cả SUBMITTED, snapshot vẫn theo schema an toàn; feedback chi tiết lấy từ result.

Ví dụ lưu nháp/nộp lấy trong examples-m02-m03.json; cùng một schema `AttemptMutation`:

```json
{
  "requestId": "22a5ae67-d4b2-4fa8-b16f-0ff8617b4508",
  "baseRevision": 4,
  "answers": [
    {"attemptQuestionId": 1001, "response": {"type":"SINGLE_CHOICE","optionId":"opt-b"}},
    {"attemptQuestionId": 1002, "response": null}
  ],
  "playerState": {
    "currentAttemptQuestionId": 1002,
    "audioPositions": []
  }
}
```

Ví dụ trên dành cho lượt có đúng hai item. Lượt 10 câu gửi đủ 10 entry, kể cả null. currentAttemptQuestionId/materialVersionId phải thuộc lượt; audioPositions không trùng và giây trong 0–86400, UI clamp theo duration/segment.

PUT draft tăng Attempt revision một lần và trả `{requestId,attemptId,revision,state,savedAt,answeredCount}`. Không chấm, không tạo answer_trial. UI chỉ ghi “Đã lưu trên máy chủ” khi nhận ACK; khi mất mạng ghi “Chưa đồng bộ”. M03 giữ đáp án trong state và có save adapter; M06 cung cấp IndexedDB/hàng đợi/reconnect theo key userId+attemptId. Không cam kết tải lại trang offline sẽ còn nháp trước khi phần IndexedDB M06 được triển khai.

Các mutation của một Attempt phải được client tuần tự hóa. Hint/check/save cùng dùng revision hiện tại; đang save thì chờ ACK trước khi check. Submit luôn có full snapshot mới nhất, không chỉ gửi một ID rồi trông chờ autosave đã xong.

Mạng lỗi/timeout: giữ nguyên requestId và payload để gửi lại; kết nối lại GET snapshot trước khi tiếp tục ghi. 401 dừng gửi và đăng nhập lại; 409 tải trạng thái và đối chiếu; 503 giữ nháp, không hiển thị đã lưu. ACK cũ không được ghi đè local state mới hơn.

## 7. Hint, check, submit và chữa

Hint/check cần item thuộc chính Attempt và state IN_PROGRESS. Chỉ role GUIDED được sử dụng. Hint đã mở có thể lấy lại, không tăng revision thêm nếu không thay đổi. Hint không tồn tại trả 404; role khác trả 409 FEEDBACK_NOT_AVAILABLE.

Check cần đáp án đầy đủ; lưu response hiện tại, tạo FIRST nếu chưa có trial hoặc GUIDED_RETRY nếu đã có, chấm rồi mở feedback. Ghi supportUsed của FIRST trước khi đánh dấu feedbackOpened; nếu đã mở hint thì true. Response check có answerKey/explanation, **không có transcript**, để không lộ câu khác chung audio.

Submit trong một transaction:

1. AccountGuard, receipt, lock Attempt và kiểm tra revision/state.
2. Validate full snapshot; lưu đáp án cuối/playerState.
3. Tạo FIRST nguồn SUBMIT cho mọi item chưa có trial, kể cả UNANSWERED. FIRST từ check trước đó giữ nguyên.
4. Chấm final, dựng result version 1, INSERT Submission unique Attempt.
5. Đổi state SUBMITTED, tăng revision một lần; cập nhật lesson_progress và ghi receipt.
6. Commit rồi trả Result. Không gửi XP, kết quả hoặc ACK thành công trước commit.

Hai submit khác key: chỉ một thành công, cái còn lại 409 ATTEMPT_FINISHED hoặc REVISION_CONFLICT. Cùng key/payload: replay đúng Result cũ. GET result trước nộp trả 409 RESULT_NOT_READY.

Result chứa first/final response/outcome/responseComplete/support, origin/objective/role/wasSeenBefore, answerKey/explanation và materialFeedback.transcript. Lưu result_json và số đếm cùng lúc để M04/M05/M07 có nguồn bất biến theo Submission ID.

Retry sau nộp cần response đầy đủ; ghi trial CORRECTION nguồn RETRY, supportUsed=true, tăng Attempt revision và trả Feedback. Không UPDATE attempt_answers sau nộp, không sửa Submission/score/FIRST/tiến độ hoàn thành. Result GET sau chữa vẫn là kết quả nộp ban đầu.

LessonCompleted chỉ true cho LEARN khi mọi item điền đủ và mọi GUIDED có trial CHECK trước nộp. Chưa làm xong vẫn được nộp nhưng không hoàn thành bài. Đã COMPLETED trước đó thì không hạ xuống STARTED khi học lại/nộp dở.

## 8. Tích hợp và kiểm tra

M02 cấp server-side PublishedContentReader; M03 không gọi HTTP nội bộ vòng về chính backend. M03 thêm AttemptAssetReadGrant và ReviewSelectionProvider; M04 cung cấp selection ôn sau. Không cấp quyền đọc ngân hàng/keys cho client bằng interface nội bộ này.

DDL tại [planned](../../docs/database/planned/) là nguồn cột/FK/index; file áp dụng thực tế sau này ở db/migration. V3 → V4 theo thứ tự. MySQL JSON bảo đảm cú pháp JSON, còn cấu trúc từng type do validator Java kiểm tra; xem [MySQL JSON](https://dev.mysql.com/doc/refman/8.4/en/json.html) và [CHECK](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html). Có thể dùng [Spring Data JPA @Lock](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html) cho khóa pessimistic theo quy trình trên.

Checklist code/test và phân chia ticket nằm trong bàn giao. Static JSON/DDL validation không thay thế test quyền, concurrent submit, mất ACK hoặc frontend.

## 9. M02 — cập nhật audio đồng loạt (19/10/2026)

Hai endpoint bổ sung, giữ nguyên các API và cấu trúc version M02/M03 đã chốt:

| API | Kết quả |
| --- | --- |
| GET /api/author/materials/{id}/audio-update | Phiên bản AUDIO công bố mới nhất, previewToken, câu/bài liên quan và lý do không thể chọn |
| POST /api/author/materials/{id}/audio-update | Tạo/xuất bản các QuestionVersion và LessonVersion mới trong một transaction |

POST nhận requestId, targetVersionId, previewToken, questionIds, lessonIds, contentConfirmed=true. Mỗi danh sách tối đa 100 ID duy nhất; cần chọn ít nhất một câu hoặc bài. Trả materialId, targetVersionId, questions:[{id,versionId}], lessons:[{id,versionId}]. Request phải có CSRF; chỉ TEACHER/ADMIN dùng API. Cùng requestId/payload replay kết quả cũ, không nhân bản phiên bản; key khác payload trả 409.

Luồng giảng viên: sửa/thay file AUDIO và xuất bản → bấm **Cập nhật audio cho câu hỏi/bài học** → xem danh sách, chọn nội dung → xác nhận transcript/đáp án vẫn đúng → áp dụng. Hệ thống chỉ thay liên kết phiên bản audio, không tự viết lại câu hỏi, transcript hoặc đáp án. Nếu thay đổi nội dung nghe làm câu/đáp án không còn đúng, cần chỉnh nội dung đó thủ công trước khi chọn cập nhật.

- Danh sách dựa trên các câu đang công bố và các bài đang công bố còn trỏ đến phiên bản AUDIO cũ của cùng một Material. Material phải active published, loại AUDIO; bản nháp Material chưa công bố không phải đích cập nhật. Không chuyển câu dùng READING/IMAGE cũ thành câu nghe.
- Giảng viên chỉ cập nhật câu/bài của mình; ADMIN được thao tác mọi nội dung như quyền biên soạn hiện hành. Tài liệu dùng chung được phép đọc vẫn có thể dùng để cập nhật phần nội dung của mình. Chi tiết nội dung của tác giả khác không trả ra, chỉ trả số lượng. Không đổi ownerId, Unit, originKey, đáp án, nhóm hoặc thứ tự câu.
- Câu/bài đang có bản nháp bị chặn để tránh ghi đè phần đang soạn. Hoàn tất hoặc bỏ nháp rồi tải lại danh sách. Nếu bài đang dùng một phiên bản câu có nội dung khác bản công bố hiện tại (ngoài materialVersionId), phải sửa bài thủ công.
- Mỗi bài trả requiredQuestionIds. Chọn bài cần chọn kèm các câu đó. Nếu câu đã công bố với audio mới và nội dung còn lại giống hệt phiên bản bài đang dùng, có thể chỉ cập nhật bài để tái sử dụng câu đã cập nhật, kể cả câu công bố của tác giả khác.
- previewToken bao quát đích audio và revision/tham chiếu của nội dung liên quan. Thay đổi sau khi xem trả 409 PREVIEW_STALE; tải lại và chọn lại. Khóa theo thứ tự Material → Question → Lesson, ID tăng dần, kiểm tra lại token sau khóa; lỗi bất kỳ hoàn tác toàn bộ và không ghi receipt thành công.
- Bản công bố cũ và file cũ giữ nguyên. M03 tiếp tục pin LessonVersion/QuestionVersion/MaterialVersion đã cấp cho Attempt; các lượt đã tạo không chuyển audio giữa chừng. Không cần thay migration/database.
- Sau mất mạng/mất ACK, giao diện giữ nguyên requestId và payload để **Thử lại cập nhật audio**. Chỉ báo thành công sau ACK. Tải lại cả trang sẽ mất thao tác đang giữ trong bộ nhớ; cần lấy lại trạng thái máy chủ trước khi thao tác mới.

Sửa quyền liên quan: Lesson mới thuộc chủ Unit kể cả khi ADMIN tạo giúp; quyền đọc asset của tài liệu công bố dùng chung bao gồm các phiên bản PUBLISHED cũ trên root còn active published. Không cấp quyền đọc nháp riêng hoặc root đã ngừng cấp.
