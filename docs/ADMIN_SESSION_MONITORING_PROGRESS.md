# Tiến độ giám sát phiên — bước 0–3

Ngày 29/10/2026, nhánh `feature/admin-session-monitoring`. Bước 0–2 đã commit tại `6941339` trên nền `764d846`; bước 3 triển khai tiếp trong working tree. Kế hoạch: [ADMIN_SESSION_MONITORING_PLAN](ADMIN_SESSION_MONITORING_PLAN.md).

## Phần đã viết

- Bước 0: hợp đồng [HTTP/hành vi](../source/common/ADMIN_SESSION_MONITORING_API.md), [OpenAPI](../source/common/openapi-admin-session.json); baseline build frontend, manager 10/10 và backend M01/socket 21/21. Baseline browser M06 chưa chạy riêng trước sửa; hồi quy sau tích hợp được ghi riêng.
- Bước 1: SessionActivityRegistry với Clock, deadline 300s/cảnh báo 30s, registry/tab/seq/rate/limits; service/controller; HTTP idle gate trước nghiệp vụ/CSRF; kiểm tra WebSocket handshake/message; sweep và listener cleanup. Tombstone hash ID 5 phút phục vụ lý do lỗi, không xác thực.
- Bước 2: tracker tương tác toàn app, context-only, coalesce, poll/deadline/bù lệch giờ, BroadcastChannel gợi ý GET, warning và Continue có ACK. API/CSRF có auth epoch fence; manager giữ lý do IDLE_TIMEOUT. Runner báo LEARN/REVIEW/RESULT từ snapshot đã tải.
- M06: pause sender, flush local có giới hạn chờ 2s, giữ working copy/journal và route, không purge khi idle. Login lại cùng user hydrate/replay theo cơ chế cũ. Logout chủ động giữ chính sách xác nhận/purge.
- Cấu hình mới trong `.env.example`, Compose và application.yml; không sửa `.env` thật, không migration mới, không thay bộ chấm/XP/ReviewPolicy.
- Bước 3: `AdminSessionController` → `AdminSessionService/impl` → `AdminSessionRepository/impl`; hai GET danh sách/chi tiết, filter/phân trang/tổng trên cùng tập tài khoản online, đọc metadata batch. `sessionRef`/`tabRef` riêng, không trả cookie/session ID. Đọc không gia hạn, phiên mất socket vẫn tra cứu được khi còn hạn. Metadata Attempt kiểm tra lại owner và mode, tên bài từ phiên bản đã chốt.
- Socket gắn document tab qua HELLO/HEARTBEAT; heartbeat giữ metadata còn sống nhưng không gia hạn idle. Activity/context/member thay đổi phát PRESENCE_CHANGED chỉ cho ADMIN đã subscribe, gom tối đa một lần/giây. Giữ API presence cũ, ACK/reconnect GET và poll visible 15s kể cả kết nối đang sống.

## Chưa thuộc lượt này

Bước 4 chưa triển khai: trang admin vẫn là danh sách online hiện có; bảng theo hoạt động và khung chi tiết phiên sẽ dùng API bước 3. Khóa tài khoản vẫn dùng M01.

Không có lịch sử session bền vững/nhiều backend, xác thực người thật hoặc phát hiện “treo máy”. 5 phút tính từ tín hiệu server chấp nhận. Activity được client báo, không thay quyền hoặc xác nhận người đang chú ý học. Nghe audio liên tục không giữ phiên nếu không có thao tác mới.

## Kiểm tra

Backend toàn bộ **350/350 PASS** (37 suite, 0 failure/error/skip, 19m27s); sau sửa nhỏ guard null session, nhóm API/registry/M01 source cuối **24/24 PASS**. Browser hồi quy **128/128 PASS** trên volume mới, **6/6 ca idle** chạy riêng với timeout 20s. Build frontend đạt, manager **10/10**, session harness **10/10**, storage **6/6**. Không cộng các lượt trùng thành tổng test mới.

Xem [biên bản bước 0–2](../statics/results/admin-session-step012-validation.md) cho source, lệnh, phạm vi và giới hạn. Chưa chạy browser chờ mặc định 300s bằng thời gian thực hoặc ca restart/recreate nghiệm thu bước 5; không dùng số từ mốc M06/M07 lịch sử để thay thế.

Kiểm tra bước 3: backend tập trung **61/61**, unit service bổ sung **8/8** (7 ca trùng, 1 ca mới); browser **10/10** rồi chạy lại ca giám sát mở rộng metadata LEARN **1/1**; manager **11/11**, tracker **10/10**, build đạt. Ghi riêng tại [biên bản bước 3](../statics/results/admin-session-step3-validation.md); số 350/128 phía trên là lịch sử bước 0–2, không phải lượt chạy lại toàn bộ trên source bước 3.
