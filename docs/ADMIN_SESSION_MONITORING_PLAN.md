# Kế hoạch giám sát hoạt động và hết phiên do không tương tác

Ngày lập: **29/10/2026**. Nhánh: `feature/admin-session-monitoring`. Bước 0–2 đã commit tại `6941339`, bước 3 tại `77dcbea`; bước 4 triển khai trong working tree, chưa commit. Bước 5 chưa nghiệm thu. Xem [tiến độ](ADMIN_SESSION_MONITORING_PROGRESS.md), [biên bản bước 0–2](../statics/results/admin-session-step012-validation.md), [bước 3](../statics/results/admin-session-step3-validation.md) và [bước 4](../statics/results/admin-session-step4-validation.md) cho bằng chứng từng lượt chạy.

## 1. Mục tiêu và phạm vi

Mở rộng ngay trang **Quản lý tài khoản**: admin biết ai đang online, đang dùng chức năng nào, thời điểm tương tác gần nhất và các phiên/tab tương ứng. Tài khoản có thể bị khóa bằng luồng M01 hiện có. Mỗi phiên đăng nhập hết hạn sau 5 phút không có tín hiệu tương tác hợp lệ.

P0 gồm:

- [x] Registry phiên/tab và thời điểm tương tác, tách khỏi heartbeat.
- [x] Server kiểm tra hạn idle ở HTTP, WebSocket và sweep nền.
- [x] Client báo hoạt động, cảnh báo trước 30 giây, xử lý hết phiên đúng lý do.
- [x] Bảng giám sát realtime, lọc/phân trang và khung chi tiết phiên trên trang admin hiện tại.
- [x] Khóa tài khoản từ bảng giám sát, tái sử dụng API/transaction/quyền M01.
- [x] Phục hồi nháp/pending M06 sau khi hết phiên và đăng nhập lại đúng user.
- [ ] Kiểm thử quyền, concurrency, nhiều tab/thiết bị, mất mạng và hồi quy.

P1, không đưa vào P0: cưỡng chế đăng xuất riêng một phiên bằng nút admin, lịch sử truy cập bền vững, định vị/IP/device fingerprint, đo thời gian học, thi cử mới, nhiều backend/Redis hoặc dashboard theo dõi đáp án. Giao diện không hiển thị “Đang thi” khi hệ thống chưa có nghiệp vụ thi.

Không tạo nhánh/commit/push, chạy lại demo hoặc nâng DB chỉ vì viết kế hoạch. Các file báo cáo chưa được theo dõi nằm ngoài phạm vi thay đổi này.

## 2. Nền hiện có và điểm cần giữ

| Phần | Source hiện tại | Hướng mở rộng |
|---|---|---|
| Đăng nhập | `AuthController.login` đổi phiên cũ, lưu context, `LiveConnections.register` | Khởi tạo deadline sau khi xác thực thành công; không chỉ đăng ký lúc mở socket |
| Phiên hiện hành | `AccountGuardFilter`, `AccountGuard` | Bổ sung idle gate, giữ kiểm tra quyền/securityVersion và actor lock của mutation |
| Thu hồi do quản trị | `AdminUserServiceImpl` → `AccountAccessChanged` AFTER_COMMIT | Giữ khóa toàn tài khoản, invalidate mọi phiên/socket sau commit |
| Socket | `LiveConnections`, `AccountSocketHandler`, heartbeat 25 s, liveness 75 s | Heartbeat chỉ cập nhật kết nối; kiểm tra idle riêng trước handshake/message |
| Presence admin | `GET /api/admin/statistics/presence`, `PRESENCE_CHANGED` | Giữ hợp đồng danh sách online hiện tại; API giám sát mới dùng registry chung |
| UI | `AdminAccounts` mount `statistics/AdminPresence` đúng một lần | Mở rộng khối trên; giữ bảng quản trị và modal xác nhận hiện có |
| Kết nối client | `features/connection/manager.ts` | Giữ ACK/backoff/generation/subscription; thêm lý do hết phiên, không tự nối lại khi auth đã hết |
| Hết phiên client | `api.ts` phát `session-expired`; `App.tsx` về auth | Mang code/lý do và fence callback theo vòng đăng nhập |
| Nháp | `learning/session.ts`, `storage/*` | Hết phiên giữ nháp/journal; logout chủ động vẫn có xác nhận và purge như hiện tại |
| Servlet timeout | `server.servlet.session.timeout: 8h` | Giữ như trần kỹ thuật dự phòng; không dựa vào việc đổi thành 5m để xử lý idle |

