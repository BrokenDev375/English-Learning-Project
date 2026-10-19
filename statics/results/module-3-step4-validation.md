# M03 bước 4 — feedback, nộp/chấm, chữa và tiến độ

Ngày: 19/10/2026. Nền Git: `759ccb3`, nhánh `feature/m03-learning-attempts`. Thay đổi bước 4 chưa commit. Windows/Docker Desktop, Java 17/Maven, MySQL 8.4 thật qua Testcontainers; không dùng DB hoặc volume demo.

## Phạm vi

| API | Hành vi |
|---|---|
| POST `/api/attempts/{id}/items/{itemId}/hint` | GUIDED đang làm; mở lần đầu tăng revision, lần sau chỉ trả lại hint |
| POST `…/items/{itemId}/check` | Response đầy đủ, lưu answer và FIRST/GUIDED_RETRY; không trả transcript |
| POST `/api/attempts/{id}/submit` | Full answers/playerState; chấm, Submission/result, state, revision, progress và receipt trong cùng transaction |
| GET `/api/attempts/{id}/result` | RESULT_NOT_READY trước nộp; sau nộp đọc Result đã lưu |
| POST `…/items/{itemId}/retry` | CORRECTION sau nộp, không thay final/điểm/FIRST/tiến độ |
| GET `/api/me/learning-progress` | NOT_STARTED/STARTED/COMPLETED, Unit/phân trang, bản cập nhật và lịch sử riêng |

Không đổi migration V1–V4. Result lưu schema 1, Submission ID thật và các số đếm; không phát XP, lịch ôn hoặc event chỉ nằm trong RAM.

## Kiểm tra

- Nhóm tập trung bản cuối: **54/54 PASS**, 0 failure/error/skipped, BUILD SUCCESS, 1 phút 47 giây: 36 AttemptApiTest, 13 AnswerGraderTest, 5 AttemptRequestsTest. Có 17 ca HTTP mới cho bước 4, giữ 19 ca bước 2–3.
- Toàn bộ backend: **136/136 PASS**, 0 failure/error/skipped, BUILD SUCCESS, 7 phút 46 giây. Gồm 70 test M01/M02, 5 receipt, 25 nền M03 và 36 HTTP AttemptApiTest. Mã nguồn được biên dịch lại trong cả lượt tập trung và lượt toàn bộ.
- `git diff --check`, whitespace của file mới và liên kết nội bộ trong tài liệu cập nhật: PASS.
- Rà soát cú pháp PowerShell của hướng dẫn thử API bằng AST parser: PASS. Không chạy đoạn hướng dẫn ghi dữ liệu trên DB demo.
- Lượt tập trung đầu phát hiện Result.submittedAt bị mất độ chính xác nanosecond khi Hibernate ghi epoch số vào MySQL JSON. Đã đặt JsonFormat STRING cho trường này; test replay/GET đọc lại đúng thời điểm. Hai assertion dùng JsonNode có khác loại số nhưng cùng giá trị được đổi sang JSONAssert so sánh giá trị số.

## Bằng chứng nghiệp vụ

