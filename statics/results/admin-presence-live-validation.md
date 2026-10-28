# Online trực tiếp trên website quản trị

Thay cơ chế polling chính bằng sự kiện WebSocket PRESENCE_CHANGED cho ADMIN, dùng ConnectionManager chung và API snapshot hiện có. Số người/số kết nối cập nhật khi trạng thái thay đổi; giữ timeout heartbeat cho trường hợp mất mạng đột ngột. Không migration, giữ mọi phần việc trước đó trên feature/m07-profile-statistics, HEAD cb31f80.

## Kiểm tra

- Backend: AccountSocketHandlerTest 6/6 và LiveConnectionsTest 4/4, tổng **10/10 PASS**, không failure/error/skip, package BUILD SUCCESS trong 1:56 phút. Bao gồm đăng ký chỉ ADMIN, từ chối scope giả, attach/detach nhiều tab, sweep hết heartbeat, đóng HTTP session/thu hồi phiên và loại admin nhận bị lỗi mà không broadcast đệ quy. Heartbeat bình thường không phát sự kiện khi số đếm giữ nguyên.
- ConnectionManager: **10/10 tình huống điều khiển PASS**, có subscribe/ACK/đọc bù, reconnect, unsubscribe và bỏ sự kiện của socket cũ. Giữ các ca M05/M06 và callback auth cũ.
- Production TypeScript/Vite và image runtime PASS. JAR là artifact của lượt kiểm thử backend; SHA-256 toàn main source/pom và client source dùng để xác minh trước cập nhật local.
- Browser **30/30 PASS**, một worker, 1.5 phút trên project riêng english-learning-admin-presence-live-e2e, 15215/18125. Bốn ca online dùng WS thật cho mở/đóng tab, nhiều tab một người, admin đọc bù sau gián đoạn; ca clock điều khiển chứng minh không polling 30 giây khi subscription hoạt động, vẫn fallback khi WebSocket bị chặn, giữ số cũ khi lỗi và dừng sau logout. Hồi quy gồm M06 phục hồi, M07 thống kê và điều hướng.

Client vẫn dùng GET snapshot sau ACK và PRESENCE_CHANGED để nhận số liệu có quyền truy cập; các yêu cầu đọc được tuần tự và có đọc bù nếu sự kiện đến trong lúc đang đọc. Server chỉ push khi số đếm thay đổi, không stream liên tục khi không có thay đổi. Mất mạng đột ngột vẫn cần timeout heartbeat/sweep để phát hiện, sau đó mới thông báo. Không chạy E2E vào 15183; log/artifact nằm riêng trong staging .test-state. Không đo tải hoặc tuyên bố độ trễ tối đa production.

## Rollout local — PASS

Đã áp dụng source lên feature/m07-profile-statistics, HEAD cb31f80. Website localhost:15183 dùng đúng hai image đã kiểm thử ở 15215; đường dẫn/hash JavaScript giống bản thử, HTTP 200 và backend health UP. Schema giữ V13, không migration hoặc reset/xóa volume; lưu ID image cũ để quay lại ứng dụng nếu cần. Các file ngoài phạm vi được đối chiếu với baseline và giữ nguyên.

Đã dừng project thử english-learning-admin-presence-live-e2e và giữ volume. Kiểm tra lại 15183/18093 vẫn hoạt động sau khi dừng bản thử. Chưa commit/push.
