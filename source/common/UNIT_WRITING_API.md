# Unit Writing API — V15

Session cookie/CSRF, AccountGuard và request_receipts dùng nền hiện có. Mọi mutation cần requestId UUID chữ thường. Các trường phải đúng và đủ; unknown/null/sai kiểu bị từ chối. ID tài nguyên dương trong miền số nguyên an toàn JavaScript. GET no-store/private, không mutation.

| Method | Route | Quyền / kết quả |
|---|---|---|
| GET | /api/author/units/{unit}/writing | TEACHER được phân công hoặc ADMIN; Task, revision=0 khi chưa có |
| PUT | /api/author/units/{unit}/writing/draft | requestId, baseRevision, definition; 200 Task |
| POST | /api/author/units/{unit}/writing/publish | requestId, baseRevision; 200 Task với phiên bản công bố mới |
| POST | /api/author/units/{unit}/writing/retire | requestId, baseRevision; 200 Task, bỏ con trỏ công bố |
| GET | /api/me/units/{unit}/writing | STUDENT; Learner, chỉ bài của principal |
| POST | /api/me/units/{unit}/writing/start | requestId; chốt phiên bản nếu đủ điều kiện; 201 mới/200 tiếp tục |
| POST | /api/me/units/{unit}/writing/draft | requestId, baseRevision, text; 200 Learner |
| POST | /api/me/units/{unit}/writing/submit | requestId, baseRevision, text đầy đủ; 200 Learner |
| GET | /api/author/writing-submissions | unitId bắt buộc; state=PENDING/GRADED/ALL (mặc định PENDING), page=0, size=20 (1–50); CatalogPage<QueueItem> |
| GET | /api/author/writing-submissions/{id} | Giáo viên hiện phụ trách hoặc ADMIN; Grading |
| POST | /api/author/writing-submissions/{id}/grade | requestId, baseRevision của Work, marks, comment, allowRevision; 200 Grading |

Definition:

```json
{
  "title": "My daily routine",
  "prompt": "Write about your daily activities.",
  "minWords": 80,
  "maxWords": 120,
  "criteria": [
    {"id": "content", "text": "Describe three activities", "points": 6},
    {"id": "grammar", "text": "Use the present simple", "points": 4}
  ]
}
```

title 1–160 ký tự có nội dung; prompt tối đa 12.000; số từ gợi ý 0≤minWords≤maxWords≤5.000, maxWords≥1. criteria 1–20 mục; id duy nhất gồm chữ/số/_/-, dài 1–40; text có nội dung, tối đa 1.000; points nguyên 1–10 và tổng=10.

Chấm:

```json
{
  "requestId": "f2f1d167-d2e4-45b8-b6ba-6fead7739106",
  "baseRevision": 2,
  "marks": [
    {"criterionId": "content", "met": true, "comment": "Clear activities."},
    {"criterionId": "grammar", "met": false, "comment": "Check verb forms."}
  ],
  "comment": "Please revise the grammar.",
  "allowRevision": true
}
```

Marks phải đúng và đủ bộ tiêu chí đã chốt, không trùng ID; met boolean bắt buộc; mỗi comment tối đa 1.000, nhận xét chung 4.000; allowRevision=true cần nhận xét có nội dung. Grade trả earnedPoints/totalPoints=10 do server tính. Không nhận studentId, teacherId, score hoặc versionId từ client để quyết định quyền/bản đề.

Task={unitId,revision,draft,published}; Version={id,definition,createdAt}; Work={id,versionId,revision,state,text,latestSubmissionId,updatedAt}; Learner={unitId,available,unlocked,blockReason,version,work,history}; Submission={id,workId,versionId,text,submittedAt,grade}; Grade={id,earnedPoints,totalPoints,marks,comment,allowRevision,gradedAt}; Grading={unitId,studentName,workRevision,canGrade,version,submission}. Không serialize entity. History giảm dần theo ID; bản nộp và Grade không đổi khi sửa.

RoundProgress thêm writing tùy chọn={required,submitted,state}; không có task/work thì không thêm trường. submitted=true khi đã có ít nhất một lần nộp, kể cả sau RETURNED. State tổng vòng là COMPLETED khi đủ Lesson và writing không bắt buộc hoặc đã nộp. GET progress không tạo lesson_progress hoặc thay Submission M03.

403 ACCESS_DENIED khi sai vai trò. 404 NOT_FOUND khi không có quyền Unit/bài riêng hoặc không có đề để bắt đầu. 409 WRITING_LOCKED nếu chưa đủ điều kiện; REVISION_CONFLICT nếu revision cũ; WRITING_READ_ONLY khi sửa/nộp bài đang chờ hoặc đã chấm; WRITING_ALREADY_GRADED khi chấm bản cũ; WRITING_DRAFT_REQUIRED nếu chưa lưu đề; REQUEST_ID_REUSED như nền M02. Replay trả cùng status/body và Idempotent-Replayed=true, không tạo lại dữ liệu.
