# M06 — Kết nối và phục hồi, hợp đồng bổ sung v1

Ngày 21/10/2026, nền M03/M04 `57f2951`. Giữ nguyên [API M03](M02_M03_API.md), [M04](M04_API.md) và session/CSRF M01. Không thêm JWT, bảng SQL hoặc sửa migration V1–V5.

## 1. Presence riêng tư qua HTTP

`GET /api/me/connections`: user đang đăng nhập, mọi vai trò; AccountGuard kiểm tra trạng thái/security_version hiện hành. Không nhận userId từ client. Response 200, `Cache-Control: private, no-store`:

```json
{"online":true,"activeConnectionCount":2,"measuredAt":"2026-10-21T00:00:00Z"}
```

Đếm socket đã xác thực còn mở/chưa hết liveness của chính user. Hai tab là hai kết nối của một user; đóng một tab không làm offline nếu còn tab hợp lệ. Không trả session/socket ID, email hoặc presence của người khác. 401 theo hợp đồng auth hiện hành. [OpenAPI bổ sung](openapi-m06.json).

## 2. WebSocket `/ws/events`

Cookie đăng nhập, allowlist origin, kiểm tra current actor ở handshake và khi nhận message. Envelope version=1, text tối đa 4 KiB; requestId là chuỗi không rỗng, tối đa 64 ký tự. CONNECTED/HELLO/HEARTBEAT/ACK/ERROR giữ tương thích M01.

| Hướng | Message | Ý nghĩa |
|---|---|---|
| Server → client | `CONNECTED {version,type,userId}` | Danh tính socket đã xác thực; client còn phải nhận HELLO ACK để báo kết nối sẵn sàng |
| Client → server | `{version:1,type:"HELLO" hoặc "HEARTBEAT",requestId}` | Xác nhận application vẫn hoạt động; server trả ACK cùng requestId |
| Client → server | `{version:1,type:"SUBSCRIBE",requestId,attemptId}` | Theo dõi Attempt do chính STUDENT sở hữu |
| Client → server | `{version:1,type:"UNSUBSCRIBE",requestId,attemptId}` | Bỏ theo dõi tài nguyên còn quyền; idempotent khi đã bỏ |
| Server → client | `{version:1,type:"ACK",requestId}` | Xác nhận message socket; không là ACK lưu/nộp bài |
| Server → client | `{version:1,type:"ERROR",requestId,code}` | INVALID_MESSAGE / UNSUPPORTED_TYPE / RESOURCE_NOT_FOUND / SUBSCRIPTION_LIMIT |
| Server → client | `{version:1,type:"ATTEMPT_CHANGED",attemptId,revision}` | Invalidation sau commit; GET lấy dữ liệu chính thức |

Attempt không tồn tại/khác owner/role không được cấp đều RESOURCE_NOT_FOUND. Giới hạn 8 subscription/socket, 120 message/phút/socket. Message sai không kéo dài liveness. Vượt rate hoặc hết liveness bị đóng và dọn khỏi presence; thu hồi phiên/quyền tiếp tục dùng 1008 SESSION_REVOKED theo M01.

Event chỉ có ID/revision, gửi cho socket của owner đã subscribe. Ghi Attempt thực sự qua repository phát sự kiện transaction; listener AFTER_COMMIT mới gửi. GET/replay không ghi aggregate nên không phát, rollback không phát. Event tạm trong RAM, không đảm bảo giao đủ hoặc theo thứ tự. Không dùng event làm ledger hay phát ACK nghiệp vụ trước commit.

Subscriber đăng ký → đợi ACK → GET; ghi nhận revision event lớn nhất và GET bù nếu event mới hơn bản vừa đọc. Bỏ event trùng/cũ. Mất socket không khóa HTTP. Focus/online/reconnect và polling có gộp hỗ trợ đối soát; không tự gọi create lại.

## 3. Tham số demo

| Tham số | Giá trị |
|---|---|
| Client heartbeat | 25 giây |
| CONNECTED/ACK deadline | 10 giây |
| Reconnect | Base 1 giây, exponential cap 30 giây, jitter 75–100% khoảng chờ |
| Server liveness | 75 giây, `WS_PRESENCE_TIMEOUT_MS` (500–300000 ms) |
| Server sweep | 5 giây, `WS_SWEEP_MS` |
| Client HTTP timeout | 15 giây theo adapter hiện có |
| Local save debounce | 250 ms |
| Server autosave debounce | 1500 ms, không conflict/pending/auth block |
| Đối soát dự phòng màn đang mở | 30 giây cùng trigger focus/online/reconnect |

