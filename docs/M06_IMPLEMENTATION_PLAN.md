# Kế hoạch triển khai M06 — Kết nối và phục hồi lỗi

Ngày lập: **21/10/2026**. Nhánh `feature/m06-connection-recovery`, nền Git **`57f2951`**: đã gồm M03, bản sửa khôi phục audio `0bf107f` và M04. Kế hoạch được cập nhật theo triển khai; bằng chứng kiểm thử nằm trong biên bản, không suy ra chỉ từ checkbox.

Cập nhật triển khai: P0 bên dưới đã được commit tại `3956855`; bản sửa concurrency ngày 23/10 chưa commit/push. Số liệu, thay đổi và giới hạn tại [tiến độ](M06_PROGRESS.md), [biên bản](../statics/results/module-6-validation.md). Checkbox là phạm vi đã triển khai/đối chiếu, không thay kết quả chạy từng nhánh của ma trận.

## 1. Mục tiêu và nguồn đặc tả

M06 giúp học viên tiếp tục một lượt đã tải khi mạng gián đoạn, lưu nháp tại thiết bị, khôi phục sau reload và đồng bộ khi có mạng/quyền hợp lệ. WebSocket phát hiện kết nối hỏng, theo dõi presence và báo dữ liệu thay đổi; HTTP/MySQL vẫn là nguồn nghiệp vụ chính thức.

Áp dụng cho **LEARN, PRACTICE và REVIEW**, không thay luật chọn câu, chấm, FIRST/final/CORRECTION hoặc lịch ôn M04.

| Nguồn | Đọc để xác định |
|---|---|
| [Kế hoạch tổng](../PLAN_ENGLISH_LEARNING_SYSTEM.md), M06 và phần giao tiếp mạng | Heartbeat, reconnect, presence, IndexedDB và tiêu chí phục hồi |
| [API M01](../source/common/API.md), mục WebSocket | CONNECTED/HELLO/HEARTBEAT/ACK/ERROR, origin, cookie và thu hồi phiên |
| [Quy tắc M02–M03](../source/common/M02_M03_API.md), [OpenAPI](../source/common/openapi-m02-m03.json) | Receipt, revision, snapshot, quyền và transaction |
| [API M04](../source/common/M04_API.md), [tiến độ M04](M04_PROGRESS.md) | Review sync, ledger, pendingSubmissions, REVIEW_SYNC_REQUIRED |
| [Nghiệm thu M03](../statics/results/module-3-validation.md), [M04](../statics/results/module-4-validation.md) | Bằng chứng có sẵn và giới hạn; không tính là test M06 đã chạy |

Không sao chép toàn bộ OpenAPI vào kế hoạch. Bước 0 lập hợp đồng M06 bổ sung, đối chiếu code và chốt schema trước khi triển khai phần phụ thuộc.

## 2. Hiện trạng đã đối chiếu

| Thành phần | Đã có | Phần cần bổ sung |
|---|---|---|
| `App.tsx` | WebSocket sau login, heartbeat 30 giây, backoff tới 30 giây cộng jitter | Tách connection manager; kiểm tra CONNECTED/ACK, heartbeat deadline, recovery khi reconnect |
| `AccountSocketHandler` | Cookie/origin/current actor; ACK HELLO/HEARTBEAT; message tối đa 4 KiB | Theo dõi heartbeat hợp lệ, subscription riêng tư, invalidation sau commit |
| `LiveConnections` | Theo dõi HTTP session/socket và thu hồi sau commit | Presence theo user/nhiều socket, timeout và dọn kết nối lỗi |
| `WebSocketConfig` | Idle timeout 75 giây | Cấu hình heartbeat/timeout nhất quán; idle TCP không thay heartbeat nghiệp vụ |
| `AttemptSession` | Nháp/pending trong RAM, generation, replay rồi GET, 401/409 và audio restore | IndexedDB, hydration, journal bền vững, tự lưu và điều phối nhiều tab |
| `api.ts` | Cookie/CSRF, timeout 15 giây, mã lỗi | Giữ nguyên adapter; bổ sung tín hiệu phục hồi, không retry mutation đại trà |
| `PersonalReview` | Sync theo batch, pending requestId trong React ref | Giữ pending sync qua reload; phục hồi ledger/dashboard sau submit/reconnect |

