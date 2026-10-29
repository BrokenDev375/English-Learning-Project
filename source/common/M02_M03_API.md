# Quy tắc API M02–M03

Hợp đồng 1.0.0 — 16/10/2026. Cập nhật 17/10: đã triển khai API M02 danh mục tác giả, asset/material, questions/lessons, copy câu, chi tiết Unit/thứ tự bài và learner catalog. PublishedContentReader đã có cho M03. Bộ seed 6 bài/90 câu đã hoàn thành, kèm 13 tài liệu và 7 file thật. API attempt/progress M03 chưa triển khai. Xem [tiến độ M02](../../docs/M02_PROGRESS.md).

Đọc cùng [bàn giao](../../docs/M02_M03_HANDOFF.md), [OpenAPI](openapi-m02-m03.json), [ví dụ](examples-m02-m03.json), [API M01](API.md). OpenAPI định nghĩa DTO chính xác; tài liệu này định nghĩa giao dịch, quyền và luồng.

Cập nhật M03 ngày 19/10: đã có API và runner UI cho học/luyện tập, nháp, hint/check, submit/result/retry và progress. Xem [tiến độ](../../docs/M03_PROGRESS.md), [giao diện](../../docs/M03_UI_SMOKE.md), [API M03](../../docs/M03_API_SMOKE.md), [nghiệm thu toàn module](../../statics/results/module-3-validation.md). Trạng thái “M03 chưa triển khai” trong mốc 17/10 ở trên là ghi nhận lịch sử, không phải trạng thái code hiện tại.

## 1. Quy ước HTTP

- Cùng origin, cookie JSESSIONID và CSRF của M01. Mọi POST/PUT/DELETE gửi header token nhận từ `GET /api/auth/csrf`. Không bổ sung JWT hoặc nhận userId/ownerId do client khai.
- Tất cả API cần tài khoản còn ACTIVE; author API cần TEACHER/ADMIN; attempt/progress cần STUDENT.
- ID là số nguyên dương không vượt 9.007.199.254.740.991; version/revision là số nguyên, không phải timestamp. UTC ISO-8601 cho thời điểm. UUID requestId dạng chữ thường.
- DTO từ chối trường lạ. Các trường nullable vẫn phải có mặt nếu schema ghi required; dùng null, không dùng chuỗi rỗng thay ID.
- Trang: `{items,totalElements,page,size,totalPages}`, page bắt đầu 0, size 1–50 (mặc định 20), q tối đa 100 ký tự tại endpoint có q.
- Danh sách author mặc định scope=OWN. Bank Material/Question/Lesson thêm scope=PUBLISHED để dùng lại nội dung đang công bố và scope=ALL chỉ cho ADMIN. Unit scope=ALL cũng chỉ ADMIN; Topic/Objective scope=ALL cho TEACHER/ADMIN vì là danh mục chung, quyền sửa vẫn own/ADMIN. Scope không có trong enum endpoint bị 400; TEACHER xin ALL trái quyền bị 403.
- Thứ tự: Unit theo roundNumber/id; Lesson theo sortOrder/id; author bank theo id giảm; Attempt theo updatedAt/id giảm. Mục tiêu và nguồn không có HTML tùy ý.
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
| ORDERING | tokens:[{id,text}] | acceptedOrders:[[tokenId,...]] | {type:"ORDERING",tokenIds:[tokenId,...]} |

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

LessonCompleted true cho LEARN khi mọi item được trả lời đầy đủ lúc nộp; trial CHECK của GUIDED là tùy chọn (quy tắc cập nhật 25/10/2026). Chưa làm xong vẫn được nộp nhưng không hoàn thành bài. Đã COMPLETED trước đó thì không hạ xuống STARTED khi học lại/nộp dở.

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

## Mở rộng vòng học và ORDERING — 25/10/2026

