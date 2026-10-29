# Hợp đồng phiên không tương tác và giám sát — bước 0–3

Nền bước 0–2 tại `6941339`, bổ sung ngày 29/10/2026. Gồm phiên cá nhân, client tracker và API giám sát admin bước 3; bảng/khung chi tiết giao diện mới thuộc bước 4.

## HTTP

Hai endpoint yêu cầu phiên cookie hợp lệ, mọi vai trò đã đăng nhập. Response có `Cache-Control: no-store, private`. Không nhận userId, session ID hoặc thời gian do client đặt.

`GET /api/auth/session`: đọc trạng thái, không gia hạn. Body:

```json
{
  "sessionGeneration": "uuid-khong-phai-credential",
  "serverTime": "2026-10-29T08:00:00Z",
  "lastInteractionAt": "2026-10-29T08:00:00Z",
  "idleExpiresAt": "2026-10-29T08:05:00Z",
  "warningAt": "2026-10-29T08:04:30Z"
}
```

`POST /api/auth/activity`: cùng cookie + header CSRF hiện có, body JSON giới hạn 1 KiB sau parse, trường ngoài hợp đồng bị từ chối. Response cùng schema GET.

```json
{
  "tabId": "7a0a89fb-d340-4c65-b36d-35574081c002",
  "seq": 0,
  "activity": "HOME",
  "visibility": "VISIBLE",
  "interaction": false
}
```

- tabId: UUID chữ thường mới cho mỗi document/vòng đăng nhập. seq là số nguyên an toàn 0…9007199254740991.
- seq=0 đăng ký context, bắt buộc interaction=false; đăng ký lại cùng tab không reset watermark hoặc gia hạn.
- seq>0 báo tiếp; trùng/cũ không cập nhật gì. Tab chưa đăng ký/hơn 75s không báo lại nhận 409 TAB_EXPIRED; client dùng UUID mới và đăng ký context-only, không phát lại tương tác cũ.
- Tối đa 16 tab metadata còn sống và 256 watermark/phiên. Vượt giới hạn trả 429 TAB_LIMIT; cần phiên mới sau khi dùng hết watermark. Không xóa watermark chỉ để nhận request cũ như mới.
- Tối đa 120 request activity/phút/phiên, kể cả request trùng. Vượt trả 429 ACTIVITY_RATE_LIMIT, không gia hạn.
- `activity`: HOME, LEARN, REVIEW, RESULT, CATALOG, PROFILE, REWARDS, LEADERBOARD, AUTHORING, ADMIN_USERS, STATISTICS, OTHER. Đây là tín hiệu màn hình, chưa phải bằng chứng nghiệp vụ/đang thi.
- `visibility`: VISIBLE/HIDDEN. `attemptId` tùy chọn, positive int64, STUDENT và owner hợp lệ; missing/foreign trả 404 RESOURCE_NOT_FOUND. Không nhận tên bài/đáp án/title tùy ý. Bước 3 sẽ ánh xạ nhãn/mode thật khi trả admin DTO.
- interaction=true mới và seq mới được chấp nhận cập nhật lastInteractionAt theo thời gian server. Không dùng receipt học và không lưu activity trong outbox.

## Chính sách thời gian và lỗi

Mặc định 300s từ tương tác server chấp nhận gần nhất; login thành công khởi tạo mốc. Cảnh báo trước 30s. Tại `now >= idleExpiresAt` không cho gia hạn/hồi sinh. Sweep 5s chỉ dọn nền; HTTP/message WebSocket phải kiểm tra tại lúc nhận. Request được nhận trước hạn có thể commit sau hạn; không hủy transaction từ sweep.

HTTP 401 `SESSION_IDLE_TIMEOUT` và socket 1008 `IDLE_TIMEOUT` khi nhận diện idle. Khóa/xóa/đổi quyền giữ M01 `SESSION_REVOKED`, mất heartbeat giữ `HEARTBEAT_TIMEOUT`. Sau khi sweep invalidate, hash session ID cũ lưu tối đa 5 phút/10.000 mục để giải thích lỗi; tombstone không xác thực và không được trả ra API. Không nhận diện được nguyên nhân trả UNAUTHENTICATED.

