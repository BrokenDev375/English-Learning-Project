# Database — English Learning (M01, M02 và M03)

Cập nhật M03 ngày 19/10/2026: V4 đã được dùng cho tạo/tiếp tục lượt, nháp, hint/check, nộp/kết quả/chữa và tiến độ. Xem [tiến độ M03](docs/M03_PROGRESS.md), [nghiệm thu toàn module](statics/results/module-3-validation.md). Backend build mới tự áp dụng migration còn thiếu khi khởi động; giữ volume hiện có.

Nguồn schema chính: `source/server/src/main/resources/db/migration/`. Runtime có Flyway V1–V4; Hibernate validate khi khởi động. Không sửa migration đã áp dụng hoặc dùng `ddl-auto=create/update` để chữa lệch schema.

| Bảng | Vai trò |
|---|---|
| users | id, email duy nhất, password_hash BCrypt, full_name, role, status, security_version, created_at |
| classrooms | Dữ liệu lớp học của nền M01, teacher_id FK users, join_code và create_request_id có unique constraint |
| class_members | Khóa ghép classroom_id/student_id, FK giữ lịch sử thành viên |
| account_control | Một hàng id=1 để tuần tự hóa thay đổi quản trị |
| flyway_schema_history | Flyway tự quản lý lịch sử migration |

Role: STUDENT (người học), TEACHER (biên soạn), ADMIN (quản trị). Status: ACTIVE, LOCKED, DELETED. Security_version tăng khi đổi quyền/trạng thái, dùng để từ chối session cũ. Xóa logic không xóa hàng users hoặc FK, không giải phóng email để đăng ký lại.

Ứng dụng dùng database english_learning và volume english-learning-project_mysql_data mặc định. Không dùng volume IELTS/IoT. M02 lưu byte ảnh/audio ở asset_data; bảng assets chỉ giữ metadata, storage_key nội bộ, SHA-256 và quyền sở hữu.

V3 tạo đủ 13 bảng học liệu/receipt đã thiết kế và đã được dùng qua API và seed ở bước 1–4. Seed chuẩn có 3 Topic, 3 Unit, 6 mục tiêu, 6 Lesson root, 90 Question root, 13 Material root và 7 Asset; dữ liệu người dùng có thể làm tổng số hàng lớn hơn. Nâng bài mẫu cũ bằng LessonVersion mới, giữ bản công bố cũ và ID/origin của các câu cũ đủ điều kiện. Material/Question/Lesson root giữ con trỏ nháp/công bố và revision; bản công bố bất biến, bản chỉnh sửa tạo version mới. Question lưu origin_key CHAR(36), sao chép giữ origin. JSON content/answer được parse theo từng dạng trước khi lưu. LessonVersion giữ instructions_json và hai tập tham chiếu lesson_objectives/lesson_questions theo Version ID; nhóm/thứ tự nằm ở slot. Bỏ nháp không xóa lịch sử công bố/file hoặc các câu/tài liệu độc lập. Bài làm đã dùng V4; lịch ôn và XP thuộc module sau. Tài liệu IELTS cũ trong docs/archive là tham khảo lịch sử, không phải schema app này.

## Dữ liệu học M03 — V4

| Bảng | Vai trò và ràng buộc chính |
|---|---|
| attempts | Owner, mode/state, LessonVersion đã chốt, selection/playerState JSON và revision nghiệp vụ |
| attempt_questions | Version/origin/slot/permutation, dấu seen trước lượt và thời điểm mở hỗ trợ; origin/vị trí không trùng trong lượt |
| attempt_answers | Một đáp án hiện hành cho mỗi item, lưu nháp hoặc final; nháp không tạo trial |
| answer_trials | FIRST/GUIDED_RETRY/CORRECTION bất biến, thứ tự trial duy nhất theo item |
| submissions | Một hàng duy nhất theo Attempt, result_json chứa factsSchemaVersion=1 và Submission ID thật |
| question_exposures | Khóa ghép user/origin; tăng khi tạo lượt mới, không tăng khi resume/GET/replay |
| lesson_progress | Khóa ghép user/Lesson root, STARTED/COMPLETED; NOT_STARTED được API tổng hợp |

Tạo lượt chốt Version ID, permutation và seen trước khi cập nhật exposure. Lưu nháp chỉ ghi answers/playerState/revision/receipt. Submit ghi final, FIRST còn thiếu, Submission, state/revision, progress và receipt cùng transaction. FIRST/Result đã nộp không đổi khi chữa; CORRECTION chỉ thêm trial và tăng revision. COMPLETED không bị hạ bởi học lại dở.

`revision` là số nghiệp vụ dưới khóa, không dùng JPA `@Version`. Repository INSERT Submission với Result nội bộ submissionId=0, gắn ID thật vào JSON rồi flush trước commit. submittedAt trong Result lưu chuỗi ISO để giữ độ chính xác. Receipt theo actor/requestId và payload hash, replay trước kiểm tra state/revision; không ghi receipt tách transaction.

Nháp và kết quả đã commit nằm trong MySQL; bytes ảnh/audio nằm trong asset_data. Session ở RAM của một backend có thể mất sau restart. [Script nghiệm thu bền vững](source/client/scripts/verify-m03-persistence.mjs) kiểm tra đăng nhập lại, restart/recreate giữ volume, seed và replay trong project riêng; xem kết quả thực tế trong biên bản M03.

## Schema M02–M03

- [V3 — M02, 13 bảng](docs/database/planned/V3__learning_content.sql): học liệu có phiên bản, ngân hàng câu và file metadata; request_receipts dùng chung M03.
- [V4 — M03, 7 bảng](docs/database/planned/V4__learning_attempts.sql): lượt làm, đáp án nháp, trial, bài nộp, lịch sử đã gặp và tiến độ bài.
- [Bàn giao và quan hệ dữ liệu](docs/M02_M03_HANDOFF.md), [hợp đồng API](source/common/M02_M03_API.md).

V3 đã được sao chép vào Flyway và áp dụng; file trong planned giữ hợp đồng gốc để đối chiếu. V4 đã có trong Flyway ở bước 1 M03; file planned giữ nguyên để đối chiếu. Giữ JPA validate và không sửa migration đã chạy. Lịch ôn/XP/league thuộc module sau, chưa có trong V3/V4. Xem [tiến độ M02](docs/M02_PROGRESS.md) và [M03](docs/M03_PROGRESS.md).