GET `/api/me/rounds?q=&topicId=&page=0&size=20`: chỉ STUDENT, cookie/session hiện hành, Cache-Control private/no-store, chỉ đọc REPEATABLE_READ. q tối đa 100 ký tự, topicId là ID hợp lệ, page ≥ 0, size 1–50. Trả CatalogPage<RoundProgress>; mỗi hàng có unit, totalLessons, completedLessons, startedLessons, state (NOT_STARTED/IN_PROGRESS/COMPLETED), nextLessonId nullable. Unit sắp theo roundNumber/id; bài theo sortOrder/id. Đếm riêng principal, chỉ các Lesson có published và chưa retire. COMPLETED khi tổng > 0 và mọi bài có progress COMPLETED; nextLessonId là bài chưa hoàn thành đầu tiên. Unit không có bài công bố bị loại như learner catalog hiện tại. Công bố thêm bài/retire làm thay đổi mẫu số hiện tại, không xóa tiến độ lịch sử. GET không đồng bộ ôn hoặc thưởng. TEACHER/ADMIN 403, chưa đăng nhập 401, query sai 400.

ORDERING giữ schemaVersion=1 và dùng migration V9 mở rộng CHECK type. Draft nhận tối đa 20 tokens và 10 acceptedOrders; các ID/text vẫn kiểm tra kiểu/độ dài. Xuất bản yêu cầu 2–20 ID duy nhất, text không trắng và 1–10 thứ tự đúng; mỗi thứ tự chứa đủ mỗi token ID đúng một lần. ID tối đa 40 ký tự, text tối đa 100. acceptedOrders thuộc answerKey, không cấp trong PublicQuestion hoặc snapshot.

Đáp án `{type:"ORDERING",tokenIds:[...]}` nhận 0–20 ID duy nhất; ID lạ/loại sai/trùng trả INVALID_ANSWER. Danh sách rỗng/null là UNANSWERED, danh sách chưa đủ có outcome=INCORRECT và responseComplete=false. Khi đủ, đối chiếu chuỗi nội dung token đã chuẩn hóa NFC với các thứ tự được chấp nhận; ID khác có cùng text được hoán đổi tương đương. Không tự chuẩn hóa case/khoảng trắng hoặc chấm gần đúng. Đảo thẻ lưu trong Display.optionIds khi tạo Attempt, snapshot phục hồi đúng thứ tự đã cấp. Thao tác UI dùng cùng draft/playerState/receipt/revision và IndexedDB M06; không bổ sung mutation hoặc thưởng riêng. FIRST, feedback, submit, correction và quyền xem khóa tiếp tục theo M03.

## Xác nhận sau khi xem đáp án — cập nhật 25/10/2026

Nộp đủ đáp án là điều kiện hoàn thành LEARN, không cần CHECK từng GUIDED và không yêu cầu đúng hết. UI sau nộp hiển thị tất cả câu trả lời/đáp án/giải thích cùng một nút Xác nhận hoàn thành bài học để trở về vòng. Hint/check vẫn là hỗ trợ tùy chọn trước nộp, không đổi luật FIRST/support.

POST `/api/attempts/{id}/complete`, body `{requestId,baseRevision}` theo schema Hint (hai trường bắt buộc), cookie/session/CSRF STUDENT. Reply LessonCompletionAck `{requestId,attemptId,revision,lessonId,lessonCompleted:true}`, private/no-store; replay có Idempotent-Replayed. Chỉ cho owner, mode LEARN, state SUBMITTED, mọi ResultItem.finalResponseComplete=true của Submission gốc. Không dùng đáp án chữa để bù câu chưa đủ khi nộp. Sai owner 404, sai vai trò 403, chưa nộp/sai mode 409, bài nộp chưa đủ 400 LESSON_INCOMPLETE.

Luồng thông thường đã có progress COMPLETED từ submit; xác nhận không tăng revision hoặc ghi lại tiến độ. Dữ liệu luật cũ có bài nộp đủ nhưng thiếu CHECK nên STARTED được xác nhận mà không phải làm lại: dưới actor→Attempt→progress lock, kiểm tra revision, ghi COMPLETED/phiên bản/thời gian và receipt cùng transaction, tăng Attempt revision để invalidation. Giữ lastAttemptId/lastScorePercent của hoạt động mới hơn. Không ghi lại Submission, FIRST, câu trả lời khi nộp, điểm, lịch ôn hoặc thưởng/huy hiệu cũ. Không thêm migration. Cùng requestId replay trước revision; xác nhận lặp bằng key mới trên bài đã hoàn thành cũng không nhân mốc tiến độ.