GET/polling/heartbeat vẫn có thể làm servlet session được truy cập. Vì vậy chúng không phải bằng chứng người dùng tương tác. `PRESENCE_CHANGED` hiện chỉ so tập user ID/số socket; activity thay đổi khi số người không đổi cần cơ chế invalidation bổ sung.

## 3. Quy tắc nghiệp vụ chốt cho P0

### 3.1. Đơn vị tính và thời gian

1. Áp dụng idle cho cả STUDENT, TEACHER và ADMIN; cấu hình chung, không miễn admin trong bản chạy thật.
2. **Một phiên HTTP dùng chung deadline cho mọi tab**. Bất kỳ tab nào của phiên có tương tác được server chấp nhận thì gia hạn phiên đó.
3. Hai trình duyệt/browser context/thiết bị đăng nhập riêng là hai phiên độc lập. Idle một phiên không tăng `security_version`, không logout phiên kia.
4. Khóa/xóa/đổi quyền tài khoản vẫn thu hồi tất cả phiên theo M01. Mở khóa không phục sinh cookie/phiên đã bị thu hồi.
5. Mặc định `idleTimeout=300s`, `warningBefore=30s`, sweep `5s`; login thành công khởi tạo `lastInteractionAt=serverNow`.
6. Server tính `idleExpiresAt = lastInteractionAt + 300s`; tại `now >= idleExpiresAt` phiên đã hết. Sweep có thể dọn trễ tối đa một chu kỳ, nhưng request/message đến hạn phải bị chặn ngay.
7. Thời gian tính từ **tín hiệu tương tác được server chấp nhận**, không tuyên bố đo chính xác thời điểm thao tác vật lý. Không dùng timestamp do client gửi để kéo deadline về tương lai.
8. Không tự logout theo “đủ 5 phút từ lúc mở trang”. Người dùng đang thao tác phải được duy trì đúng phiên.

### 3.2. Điều gì là tương tác

Tín hiệu hợp lệ phía client: thao tác bàn phím/nhập liệu, pointer down/chạm, cuộn do người dùng, bấm nút điều hướng hoặc điều khiển audio. Lắng nghe toàn ứng dụng để tính cả thao tác chưa tạo request nghiệp vụ. Chỉ nhận sự kiện trình duyệt có `isTrusted`; đây là bộ lọc tín hiệu, không phải cơ chế chống bot được chứng minh.

Không gia hạn bởi: heartbeat/ACK, mở lại socket, GET, polling, autosave/replay, timer, render, focus/visibility tự nó, chuột đứng yên/mousemove, autoplay hoặc audio timeupdate. Nghe audio liên tục không phát sinh tương tác mới; giữ luật 5 phút và cảnh báo để người nghe chủ động tiếp tục. Không thêm ngoại lệ ngầm cho playback.

Route/context được báo riêng. Thay đổi context không tự gia hạn; thao tác bấm dẫn tới đổi route có thể đồng thời tạo tín hiệu tương tác. Admin chỉ xem trang giám sát mở sẵn cũng hết phiên nếu không thao tác.

### 3.3. Các chiều trạng thái

Không trộn trạng thái tài khoản, kết nối, chức năng và idle vào một enum:

| Chiều | Giá trị đề xuất |
|---|---|
| Tài khoản | ACTIVE / LOCKED / DELETED hiện có |
| Kết nối | Có socket còn heartbeat / không có socket sống |
| Chức năng tab | HOME, LEARN, REVIEW, RESULT, CATALOG, PROFILE, REWARDS, LEADERBOARD, AUTHORING, ADMIN_USERS, STATISTICS, OTHER |
| Hiển thị tab | VISIBLE / HIDDEN |
| Tương tác phiên | ACTIVE khi dưới 60s, IDLE khi từ 60s đến dưới 300s, EXPIRED tại 300s |

UI dùng “Không tương tác”, “Tab ở nền”; không khẳng định người dùng “treo máy” hay đang chú ý học. Không có socket sống không đồng nghĩa cookie đã hết hoặc HTTP không hoạt động. Phiên HTTP hợp lệ có thể còn tồn tại khi socket mất mạng.

Một account có nhiều tab/phiên chỉ tính một lần trong số tài khoản online. Chức năng đại diện lấy tab online/visible có tương tác gần nhất, nếu không có thì tab online được thấy gần nhất; chi tiết cho biết số tab/phiên và các chức năng còn lại. Các ô tổng theo hoạt động phải dùng cùng quy tắc đại diện để tránh đếm một user vào nhiều ô.

### 3.4. Hết phiên và dữ liệu người học

- Cảnh báo ở 270s dựa trên deadline server; hiển thị đếm ngược và nút “Tiếp tục làm việc”. Nút gửi tương tác thật; chỉ đóng cảnh báo sau khi server xác nhận còn phiên và trả deadline mới.
- Tab B không được tự hết phiên theo deadline cũ khi tab A đã gia hạn. Đối soát trạng thái phiên trước khi kết luận; khi đang mất mạng hiển thị “Chưa xác minh được phiên”.
- Offline vẫn có thể lưu nháp tại máy. Thao tác offline không gia hạn phiên trên server, không được replay sự kiện cũ để hồi sinh phiên khi online lại.
- Khi server báo hết phiên: dừng sender/autosave/reconnect có auth, giữ route học và working copy/journal đã lưu; cùng user login lại rồi GET/replay đúng key theo M06.
- Không gọi `/api/auth/logout` hoặc `clearLearningSessions()` để xử lý idle. Không đặt marker purge localStorage cho hết phiên tự động.
- Flush bản RAM xuống IndexedDB trước chuyển auth nếu có thể; lỗi/quota phải báo đúng, không đảm bảo bản chưa commit được giữ. Không chờ lưu server sau khi phiên đã hết.
- Đổi user/logout chủ động giữ chính sách cách ly/purge hiện có. ACK/401/callback của phiên đăng nhập cũ không được làm hết phiên mới, kể cả cùng user ID.

## 4. Thiết kế kỹ thuật đề xuất

### 4.1. Registry trong một backend

Tách trách nhiệm theo dõi deadline/tab thành `SessionActivityRegistry` và service có interface/implementation; để `LiveConnections` phối hợp socket, không gộp tất cả vào một lớp lớn. Dùng `Clock` hiện có, không tạo clock/worker trùng.

- Session entry: userId, internal HttpSession reference, login generation, lastInteractionAt, idleExpiresAt, state; map tab đang sống.
- Tab entry: tabId, seq, context đã chuẩn hóa, visibility, lastSeenAt, lastInteractionAt và socket binding nếu có.
- `tabId` là UUID ngẫu nhiên mới cho mỗi document; reload/reconnect đăng ký lại. Không dùng writer ID của M06 làm tab ID vì quy tắc clone/recovery khác nhau.
- Admin nhận `sessionRef`/`tabRef` ngẫu nhiên chỉ để hiển thị/tra cứu; **không trả HttpSession ID, cookie hoặc access token**. Handle không dùng làm credential.
- Tab metadata hết hạn khi không báo context/heartbeat trong cửa sổ 75s; reload/đóng tab cleanup best effort, không phụ thuộc beforeunload. Giới hạn 16 tab/phiên và kích thước context; lỗi đăng ký tab không được phá việc học đã lưu.
- Session registry được đăng ký ngay lúc login, không phụ thuộc đã có socket hay chưa. Session listener dọn map khi logout/idle/container destroy.
- Khởi tạo/đổi ID phiên/đổi user phải bỏ mapping cũ. Restart mất registry và session RAM như kiến trúc hiện tại.

