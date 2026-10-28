# Một danh sách người online trong Quản lý tài khoản

Chỉ hiển thị một danh sách tên/vai trò trong AdminAccounts; bỏ ô toàn cục ở App, nút cập nhật online/Tải lại, đoạn giải thích và thời gian đo. Giữ WebSocket và thay dữ liệu theo ID, không nối thêm danh sách. API chỉ ADMIN, users không chứa email/password/session; dữ liệu batch theo socket sống và trạng thái ACTIVE. Không migration.

Backend **12/12 PASS**, không failure/error/skip, package BUILD SUCCESS 2:57 phút: hai ca API admin presence, sáu ca WebSocket và bốn ca snapshot/heartbeat. Kiểm tra trả đúng id/tên/vai trò, không email/password/session, hai tab một ID, loại tài khoản LOCKED và socket đóng. Query tài khoản dùng batch theo tập ID của một snapshot RAM; membership set tham gia phát hiện thay đổi.

Production TypeScript/Vite và image runtime PASS. Browser **27/27 PASS**, một worker, 1.2 phút trên môi trường riêng english-learning-admin-online-list-e2e, 15217/18127. Bốn ca online xác nhận đúng một panel, không nút/lời giải thích, không dòng trùng sau ba lần mở/đóng tab, rời/quay lại màn hình, tự cập nhật/reconnect, lỗi giữ danh sách cũ và fallback. Hồi quy gồm tài khoản, thống kê, điều hướng và duyệt học liệu. Đã xem ảnh desktop; danh sách tên/vai trò gọn và bảng quản lý tài khoản giữ nguyên. Các ảnh/log ở staging .test-state.

Hợp đồng M07: schema và sáu fixture users hợp lệ/không hợp lệ PASS, có chặn trường email/passwordHash và role lạ; liên kết Markdown hợp lệ. Không chạy E2E lên 15183. Mọi phần việc trước đó được giữ nguyên, chưa commit/push.

## Rollout local — PASS

Source đã áp dụng trên feature/m07-profile-statistics, HEAD cb31f80. Website 15183 dùng đúng image backend/client đã thử ở 15217; đường dẫn/hash JavaScript trùng bản thử, HTTP 200 và health UP. Schema vẫn V13; không migration/reset/xóa volume. Giữ ID image cũ để quay lại ứng dụng. Các file ngoài phạm vi khớp baseline.

Đã dừng môi trường thử english-learning-admin-online-list-e2e, giữ volume. 15183/18093 tiếp tục hoạt động. Chưa commit/push.