Client tập trung cấu hình trong `features/connection/manager.ts`; debounce trong AttemptSession. Server cấu hình qua application.yml. Idle WebSocket/nginx vẫn 75 giây, không thay thế application heartbeat. Browser tab nền có thể bị throttle: presence có độ trễ, không mô tả là trạng thái tuyệt đối theo thời gian thực.

## 4. IndexedDB và journal

Database `english-learning-recovery`, schema version 1. Thiết kế thực tế gộp baseline, working copy và pending Attempt vào một hàng để ghi/chốt nguyên tử, thay vì nhân đôi chúng ở nhiều store:

| Store | Khóa/nội dung |
|---|---|
| workingDrafts | userId:attemptId:writerId; snapshot học viên, draft/playerState, result/feedback đã được cấp, generation, manualSaveRequired, pending operation và pendingAck |
| commands | userId:requestId; pending create hoặc review-sync, body nguyên bản, PREPARED/ACKED và ACK khi có |
| leases | userId:lane; holder/fence/expiry để phối hợp sender cùng origin |

Writer ID ở sessionStorage chỉ chứa định danh, không chứa đáp án. Web Locks nhận diện writer trùng do nhân tab và giữ exclusive lane khi hỗ trợ; fallback dùng lease IndexedDB 25 giây, renew 5 giây. Lease không thay server revision/receipt: request của sender đã chết vẫn có thể tới server.

Không Web Locks thì writer mới cho mỗi document, nhận lại bản cũ qua nút khôi phục thay vì tin ID được clone từ sessionStorage. Cache sạch được dọn khi xác minh user: tối đa 100 hàng sạch/user hoặc 30 ngày; dirty/pending/conflict không bị xóa để đủ số lượng. Record sai cấu trúc/command được chuyển sang khóa quarantine và không replay. Cached Result không dùng trực tiếp khi hydrate; đọc Result chính thức lại sau xác minh user.

Snapshot/feedback chỉ lấy từ API học viên theo thời điểm cho phép. Không lưu password/cookie/CSRF/HTTP session ID/storageKey hoặc DTO tác giả. Logout marker trong localStorage chỉ gồm ID user và cờ chặn; không phải token đăng nhập. Schema và route command được kiểm tra trước replay; không dispatch URL/method tùy ý từ storage.

Lưu tại máy chỉ được báo sau transaction complete. Trước mutation Attempt, persist full bản nhập + command bất biến; sau ACK persist pendingAck; sau GET đối soát persist baseline và clear pending. Nếu crash ở giữa, hydrate command cũ và replay cùng key/payload trước khi cho ghi tiếp. Create/review sync dùng journal tương tự; create còn GET Attempt trước clear command.

Nhận ACK cũ không được làm mất draft generation mới hơn. GET SUBMITTED không chứng minh command của mình thắng: replay receipt xác nhận operation cụ thể. 409 giữ hai bản, user chọn rõ; chọn giữ bản nhập yêu cầu bấm lưu, không tự autosave đè. CORRECTION không đổi FIRST/final/Submission/điểm.

## 5. Xác thực, mất mạng và storage lỗi

- App phải xác minh auth trước hydrate; reload khi không tải được app/không xác minh phiên được thì không tự mở cache riêng. Không có service worker hay đăng nhập offline.
- Offline khi màn đã mở: tiếp tục bản nội dung đã tải, local autosave; không hứa bytes audio chưa tải có sẵn. Submit chỉ ghi yêu cầu chờ khi user xác nhận và khóa sửa tới khi đối soát; không tự nộp từ draft.
- 401 giữ bản riêng và dừng ghi tới khi cùng user đăng nhập lại. Đổi user fence callback cũ. Logout chủ động cảnh báo, xóa dữ liệu riêng, thông báo tab khác qua marker; xóa lỗi giữ marker chặn đọc/replay tới khi dọn được.
- IndexedDB không khả dụng/quota/transaction abort: giữ RAM, cảnh báo chưa lưu tại máy; không gửi mutation qua luồng durable khi journal chưa commit. Bản server đã commit vẫn được đọc qua HTTP. Không âm thầm hạ cấp sang gửi không journal.
- Bản nhập của tab khác chỉ được nhận qua lựa chọn khôi phục; không ghi đè working copy của tab đang mở. Hai thiết bị không chia sẻ IndexedDB, server 409 vẫn là cơ chế đối soát cuối.
- M04 sync dùng requestId bền vững và ledger hiện hành, tối đa 10 batch/lượt gọi; submit đã xác nhận kích hoạt sync, lỗi sync không đổi kết quả nộp. Dashboard vẫn báo pendingSubmissions theo server.

IndexedDB có thể bị trình duyệt xóa; dirty draft/pending không được coi là backup đảm bảo. XP/M05 và dashboard presence M07 không được triển khai trong M06.