P0 không cần bảng/migration mới. Chỉ thêm DB audit nếu người dùng yêu cầu lịch sử bền vững ở P1.

### 4.2. Điều phối timeout và concurrency

Activity update, kiểm tra hết hạn và expire phải được phân xử nguyên tử theo từng session entry. Không có tình huống một thread gia hạn một entry đã EXPIRED. Không giữ khóa registry khi gọi DB, gửi socket hoặc callback invalidate.

Quy trình: dưới khóa ngắn xác định ACTIVE/EXPIRED và chốt chuyển trạng thái; ngoài khóa thực hiện invalidate/close/notify. Expire idempotent, HTTP và sweep cùng chạy chỉ xử lý một lần. Không lấy khóa DB tài khoản trong khi giữ khóa registry để tránh đảo thứ tự với M01.

HTTP idle gate sau load SecurityContext, trước xử lý nghiệp vụ và trước CSRF rejection khi cần phản hồi phiên hết. WebSocket kiểm tra ở handshake và trước **mọi** message, gồm heartbeat. Bộ lọc/account guard vẫn kiểm tra quyền hiện hành.

Các endpoint công khai health/login/register/CSRF không bị chặn vô hạn bởi cookie của phiên cũ đã hết. Login mới phải đi được sau cleanup phiên cũ và CSRF thích hợp; GET CSRF không gia hạn phiên nghiệp vụ. `/api/auth/session` không tự tạo phiên đăng nhập khi chưa có auth. Test riêng trường hợp idle rồi login lại ngay trên cùng trình duyệt.

Request được nhận khi phiên còn hạn có thể hoàn tất/commit sau mốc hết hạn; không cố rollback từ sweep. Request đến sau hạn bị từ chối. Quyền/actor lock nghiệp vụ và cơ chế receipt hiện tại vẫn quyết định transaction. Khi response muộn đến client đã về auth, generation fence giữ journal, không tự tái đăng nhập hoặc xóa lệnh chờ.

### 4.3. HTTP bổ sung và tương thích

Chốt DTO/OpenAPI ở bước 0 trước khi viết implementation:

| API dự kiến | Hành vi |
|---|---|
| `GET /api/auth/session` | Đọc serverTime, login generation công khai không nhạy cảm, lastInteractionAt, idleExpiresAt, warningAt; không gia hạn |
| `POST /api/auth/activity` | Session + CSRF, tabId/seq/context/visibility và cờ có tương tác mới; trả deadline server; không nhận userId hoặc client deadline |
| `GET /api/admin/sessions` | ADMIN: tổng hợp theo account, tìm tên/email, role/activity, phân trang; serverTime và basis rõ ràng |
| `GET /api/admin/sessions/{sessionRef}` | ADMIN: chi tiết phiên/tab đang có; handle thiếu/hết hạn trả 404 |

GET admin cần AccountGuard hiện hành, cache `no-store, private`, không tạo/gia hạn phiên theo dõi. DTO tối thiểu: userId/tên/vai trò, accountStatus, số phiên/tab/socket còn sống, activity/visibility, lastInteractionAt/idleExpiresAt, nhãn bài/vòng hợp lệ và thời điểm đo. Không trả đáp án, keys, pending payload hoặc toàn URL.

Tổng online và các ô hoạt động tính trên tập user đã lọc có socket còn sống, trước phân trang; trả thêm tổng phiên còn hạn để giải thích khác biệt. Chi tiết phiên vẫn có thể cho biết socket đang gián đoạn. Metadata người dùng lấy batch qua repository hiện có, không N+1.

Context chỉ nhận enum và ID allowlist. Attempt ID phải thuộc owner; nhãn/mode/bài do server đọc và ánh xạ DTO, không tin title tùy ý từ client. Context không phải căn cứ cấp quyền hay chứng minh đang học. Không nhận thông tin màn hình thi chưa có.