Backend hiện có Flyway **V1–V5**. M06 P0 dự kiến không thêm bảng backend: presence/subscription là dữ liệu tạm; nháp/outbox tại máy dùng IndexedDB. Nếu phát hiện cần dữ liệu server mới, giải thích trước và dùng migration mới, không sửa V1–V5.

## 3. Phạm vi và các quyết định triển khai

### P0 — phải có

- Kết nối có trạng thái rõ: đang kiểm tra, đã kết nối socket, gián đoạn socket, đang phục hồi, cần đăng nhập. Socket hỏng không đồng nghĩa HTTP/Internet đều hỏng.
- Heartbeat có requestId/ACK và deadline; reconnect backoff/jitter, không sinh nhiều timer/socket từ cùng manager.
- Presence online khi user còn ít nhất một socket đã xác thực và heartbeat còn hợp lệ; đóng một tab không làm offline toàn user.
- Subscription cho Attempt của chính user; thông báo chỉ để vô hiệu hóa bản đọc, GET lấy dữ liệu chính thức. Không truyền đáp án/keys/transcript qua socket.
- IndexedDB lưu bản nhập, snapshot học viên, thông tin đồng bộ và pending command theo user/Attempt; cách ly bản nhập từng tab.
- Tự lưu tại máy và đồng bộ draft lên server khi đủ điều kiện; vẫn có nút lưu chủ động. Phân biệt dữ liệu đang nhập, đã lưu tại máy, đang đồng bộ, đã lưu trên server, chờ nộp và đã nộp.
- Phục hồi đúng requestId/payload cho create/draft/hint/check/submit/retry và M04 review sync; không tạo tác dụng lần hai sau crash hoặc mất phản hồi.
- Xử lý 401/403/404/409, storage lỗi, nhiều tab, mất mạng/bật lại và server restart.

### Ngoài P0

- Không hứa mở ứng dụng lần đầu hoặc đăng nhập lần đầu hoàn toàn offline. Không thêm service worker/PWA hoặc tải trước toàn bộ ngân hàng.
- Không đảm bảo file chưa tải sẽ phát offline; không tạo cache công khai cho asset có quyền. P0 giữ metadata và vị trí audio, tận dụng phần bytes trình duyệt thực sự có.
- Không thêm Redis/STOMP/broker, nhiều backend, chat, presence chi tiết của mọi tài khoản hoặc dashboard thống kê M07.
- Không thêm XP/streak/giải đấu M05; nếu M05 tích hợp sau, hiệu ứng phải dựa Submission/ledger bền vững, không dựa socket.
- Không tự queue upload, mutation quản trị/biên soạn hoặc tự tạo lượt mới khi offline.

### Chính sách P0 cần giữ