- Hint replay cùng key không tăng revision lần hai; key mới lấy hint đã mở cũng không tăng. Key mới với revision cũ bị từ chối. Không có hint trả HINT_NOT_AVAILABLE mà không ghi state/trial.
- Check trước khi mở hint ghi FIRST.supportUsed=false; sau hint là true. Lần check tiếp theo là GUIDED_RETRY, trialNo tăng và supportUsed=true. FIRST sai vẫn giữ nguyên khi sửa đáp án đúng và nộp.
- Check/hint của INDEPENDENT/REVIEW_VARIANT, item khác Attempt hoặc Attempt của người khác bị từ chối. Check/retry thiếu đáp án không ghi dữ liệu. Feedback có keys/giải thích nhưng không có transcript chung audio.
- Submit không cần autosave; final answers/playerState được khôi phục qua snapshot. Thiếu item, option ID lạ hoặc current item lạ không tạo Submission/trial hoặc thay tiến độ.
- Nộp sớm với null/partial được chấp nhận; tạo FIRST nguồn SUBMIT cho cả UNANSWERED. Final answered/unanswered, correct, score và các fact khớp. Result có materialFeedback đã khử trùng phiên bản.
- Đủ câu nhưng chưa CHECK mọi GUIDED không hoàn thành bài. Đã CHECK mọi GUIDED và final đầy đủ thì hoàn thành, kể cả trả lời sai. Học lại rồi nộp dở giữ COMPLETED, completedAt và lastCompletedVersionId; lastAttempt/lastScore vẫn phản ánh lần mới.
- Fresh independent counts loại GUIDED, origin đã gặp và câu rỗng; tính partial là answered. REVIEW_VARIANT chưa gặp cũng được tính. PRACTICE 2/3 đúng cho 66.67%, không hoàn thành Lesson; REVIEW cũng không hoàn thành Lesson.
- GET result không chấm lại; CORRECTION chỉ tạo trial với source RETRY/support=true và tăng Attempt revision. Final answers vẫn như lúc nộp, FIRST/result/điểm/progress không đổi. Snapshot vẫn không lộ keys, giải thích hoặc transcript sau nộp.
- Hai submit cùng key replay cùng Submission, revision chỉ tăng một lần. Khác key chỉ một thành công. Submit cạnh tranh PUT draft hoặc check cùng revision có đúng một bên thắng; giải quyết revision rồi nộp vẫn chỉ có một Submission.
- Bỏ nội dung HTTP response sau submit đã commit rồi gửi lại nguyên key/payload trả Result cũ, kể cả sau CORRECTION. Đây là test bỏ body sau commit, không giả lập mất gói TCP hoặc ACK ở cấp mạng. Key cũ đổi payload bị REQUEST_ID_REUSED.
- CHECK tạm trên lesson_progress gây lỗi sau insert Submission; CHECK trên request_receipts gây lỗi sau cập nhật tiến độ. Cả hai trả 503 và rollback Submission, final answers, FIRST, revision/state và progress; không receipt thành công. Bỏ constraint rồi retry cùng key thành công. Constraint chỉ tạo trong DB test.
- Tiến độ phân trang gồm bài đang công bố và lịch sử của chính học viên; không tạo/lộ hàng của người khác, không đưa bài nháp vào. Unit không có bài trả rỗng; page/size sai bị từ chối. Bài nghỉ vẫn giữ lịch sử riêng với currentPublishedVersionId=null.
- Xuất bản QuestionVersion/LessonVersion mới trong lúc làm: submit lượt cũ chấm theo keys cũ, ghi completedVersion cũ, updateAvailable=true so với bản đang công bố. Nghỉ bài sau đó không thay GET result đã lưu.
- Kiểm tra owner, STUDENT/TEACHER/ADMIN, CSRF và session. API mutations dùng actor lock và receipt trước business lock/revision/state theo cơ chế chung.

## Tái hiện

Từ root, dùng test image đã có và bind mount nguồn hiện tại để Maven biên dịch lại:

```powershell
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "D:/Code/PTIT/BTL_LTM/English-Learning-Project/source/server/src:/app/src:ro" -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-m03-step0-test mvn -B -ntp '-Dtest=AttemptApiTest,AnswerGraderTest,AttemptRequestsTest' test
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "D:/Code/PTIT/BTL_LTM/English-Learning-Project/source/server/src:/app/src:ro" -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-m03-step0-test mvn -B -ntp test
```

Nếu chưa có image, build `docker build --target test -t english-learning-m03-step0-test ./source/server` trước. Không dùng image cũ mà bỏ bind mount nguồn khi nghiệm thu thay đổi mới. Log phiên làm việc nằm trong `.test-state/m03-step4-focused-final.log` và `.test-state/m03-step4-backend.log` (ignored).

## Giới hạn

Giao diện runner và browser E2E M03 thuộc bước 5–6, chưa triển khai hoặc chạy ở bước này. Chưa có REVIEW provider thật M04, XP M05 hoặc đồng bộ offline bền vững M06. Hướng dẫn [thử API bước 4](../../docs/M03_STEP4_API_SMOKE.md) đã được kiểm tra cú pháp; luồng nghiệp vụ được kiểm tra bằng HTTP integration tests trong môi trường riêng. Không build/restart demo hay thay dữ liệu demo để nghiệm thu.
