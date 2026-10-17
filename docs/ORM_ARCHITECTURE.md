# ORM và transaction của M01

Spring Data JPA/Hibernate, MySQL 8.4 và Flyway. Hibernate ddl-auto=validate, open-in-view=false. Không dùng JdbcTemplate hoặc SQL tự viết trong service/repository.

Controller chỉ nhận DTO và chuyển service interface; implementation ở service.impl. Các service gọi repository interface; adapter ở repository.impl dùng các interface Spring Data JPA. DTO UserRecord dùng nội bộ, UserView không chứa mật khẩu/phiên bản quyền.

## Schema

V1__accounts_and_classrooms.sql được giữ từ nền tảng M01, tạo users/classrooms/class_members. V2__account_administration.sql mở rộng role ADMIN, status ACTIVE/LOCKED/DELETED, security_version và account_control.

account_control có một hàng duy nhất. Thao tác admin khóa hàng này trước, rồi khóa actor/target để kiểm tra lại quyền và bảo vệ admin cuối cùng khi có request đồng thời. Thay đổi tài khoản dùng khóa bi quan; không dựa vào trạng thái từng đọc trước transaction.

ClassroomService gọi AccountGuard.lockCurrent đầu mỗi transaction ghi, trước khóa tài nguyên. Module sau phải dùng cùng quy tắc. Request đọc có AccountGuardFilter kiểm tra trạng thái và security_version.

AccountAccessChanged xử lý AFTER_COMMIT để thu hồi session/socket sau khi DB đã lưu. DB là nguồn kiểm tra quyền chính, registry RAM là cách đóng kết nối ngay trên backend hiện tại.

## Giới hạn

Một backend, session và kết nối trong RAM. Chưa có session phân tán hoặc Redis. Lớp học thuộc nền cũ được giữ ở API; app mới không bắt buộc tham gia lớp. Không chuyển dữ liệu database IELTS vào DB app mới.