1. Chỉ báo lưu tại máy sau transaction IndexedDB hoàn tất; chỉ báo lưu server sau ACK hợp lệ và đối soát. Không dùng `navigator.onLine` hoặc socket open làm bằng chứng request đã thành công.
2. Cửa sổ nhập chưa kịp ghi IndexedDB vẫn tồn tại: debounce tại máy dự kiến 250 ms, lưu ngay khi rời câu/blur và trước hành động quan trọng. Cảnh báo rời trang khi còn dữ liệu chưa commit tại máy; không hứa flush bất đồng bộ luôn xong lúc đóng tab.
3. Autosave server dự kiến debounce 1.500 ms khi hết nhập và có quyền, không conflict/pending; nút lưu bỏ qua chờ debounce. Chỉ gộp bản nhập chưa đóng thành command. Command đã ghi journal/gửi phải bất biến.
4. Tối đa một command chưa đối soát trên mỗi Attempt; bản nhập mới hơn vẫn được giữ riêng. Không tạo một hàng outbox cho mỗi phím bấm.
5. Submit cần thao tác/xác nhận rõ của user. Nếu offline, có thể ghi một yêu cầu nộp đã xác nhận vào journal và hiện “Chờ nộp”; khóa sửa lượt cho tới đối soát. Không tự nộp chỉ vì draft đã đủ câu. Hint/check/chữa cần phản hồi server: vô hiệu hóa khi đã biết HTTP không khả dụng, không chấm cục bộ.
6. Cùng user đăng nhập lại được khôi phục bản riêng. Đăng xuất chủ động xác nhận bỏ phần chưa đồng bộ rồi xóa dữ liệu cục bộ của user, hủy timer/lane; đổi user tuyệt đối không hydrate/gửi bản của user trước.
7. Sau reload phải xác minh `/api/auth/me` trước khi mở dữ liệu riêng hoặc replay. Nếu reload khi offline, hiện cổng chờ xác thực; không lấy userId trong storage làm bằng chứng phiên còn hợp lệ. Việc làm tiếp offline áp dụng cho màn đã mở và đã xác thực trước đó.
8. IndexedDB là bản sao trên thiết bị, có thể bị trình duyệt xóa/thiếu quota; không thay backup DB hoặc được mô tả như kho bảo đảm không mất dữ liệu.

## 4. Thiết kế dữ liệu cục bộ và vòng đời command

Tên thành phần dưới đây là đề xuất để triển khai, không phải file đã tồn tại:

```text
features/connection/        manager, heartbeat, reconnect, recovery coordinator
features/learning/storage/ IndexedDB schema, repository, hydration, retention
features/learning/sync/    journal, lane theo Attempt, lease và cross-tab notices
```

| Store/khóa đề xuất | Nội dung tối thiểu |
|---|---|
| `attemptSnapshots` — userId + attemptId | Snapshot học viên hợp lệ, revision/state, thời điểm xác nhận server, schemaVersion |
| `workingDrafts` — userId + attemptId + writerId | Full answers/playerState, baseRevision, generation, persistedGeneration, updatedAt |
| `commands` — userId + requestId | Intent/kind, attemptId nếu có, method/path, payload nguyên bản, generation, trạng thái, ACK nếu đã nhận |
| `leases` — userId + laneKey | Holder, fencing token và thời hạn; điều phối sender cùng origin |

Thiết kế thực tế gộp attemptSnapshots và workingDrafts cùng pending/pendingAck của Attempt thành một hàng `workingDrafts` để chốt nguyên tử; store `commands` dành cho create/review-sync. Dùng Web Locks khi có, lease IndexedDB dự phòng và storage event cho logout; không cần BroadcastChannel để truyền dữ liệu học. Chi tiết schema/trạng thái thực tế ở [hợp đồng M06](../source/common/M06_API.md).

`writerId` nhận diện bản nhập của một tab, giữ qua reload của tab bằng sessionStorage chỉ chứa ID. Tab mới có writerId mới. Bản cũ mồ côi được nhận lại qua luồng khôi phục có lựa chọn; không để hai tab ghi đè cùng một draft row. Pending create chưa có Attempt ID dùng intentId riêng, không tự suy ra từ route.

- Chỉ persist DTO học viên/feedback đã được server cho phép ở thời điểm nhận; không cache DTO tác giả hoặc PublishedContentReader chứa keys trước nộp. Schema feedback cần thiết cho phục hồi check/hint phải chốt ở bước 0, không tự CHECK lại để dựng giao diện.
- Không lưu password, cookie, CSRF token, HTTP session ID hoặc storageKey. Mỗi operation khi gửi lấy cookie/CSRF hiện hành từ adapter chung.
- Không persist URL/method tùy ý để phát lại: validate kind, route thuộc whitelist và user/Attempt trước dispatch; schema cũ hoặc record hỏng phải cách ly, không tự gửi.
- Dọn cache sạch theo tuổi/kích thước cấu hình; không tự xóa dirty draft hoặc pending command. Cho user xóa dữ liệu sau cảnh báo. Giữ giới hạn receipt hiện hành; không tự TTL receipt làm replay mất an toàn.

