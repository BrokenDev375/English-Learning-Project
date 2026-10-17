# Cấu trúc backend M01

Package vn.edu.ptit.english:
- controller: AuthController, AdminUserController, ClassroomController, HealthController.
- service và service.impl: hợp đồng nghiệp vụ và implementation riêng.
- repository và repository.impl: hợp đồng truy cập dữ liệu, adapter dùng Spring Data JPA.
- entity/enums/dto: ánh xạ ORM, trạng thái, dữ liệu request/response.
- security: CSRF/session, kiểm tra tài khoản, khóa ghi, thu hồi kết nối.
- socket/config: đăng ký WebSocket, giới hạn origin, xác thực phiên.
- bootstrap: admin đầu tiên và tài khoản demo có cấu hình.

Admin không lấy passwordHash qua API. Mọi endpoint admin kiểm tra ROLE_ADMIN và trạng thái hiện tại trong DB. Các thay đổi quyền/trạng thái lấy account_control trước, rồi khóa người thực hiện và tài khoản đích. Các thao tác ghi nghiệp vụ lấy khóa người thực hiện trước khóa tài nguyên; module sau cần giữ thứ tự này.

AccountGuardFilter xác nhận phiên bản quyền cho request đã xác thực. AccountGuard.lockCurrent chạy trong transaction ghi để xử lý request đang chạy khi admin khóa tài khoản. AccountAccessChanged chỉ thu hồi session/socket sau commit; security_version trong DB ngăn session cũ hoạt động kể cả khi sự kiện RAM không còn.

Đăng nhập lưu SecurityContext qua HttpSessionSecurityContextRepository và đổi session id. WebSocket handshake kiểm tra quyền, origin, phiên HTTP; mở kết nối xong kiểm tra lại để tránh race với thao tác thu hồi.
