# Dữ liệu demo module 1

Hai tài khoản mẫu được DemoInitializer tạo khi DEMO_ENABLED=true, theo cấu hình .env. Mật khẩu băm trước khi lưu; không có file SQL chứa mật khẩu người dùng thật.

Database ban đầu chưa có lớp. Giảng viên tạo lớp và sinh viên đăng ký/tham gia qua giao diện để demo các luồng mạng. Không đưa bộ Reading/Listening/Speaking/Writing vào module này.

Testcontainers dùng database/container riêng và fixture riêng. Browser tests tạo lớp/học viên thử trong database demo mà chúng đang chạy; không dùng E2E trên dữ liệu sử dụng thực tế.