Luồng ghi chuẩn:

```text
Nhập → commit workingDraft → đủ điều kiện đồng bộ
  → lấy lane, GET/đối chiếu khi cần → đóng command đúng revision
  → commit journal trước HTTP → gửi đúng key/payload
  → nhận ACK, persist ACK → GET snapshot/result/progress cần thiết
  → transaction chốt baseline + trạng thái command
  → giữ bản nhập generation mới hơn hoặc hiển thị conflict
```

Crash sau server commit nhưng trước persist ACK vẫn replay command gốc. Crash sau ACK nhưng trước GET vẫn tiếp tục đối soát. Không xóa command chỉ vì nhận HTTP 200, nếu baseline chưa xác nhận hoặc GET còn lỗi. GET thấy SUBMITTED không đủ để tự suy ra request của mình thắng; dùng receipt replay để biết kết quả command cụ thể.

## 5. Giao tiếp, presence và phục hồi

### WebSocket

- Giữ `version: 1` và CONNECTED/HELLO/HEARTBEAT/ACK/ERROR hiện có. Bổ sung có tài liệu, không đổi ý nghĩa ACK thành ACK nghiệp vụ học.
- Đề xuất SUBSCRIBE/UNSUBSCRIBE với Attempt ID; ACK subscription sau kiểm tra current actor/owner. Resource không có quyền trả lỗi không lộ tồn tại. Giới hạn số subscription/socket, kích thước/tần suất message.
- Đề xuất `ATTEMPT_CHANGED { attemptId, revision }`, chỉ gửi cho owner đang subscribe. Không gửi answer/result hoặc dữ liệu riêng của user khác.
- Phát invalidation sau transaction commit cho mutation thật, không phát từ replay/GET hoặc trước commit. Mất sự kiện sau commit là tình huống chấp nhận được vì recovery GET không phụ thuộc event.
- Subscriber đăng ký trước rồi GET; giữ revision lớn nhất đã nghe. Nếu event đến trong lúc GET và báo revision mới hơn thì GET lại có gộp lượt; bỏ event trùng/cũ, không giảm baseline.
- Timer manager có generation/cancel để callback của socket cũ không tác động user/socket mới. Reset backoff sau handshake/ACK hợp lệ, không chỉ sự kiện TCP open.

### Tham số demo ban đầu

| Tham số | Đề xuất và cách kiểm tra |
|---|---|
| Heartbeat interval | 25 giây, requestId riêng; server chỉ cập nhật liveness với message hợp lệ |
| ACK deadline | 10 giây; quá hạn chuyển sang nghi ngờ kết nối, đóng/reconnect có kiểm soát |
| Presence expiry | 75 giây từ hoạt động xác thực hợp lệ; sweep khoảng 5 giây; test dùng clock/cấu hình ngắn |
| Backoff | Base 1 giây, trần 30 giây, jitter ngẫu nhiên có giới hạn; inject RNG/clock trong test |
| HTTP timeout | Giữ 15 giây hiện tại trừ khi có lý do/hợp đồng mới |
| GET dự phòng | Khi màn học đang mở và socket gián đoạn: tối đa một lượt mỗi khoảng 30 giây, cùng sự kiện focus/online; có gộp và backoff |

Đưa tham số backend vào application.yml, frontend vào một cấu hình có giới hạn hợp lệ. Đối chiếu idle timeout/nginx 75 giây; không coi browser tab nền bị throttle là lỗi quyền. Presence có độ trễ, không lấy số socket làm số user.

Presence P0 quan sát qua endpoint riêng của chính user, đề xuất `GET /api/me/connections`: online, activeConnectionCount và thời điểm đo; không trả session/socket ID hoặc danh sách người khác. Dashboard/admin tổng hợp để M07, không mở API presence công khai.