Public health/login/register/CSRF vẫn truy cập được với cookie cũ; login cần quy trình CSRF mới, không phục sinh phiên. `GET session` không tự tạo auth session. Session generation là ID vòng đăng nhập công khai, không thay cookie/CSRF.

## Client và M06

- Bắt thao tác thật (pointerdown/keyboard/input/wheel/touchstart). Bấm play/pause/seek cũng tạo pointer/keyboard; autoplay/timeupdate không gia hạn.
- Gửi leading event rồi coalesce tối đa một lần/10s; cận hạn ưu tiên gửi ngay. Không gửi heartbeat giả dưới tên interaction. Bỏ tín hiệu đã chờ quá 15s hoặc khi offline; không replay sau nối lại.
- Mỗi 15s khi visible đọc trạng thái và gửi context-only để cập nhật metadata tab. Focus/online/visibility/route không tự là tương tác. GET/heartbeat/autosave không gia hạn.
- Tab cùng cookie dùng chung deadline, BroadcastChannel chỉ báo cần GET. Poll vẫn là phương án dự phòng. Phiên riêng trên thiết bị khác độc lập.
- Client dùng serverTime bù lệch giờ, không tự kết luận server hết phiên chỉ vì đồng hồ cục bộ. Mạng lỗi hiển thị chưa xác minh; tiếp tục phải có ACK thật.
- Hết phiên pause sender, flush nháp local nếu được, fence API/CSRF callbacks theo vòng auth, giữ journal/pending và route học. Không gọi logout/purge. Cùng user login lại hydrate/replay M06; tài khoản khác không nhận dữ liệu cũ.
- Chính sách session 8h của servlet vẫn giữ làm trần dự phòng. Idle áp dụng riêng cho cả ba vai trò.

## Giám sát quản trị — bước 3

Chỉ ADMIN hiện hành được đọc; session cookie, AccountGuard và idle gate vẫn áp dụng. Mọi response thành công có `Cache-Control: no-store, private`. Không có API gia hạn/kết thúc phiên của người khác trong bước này.

### `GET /api/admin/sessions`

Tham số: `q` tên/email (trim, không phân biệt hoa thường, tối đa 100 ký tự), `role` STUDENT/TEACHER/ADMIN, `activity` thuộc tập chức năng activity, `page` từ 0 đến 1000000 (mặc định 0), `size` 1–50 (mặc định 20). Tham số lạ, lặp hoặc sai giá trị trả 400 `INVALID_SESSION_QUERY`. IDLE là chiều tương tác riêng, không phải activity.

Response `serverTime`, `basis=LOCAL_BACKEND_LIVE_SOCKET`, `summary`, `items`, `totalElements`, `page`, `size`; schema đầy đủ trong OpenAPI. Chỉ đưa tài khoản ACTIVE có ít nhất một socket mở còn heartbeat và phiên registry còn hạn vào danh sách. Một tài khoản chỉ có một dòng; sắp theo userId tăng. Bộ lọc áp dụng trước tổng và phân trang; trang ngoài phạm vi trả `items=[]` nhưng giữ tổng đúng.