Activity không dùng receipt M03: đây là tín hiệu ngắn hạn, không phải mutation học. `seq` tăng trong tab, trùng/cũ không cập nhật lastInteractionAt, không làm lùi context/deadline. Response cũ bị client bỏ theo generation/seq. Có rate limit đề xuất 120 request/phút/phiên, payload tối đa 1 KiB, lỗi rate không gia hạn.

Việc dọn metadata tab hiển thị không được làm mất hàng rào seq để request cũ được coi là tương tác mới. Thiết kế watermark/đăng ký tab có thế hệ và giới hạn bộ nhớ riêng, chốt trong contract bước 0; request tới tab đã kết thúc không tự tái đăng ký/gia hạn. Đăng ký tab mới chỉ gửi context, không mặc định là thao tác người dùng. Tín hiệu activity không được đưa vào durable outbox để phát lại sau offline.

Khi idle: HTTP trả 401 `SESSION_IDLE_TIMEOUT`, message riêng; socket đóng 1008 reason `IDLE_TIMEOUT`. Thu hồi quản trị vẫn `SESSION_REVOKED`; mất heartbeat vẫn `HEARTBEAT_TIMEOUT`. Không dùng một lý do cho mọi trường hợp.

Để request sau sweep còn nhận diện được lý do, dùng tombstone RAM có TTL 5 phút và giới hạn số lượng, key là hash server-side của session ID cũ; không log/trả ID. Tombstone chỉ phục vụ thông báo, không khôi phục authentication. Hết TTL/không nhận diện được trả 401 UNAUTHENTICATED với thông báo chung; không đoán nguyên nhân.

Giữ nguyên payload `/api/auth/me`, login UserView, `/api/admin/statistics/presence` và event PRESENCE_CHANGED cho consumer cũ. Nếu mở rộng DTO hiện có, chỉ thêm trường có tương thích và cập nhật đúng contract/test.

### 4.4. Client activity tracker và đồng bộ tab

Tạo `features/session/` dùng adapter `api.ts` chung, một tracker/document sau xác minh auth. Tách khỏi ConnectionManager nhưng dùng cùng vòng đời đăng nhập.

- Sự kiện tương tác đầu tiên gửi ngay; coalesce các tương tác tiếp theo, tối đa một lần mỗi 10s và chỉ gửi khi có sự kiện mới. Timer không tự đặt cờ “có tương tác”. Khi gần hạn, thao tác thật/“Tiếp tục” ưu tiên gửi ngay với rate guard.
- Route/visibility gửi context-only khi thay đổi; không tăng idle deadline. Không gửi request chỉ vì mousemove.
- Mỗi document có seq, queue coalesced và tối đa một request activity đang chạy; không dùng lane mutation Attempt của M06. Mạng lỗi không tạo backlog các sự kiện cũ.
- Đọc deadline ở bootstrap, reconnect/focus/online và trước kết luận hết phiên. `GET /api/auth/session` trong tab visible đối soát 15s, không gia hạn.
- Có thể dùng BroadcastChannel để báo deadline ACK mới cho tab cùng origin; đây chỉ là gợi ý, server là nguồn chính thức. Không gửi cookie, đáp án hoặc token qua kênh này; GET/poll vẫn hoạt động nếu BroadcastChannel không có.
- Dùng serverTime để bù lệch đồng hồ client; sleep/throttle không phụ thuộc decrement timer mỗi giây. Khi máy thức dậy, tính lại và xác minh server.
- Cleanup listener/timer/channel khi logout/user change/unmount. Expiration event mang code và epoch hiện tại; App và sender bỏ event/ACK của vòng đăng nhập cũ.

### 4.5. Cập nhật realtime cho admin

Mở rộng subscription PRESENCE hiện có, dùng event chỉ báo cần GET; không phát toàn bộ danh sách/context cho mọi socket. Chỉ ADMIN đã subscribe mới nhận invalidation giám sát.