Client dùng pending Operation kind=complete trong cùng journal làm bài M06, giữ nguyên key/payload qua timeout/reload và GET đối soát. Xác nhận thành công điều hướng về đúng Unit/vòng; GET result vẫn là dữ liệu bất biến theo lúc nộp.

## Bản đồ và khóa vòng — 26/10/2026

RoundProgress thêm number (integer ≥1), locked (boolean), prerequisiteUnitId, prerequisiteRoundNumber và prerequisiteTitle (nullable). Thứ tự toàn cục là Unit.roundNumber/id của Unit có Lesson đang công bố. Số thứ tự và điều kiện mở khóa tính trước q/topicId/page/size. Tìm kiếm và phân trang giữ hành vi catalog hiện hành. GET `/api/me/rounds/{id}` trả một RoundProgress; quyền/session/cache như danh sách, ID sai 400, vòng không còn công bố 404, không tự ghi tiến độ.

Vòng COMPLETED được chơi lại. Vòng khác bị khóa nếu có bất kỳ vòng trước chưa hoàn thành; prerequisite chỉ vòng chưa hoàn thành đầu tiên cần làm. Tất cả Lesson đang công bố của vòng trước cần progress COMPLETED; không yêu cầu điểm tối thiểu. Bài mới/retire làm thay đổi mẫu số hiện tại theo luật vòng học.

POST `/api/attempts` mode LEARN kiểm tra khóa tại server sau actor lock và khóa Lesson, trước khi tạo Attempt/exposure/progress. Vòng bị khóa trả 409 ROUND_LOCKED kèm thông báo vòng cần hoàn thành, không ghi receipt thành công. Sau khi mở khóa có thể gửi lại request chưa thành công. Lượt đang IN_PROGRESS đã tồn tại theo cùng selection vẫn được tiếp tục trước bước kiểm tra khóa; replay receipt vẫn đi trước kiểm tra trạng thái. Đây là tương thích dữ liệu đang làm, không cấp quyền tạo lượt mới vượt vòng. PRACTICE/REVIEW không bị khóa và không tăng tiến độ vòng.

Số vòng không lưu vào Submission và không thay đổi FIRST/điểm/XP. API mới không cấp khóa đáp án. Không migration, không backfill hoặc tự hoàn thành bài cũ.

## Bài 18 câu và hoạt động đọc — 28/10/2026

Bài viết tự luận cuối Unit dùng luồng và bảng riêng V15, ngoài Attempt/Submission tự chấm M03. Xem [hợp đồng bài viết](UNIT_WRITING_API.md) và [OpenAPI](openapi-writing.json). RoundProgress có `writing` tùy chọn; nộp bài viết mở vòng sau mà không cần đợi giáo viên chấm.

LessonCreate/Update/Version có `activities` tùy chọn, lưu theo LessonVersion bằng V14. Thiếu trường hoặc danh sách rỗng dùng cấu trúc cũ; dữ liệu tính hash receipt bài cũ không tự thêm trường rỗng. Mỗi activity có `id`, `kind`, `questionVersionIds`, `passages`. Mỗi bài chọn 1–7 dạng khác nhau theo thứ tự CHOICE, MATCHING, ORDERING, FILL, READING_CLOZE, READING_CHOICE, LISTENING_CHOICE; được bỏ dạng chưa cần. Tổng **15–20 câu LEARN**, mỗi phần ít nhất hai câu gồm một GUIDED đầu tiên rồi INDEPENDENT; REVIEW_VARIANT nằm ngoài danh sách hoạt động. Xuất bản kiểm tra đúng 15–20 câu duy nhất, role/type/thứ tự và toàn bộ slot; nháp được phép chưa hoàn tất. Cấu trúc sáu và bảy phần cũ đủ 18 câu vẫn hợp lệ.

