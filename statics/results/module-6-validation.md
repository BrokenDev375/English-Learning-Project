# Nghiệm thu M06 — Kết nối và phục hồi

Ngày **21/10/2026**, nhánh `feature/m06-connection-recovery`, nền Git **`57f2951`**, source M06 chưa commit. Không sửa migration V1–V5, luật chấm M03 hoặc ReviewPolicy M04; không thêm dependency. Môi trường Windows, Node **24.15.0**/npm **11.14.0**, Chrome có sẵn qua Playwright; backend Java 17/Maven 3.9/ffmpeg trong Docker, MySQL 8.4 riêng.

## Kết quả thực tế

| Kiểm tra | Kết quả |
|---|---|
| Baseline frontend trước sửa | PASS, TypeScript/Vite, 54 modules |
| Baseline backend tập trung quyền/socket, Attempt, PersonalReview | **59/59 PASS**, 0 failure/error/skipped, 2 phút 52 giây |
| Backend tập trung sau phần nền M06 | **62/62 PASS**, gồm 3 LiveConnectionsTest |
| Backend toàn bộ sau thêm integration presence và event/rollback | **162/162 PASS**, 0 failure/error/skipped, BUILD SUCCESS, **9 phút 41 giây** |
| Frontend production build | PASS, 59 modules; không đổi lockfile/dependency |
| Harness connection manager dùng code thật transpile và fake clock/RNG/transport | **5/5 tình huống PASS**, gồm close 1006 khi phiên đã mất |
| E2E sau sửa lease/recovery | **62/62 PASS**, 3.8 phút |
| E2E sau thêm quarantine/retention | **64/64 PASS**, 0 failed/skipped, 4.3 phút; 9 M06 + 55 hồi quy |
| E2E sau bổ sung fallback không Web Locks và disable hỗ trợ offline | **65/65 PASS**, 0 failed/skipped, 4.1 phút; 10 M06 + 55 hồi quy |
| E2E sau guard snapshot hỏng trước hydrate | **66/66 PASS**, 0 failed/skipped, 4.4 phút; 11 M06 + 55 hồi quy |
| E2E bản bàn giao sau auth check close 1006 và logout xem pending trong DB | **66/66 PASS**, 0 failed/skipped, **4.4 phút**; 11 M06 + 55 hồi quy |
| Browser/IndexedDB qua restart và recreate giữ volume | **PASS**, cả 2 checkpoint, kiểm tra lại từ source có quarantine/retention |
| Kiểm tra tài liệu/cấu hình | PASS: Compose config, JSON, liên kết local, whitespace/fences, `git diff --check`; chỉ `.env.example`, không có `.env`/build/test artifact trong danh sách thay đổi |

Backend 162 ca gồm baseline M01–M04 157 ca và 5 ca bổ sung: 3 unit liveness/presence/routing, 1 HTTP presence/two-socket, 1 WebSocket integration về owner/commit/replay/rollback. Các lỗi constraint trong log là test ép rollback, không phải test failure.

Docker Desktop ban đầu chưa chạy; đã khởi động nền. Các lệnh Docker/browser chạy với quyền thực thi phù hợp khi sandbox Windows chặn pipe/tiến trình con. Không sửa ứng dụng để né lỗi môi trường.

## Bằng chứng theo nhóm

| Nhóm | Cách kiểm tra và điều đã xác nhận |
|---|---|
| Heartbeat/presence | Clock unit ép expiry/rate; HTTP hai socket cùng user 2→1, user khác không bị tính vào; cookie/origin/thu hồi phiên qua hồi quy M01 |
| Subscription/event | WebSocket thật subscribe owner/ID không có quyền; commit có event ID/revision, replay không có event, CHECK constraint ép receipt lỗi sau aggregate write thì không phát |
| Manager | Harness chạy source manager với fake clock: ACK deadline, subscription ACK, cap/jitter backoff, cancel timer, 1008/401 và 1006/401 dừng reconnect, ACK sai và callback socket cũ |
| Mất ACK socket | Playwright giữ server thật, bỏ ACK connection đầu; HTTP vẫn lưu, client reconnect sau deadline, không tạo Attempt mới |
| Offline/local save | `context.setOffline(true)` sau mở bài, sửa đáp án; IndexedDB commit rồi báo lưu tại máy, bật mạng/reload khôi phục và autosave cùng Version ID |
| Draft/create journal | Server commit thật rồi browser abort response; reload dùng payload/key gốc, revision/số lượt không tăng lần hai |
| Offline submit | User xác nhận rồi journal persist; trường trả lời bị khóa, reconnect/reload có một Result bất biến |
| Storage lỗi | Gây QuotaExceededError ở readwrite transaction, không phát draft chưa journal, giữ radio trong RAM và cảnh báo |
| Record/retention | Sửa pending thành URL logout, reload cách ly và không gọi logout; gieo 102 bản clean với một dirty cũ, prune clean và giữ dirty |
| Nhiều tab/audio | Socket báo conflict khi bản bên kia dirty; giữ bản local hoặc dùng server đều restore giây audio đúng; ACK chậm không đè vị trí mới |
| Auth/M04 | 401 rồi login lại, đổi user/logout không lẫn dữ liệu; replay review-sync không nhân ledger; LEARN/PRACTICE/REVIEW qua hồi quy hiện có |
| Browser restart/recreate | Script giữ IndexedDB và pending sau ACK mất, restart backend, cookie cũ hết hạn; login lại replay đúng command. Recreate giữ Result/progress/dashboard/ledger và số hàng DB |

