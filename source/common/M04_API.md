# Hợp đồng M04 — Ôn tập cá nhân

Ngày 21/10/2026. Cookie session + CSRF như M01/M03. Mọi API chỉ cho STUDENT; danh tính lấy từ principal. Response dùng Cache-Control: no-store, private. Không trả đáp án/giải thích/transcript, nội dung câu chưa làm hoặc storageKey trong dashboard.

## POST /api/me/review/sync

Body chỉ gồm requestId UUID chữ thường:

```json
{"requestId":"9f219d91-5324-4a3b-89ba-d1001e5d6737"}
```

200:

```json
{"processedSubmissions":1,"remainingSubmissions":0}
```

Mỗi request xử lý tối đa 100 Submission chưa có ledger của chính user, theo ID tăng dần. Actor lock trước đọc/ghi; tiến độ, bằng chứng, ledger và receipt cùng transaction. Cùng key replay đúng response có Idempotent-Replayed: true; không xử lý lại kết quả. Lấy GET sau replay để biết trạng thái mới nhất nếu có lượt nộp khác. Có remaining thì gửi batch tiếp bằng key mới.

Khi chưa rõ ACK, giữ key cũ tới lúc retry thành công. Sau reload có thể gửi key mới an toàn vì ledger đã chống trùng. GET không thực hiện backfill.

## GET /api/me/review

Query:

| Tham số | Luật |
|---|---|
| topicId | Tùy chọn, ID dương; bỏ qua để xem các chủ đề của mình |
| kind | ALL (mặc định), DUE hoặc MISTAKES |
| page | Từ 0 |
| size | 1–50, mặc định 20 |

Response Dashboard:

- generatedAt: thời điểm server dựng danh sách.
- pendingSubmissions: số kết quả chưa đồng bộ, tính trên toàn bộ chủ đề của user.
- totalObjectives / needsReviewCount / mistakeObjectives: tổng trong chủ đề đang lọc, trước lọc kind/phân trang.
- objectives: CatalogPage với items, totalElements, page, size, totalPages.
- choices: số nguồn câu khác nhau có thể cấp cho ba lựa chọn mới: `all`, `priority`, `wrong`. Áp dụng `topicId`, không bị giới hạn bởi `kind` hoặc phân trang danh sách mục tiêu. Chỉ tính câu còn trong bài đang công bố; không trả nội dung/đáp án của các câu này.

Mỗi ObjectiveReview có:

| Trường | Ý nghĩa |
|---|---|
| objectiveId, topicId, name | Mục tiêu đã có kết quả của chính học viên |
| status | NO_EVIDENCE / NEEDS_PRACTICE / NEEDS_INDEPENDENT_WORK / DUE / SCHEDULED |
| reason | Giải thích ngắn từ bằng chứng |
| needsReview, hasMistakes | Cần luyện/tới mốc và cờ lỗi sai chưa giải quyết |
| stage | 0–3; mốc tăng tối đa một lần mỗi ngày Asia/Ho_Chi_Minh |
| lastPracticedAt, nextReviewAt | Instant ISO 8601, lưu UTC |
| submissionCount | Số Submission chứa mục tiêu, không đếm từng câu là một lượt |
| firstAnsweredCount, firstCorrectCount | Tổng số FIRST có đáp án/đúng, gồm cả nhóm hướng dẫn |
| assistedCount | Số câu đã trả lời thuộc GUIDED hoặc FIRST dùng hỗ trợ |
| unansweredCount | Tổng FIRST bỏ trống |
| lastWrongCount | Số FIRST sai trong Submission mới nhất chứa mục tiêu |
| lastIndependentAnswered, lastIndependentCorrect | Số câu tự làm có đáp án/đúng của lượt mới nhất |
| availableQuestionCount, unseenQuestionCount | Số origin khác nhau ở mức ưu tiên ôn trong bài đang công bố |
| currentDifficulty | Mức ưu tiên ôn: EASY/MEDIUM/HARD, không phải trình độ được chứng nhận |
| recommendedDifficulty | Mức gợi ý, không tự ghi đè lựa chọn của học viên |
| recommendationReason | KEEP_PRACTICING / NOT_ENOUGH_DATA / NOT_ENOUGH_NEW_QUESTIONS / HIGHEST_DEMO_LEVEL / TRY_NEXT_DIFFICULTY |
| freshSetCount | Số phần cùng mức có đủ câu mới trong các Submission; có thể gồm bộ làm chưa tốt |
| freshAnsweredCount, freshCorrectCount | Tổng FIRST câu mới tự làm, kể cả bộ chưa đủ số câu |

HAS_MISTAKES có thể vẫn true dù lastWrongCount=0: lượt mới nhất chỉ bỏ trống/hướng dẫn chưa xóa được lỗi cũ. DUE gồm needsPractice hoặc nextReviewAt <= thời gian server.

## Tạo/tiếp tục lượt ôn

Dùng nguyên hợp đồng M03 POST /api/attempts:

```json
{
  "requestId":"105a1998-93fc-4223-b404-65280324cb21",
  "mode":"REVIEW",
  "reviewKind":"PRIORITY",
  "topicId":null,
  "count":10
}
```

Giao diện ngày 28/10 dùng ba `reviewKind`:

| reviewKind | Nguồn câu |
|---|---|
| ALL — Ôn tất cả | Câu sai ở vòng chơi chưa ôn đúng + toàn bộ câu gắn REVIEW_VARIANT trong bài đang công bố |
| PRIORITY — Ôn ưu tiên | Câu sai chưa ôn đúng + REVIEW_VARIANT cùng Objective với những câu sai chưa được giải quyết |
| WRONG — Ôn các câu sai | Chỉ các nguồn câu sai ở vòng chơi chưa ôn đúng, gồm GUIDED/INDEPENDENT |

Nguồn sai lấy FIRST=INCORRECT từ LEARN đã có Submission của chính principal; lỗi chỉ xuất hiện ở PRACTICE/REVIEW, bỏ trống và lượt chưa nộp không tạo nguồn sai mới. Khi một REVIEW nộp sau lần LEARN sai gần nhất có `finalOutcome=CORRECT` cho **cùng originKey**, câu đó rời danh sách sai. Lưu nháp, làm đúng biến thể khác cùng Objective hoặc CORRECTION sau nộp không giải quyết câu gốc. Nếu LEARN sau lại sai, nguồn câu được thêm lại. Không sửa/xóa FIRST, Submission hoặc dữ liệu bài cũ. Thứ tự Submission ID xác định sự kiện mới hơn dưới actor lock; không dựa updatedAt của lần chữa bài.

Ba lựa chọn mới không lọc theo currentDifficulty, mốc lịch hoặc yêu cầu Objective đã học đối với toàn bộ biến thể của ALL. Chỉ cấp các phiên bản đang nằm trong bài công bố; origin sai cũ được nối tới phiên bản hiện có cùng nguồn, không tự mở lại bài đã retire. Khử trùng origin trong kho và lượt làm; số `choices` dùng cùng tập hợp với bộ chọn. ALL/WRONG ưu tiên câu ít/lâu gặp; PRIORITY xen kẽ một câu gốc sai và một biến thể liên quan, lấy tiếp từ nhóm còn câu khi một nhóm hết. Giữ các câu chung đoạn đọc/audio thành nhóm theo thứ tự biên soạn.

topicId phải có mặt, nhận ID hoặc null. count 1–10. 201 tạo mới/200 tiếp tục lượt cùng mode/reviewKind/topicId/count đang dở, giữ snapshot và không tính lại bộ câu. Có thể cấp ít hơn count; rỗng trả NO_QUESTIONS_AVAILABLE. Câu GUIDED được giữ vai trò và hỗ trợ theo M03. Snapshot, draft/submit/result/retry và quyền file giữ hợp đồng M03.

DUE và MISTAKES vẫn được chấp nhận để phục hồi request/lượt cũ, không hiển thị trên giao diện mới. Hai giá trị này giữ thuật toán P0: REVIEW_VARIANT mới → INDEPENDENT mới → câu cũ ít/lâu gặp, cùng mục tiêu có lịch sử và đúng mức ưu tiên ôn. MISTAKES cũ là luyện mục tiêu còn cờ lỗi sai, không đồng nghĩa WRONG mới. M03 tiếp tục kiểm tra ID/state, chốt phiên bản và exposure. Lịch 1–3–7, projection M04 và bằng chứng thưởng M05 không đổi.

## Lỗi

| HTTP/code | Xử lý |
|---|---|
| 400 VALIDATION_FAILED | Sửa body/bộ lọc |
| 401 UNAUTHENTICATED/SESSION_REVOKED | Đăng nhập lại, không trộn tài khoản |
| 403 FORBIDDEN/CSRF_INVALID | Kiểm tra vai trò/CSRF |
| 409 REVIEW_SYNC_REQUIRED | Cập nhật nội dung ôn rồi thử tạo lượt lại |
| 409 NO_QUESTIONS_AVAILABLE | Không có mục tiêu/câu phù hợp; chờ mốc, đổi chủ đề hoặc bổ sung học liệu |
| 409 REQUEST_ID_REUSED | Không dùng lại UUID cho thao tác/body khác |
| 503 DATABASE_UNAVAILABLE/REVIEW_SCHEMA_UNSUPPORTED | Giữ requestId khi kết quả ghi chưa rõ, thử lại sau |

## Cấu hình prototype

- app.review.intervals-days=1,3,7 (đúng 3 khoảng, tăng dần, 1–365 ngày).
- app.review.minimum-fresh-questions=3 (1–10).
- app.review.good-percent=80 (1–100).
- app.review.minimum-good-sets=2 (ít nhất 2).
- app.review.zone=Asia/Ho_Chi_Minh.

Sai ưu tiên ngay; tự làm đúng toàn bộ phần đã trả lời và không có FIRST sai thì tăng stage, mốc sau 1/3/7 ngày. Toàn bỏ trống giữ nguyên mốc; chỉ hướng dẫn giữ cần luyện. Gợi ý tăng mức cần đủ bộ mới, không còn cần luyện và ít nhất minimum-fresh-questions câu mới ở mức kế tiếp. Đây là heuristic nội bộ chưa đo hiệu quả, không có mô hình đánh giá trình độ.

Schema máy đọc cho hai endpoint mới: [openapi-m04.json](openapi-m04.json). Nguồn câu hỏi, đọc/nghe/ảnh vẫn là [hợp đồng M02/M03](M02_M03_API.md); không thêm nguồn học liệu web mới trong M04.
