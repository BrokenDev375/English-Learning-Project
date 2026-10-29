# Tiến độ giám sát phiên — hoàn tất bước 0–5

Sửa sau nghiệm thu ngày 29/10: tách ACCOUNT/Tài khoản với PROFILE/Hồ sơ học tập, bỏ Attempt context ngoài runner và rebind tabId ngay nếu đổi trong lúc HELLO chờ ACK. Hồi quy bản sửa: **35/35 backend, 19/19 browser, manager 12/12, tracker 11/11, build đạt**. Xem [biên bản sửa nhãn/tab](../statics/results/admin-session-route-fix-validation.md). Số full 361/137 bên dưới là bằng chứng trước bản sửa, không phải lượt chạy lại source này.

Ngày 29/10/2026, nhánh `feature/admin-session-monitoring`. Bước 0–2 commit `6941339`, bước 3 `77dcbea`, bước 4 `60fc657`; bước 5 đã hoàn tất trong working tree, chưa commit/push. Kế hoạch: [ADMIN_SESSION_MONITORING_PLAN](ADMIN_SESSION_MONITORING_PLAN.md), [bàn giao](ADMIN_SESSION_MONITORING_HANDOFF.md).

## Phần đã viết

- Bước 0: hợp đồng [HTTP/hành vi](../source/common/ADMIN_SESSION_MONITORING_API.md), [OpenAPI](../source/common/openapi-admin-session.json); baseline build frontend, manager 10/10 và backend M01/socket 21/21. Baseline browser M06 chưa chạy riêng trước sửa; hồi quy sau tích hợp được ghi riêng.
- Bước 1: SessionActivityRegistry với Clock, deadline 300s/cảnh báo 30s, registry/tab/seq/rate/limits; service/controller; HTTP idle gate trước nghiệp vụ/CSRF; kiểm tra WebSocket handshake/message; sweep và listener cleanup. Tombstone hash ID 5 phút phục vụ lý do lỗi, không xác thực.
- Bước 2: tracker tương tác toàn app, context-only, coalesce, poll/deadline/bù lệch giờ, BroadcastChannel gợi ý GET, warning và Continue có ACK. API/CSRF có auth epoch fence; manager giữ lý do IDLE_TIMEOUT. Runner báo LEARN/REVIEW/RESULT từ snapshot đã tải.
- M06: pause sender, flush local có giới hạn chờ 2s, giữ working copy/journal và route, không purge khi idle. Login lại cùng user hydrate/replay theo cơ chế cũ. Logout chủ động giữ chính sách xác nhận/purge.
- Cấu hình mới trong `.env.example`, Compose và application.yml; không sửa `.env` thật, không migration mới, không thay bộ chấm/XP/ReviewPolicy.
- Bước 3: `AdminSessionController` → `AdminSessionService/impl` → `AdminSessionRepository/impl`; hai GET danh sách/chi tiết, filter/phân trang/tổng trên cùng tập tài khoản online, đọc metadata batch. `sessionRef`/`tabRef` riêng, không trả cookie/session ID. Đọc không gia hạn, phiên mất socket vẫn tra cứu được khi còn hạn. Metadata Attempt kiểm tra lại owner và mode, tên bài từ phiên bản đã chốt.
- Socket gắn document tab qua HELLO/HEARTBEAT; heartbeat giữ metadata còn sống nhưng không gia hạn idle. Activity/context/member thay đổi phát PRESENCE_CHANGED chỉ cho ADMIN đã subscribe, gom tối đa một lần/giây. Giữ API presence cũ, ACK/reconnect GET và poll visible 15s kể cả kết nối đang sống.
- Bước 4: SessionMonitor thay AdminPresence, summary/bảng lọc độc lập với CRUD, chọn phiên trong drawer, thời gian tương đối theo serverTime, lỗi giữ dữ liệu cũ và 404 hiển thị phiên kết thúc. Mỗi resource một GET đang chạy + một refresh queued, GET sau ACK/reconnect và poll visible 15s. Native dialog có vòng Tab/Shift+Tab, Escape/focus restore, cuộn và layout mobile.
- Khóa từ bảng giám sát dùng chung Action/modal/PATCH M01. ACK làm mới cả hai bảng; lỗi mạng/timeout/5xx GET đối soát đúng userId, không resend tự động; kết quả chưa rõ giữ nút Kiểm tra trạng thái. Callback khác auth epoch hoặc component đã unmount không khởi động đối soát mới. Không tự khóa; lỗi quyền/quy tắc last-admin do backend hiện có quyết định, hiển thị trong modal.

