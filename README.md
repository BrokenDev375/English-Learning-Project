# English Learning

Ứng dụng học và ôn tập tiếng Anh theo mô hình client–server. M01 đã có đăng nhập, đăng ký người học, quản trị tài khoản và nền session/WebSocket. Các bài học, ôn tập, XP và Giải đấu tuần thuộc các module tiếp theo; giao diện không hiển thị thành tích giả.

## Công nghệ và cấu trúc

React 19 + TypeScript + Vite; Java 17 + Spring Boot 3.5.16; Spring Security; Spring Data JPA/Hibernate; MySQL 8.4; Flyway; Docker Compose. Backend dùng controller → service interface → service.impl → repository interface/implementation → Spring Data JPA.

- source/client: giao diện, gọi HTTP và kết nối WebSocket.
- source/server: backend và migration.
- source/common/API.md: hợp đồng API M01.
- docs: kiến trúc, kiểm tra và ghi chú triển khai.
- statics: sơ đồ, kết quả kiểm thử thực tế.
- report: nơi chuẩn bị báo cáo.
- INSTRUCTION.md, TOPICS.md, SUBMISSION.md: tài liệu môn học.
- PLAN_ENGLISH_LEARNING_SYSTEM.md: kế hoạch 7 module và solo chờ xác nhận.

## Chạy bằng Docker trên Windows

Cần Docker Desktop đang chạy. Tại thư mục dự án:

~~~powershell
.\start.cmd
~~~

Hoặc chạy scripts/init-env.ps1 rồi docker compose up -d --build --wait. Giao diện: http://localhost:5173; backend: http://localhost:8080/api/health. Dùng đúng cổng 5173/8080. Nếu dự án cũ đang dùng các cổng này, dừng server/client của dự án cũ trước khi chạy; không cần xóa volume.

Đăng nhập admin: admin@english.local. Mật khẩu được tạo ngẫu nhiên trong dòng BOOTSTRAP_ADMIN_PASSWORD của file .env. Không commit .env. Với DEMO_ENABLED=true, tài khoản demo người học: student@english.local / Student@12345; người biên soạn: teacher@english.local / Teacher@12345. Chỉ dùng những tài khoản demo này cho môi trường local.

Bootstrap chỉ tạo admin khi email chưa tồn tại; khởi động lại không tự đổi mật khẩu, cấp lại quyền hoặc mở khóa tài khoản đã có.

Linux/macOS: sh scripts/start.sh (cần openssl). Khi dùng HTTPS/cổng khác phải cập nhật SESSION_COOKIE_SECURE và WS_ALLOWED_ORIGINS.

## M01 và quyền

Đăng ký công khai luôn tạo STUDENT. TEACHER là người biên soạn; ADMIN quản lý người dùng. Admin có thể tìm theo tên/email, lọc vai trò/trạng thái, phân trang, đổi quyền, khóa/mở khóa và xóa logic.

Xóa logic giữ hàng dữ liệu cùng lịch sử; không được đăng nhập, không mở lại, email chưa được tái sử dụng. Admin không tự khóa, xóa hoặc đổi quyền mình; hệ thống giữ ít nhất một admin hoạt động. Khóa/xóa/đổi quyền thu hồi tất cả session và đóng WebSocket của tài khoản sau commit. Mở khóa không phục hồi session cũ.

Session cookie HttpOnly + SameSite=Lax, CSRF cho request thay đổi dữ liệu, BCrypt 12. Mỗi request có kiểm tra trạng thái/phiên bản quyền trong DB; các thao tác ghi của M01 khóa và kiểm tra lại người thực hiện trong transaction. Các module viết tiếp phải gọi AccountGuard.lockCurrent trước khi ghi. Đây không phải tuyên bố chống mọi hình thức gian lận.

API lớp học từ nền tảng cũ vẫn được giữ; lớp học không bắt buộc cho app mới. Màn hình chính M01 tập trung tài khoản và quản trị.

## Dữ liệu bền vững

Compose project english-learning-project dùng hai volume riêng: mysql_data (MySQL) và asset_data (file). Không dùng volume IELTS hoặc IoT. docker compose down rồi up giữ volume; down -v/xóa volume/gỡ Docker có thể làm mất dữ liệu. Sao lưu MySQL và file trước khi xóa.

Flyway V1 tạo nền tài khoản/lớp; V2 bổ sung ADMIN, trạng thái và phiên bản thu hồi quyền. Hibernate chỉ validate schema; không dùng ddl-auto=create/update.

## Phát triển và kiểm thử

~~~powershell
cd source\server
.\mvnw.cmd test
~~~

Backend tests dùng Testcontainers MySQL 8.4 với DB dùng riêng cho test; không xóa DB phát triển. Java 17 trở lên. Nếu wrapper cần tải Maven/dependency, cần kết nối Internet.

~~~powershell
cd source\client
npm ci
npm run build
npx playwright install chromium
npm run test:e2e
~~~

Kiểm thử giao diện cần app đang chạy. E2E tạo tài khoản có tiền tố e2e; thông tin admin đọc từ .env hoặc biến môi trường E2E_ADMIN_EMAIL/E2E_ADMIN_PASSWORD. Có thể chạy frontend bằng npm run dev, backend bằng .\mvnw.cmd spring-boot:run; mở MySQL cho IDE qua docker compose -f compose.yml -f compose.dev.yml up -d mysql rồi cấp DB_URL/DB_USERNAME/DB_PASSWORD.

Xem statics/results/module-1-validation.md để biết những kiểm tra thực tế đã chạy. Chưa có đo tải hoặc kết luận hiệu quả học tập.

## Mạng, giới hạn và contribution

HTTP truyền auth/API; /ws/events có xác thực cookie, giới hạn origin, HELLO/HEARTBEAT và ACK/ERROR để kiểm tra phiên và thu hồi kết nối. Presence hoàn chỉnh, đồng bộ tiến độ khi mất mạng và sự kiện Giải đấu tuần thuộc M06; chưa hoàn thành ở M01.

Session/kết nối nằm trong RAM của một backend, restart cần đăng nhập lại; account/role/status bền vững trong DB. Chưa triển khai rate limiting đăng nhập, khôi phục mật khẩu qua email hoặc nhiều backend. Các hướng contribution của toàn dự án vẫn cần thực nghiệm theo plan.

Nguồn: [Spring Security session management](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html), [Spring WebSocket](https://docs.spring.io/spring-framework/reference/6.2/web/websocket/server.html), hướng dẫn môn học tại INSTRUCTION.md.