- Phát khi login/logout, attach/detach/expire, chuyển chức năng/visibility hoặc đổi thành phần danh sách; không chỉ khi số người thay đổi.
- Gom sự kiện tối đa một lần/giây để tránh GET theo mọi lần nhập. Không phát mỗi giây chỉ vì idle tăng.
- Admin client vẫn GET định kỳ 15s khi visible, kể cả socket còn sống, để phản ánh mốc IDLE/deadline và bù event mất; xử lý một GET đang chạy và một lần reload queued.
- Subscribe ACK/reconnect → GET lại; tab admin hidden dừng poll, khi visible GET ngay. Không tạo thêm socket hoặc mount đôi panel.
- Khi lỗi giữ dữ liệu lần thành công cuối kèm dấu “Chưa cập nhật”; không biến 401/503 thành danh sách rỗng hoặc đếm 0 giả.

## 5. Giao diện Quản lý tài khoản

Giữ một trang, hai khu vực:

1. **Giám sát hoạt động** thay khối “Người đang online”: ô tổng số tài khoản online/đang làm bài/đang ôn/không tương tác; tìm kiếm và lọc; bảng có tài khoản, hoạt động đại diện, tương tác gần nhất, số phiên/tab, nút Xem phiên và Khóa.
2. **Quản lý tài khoản** bên dưới: tìm kiếm/quyền/trạng thái/ngày tạo và khóa/mở khóa/xóa như hiện tại. Hai bộ lọc độc lập và nhãn rõ ràng.

Khung chi tiết phiên có loginAt, deadline, chức năng/tab và tình trạng kết nối. Nhãn thời gian cập nhật cục bộ từ timestamp, không gọi API mỗi giây. Có loading, empty, stale/error và expired-row state. Dòng biến mất trong lúc mở khung phải hiển thị “Phiên đã kết thúc”, không báo lỗi ứng dụng.

Khóa từ bảng giám sát dùng cùng modal/callback M01. Không thêm API khóa thứ hai; giữ không tự khóa, bảo vệ admin cuối cùng và rollback. Chỉ cập nhật thành công sau ACK; mất phản hồi thì GET trạng thái tài khoản để đối soát, không khẳng định thất bại khi server có thể đã commit.

Mobile: summary xuống dòng, bảng cuộn trong khung hoặc card; khung chi tiết và modal dùng bàn phím, focus trap, Escape, nhãn rõ và không chỉ dùng màu phân biệt trạng thái.

## 6. Các bước triển khai và điều kiện hoàn thành

### Bước 0 — Chốt hợp đồng và baseline

- [x] Đọc diff và các test account/presence/recovery liên quan; ghi mốc source, không áp dụng số test cũ cho source mới.
- [x] Chốt DTO/error codes, giới hạn input/rate, timeout, trạng thái UI và admission rule ở mục 3–4.
- [x] Viết `source/common/ADMIN_SESSION_MONITORING_API.md` và OpenAPI bổ sung cho bước 0–2; phần admin session DTO bổ sung khi thực hiện bước 3.
- [x] Baseline frontend build/manager 10 và backend M01/socket 21; browser M06 không có lượt baseline riêng trước sửa, ghi rõ ở biên bản.

Đạt khi không còn nhập nhằng account/session/tab, idle/heartbeat và quyền xem dữ liệu. Chưa thay hành vi runtime ở bước này.

### Bước 1 — Backend registry và timeout

- [x] Service/registry session, login registration, deadline, listener cleanup, tombstone có giới hạn và per-session concurrency guard.
- [x] HTTP idle gate + WebSocket handshake/message check + sweep; giữ M01 guard/actor lock/AFTER_COMMIT.
- [x] Endpoint session/activity, parser nghiêm/CSRF/rate/seq và Clock cấu hình.
- [x] Kiểm thử clock ranh giới 299999/300000 ms, context/GET không gia hạn, phiên độc lập, expire/touch đồng thời và socket thật đóng khi idle.