Mỗi phần đọc nếu có dùng MaterialVersion ví dụ riêng cho GUIDED và một MaterialVersion chính dùng chung cho các INDEPENDENT. LISTENING_CHOICE yêu cầu MaterialVersion AUDIO và 3–6 option/câu; transcript chỉ trả qua feedback/result theo quyền hiện hành. READING_CHOICE yêu cầu đúng bốn option/câu. READING_CLOZE yêu cầu một blank/câu và đủ hai passage. Passage có `materialVersionId`, `parts`; phần TEXT là `{kind:"TEXT",text}`, phần BLANK là `{kind:"BLANK",questionVersionId,blankId}`. Thay từng BLANK bằng `____` rồi nối các phần phải khớp readingText của đúng MaterialVersion; mỗi câu/blank được ánh xạ đúng một lần, không cho tham chiếu ngoài hoạt động/học liệu.

Snapshot lượt mới thêm `activities` khi có dữ liệu. Activity công khai chỉ có `id`, `kind`, `attemptQuestionIds`, `passages`; BLANK dùng `attemptQuestionId` thay questionVersionId. Chỉ trả material/item thuộc lượt. Với REVIEW/PRACTICE chỉ chọn một số câu của nhóm, các ô không thuộc lượt được thay bằng TEXT `____`; không trả khóa hoặc buộc gửi thêm đáp án ngoài lượt. Nguồn cấu trúc được đọc từ LessonVersion của lesson_question_id đã chốt, không tìm theo phiên bản đang công bố.

GET `/api/lessons/{id}` thêm `activitySummary` tùy chọn gồm `{kind,questionCount}` theo thứ tự hoạt động; không trả cấu trúc ô, ID học liệu hoặc đáp án trước tạo lượt. Hợp đồng Snapshot/LessonPublic cũ giữ nguyên khi không có metadata mới.

LEARN tạo mới lưu vị trí theo activities, không gom toàn bộ GUIDED lên đầu; lượt cũ giữ vị trí đã lưu. Điền từ trực tiếp trong đoạn vẫn gửi Answer FILL_BLANK theo từng item, không thêm kiểu đáp án hoặc endpoint chấm nhóm. Chấm/submit/complete/receipt/XP dùng luật hiện hành. Xem [triển khai và bài mẫu](../../docs/QUESTION_VARIETY_PROGRESS.md).

### V12 — số vòng lưu cố định

Unit thêm roundNumber (INT dương, tối đa 2147483647, duy nhất toàn hệ thống kể cả vòng chưa công bố). number trong RoundProgress bằng unit.roundNumber, không đánh lại từ tập công bố, trang hoặc bộ lọc. roundNumber quyết định thứ tự và prerequisite; stage/sortOrder vẫn được đọc/ghi để tương thích nhưng không còn quyết định thứ tự vòng.

POST/PUT author units nhận roundNumber tùy chọn. POST bỏ trống/null: max hiện tại + 1; PUT bỏ trống/null: giữ số cũ. DTO bỏ trường null khi băm receipt để replay payload cũ giữ nguyên hash. Request/response receipt đã lưu không được viết lại. Số đã dùng trả 409 ROUND_NUMBER_CONFLICT (kể cả tranh chấp unique index); revision/quyền sở hữu và requestId vẫn như cũ. Lỗi không tạo receipt thành công. Khi tạo đồng thời với số tự động, bên bị xung đột có thể gửi lại để chọn số tiếp theo.

Số có thể có khoảng trống. Đổi sang số trống để di chuyển vòng; đổi chỗ hai vòng đang chiếm số cần một số tạm còn trống. Không tự đánh lại các vòng của tác giả khác. ID Unit/Lesson, Attempt, kết quả, XP và tiến độ lịch sử không đổi. Số trên kết quả cũ không được viết lại; khóa vòng mới được tính theo thứ tự hiện tại, vòng hoàn thành luôn mở.

V12 backfill các Unit có bài công bố chưa retire trước, giữ stage/sortOrder/id cũ để bảo toàn số học viên đang thấy; các Unit còn lại theo sau. Không sửa migration cũ. Seed chỉ cấp số khi tạo mới, không ghi đè số tác giả đã sửa.

Response replay của receipt tạo/sửa Unit trước V12 có thể thiếu roundNumber (LegacyUnitReceipt); body cũ giữ nguyên và có Idempotent-Replayed. UI tải lại catalog sau lưu; các GET và thao tác mới luôn trả roundNumber. Không suy số mới từ nội dung response cũ.