## Chưa thuộc lượt này

Đã nghiệm thu bước 5 trên runtime `60fc657`: backend đầy đủ, browser toàn bộ, timeout mặc định 300s thật, idle/draft/pending submit mất ACK, restart/recreate/receipt/ledger/media và khóa khi đang REVIEW thật. Bước 5 không sửa backend/migration/timeout hoặc luật học. Chưa chạy sleep/resume thiết bị vật lý, trình duyệt khác Chrome, load/soak lớn hoặc nhiều backend; ghi NOT RUN trong biên bản.

Không có lịch sử session bền vững/nhiều backend, xác thực người thật hoặc phát hiện “treo máy”. 5 phút tính từ tín hiệu server chấp nhận. Activity được client báo, không thay quyền hoặc xác nhận người đang chú ý học. Nghe audio liên tục không giữ phiên nếu không có thao tác mới.

## Kiểm tra

Backend toàn bộ **350/350 PASS** (37 suite, 0 failure/error/skip, 19m27s); sau sửa nhỏ guard null session, nhóm API/registry/M01 source cuối **24/24 PASS**. Browser hồi quy **128/128 PASS** trên volume mới, **6/6 ca idle** chạy riêng với timeout 20s. Build frontend đạt, manager **10/10**, session harness **10/10**, storage **6/6**. Không cộng các lượt trùng thành tổng test mới.

Xem [biên bản bước 0–2](../statics/results/admin-session-step012-validation.md) cho source, lệnh, phạm vi và giới hạn. Chưa chạy browser chờ mặc định 300s bằng thời gian thực hoặc ca restart/recreate nghiệm thu bước 5; không dùng số từ mốc M06/M07 lịch sử để thay thế.

Kiểm tra bước 3: backend tập trung **61/61**, unit service bổ sung **8/8** (7 ca trùng, 1 ca mới); browser **10/10** rồi chạy lại ca giám sát mở rộng metadata LEARN **1/1**; manager **11/11**, tracker **10/10**, build đạt. Ghi riêng tại [biên bản bước 3](../statics/results/admin-session-step3-validation.md); số 350/128 phía trên là lịch sử bước 0–2, không phải lượt chạy lại toàn bộ trên source bước 3.

Bước 4: browser tập trung **23/23 PASS** trong một lượt, manager **11/11**, tracker **10/10**, build TypeScript/Vite đạt; đã nhìn ảnh desktop/mobile và kiểm tra keyboard/focus/overflow. Khóa LEARN thật trên hai thiết bị, mở khóa/login mới và mất ACK sau commit đều qua. Xem [biên bản bước 4](../statics/results/admin-session-step4-validation.md), gồm phân biệt fixture lỗi UI với API thật và các ca chưa chạy ở bước 5.

Bước 5: **361/361 backend**, 38 suite, 0 failure/error/skip, BUILD SUCCESS trong 18m15s; browser hồi quy **137/137** (7.0 phút), idle/recovery **8/8** + audio bổ sung **1/1**, default 300s **1/1** (5.1 phút). Tổng 147 browser khác nhau qua bốn lượt, không phải một lượt 147/147. Script **3/3 chặng PASS**, build/harness **11/11 + 10/10 + 6/6** đạt. Xem [biên bản bước 5](../statics/results/admin-session-step5-validation.md), gồm các lỗi fixture/assertion đã sửa và giới hạn còn lại; không cộng các lượt trùng.
