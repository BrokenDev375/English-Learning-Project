# Đối chiếu hợp đồng M03 — bước 0

Ngày: 19/10/2026. Nền mã nguồn: `a023088`, nhánh `feature/m03-learning-attempts`, đã chứa cập nhật audio/ownership M02 (`65555ff`). Không sửa hợp đồng 1.0.0 hoặc schema V1–V3 trong bước này.

| Luồng | Schema OpenAPI | Yêu cầu khi triển khai |
|---|---|---|
| Tạo/tiếp tục | AttemptCreate | oneOf theo LEARN/PRACTICE/REVIEW; không nhận userId hoặc baseRevision; 201/200 chọn trong transaction |
| Lưu/nộp | AttemptMutation, AnswerEntry, PlayerState | Full snapshot, required nullable, revision của Attempt; kiểm tra thuộc lượt ở service |
| Hint | Hint, HintResult | Chỉ requestId/baseRevision; chỉ GUIDED, mở lại không ghi hỗ trợ lần hai |
| Check/chữa | Check, Answer, Feedback | Đáp án đủ khi check/retry; dùng AttemptQuestion ID; feedback không có transcript |
| Snapshot | AttemptSnapshot, AttemptItem, PublicQuestion, PublicMaterial | Không keys/explanation/transcript/storageKey; không serialize DTO nội bộ PublishedContentReader |
| ACK | DraftAck | Revision và số câu đã trả lời sau commit, không tạo trial |
| Kết quả | Result, ResultItem | factsSchemaVersion=1; FIRST/final tách biệt; kết quả bất biến theo Submission |
| Tiến độ | LessonProgress, LessonProgressPage | NOT_STARTED tổng hợp; COMPLETED không hạ vì học lại dở; updateAvailable theo bản công bố |

Các quy tắc chấm, quyền, transaction và state lấy từ [API mục 1–3, 6–8](../source/common/M02_M03_API.md) và [bàn giao mục 4–7](M02_M03_HANDOFF.md). DTO parser bước 1 kiểm tra hình dạng, enum, giới hạn và required/null; quyền sở hữu, full bộ item, completeness so với câu, revision và trạng thái thuộc service ở các bước sau.

Fixture tại `source/server/src/test/resources/contracts/m03-fixtures.json`: 16 mẫu hợp lệ (tái sử dụng ví dụ hợp đồng và bổ sung PRACTICE/REVIEW/hint/check/progress), 5 mẫu âm cho field giả mạo, thiếu revision, lộ answerKey/transcript và sai factsSchemaVersion. Các ví dụ là độc lập, không phải fixture SQL có FK liên kết sẵn.

Kiểm chứng bằng JSON Schema Draft 2020-12, bật format checker:

```powershell
python -m venv .test-state/schema-venv
.\.test-state\schema-venv\Scripts\python.exe -m pip install jsonschema==4.26.0
.\.test-state\schema-venv\Scripts\python.exe scripts/verify-m03-contract.py
```

Đã chạy: 21/21 ca cho kết quả mong đợi. Đây là kiểm tra fixture với schema, không chứng minh API Attempt runtime đã có. Backend/frontend baseline và test receipt ghi riêng trong [biên bản bước 0](../statics/results/module-3-step0-validation.md).

`RequestReceiptService` thêm overload nhận `Supplier<Reply>` với status/body do action chọn dưới actor lock. Overload status cố định của M02 giữ nguyên cách gọi và dùng chung logic. Receipt replay trước action/revision; dữ liệu và status lưu cùng transaction. Không có endpoint Attempt mới trong bước 0.
