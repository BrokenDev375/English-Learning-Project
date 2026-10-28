# Kiểm tra số người online trên giao diện quản trị

Nhánh feature/m07-profile-statistics, nền cb31f80. Bổ sung ô Người đang online trên Trang chủ, Quản lý tài khoản và Thống kê học tập. API riêng đọc snapshot heartbeat, không migration. Giữ toàn bộ thay đổi trước lượt này.

## Hành vi

- ADMIN thấy số người online và số kết nối ngay trên Trang chủ, Quản lý tài khoản và Thống kê học tập. Gồm học viên/giáo viên/admin; nhiều tab hoặc thiết bị cùng tài khoản tính một người.
- Số liệu dựa trên socket còn mở và trong cửa sổ heartbeat của backend hiện tại. Kết nối đã hết liveness bị loại kể cả trước lần sweep tiếp theo. Đây là số tài khoản đang kết nối, không suy ra số người thực sự đang học.
- API riêng GET /api/admin/statistics/presence kiểm tra ADMIN hiện hành, trả generatedAt/connectedAccounts/openConnections/basis, private/no-store, không trả danh tính. Không truy vấn tổng hợp lượt làm/câu hỏi, không ghi DB.
- Đọc mỗi 30 giây khi trang đang hiển thị, đọc lại khi quay lại tab/khôi phục mạng; có nút cập nhật và thời điểm đo. Không gửi chồng yêu cầu, dừng hẹn giờ sau unmount/logout.
- Khi lỗi ban đầu hiển thị chưa biết; khi lỗi cập nhật giữ số lần trước và cảnh báo rõ. Bộ lọc kỳ thống kê không thay phạm vi số online.

## Kiểm tra

Backend: StatisticsApiTest, LiveConnectionsTest, AccountSocketHandlerTest **23/23 PASS**, không failure/error/skip, BUILD SUCCESS, 4:06 phút. Có ca mới kiểm tra API admin/private/no-store và ca snapshot dedup tài khoản, loại socket đóng/hết heartbeat ngay trước sweep. MySQL Testcontainers riêng; không dùng database demo.

Production build TypeScript/Vite PASS. Image backend dùng đúng JAR vừa kiểm thử; main source/pom và client source được giữ SHA-256 để kiểm tra trước cập nhật local.

Browser: ba ca mới kiểm tra số online thật qua hai tài khoản/ba tab, đóng tab/đóng context, vị trí hiển thị và mobile; polling bằng đồng hồ kiểm soát, lỗi refresh giữ số cũ, dừng sau logout; lỗi lần đầu không gán 0 giả. Các nhóm hồi quy gồm thống kê, tài khoản, điều hướng và duyệt học liệu. Lượt hồi quy 26/26 PASS trong 59.2 giây, một worker. Sau chỉnh bố cục web ở viewport hẹp, production image mới và 3/3 ca online PASS; có kiểm tra chiều rộng vùng hiển thị để tránh chữ bị bó hẹp dù trang không tràn ngang.

Đã sửa fixture đăng nhập API để reload ứng dụng nhận phiên mới; ca đồng hồ kiểm soát chờ logout hoàn tất trước khi tua giờ. Không sửa logic ứng dụng để né các lỗi fixture này.

Hợp đồng M07: toàn bộ schema và 40 local references hợp lệ, 6 fixture online hợp lệ/không hợp lệ và các liên kết Markdown của phần thay đổi PASS. Không chạy E2E lên 15183. Log/artifact nằm riêng trong staging .test-state.

## Cập nhật local

Đã áp dụng source trên feature/m07-profile-statistics, HEAD cb31f80, giữ mọi thay đổi có trước. Bản localhost:15183 dùng đúng image frontend/backend đã kiểm thử tại 15213, HTTP 200, đường dẫn/hash JavaScript trùng bản thử và health UP. Schema vẫn V13; không migration, không reset/xóa volume. Lưu ID hai image cũ để có thể quay lại bản ứng dụng trước.

Đã dừng môi trường kiểm thử english-learning-admin-online-e2e sau nghiệm thu và giữ volume. Kiểm tra lại 15183/18093 vẫn hoạt động. Log và manifest nằm riêng trong staging .test-state; không đưa dữ liệu tài khoản/thông tin xác thực vào Git. Chưa commit/push.