### Khi nối lại

1. Kiểm tra phiên hiện hành; 401 dừng replay, giữ bản cục bộ cho đúng user đăng nhập lại. 1008 phải kiểm tra `/api/auth/me`, không quay vòng reconnect vô hạn khi đã bị thu hồi.
2. Socket hoạt động thì đăng ký lại tài nguyên được phép; HTTP hoạt động vẫn cho đồng bộ nếu socket đang lỗi.
3. Hydrate/validate bản cục bộ, lấy lane, đối soát pending command gốc. GET trước có thể cung cấp trạng thái nhưng không thay chứng cứ receipt.
4. GET Attempt/Result mới nhất, so baseRevision và generation; nếu có khác biệt chưa được command giải thích thì hiển thị hai bản để lựa chọn.
5. Chỉ sau khi giải quyết pending/conflict mới tạo autosave command cho draft mới hơn. User chọn giữ bản nhập không đồng nghĩa tự gửi đè.
6. Sau submit đã xác nhận, chạy review sync M04 theo batch hữu hạn; pending sync dùng đúng key/payload. Còn remainingSubmissions thì báo đang cập nhật/cho tiếp tục, không tự dựng REVIEW khi REVIEW_SYNC_REQUIRED.

## 6. Các bước triển khai và cổng kiểm tra

### Bước 0 — Chốt hợp đồng, baseline và tình huống lỗi

Phụ thuộc: nền `57f2951`, thư mục làm việc sạch hoặc đã phân biệt diff của user.

- [x] Ghi baseline các test M01/M03/M04 hiện tại, phiên bản công cụ và cách tái hiện; không dùng số test lịch sử thay một lượt chạy mới.
- [x] Tạo `source/common/M06_API.md`: envelope, subscription, presence, error/close code, timeout và quyền. Chỉ thêm schema/OpenAPI cho endpoint/message bổ sung, không chép lại toàn bộ M03.
- [x] Chốt schema IndexedDB/version upgrade, trạng thái command và recovery từng điểm crash; quyết định cache feedback an toàn.
- [x] Ghi fixture ma trận 401/403/404/409/503, timeout sau commit, IndexedDB abort/quota, nhiều tab/user và server restart.
- [x] Chốt mặc định autosave, giới hạn queue/cache/subscription và thứ tự lock; ghi dữ liệu nào được giữ/xóa sau logout/401.

Cổng: mỗi tình huống xác định được bản chính thức, dữ liệu giữ tại máy, command được replay và điều kiện cho phép ghi tiếp.

Commit đề xuất: `docs(m06): define connection and recovery contracts`.

### Bước 1 — Backend heartbeat, timeout và presence

Sửa chính: AccountSocketHandler, LiveConnections, WebSocketConfig; bổ sung service/controller presence đúng kiến trúc hiện hành.

- [x] Theo dõi thời điểm heartbeat hợp lệ theo socket, cleanup close/error/expiry; gửi qua decorator thread-safe.
- [x] Giữ current actor check và thu hồi session/socket sau commit; cleanup không phụ thuộc client gửi close sạch.
- [x] Tính presence unique user từ các socket còn hợp lệ; thêm GET presence của chính user.
- [x] Cấu hình và validate tham số; heartbeat sai, flood hoặc message không hợp lệ không kéo dài presence vô hạn.
- [x] Test handshake origin/cookie, ACK đúng requestId, im lặng quá hạn, hai tab/một tab đóng, hai user, lock/logout/session expiry.

Cổng: test server thật RANDOM_PORT/Testcontainers và clock kiểm soát; presence đúng sau timeout và thu hồi, không ảnh hưởng tài khoản khác.

Commit đề xuất: `feat(m06): track heartbeat liveness and user presence`.

### Bước 2 — Client connection manager và subscription