Đạt khi backend tự hết phiên dù không có client timer; phiên đã hết không được activity/reconnect hồi sinh; không tác động DB học.

### Bước 2 — Client tracker và phục hồi auth

- [x] Tracker toàn app, context-only/interaction, coalesce, seq/epoch, time offset và lifecycle cleanup.
- [x] Cảnh báo 30s, tiếp tục có ACK, trạng thái offline/không xác minh, hết phiên có lý do.
- [x] Giữ nháp/pending M06; login lại cùng user, đổi user và logout chủ động đúng chính sách.
- [x] Kiểm thử nhiều tab, reload, phản hồi cũ, warning/input offline, callback storage treo; chưa thử sleep OS/thiết bị vật lý hoặc chờ 300s thật.

Đạt khi tab A gia hạn không làm tab B logout nhầm; client cũ không gửi replay/ACK vào vòng auth mới và không purge nháp khi idle.

### Bước 3 — API giám sát và invalidation

- [x] Projection theo account/session/tab, lookup batch user và context hợp lệ, filter/page/count nhất quán.
- [x] Invalidation hoạt động có coalescing, ADMIN-only, giữ hợp đồng presence cũ và reconnect GET.
- [x] Kiểm thử quyền, ID giả/foreign, nhiều tab đếm một account, đổi hoạt động khi số người không đổi, mất socket còn phiên HTTP.

Đạt khi không lộ session ID/đáp án, không N+1 và admin đọc không kéo dài phiên của người bị theo dõi.

Đã commit tại `77dcbea` trên nền `6941339`; phạm vi và bằng chứng tại [biên bản bước 3](../statics/results/admin-session-step3-validation.md). Biên bản bước 3 hiện là file untracked, cần stage khi người dùng commit tài liệu.

### Bước 4 — Trang admin và tích hợp khóa

- [x] Thay panel hiện có bằng bảng/summary/khung chi tiết; giữ CRUD tài khoản.
- [x] Tái sử dụng modal/service khóa và đối soát sau mất phản hồi; cập nhật list sau thu hồi.
- [x] Loading/empty/stale/error, role/current-user/last-admin constraints, desktop/mobile/keyboard.

Đạt khi khóa user đang học/ôn làm các phiên/socket của user bị thu hồi, tài khoản khác không bị ảnh hưởng, mở khóa cần login mới.

### Bước 5 — Hồi quy, nghiệm thu và tài liệu

- [ ] Backend đầy đủ sau tích hợp; production build, manager/storage harness và browser E2E liên quan rồi toàn bộ khi ổn định.
- [ ] Script/ca nghiệm thu riêng cho idle + nháp + pending submit mất ACK, restart và account lock; dùng môi trường test riêng.
- [ ] Manual smoke desktop/mobile, hai tab cùng context và hai browser context; kiểm tra cấu hình mặc định 300s thật trước bàn giao.
- [ ] Cập nhật README, AGENTS, hợp đồng M06/M07 liên quan, progress và biên bản mới có ngày/source/lệnh/kết quả/giới hạn.
- [ ] `git diff --check`, trạng thái Git, không thêm `.env`/credentials/artifact. Chỉ commit/push khi được yêu cầu.

Đạt khi ma trận dưới đây có bằng chứng thật; ca chưa chạy ghi NOT RUN, không ghi PASS suy diễn.

## 7. Ma trận kiểm tra tối thiểu

