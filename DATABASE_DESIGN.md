# Database M01 — English Learning

Nguồn schema chính: source/server/src/main/resources/db/migration/. Flyway V1 và V2; Hibernate validate khi khởi động.

| Bảng | Vai trò |
|---|---|
| users | id, email duy nhất, password_hash BCrypt, full_name, role, status, security_version, created_at |
| classrooms | Dữ liệu lớp học của nền M01, teacher_id FK users, join_code và create_request_id có unique constraint |
| class_members | Khóa ghép classroom_id/student_id, FK giữ lịch sử thành viên |
| account_control | Một hàng id=1 để tuần tự hóa thay đổi quản trị |
| flyway_schema_history | Flyway tự quản lý lịch sử migration |

Role: STUDENT (người học), TEACHER (biên soạn), ADMIN (quản trị). Status: ACTIVE, LOCKED, DELETED. Security_version tăng khi đổi quyền/trạng thái, dùng để từ chối session cũ. Xóa logic không xóa hàng users hoặc FK, không giải phóng email để đăng ký lại.

Ứng dụng dùng database english_learning và volume english-learning-project_mysql_data mặc định. Không dùng volume IELTS/IoT. asset_data được chuẩn bị cho file của các module sau; M01 chưa có API upload.

Các bảng học liệu, bài làm, ôn tập và XP trong plan chưa được áp dụng. Tài liệu IELTS cũ trong docs/archive là tham khảo lịch sử, không phải schema app này.

## Schema M02–M03 đã thiết kế, chưa áp dụng

- [V3 — M02, 13 bảng](docs/database/planned/V3__learning_content.sql): học liệu có phiên bản, ngân hàng câu và file metadata; request_receipts dùng chung M03.
- [V4 — M03, 7 bảng](docs/database/planned/V4__learning_attempts.sql): lượt làm, đáp án nháp, trial, bài nộp, lịch sử đã gặp và tiến độ bài.
- [Bàn giao và quan hệ dữ liệu](docs/M02_M03_HANDOFF.md), [hợp đồng API](source/common/M02_M03_API.md).

Đây là DDL kế hoạch. Runtime hiện vẫn chỉ chạy V1/V2; khi triển khai đưa V3 vào Flyway trước V4, giữ JPA validate và không sửa migration đã chạy. Lịch ôn/XP/league thuộc module sau, chưa có trong V3/V4.