Các ca thời gian manager dùng source thật với các cổng clock/transport/auth thay thế, không phải thử TCP. IndexedDB và các API nghiệp vụ trong browser test chạy thật. Lỗi HTTP 401/503/response abort/delay hoặc metadata clip được fixture rõ trong test M03/M04; không gọi là lỗi hạ tầng vật lý.

## Script restart và hiệu ứng một lần

`scripts/verify-m06-recovery.mjs` luôn dùng project **english-learning-m06-acceptance**, UI/API **5176/8083**, DB/file volume riêng. Tạo user mới mỗi lượt, không reset demo. Hai checkpoint trong `.test-state/m06-restart.json`:

1. `restart-with-lost-draft-ack`: GET snapshot bằng baseline sau đăng nhập lại; mọi replay gửi nguyên payload/key. Có thể có nhiều lượt replay do online event và reload; số hàng/revision giữ nguyên, không đòi HTTP chỉ gửi đúng hai lần.
2. `recreate-with-submission-and-review-ledger`: cả môi trường được force recreate giữ volume; đọc Result/progress/dashboard (bỏ riêng generatedAt khi so), ledger và số hàng như baseline sau drain sync; bytes audio giữ SHA-256 và Range bytes=0-15 trả 206 đúng 16 byte.

Script đối chiếu số hàng Attempt/Submission/trial/exposure/progress, ba bảng projection/ledger M04, receipt, Question và Lesson; không in đáp án/cookie/password vào báo cáo commit. Seed chạy lại không thêm bài/câu. Lượt bổ sung media đã so bytes/Range qua recreate, không phải thử mất ổ đĩa.

## Các lượt phát triển và điều chỉnh

- Hồi quy đầu 50/55: các test cũ chờ retry/nút lưu sau khi M06 đã tự replay hoặc nhận invalidation. Đã giữ các assertion dữ liệu, đổi trình tự để tab có bản dirty trước thay đổi remote và đợi recovery tự động.
- Nhóm 24 ca đạt 22: một lỗi thật là lease cũ còn sống sau reload trong lúc gửi, đã thêm Web Locks cho document lifecycle và vẫn giữ revision/receipt server. Lỗi harness còn lại đòi PRACTICE 10 câu khi pool thực cấp 3; đã yêu cầu 3 câu rõ ràng.
- Harness restart ban đầu đòi đúng 2 HTTP call; đã sửa để chấp nhận replay nhiều lần cùng key, bắt buộc số hàng không tăng và snapshot bằng nhau. Đợi background review sync xong trước baseline.
- Ca fallback kiểm tra writer quá sớm trước auth/hydration; đã đợi observable writer mới bằng expect.poll. Không bỏ assertion phục hồi đáp án.
- Rà soát phát hiện snapshot hỏng có thể vượt guard chỉ kiểm tra header. Đã thêm structural guards trước hydrate/render và bỏ dùng cached Result trực tiếp; bổ sung ca items=null bên cạnh command sai URL.
- Sau close 1006 (restart/handshake lỗi), manager cũng kiểm tra `/api/auth/me`, không chỉ close 1008; callback auth muộn sau stop bị bỏ. Review sync 401 yêu cầu đăng nhập lại cho đúng user. Logout kiểm tra cả pending/draft tại máy dù user đã rời màn học.

## Tái hiện

Backend từ root:

```powershell
docker build --target test -t english-learning-m06-test ./source/server
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-m06-test mvn -B -ntp test
```

Lượt thực tế bind mount `.test-state` vào `/evidence`, copy `target/surefire-reports` sau Maven để đối chiếu XML. Image được build mới sau thay đổi source backend.

Frontend từ `source/client`:

```powershell
node scripts/test-connection.mjs
npm run build
$env:PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
npm run test:e2e
node scripts/verify-m06-recovery.mjs
```

E2E dùng `english-learning-e2e`, 5174/8081; không đặt E2E_BASE_URL vào demo. Không `down -v`, truncate hoặc xóa volume. Hai project test dừng container khi xong, giữ volume. Artifact ignored: `.test-state/m06-baseline-*.log`, `m06-backend-*.log`, `m06-surefire/`, `m06-client-*.log`, `m06-connection-clock.log`, `m06-e2e-*.log`, `m06-no-weblocks.log`, `m06-restart*.log/json`; lượt cuối ở `m06-e2e-release.log`, `m06-restart-release.log` và `m06-restart.json`. Playwright report/trace/ảnh trong thư mục ignored.

## Giới hạn

Chưa có phiên thao tác tay độc lập chạy hết [checklist](../../docs/M06_MANUAL_TEST_CASES.md), hai thiết bị vật lý hoặc đo tải. P0 schema IndexedDB v1 chưa có migration v1→v2; chưa ép riêng blocked-upgrade hoặc policy chặn xóa DB/localStorage. Không mặc định các nhánh đó đã PASS.

Không có service worker/PWA, login offline hoặc cache toàn bộ audio. Nếu reload khi không tải được shell/không xác minh được auth thì không mở cache riêng. Phần chưa commit tại máy có thể mất; IndexedDB bị browser xóa không thay backup. Fallback không Web Locks dùng writer mới và khôi phục có lựa chọn, lease có thể đợi expiry.

Session/presence/subscription ở RAM một backend, nháp/kết quả/ledger server ở MySQL/file volume. Chưa có XP M05 hoặc dashboard M07. Chưa commit/push/tạo PR; nội dung PR cung cấp trong cuộc trao đổi khi người dùng yêu cầu.
