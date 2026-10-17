# Kiểm tra M01 — English Learning — 16/10/2026

Kiểm tra thực tế trên Windows, backend Java 21 biên dịch target 17; Docker chạy JRE 17; MySQL 8.4.9; Chromium có sẵn trên máy qua PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH. Đây là kiểm tra chức năng M01, chưa là đo tải hoặc đánh giá hiệu quả học tập.

| Kiểm tra | Kết quả |
|---|---|
| Backend build/package | PASS |
| Backend integration bằng HTTP thật + MySQL Testcontainers | 22 test, 0 failure, 0 error, 0 skipped |
| AccountAdministrationApiTest | 10 test PASS |
| ModuleOneApiTest — auth và API lớp từ nền ORM | 12 test PASS |
| Frontend TypeScript + Vite build | PASS, cả build Windows và Docker |
| Docker build server/client | PASS |
| Compose mysql/server/client healthcheck | PASS, đúng cổng 5173/8080 |
| Browser E2E | 5/5 PASS |
| Hai trình duyệt độc lập — đổi quyền/khóa/mở khóa/xóa | PASS, socket và session bị thu hồi |
| Mobile 390 × 844 | PASS, không tràn ngang; đã xem ảnh |
| Tắt request login | PASS, báo lỗi và giữ email |
| Tạo lại cả 3 container, giữ volume | PASS: tài khoản, lớp và membership vẫn còn |
| Dự án cũ | Không sửa code hoặc xóa volume; bộ IELTS cũ được dừng để nhường cổng |

## Những trường hợp quản trị đã kiểm tra

- Chặn người học/người biên soạn/chưa đăng nhập truy cập admin.
- Tìm/lọc/phân trang, giới hạn đầu vào, escape wildcard; response không lộ passwordHash/securityVersion.
- Cấp quyền làm mất hiệu lực tất cả session cũ; cùng giá trị không thu hồi lại.
- Khóa rồi mở khóa không khôi phục session cũ.
- Xóa logic lặp lại an toàn, giữ FK lịch sử; không mở lại hoặc đổi quyền user đã xóa.
- Chặn tự khóa/xóa/đổi quyền admin đang dùng; chặn CSRF và role đăng ký giả.
- Hai admin khóa lẫn nhau đồng thời vẫn còn một admin hoạt động.
- Đóng WebSocket khi khóa/logout; từ chối origin ngoài danh sách.
- Đổi tài khoản trong cùng trình duyệt không để danh tính cũ thu hồi nhầm phiên mới.
- Khóa tài khoản chờ transaction ghi đang chạy; actor cũ không thể ghi tiếp sau khi khóa hoàn tất.

## Tái hiện

Backend: tại source/server chạy .\mvnw.cmd test với Docker Desktop. Testcontainers tạo DB riêng, không dùng dữ liệu phát triển.

Giao diện: khởi động app, tại source/client chạy npm ci rồi npm run test:e2e. Nếu chưa tải browser của Playwright thì npx playwright install chromium; lần kiểm tra này dùng Chromium đã có sẵn, chỉ định qua PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH.

Persistence:
1. powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-persistence.ps1 -Phase prepare
2. docker compose up -d --force-recreate --wait
3. powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-persistence.ps1 -Phase verify

Ảnh module-1-admin-desktop.png, module-1-learner-desktop.png và module-1-learner-mobile.png là ảnh từ ứng dụng thật; các tài khoản e2e là dữ liệu kiểm thử. Không dùng các tài khoản hoặc số lượng này làm bằng chứng hiệu quả học tập.

Chưa hoàn thành các module M02–M07; mới có nền session/socket phục vụ M01. Presence đầy đủ, khôi phục bài làm, XP và Giải đấu tuần sẽ làm sau. Solo M08 vẫn chờ xác nhận. Chưa có benchmark/contribution định lượng.
