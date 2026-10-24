# Database — English Learning (M01–M04)

Cập nhật M03 ngày 19/10/2026: V4 đã được dùng cho tạo/tiếp tục lượt, nháp, hint/check, nộp/kết quả/chữa và tiến độ. Xem [tiến độ M03](docs/M03_PROGRESS.md), [nghiệm thu toàn module](statics/results/module-3-validation.md). Backend build mới tự áp dụng migration còn thiếu khi khởi động; giữ volume hiện có.

Nguồn schema chính: `source/server/src/main/resources/db/migration/`. Runtime có Flyway V1–V5; Hibernate validate khi khởi động. Không sửa migration đã áp dụng hoặc dùng `ddl-auto=create/update` để chữa lệch schema.

| Bảng | Vai trò |
|---|---|
| users | id, email duy nhất, password_hash BCrypt, full_name, role, status, security_version, created_at |
| classrooms | Dữ liệu lớp học của nền M01, teacher_id FK users, join_code và create_request_id có unique constraint |
| class_members | Khóa ghép classroom_id/student_id, FK giữ lịch sử thành viên |
| account_control | Một hàng id=1 để tuần tự hóa thay đổi quản trị |
| flyway_schema_history | Flyway tự quản lý lịch sử migration |

Role: STUDENT (người học), TEACHER (biên soạn), ADMIN (quản trị). Status: ACTIVE, LOCKED, DELETED. Security_version tăng khi đổi quyền/trạng thái, dùng để từ chối session cũ. Xóa logic không xóa hàng users hoặc FK, không giải phóng email để đăng ký lại.

Ứng dụng dùng database english_learning và volume english-learning-project_mysql_data mặc định. Không dùng volume IELTS/IoT. M02 lưu byte ảnh/audio ở asset_data; bảng assets chỉ giữ metadata, storage_key nội bộ, SHA-256 và quyền sở hữu.

V3 tạo đủ 13 bảng học liệu/receipt đã thiết kế và đã được dùng qua API và seed ở bước 1–4. Seed chuẩn có 3 Topic, 3 Unit, 6 mục tiêu, 6 Lesson root, 90 Question root, 13 Material root và 7 Asset; dữ liệu người dùng có thể làm tổng số hàng lớn hơn. Nâng bài mẫu cũ bằng LessonVersion mới, giữ bản công bố cũ và ID/origin của các câu cũ đủ điều kiện. Material/Question/Lesson root giữ con trỏ nháp/công bố và revision; bản công bố bất biến, bản chỉnh sửa tạo version mới. Question lưu origin_key CHAR(36), sao chép giữ origin. JSON content/answer được parse theo từng dạng trước khi lưu. LessonVersion giữ instructions_json và hai tập tham chiếu lesson_objectives/lesson_questions theo Version ID; nhóm/thứ tự nằm ở slot. Bỏ nháp không xóa lịch sử công bố/file hoặc các câu/tài liệu độc lập. Bài làm dùng V4, lịch ôn dùng V5; XP thuộc M05. Tài liệu IELTS cũ trong docs/archive là tham khảo lịch sử, không phải schema app này.

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

V3 đã được sao chép vào Flyway và áp dụng; file trong planned giữ hợp đồng gốc để đối chiếu. V4 đã có trong Flyway ở bước 1 M03; file planned giữ nguyên để đối chiếu. Giữ JPA validate và không sửa migration đã chạy. M04 thêm V5; XP/league thuộc M05. Xem [tiến độ M02](docs/M02_PROGRESS.md), [M03](docs/M03_PROGRESS.md) và [M04](docs/M04_PROGRESS.md).

## Ôn tập cá nhân M04 — V5

Schema thực thi: [V5__personal_review.sql](source/server/src/main/resources/db/migration/V5__personal_review.sql).

| Bảng | Khóa và trách nhiệm |
|---|---|
| review_objective_progress | PK user_id + objective_id; số liệu FIRST, cờ lỗi/cần luyện, độ khó ưu tiên, stage, ngày thành công gần nhất và next_review_at |
| review_difficulty_evidence | PK user_id + objective_id + difficulty; số bộ/câu mới, số đúng và chuỗi bộ tốt đủ câu |
| review_processed_submissions | PK submission_id, kèm user_id/processed_at; ledger chống xử lý cùng Submission hai lần |

Kết quả gốc vẫn ở submissions.result_json của V4. POST sync đọc tối đa 100 kết quả chưa có ledger theo user, tổng hợp mỗi mục tiêu một lần, ghi cả ba bảng và request_receipts cùng transaction dưới actor lock. Học viên cũ được backfill khi mở Ôn tập cá nhân; GET chỉ đọc. Restart không mất ledger/lịch; gửi lại không tăng số lượt.

Mốc dựa vào submittedAt của kết quả gốc, không lấy thời điểm sync để làm mới lịch. Stage tăng tối đa một lần theo ngày Việt Nam; mốc lưu UTC. Gợi ý mức không lấy câu từng gặp/GUIDED/CORRECTION làm bộ câu mới. Xem [quy tắc/API](source/common/M04_API.md).

## M07 — phép chiếu chỉ đọc

Thống kê TEACHER (24/10) không thêm bảng: quyền theo units.owner_id, nguồn LEARN qua attempts.lesson_version_id → lesson_versions.lesson_id → lessons.unit_id. Không nối membership câu hiện tại để suy Unit; không lẫn các lượt dùng chung câu ở Unit khác hoặc PRACTICE/REVIEW không có Unit nguồn. Kết quả dùng answer_trials FIRST đã có Submission; câu/phiên bản cũ vẫn được giữ. Hoàn thành đếm cặp user/lesson trong lesson_progress, hints đếm AttemptQuestion mở gợi ý, mọi dữ liệu trả ra là tổng hợp.

M07 không có bảng hoặc migration riêng, dùng V1–V5 hiện có. StatisticsRepository dùng JPQL aggregate trên Attempt/Submission, AnswerTrial/AttemptQuestion/QuestionVersion; LessonProgress và ReviewProgress. FIRST chỉ vào thống kê sau khi có Submission, lấy đúng metadata phiên bản; GUIDED_RETRY/CORRECTION tách riêng. Không đọc toàn bộ JSON kết quả chứa đáp án để trả cho UI. GET không ghi ledger/lịch ôn. XP/league của M05 chưa được giả định tên bảng; tích hợp qua LearnerRewardsReader. Ước lượng kết nối lấy từ RAM LiveConnections, không ghi thành dữ liệu online bền vững. Xem [API M07](source/common/M07_API.md).