- [x] Tách effect socket khỏi App.tsx; có một manager theo active user và state quan sát được.
- [x] CONNECTED/ACK deadline, backoff/jitter, hủy timer/socket cũ; focus/visibility/online chỉ là trigger kiểm tra.
- [x] Backend subscription/invalidation sau commit; quyền theo owner, bỏ subscription khi phiên mất; không emit khi rollback/replay.
- [x] Đăng ký rồi GET có đối soát revision; fallback HTTP khi WebSocket lỗi, không khóa việc học chỉ vì socket chưa nối.
- [x] Test fake clock/RNG cho state machine; E2E server/proxy thật cho close/reconnect và thu hồi quyền. Không để reconnect tạo Attempt mới.

Cổng: mất socket rồi nối lại tải đúng Attempt, không tạo vòng lặp API, không nhận event của owner khác.

Commit đề xuất: `feat(m06): reconnect sockets and reconcile attempt notifications`.

### Bước 3 — IndexedDB, hydration và cách ly dữ liệu

- [x] Implement repository storage nhỏ có transaction và nâng version; mặc định dùng IndexedDB native, không thêm dependency nếu chưa có lợi ích cụ thể.
- [x] Ghi workingDraft tự động; persist generation và baseline; snapshot qua đúng DTO học viên.
- [x] Khôi phục sau reload chỉ sau xác minh user; chọn lại bản tab mồ côi rõ ràng, không ghi đè tab đang sửa.
- [x] Xử lý unavailable/quota/abort/blocked upgrade/record hỏng: giữ RAM, hiện chưa lưu tại máy, vẫn cho lưu server khi có mạng.
- [x] Logout/đổi user có generation fence, hủy tác vụ cũ; DB xóa lỗi phải khóa truy cập/replay dữ liệu cũ và báo rõ, không giả vờ đã xóa.
- [x] Test bằng IndexedDB trình duyệt thật: reload/khôi phục bản tab khác, hai user, khởi tạo schema v1, record hỏng và lỗi storage. Chưa có migration v1→v2; blocked-upgrade/policy chặn xóa được ghi rõ chưa ép độc lập trong biên bản.

Cổng: câu trả lời/playerState đã báo lưu tại máy khôi phục chính xác; bản chưa commit không được báo đã lưu; user khác không thấy hoặc phát lại được.

Commit đề xuất: `feat(m06): persist learner drafts in IndexedDB`.

### Bước 4 — Journal bền vững và điều phối nhiều tab

- [x] Persist command nguyên bản trước fetch; khôi phục pending create và review sync cùng các mutation Attempt.
- [x] Trạng thái command thể hiện qua PREPARED/ACKED cho create/sync và pending/pendingAck/conflict/loginRequired cho Attempt; chỉ clear pending sau GET và chốt baseline bền vững. Không cần persist một enum trùng các flags này.
- [x] Lane/lease lấy bằng transaction IndexedDB, kết hợp Web Locks; storage event chỉ báo logout, không là khóa/kênh phát đáp án. Invalidation dữ liệu do socket và GET đối soát.
- [x] Fencing token và re-check lease trước gửi; sender cũ có thể còn request đang bay: server revision/receipt vẫn là hàng rào cuối, không hứa client đạt exactly-once.
- [x] Không để tab B lấy baseline tab A rồi gắn vào draft cũ để gửi đè; conflict phải so với baseRevision của chính working copy.
- [x] Mất lease/crash/reload: replay cùng key; hai sender cùng command không tạo hiệu ứng đôi. Lỗi journal trước send thì không phát mutation chưa được ghi bền vững trong luồng bảo đảm phục hồi; cho người dùng xử lý storage, không đổi sang key khác âm thầm.

Cổng: đóng tab tại từng ranh giới journal/fetch/ACK/GET, mở lại khôi phục được cùng key/payload; hai tab không xóa pending hoặc bản nhập của nhau.

Commit đề xuất: `feat(m06): recover durable commands across reloads and tabs`.

### Bước 5 — Autosave, offline và tích hợp M03/M04

