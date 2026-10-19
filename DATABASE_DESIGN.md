# Database — English Learning (M01 và M02 hoàn chỉnh)

Cập nhật M03 bước 1–3 (19/10/2026): đã có V4/model/repository và API tạo/tiếp tục, snapshot, danh sách, media, lưu nháp. Attempts/questions/answers/exposures và STARTED progress được ghi khi tạo; draft không ghi trial/submission. Nộp/chấm và API đọc tiến độ thuộc bước 4. Kiểm thử dùng MySQL riêng, không nâng DB demo; backend build mới sẽ tự áp dụng V4 khi khởi động. Xem [tiến độ M03](docs/M03_PROGRESS.md).

Nguồn schema chính: source/server/src/main/resources/db/migration/. Runtime đã có Flyway V1, V2 và V3; Hibernate validate khi khởi động. M02 bước 2–4 dùng tiếp V3, không thêm hoặc sửa migration.

| Bảng | Vai trò |
|---|---|
| users | id, email duy nhất, password_hash BCrypt, full_name, role, status, security_version, created_at |
| classrooms | Dữ liệu lớp học của nền M01, teacher_id FK users, join_code và create_request_id có unique constraint |
| class_members | Khóa ghép classroom_id/student_id, FK giữ lịch sử thành viên |
| account_control | Một hàng id=1 để tuần tự hóa thay đổi quản trị |
| flyway_schema_history | Flyway tự quản lý lịch sử migration |

Role: STUDENT (người học), TEACHER (biên soạn), ADMIN (quản trị). Status: ACTIVE, LOCKED, DELETED. Security_version tăng khi đổi quyền/trạng thái, dùng để từ chối session cũ. Xóa logic không xóa hàng users hoặc FK, không giải phóng email để đăng ký lại.

Ứng dụng dùng database english_learning và volume english-learning-project_mysql_data mặc định. Không dùng volume IELTS/IoT. M02 lưu byte ảnh/audio ở asset_data; bảng assets chỉ giữ metadata, storage_key nội bộ, SHA-256 và quyền sở hữu.

V3 tạo đủ 13 bảng học liệu/receipt đã thiết kế và đã được dùng qua API và seed ở bước 1–4. Seed chuẩn có 3 Topic, 3 Unit, 6 mục tiêu, 6 Lesson root, 90 Question root, 13 Material root và 7 Asset; dữ liệu người dùng có thể làm tổng số hàng lớn hơn. Nâng bài mẫu cũ bằng LessonVersion mới, giữ bản công bố cũ và ID/origin của các câu cũ đủ điều kiện. Material/Question/Lesson root giữ con trỏ nháp/công bố và revision; bản công bố bất biến, bản chỉnh sửa tạo version mới. Question lưu origin_key CHAR(36), sao chép giữ origin. JSON content/answer được parse theo từng dạng trước khi lưu. LessonVersion giữ instructions_json và hai tập tham chiếu lesson_objectives/lesson_questions theo Version ID; nhóm/thứ tự nằm ở slot. Bỏ nháp không xóa lịch sử công bố/file hoặc các câu/tài liệu độc lập. Bài làm V4, ôn tập và XP chưa áp dụng. Tài liệu IELTS cũ trong docs/archive là tham khảo lịch sử, không phải schema app này.

## Schema M02–M03

- [V3 — M02, 13 bảng](docs/database/planned/V3__learning_content.sql): học liệu có phiên bản, ngân hàng câu và file metadata; request_receipts dùng chung M03.
- [V4 — M03, 7 bảng](docs/database/planned/V4__learning_attempts.sql): lượt làm, đáp án nháp, trial, bài nộp, lịch sử đã gặp và tiến độ bài.
- [Bàn giao và quan hệ dữ liệu](docs/M02_M03_HANDOFF.md), [hợp đồng API](source/common/M02_M03_API.md).

V3 đã được sao chép vào Flyway và áp dụng; file trong planned giữ hợp đồng gốc để đối chiếu. V4 đã có trong Flyway ở bước 1 M03; file planned giữ nguyên để đối chiếu. Giữ JPA validate và không sửa migration đã chạy. Lịch ôn/XP/league thuộc module sau, chưa có trong V3/V4. Xem [tiến độ M02](docs/M02_PROGRESS.md) và [M03](docs/M03_PROGRESS.md).