| Nhóm | Tình huống bắt buộc |
|---|---|
| Thời gian | 0/269999/270000/299999/300000 ms; server/client lệch giờ; sweep trễ; máy sleep rồi thức |
| Không gia hạn giả | GET/polling/heartbeat/autosave/replay/focus/visibility/audio timeupdate liên tục vẫn hết hạn |
| Tương tác | Nhập/chọn/scroll/control audio có tín hiệu; coalesce chỉ gửi khi có sự kiện mới; seq cũ không gia hạn |
| Warning | Còn 30s hiện cảnh báo; tiếp tục có ACK mới đóng; nút ở đúng/ngay sau hạn không hồi sinh phiên |
| Nhiều tab | Cùng cookie dùng chung deadline; foreground/background riêng; reload đổi tabId, metadata cũ bị dọn; đóng tab không giữ online mãi |
| Nhiều phiên | Cùng account hai context: idle một phiên không logout phiên kia; lock account đóng cả hai |
| Concurrency | touch/expire cùng lúc chỉ một kết quả; session destroyed/sweep/lock idempotent; request nhận trước hạn có thể commit, request sau hạn bị chặn |
| HTTP/WS | Chưa có socket vẫn idle; socket đã mở không giữ session vô hạn; heartbeat quá hạn bị từ chối; cookie expired không re-handshake |
| Quyền | Anon/TEACHER/STUDENT không xem admin; stale/locked admin bị từ chối; context foreign không lộ metadata; không raw session ID/keys/token |
| Realtime | Đổi chức năng khi user count không đổi; thay thành viên cùng số lượng; mất invalidation/reconnect được GET bù; event storm được gom |
| M06 | Dirty local khi hết phiên; pending draft và submit mất ACK; login cùng user replay đúng key/payload; đổi user không hydrate bản trước |
| Lỗi | Activity timeout/429/503; IDB quota; callback auth cũ; admin GET lỗi giữ stale; phiên kết thúc giữa mở drawer và refresh |
| M01 | Khóa/xóa/đổi quyền sau commit; rollback không thu hồi sai; không tự khóa hoặc phá admin cuối cùng; mở khóa không phục hồi phiên cũ |
| Màn hình | Một panel, nhiều tab điều hướng không leak timer/socket; keyboard/mobile; online khác ACTIVE, không tạo trạng thái thi giả |

Backend dùng Clock có điều khiển, MySQL Testcontainers cho transaction/quyền. Client harness kiểm tra timer/epoch/coalesce; browser dùng session/socket/API thật trong stack test. Timeout test rút ngắn bằng env **chỉ trong stack test** và có ít nhất một ca mặc định 300s khi nghiệm thu; không sửa `.env` demo hoặc dùng chờ 5 phút cho mọi test.

## 8. Cấu hình, rollout và bàn giao

Đề xuất env: `SESSION_IDLE_TIMEOUT_SECONDS=300`, `SESSION_IDLE_WARNING_SECONDS=30`, `SESSION_IDLE_SWEEP_MS=5000`; activity coalesce 10s và admin/session poll 15s có hằng/config client rõ. Validate giá trị dương, warning nhỏ hơn timeout, không âm/overflow. Môi trường test có profile riêng; không để cấu hình test lọt vào demo.

Không đổi migration V1–V13 của nền `764d846`, schema chấm/Submission/receipt/XP. Build backend mới có thể áp dụng migration đã có nhưng kế hoạch này không cho phép reset volume hay nâng demo để thử. Khi được yêu cầu chạy demo, thông báo việc rebuild/restart làm mất session RAM; dữ liệu đã commit vẫn giữ theo nền hiện tại.

Thứ tự rollout dev: backend contract/idle → client tracker/reason → API giám sát → UI → hồi quy. Triển khai backend và client khớp nhau trước bật policy ở môi trường dùng thật; backend 5 phút với client chưa báo activity sẽ logout người đang thao tác. Không có phương án “client cũ tự tương thích” mặc định.

Commit tham khảo theo phần hoàn chỉnh (chỉ khi được yêu cầu):

1. `feat(session): enforce inactivity timeout and track session activity`
2. `feat(session): add activity reporting and idle recovery`
3. `feat(admin): monitor active sessions and user activities`
4. `test(session): verify idle expiry and monitoring integration`

Không công bố đảm bảo realtime tuyệt đối hoặc phát hiện người thật. Hoạt động màn hình là tín hiệu tự báo có xác minh giới hạn; online là presence theo heartbeat của một backend. Kết quả nghiệm thu cần nêu rõ các cửa sổ timeout/coalesce/poll và giới hạn này.