- [x] Nối storage/journal vào AttemptSession, runner, player và App; giữ generation bảo vệ đáp án mới hơn ACK.
- [x] Local autosave và server debounce theo quyết định mục 3; flush chủ động không tạo command song song.
- [x] Offline giữ nội dung đã tải, nhập và audio position; hint/check/feedback không có server không được giả lập thành công.
- [x] Hiển thị trạng thái cục bộ/server/nộp độc lập với socket; nút thử lại/đối chiếu/đăng nhập đủ rõ trên mobile và bàn phím.
- [x] Hết phiên rồi đăng nhập lại cùng user khôi phục pending; đổi user/logout không replay phần cũ. 403/404 do mất quyền đóng tài nguyên, không retry vô hạn hoặc lộ snapshot cũ cho user khác.
- [x] Giữ lựa chọn 409; Attempt đã SUBMITTED ở nơi khác khóa sửa và mở kết quả chính thức, không tự nộp lần nữa.
- [x] M04 sync bền vững, batch hữu hạn; replay không tăng ledger/progress/evidence hai lần. Không sửa thuật toán ReviewPolicy.

Cổng: LEARN/PRACTICE/REVIEW chạy từ nhập → lưu cục bộ → mạng trở lại → lưu server → nộp → chữa; F5 và reconnect không đổi bộ câu/audio. Khi storage lỗi, giao diện không hứa dữ liệu đã lưu tại máy.

Commit đề xuất: `feat(m06): autosave drafts and recover learning sessions offline`.

### Bước 6 — Nghiệm thu, tài liệu và bàn giao

- [x] Chạy ma trận mục 7; sửa lỗi và hồi quy M01–M04 vì thay App/auth/adapter/session/socket dùng chung.
- [x] Script môi trường riêng tạo draft/pending/submission/review ledger, restart server và recreate giữ volume; đăng nhập lại đối soát chính xác.
- [x] Thêm `docs/M06_PROGRESS.md`, `docs/M06_MANUAL_TEST_CASES.md`, `statics/results/module-6-validation.md`; ghi số pass/fail/skip và phạm vi fixture thật.
- [x] Cập nhật README/AGENTS/API/kiến trúc theo phần thực sự đã xong; giữ log/ảnh/IndexedDB dump/credentials ngoài Git.
- [x] Kiểm tra diff/link/whitespace và chuẩn bị mô tả PR trong cuộc trao đổi. Không tự commit/push/tạo PR khi chưa được yêu cầu; không tạo file mô tả PR riêng.

Commit đề xuất: `test(m06): verify offline recovery and cross-tab consistency`; `docs(m06): record recovery behavior and validation`.

## 7. Ma trận kiểm thử bắt buộc

| Nhóm | Tình huống phải ép | Đối chiếu sau phục hồi |
|---|---|---|
| Heartbeat | ACK mất/chậm/sai ID; TCP mở nhưng application không ACK; message sai | Timeout/reconnect đúng, một manager, presence hết hạn |
| Presence | Hai tab cùng user, đóng một tab, tab chết không close; user khác/logout/lock | Unique user và active count đúng, không ảnh hưởng user khác |
| Subscription | Owner khác, ID lạ, giới hạn subscription; event trùng/cũ, event trong lúc GET | Không lộ dữ liệu; baseline không lùi, GET bù được event mất |
| Offline đang học | Sau local commit tắt mạng; nhập ba dạng, đổi câu, vị trí audio | Chỉ nội dung đã tải dùng tiếp; nháp/local generation giữ đúng |
| Reload/đóng mở | Draft đã persist; crash trước/sau journal, server commit, ACK, GET | Khôi phục đúng user/Attempt, key/payload, không ghi/nộp hai lần |
| Storage lỗi | Quota/unavailable/abort/blocked upgrade/record sai schema/xóa lỗi | Không báo đã lưu giả, không phát record tùy ý; giữ RAM/cảnh báo |
| Đồng thời | Hai tab cùng revision, lease hết hạn, sender cũ còn HTTP; hai thiết bị | Không silent overwrite; 409 giữ working copy; một Submission |
| Timeout/503 | Commit thật rồi bỏ ACK hoặc trì hoãn vượt 15 giây; GET sau ACK lỗi | Replay receipt gốc; không bỏ pending sớm, generation mới không mất |
| Auth | 401, socket 1008, lock/đổi role, logout khi có pending, user khác login | Dừng ghi khi mất quyền, không lẫn cache/outbox hoặc reconnect vô hạn |
| Submit/chữa | Chờ nộp offline, submit khác tab, replay sau CORRECTION | FIRST/final/Submission/điểm không đổi sai; không tự submit draft |
| M04 | Sync ACK mất, reload giữa batch, còn pendingSubmissions, REVIEW_SYNC_REQUIRED | Ledger/evidence/stage không cộng đôi; REVIEW đúng phiên bản đã chọn |
| Restart/seed | Restart phiên RAM mất; recreate cả ba container cùng volume; seed lại | Nháp/kết quả/progress/review ledger/file/receipt giữ nguyên |
| UI/hồi quy | Desktop/mobile/keyboard, nhiều route, M01–M04 | Không tràn, không báo thành công giả, auth/authoring vẫn chạy |