## V13 — quản lý lộ trình và duyệt học liệu

ADMIN tạo/sửa topics/objectives/units, phân công giáo viên qua assignedTeacherId; clearAssignment=true bỏ phân công, không gửi cùng assignedTeacherId khác null. Thiếu/null các trường mới khi cập nhật giữ phân công hiện tại. Chỉ chọn ACTIVE TEACHER. ownerId giữ người tạo. GET author units scope=OWN: giáo viên lấy vòng được giao; ADMIN lấy vòng do mình tạo. Lesson bank OWN tương ứng bài trong vòng được giao cho giáo viên và bài do ADMIN tạo khi người gọi là ADMIN. ADMIN ALL giữ mọi tác giả.

GET /api/author/teachers?page=0&size=20: chỉ ADMIN, CatalogPage<{id,fullName}>, không email/hash. POST author {questions|lessons}/{id}/publish và /retire, POST materials/{id}/audio-update: ADMIN cho ghi mới; receipt thành công cũ vẫn replay. Giáo viên quản lý nguyên liệu Material của mình như cũ. Lesson ownerId là người tạo; canEdit của server lấy theo Unit.assignedTeacherId, quyền câu hỏi/tài liệu theo người tạo hoặc ADMIN.

POST /api/author/{questions|lessons}/{id}/submit-review: body Publish (requestId, draftVersionId, baseRevision, previewToken), 201 {review,content}. Chỉ quản lý hiện tại, yêu cầu preview không lỗi và token/revision còn đúng. Lưu snapshot + một yêu cầu PENDING, đóng băng nháp; không công bố. Root/summary tác giả thêm canEdit và review nullable; trường mới có thể vắng trên replay trước V13. Metadata review không lộ qua bản công bố đọc bởi giáo viên khác.

GET /api/author/reviews?state=PENDING&page=0&size=20 (state ALL/PENDING/APPROVED/RETURNED/WITHDRAWN), GET /reviews/{id}: chỉ tác giả/phụ trách hiện tại hoặc ADMIN. Trả Review hoặc Detail {review,snapshot,stale,canWithdraw}; snapshot gồm preview tại lúc gửi và materials đầy đủ. ROOT quyền được kiểm tra lại khi đọc, không chỉ so submittedBy.

POST /api/author/reviews/{id}/{approve|return|withdraw}: {requestId,baseRevision,note?}, 200 Review, revision của yêu cầu. approve/return chỉ ADMIN; return cần note không trắng ≤2000 ký tự. withdraw theo quyền quản lý hiện tại. Quyết định được ghi người xử lý/thời gian; snapshot cũ giữ nguyên. APPROVED công bố đúng token/version đã gửi cùng transaction. Bản cũ vẫn dùng đến khi duyệt; history Attempts/Submission/XP không viết lại. Câu hỏi chờ riêng được duyệt cùng nếu nằm trong closure bài được duyệt.

409 REVIEW_PENDING khi cố sửa/bỏ nháp hoặc công bố trực tiếp một root chờ duyệt; PREVIEW_STALE khi phụ thuộc/token đổi; ASSIGNMENT_CHANGED khi người phụ trách đổi; REVIEW_FINISHED khi đã quyết định; REVISION_CONFLICT và REQUEST_ID_REUSED giữ luật cũ. Lỗi không commit receipt/thay đổi công bố dở. Hàng đợi đánh stale cả khi phụ thuộc không còn đủ điều kiện. Một root chỉ có một PENDING bằng unique key.

GET /api/author/versions/{questions|materials}/{id}: giải quyết chính xác version cho editor. Chỉ TEACHER/ADMIN; sở hữu, công bố đang được phép hoặc dependency đã thực sự được Lesson trong vòng đang phân công tham chiếu. Không cấp quyền sửa root của tác giả khác và không dùng cho STUDENT. File private theo dependency được đọc cùng phạm vi, thu hồi khi bỏ phân công.

Chi tiết nghiệp vụ tại [phân công và duyệt học liệu](../../docs/CONTENT_GOVERNANCE.md). Quyền lịch sử công bố trước V13 được giữ, không tạo hàng duyệt giả cho dữ liệu cũ.