- `summary.connectedAccounts`: số tài khoản phù hợp bộ lọc, không phải số phiên/tab. `activities` chia mỗi tài khoản vào đúng activity đại diện; `idleAccounts` là tập con riêng.
- `validSessions`: tổng phiên HTTP còn hạn của các tài khoản đó, kể cả phiên đã mất socket. Không phải tổng mọi phiên offline của hệ thống.
- `tabs` và `Account.tabCount`: tab đã báo context trong 75s và được gắn ít nhất một socket sống; nhiều socket cùng tab vẫn đếm một. `openConnections` đếm socket; socket client cũ chưa gắn tab vẫn đếm online nhưng context là OTHER/visibility UNKNOWN nếu không còn tab khác.
- `Account.sessions`: các phiên còn hạn với `sessionRef`, `loginAt`, `lastInteractionAt`, `idleExpiresAt`, `interactionState`, `connected`, `openConnections`, `tabCount`. Riêng `Session.tabCount` đếm metadata tab còn hạn, bao gồm tab chưa có socket.
- Activity đại diện: tab có socket, VISIBLE và có tương tác gần nhất; nếu không có thì tab có socket được thấy gần nhất. Hòa timestamp dùng tabRef để ổn định. Trạng thái tương tác account lấy thời điểm tương tác mới nhất giữa các phiên, deadline account là hạn muộn nhất; mỗi phiên vẫn có deadline độc lập trong chi tiết.

### `GET /api/admin/sessions/{sessionRef}`

Trả `serverTime`, `basis`, `user`, `session`, `tabs`. Một phiên mất socket vẫn được đọc nếu còn hạn. Handle giả, raw session ID, phiên đã logout/hết hạn hoặc tài khoản không còn ACTIVE cùng trả 404 `SESSION_NOT_FOUND`. Đây là mã tham chiếu ngẫu nhiên, không phải credential; chỉ ADMIN mới dùng được.

Tab gồm `tabRef`, `connected`, `visibility`, `lastInteractionAt` (null khi chưa có tương tác), `lastSeenAt`, `context`. Context chứa activity, attemptId và tên bài/roundNumber nếu có. Có Attempt thì server kiểm tra lại owner, lấy mode/state và tên từ LessonVersion đã chốt; RESULT chỉ hợp lệ khi đã nộp. Thiếu hoặc sai owner trở thành OTHER và không trả metadata. Context không có Attempt chỉ là tín hiệu route do client báo, không chứng minh đang chú ý học. Không trả đáp án, nội dung câu, pending command, URL, cookie, HttpSession ID hoặc securityVersion.

Projection sao chép registry trong khóa ngắn, ghép metadata qua hai truy vấn batch scalar user/Attempt (bỏ truy vấn rỗng); không đọc collection đáp án/phiên bản hoặc truy vấn từng hàng. Đây là snapshot RAM + DB tại một backend, không cam kết ảnh chụp nguyên tử giữa các nguồn. GET không touch/expire/gia hạn phiên đang được xem; worker idle vẫn dọn riêng.

### WebSocket và invalidation

HELLO/HEARTBEAT nhận thêm `tabId` UUID tùy chọn để ghép socket với tab trong đúng phiên HTTP đã xác thực; client cũ không có trường này vẫn dùng được. Tab ID không cấp quyền và không tự tạo context. Heartbeat giữ metadata tab đã tồn tại còn sống, không cập nhật tương tác/deadline/seq hoặc phục sinh tab đã hết cửa sổ.

Giữ event `{"version":1,"type":"PRESENCE_CHANGED"}` và subscription PRESENCE cũ. Chỉ ADMIN đã subscribe nhận invalidation, không có dữ liệu giám sát trong event. Login/logout, socket/member thay đổi, activity/visibility/tương tác mới làm dirty; phát tối đa một lần/giây, đợt tiếp theo được scheduled flush. Heartbeat/context cùng giá trị và thời gian idle tăng tự nhiên không tự phát event mỗi giây. ACK subscribe/reconnect vẫn yêu cầu GET; giao diện online cũ poll 15s khi visible kể cả socket còn sống, chỉ một GET đang chạy và một refresh queued, giữ dữ liệu cũ khi lỗi. Trang chi tiết/bảng giám sát mới thuộc bước 4.

## Cấu hình

`SESSION_IDLE_TIMEOUT_SECONDS=300`, `SESSION_IDLE_WARNING_SECONDS=30`, `SESSION_IDLE_SWEEP_MS=5000`. Cấu hình test riêng có thể rút ngắn; không đổi `.env` demo. Backend và client phải rollout cùng nhau: client cũ không báo tương tác nên sẽ hết phiên sau timeout.