Test thời gian dùng clock/RNG kiểm soát; test lock/concurrency dùng barrier/latch hoặc giữ request để ép giao nhau. Dùng Playwright `context.setOffline` cho mất mạng thực ở browser; abort/delay response sau commit là fixture riêng, ghi rõ khác biệt. Không gọi fixture này là đo tải, hỏng ổ đĩa hoặc mất gói TCP vật lý.

## 8. Cách chạy và bằng chứng

Từ `source/server`: nhóm socket/presence mới cùng AccountAdministrationApiTest/AttemptApiTest/PersonalReviewApiTest khi sửa phần liên quan; chạy toàn bộ khi tích hợp. Dùng Java 17 và MySQL Testcontainers riêng hoặc Docker target test build mới từ source.

Từ `source/client`:

```powershell
npm run build
npm run test:e2e -- module-six-recovery.spec.ts
# Cuối tích hợp, chạy toàn bộ hồi quy:
npm run test:e2e
```

File E2E M06 được tạo ở bước triển khai, lệnh trên là mục tiêu tái hiện. IndexedDB/clock test có thể dùng Playwright và harness riêng; không tự thêm framework chỉ để mirror implementation.

E2E dùng project test riêng 5174/8081 theo runner hiện có. Script restart cần môi trường riêng được đặt tên và giữ volume, không restart demo hoặc trỏ E2E_BASE_URL vào 5173. Không `down -v`/truncate để làm test qua. Log, report, ảnh và dữ liệu trình duyệt đặt trong thư mục ignored; báo cáo commit chỉ chứa số liệu và mô tả đã kiểm tra.

## 9. Điều kiện hoàn thành

- [x] Tắt mạng/bật lại, reload và restart không làm mất bản đã xác nhận lưu trong phạm vi đã cam kết.
- [x] Nháp tại máy, ACK server và Submission được phân biệt; lỗi storage/network/auth không tạo thành công giả.
- [x] Pending command bền vững; replay giữ key/payload; không có Submission/trial/receipt hoặc review ledger tăng trùng do gửi lại.
- [x] Hai tab/thiết bị có conflict rõ, không silent rebase hoặc âm thầm ghi đè.
- [x] Presence/heartbeat/reconnect/subscription đúng quyền, có timeout và giới hạn; mất socket không cản HTTP đang hoạt động.
- [x] LEARN/PRACTICE/REVIEW, audio, FIRST/final/CORRECTION và M04 sync giữ đúng luật hiện hành.
- [x] Toàn bộ kiểm tra phù hợp đạt và phần chưa kiểm tra được ghi rõ; tài liệu phản ánh code thực tế.

Thứ tự thực hiện: **0 → 1 → 2 → 3 → 4 → 5 → 6**. Mỗi bước kết thúc bằng diff và bằng chứng đúng phạm vi; kế hoạch không tự cho phép tạo commit hoặc push.
