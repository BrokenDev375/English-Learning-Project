# Kiểm tra module 1 sau khi đổi cấu trúc backend

Ngày 13/10/2026. 29 class Java được tổ chức theo controller/service/repository/entity/dto, cùng enums/config/security/exception/bootstrap.

- Maven test trên Java 21, target 17: PASS, 12/12 test, không fail/error/skip.
- MySQL 8.4.9/Testcontainers riêng, chỉ Flyway V1: PASS.
- Spring tìm được 3 interface JpaRepository; Hibernate validate mapping và JPQL projection sang dto.ClassroomRecord/MemberView: PASS.
- Kiểm tra đăng nhập/đăng ký, BCrypt, CSRF, phân quyền lớp, membership, request trùng và đồng thời: PASS qua bộ ModuleOneApiTest.
- Các cổng mặc định: frontend 5173, API 8080, MySQL IDE 3306.

Chi tiết cấu trúc: [BACKEND_STRUCTURE.md](../../docs/BACKEND_STRUCTURE.md). Kết quả giao diện và persistence của module 1 trước lần đổi package giữ tại module-1-validation.md.
