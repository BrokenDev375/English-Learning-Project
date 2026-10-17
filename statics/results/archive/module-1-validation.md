# Kiểm tra module 1 — 13/10/2026 — bản ORM

Bản ORM dùng Spring Data JPA/Hibernate 6.6.53.Final do Spring Boot 3.5.16 quản lí dependency. 12 API test và 3 browser test qua trên MySQL 8.4.9 riêng; Hibernate validate mapping, Flyway chạy V1. Đã kiểm tra giữ dữ liệu qua recreate server/client. Đây là kiểm tra chức năng, chưa phải benchmark hoặc đánh giá hiệu quả học tập.

| Kiểm tra | Kết quả |
|---|---|
| Build backend Java target 17 | PASS, Maven package |
| Maven Wrapper | PASS, tự tải/chạy Maven 3.9.11 |
| API integration trên MySQL 8.4 riêng của Testcontainers | 12 test, 0 failure, 0 error, 0 skipped |
| Build frontend trên Windows | PASS, TypeScript + Vite 8.3.4 |
| Build server/client bằng Docker | PASS; server chạy JRE 17 |
| Docker Compose chờ database/server healthcheck | PASS; cả ba service chạy |
| Browser E2E trên backend ORM | 3 test passed |
| Refresh trình duyệt giữ membership | PASS trong E2E |
| Tạo lại container server/client rồi đăng nhập lại | PASS, tài khoản/lớp/membership đã tạo trước đó vẫn còn |
| Xem screenshot desktop/login/mobile | Đã kiểm tra hình thật, mobile 390px không tràn ngang |

## Nội dung 12 API tests

1. Chưa đăng nhập/sai mật khẩu nhận JSON error phù hợp.
2. Đăng ký chuẩn hóa email, băm mật khẩu và tạo STUDENT.
3. Từ chối role giả, mật khẩu quá ngắn hoặc quá 72 byte UTF-8.
4. Chặn mutation thiếu CSRF token.
5. Học viên không tạo lớp; giảng viên chỉ thấy lớp sở hữu.
6. Join lại không trùng; một học viên tham gia nhiều lớp.
7. Chặn giảng viên khác xem/sửa/xóa thành viên lớp; học viên không đọc roster.
8. Sửa lớp, xóa và rời lớp đúng phạm vi; DELETE gửi lại vẫn hợp lệ.
9. requestId tạo lớp được phát lại; nội dung khác cùng ID trả conflict.
10. 4 request tạo trùng và 4 request join trùng đồng thời vẫn chỉ có một dòng tương ứng.
11. Mã lớp/ID sai trả 400 hoặc 404 thay vì làm server ngừng.
12. Logout làm session cũ mất hiệu lực.

Browser tests: giảng viên tạo lớp → học viên mới đăng ký/đăng nhập/join → refresh → giảng viên xem/xóa thành viên → học viên tải lại không còn lớp; giao diện mobile; sai mật khẩu có thông báo và giữ email.

## Cách chạy lại

Xem README.md. Kiểm tra persistence:

    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-persistence.ps1 -Phase prepare
    docker compose up -d --force-recreate --wait
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-persistence.ps1 -Phase verify

Script tạo lớp và membership demo, lưu mã nguồn kiểm tra trong .test-state rồi xác nhận sau khi khởi động lại. Chỉ chạy với database demo; test browser cũng tạo dữ liệu mẫu.

Ảnh module-1-login.png, module-1-teacher.png và module-1-mobile.png là ảnh từ ứng dụng chạy thật trên database ORM kiểm thử/demo. Tên lớp, mã và số lượng trong ảnh là dữ liệu test, không phải bộ dữ liệu học tập hoặc số liệu nghiên cứu.

Chưa có đo tải theo nhiều mức client, throughput/latency, độ chính xác AI hoặc mức tăng band; không suy những kết quả đó từ các test chức năng này.
