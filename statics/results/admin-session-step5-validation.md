# Nghiệm thu phiên và giám sát — bước 5

Sau nghiệm thu có [bản sửa nhãn hoạt động/tab](admin-session-route-fix-validation.md) với kiểm tra riêng. Các số toàn bộ trong biên bản này thuộc runtime `60fc657` trước bản sửa, không phải một lượt full trên source mới.

Ngày 29/10/2026, nhánh `feature/admin-session-monitoring`, runtime nền `60fc657` sau commit bước 4. Working tree bước 5 chỉ bổ sung kiểm thử/script/tài liệu, không sửa backend, schema/migration, `.env`, luật chấm/Review/XP. Chưa commit/push. File báo cáo của người dùng trong `report/` giữ nguyên.

## Kết quả từng lượt

| Nhóm | Kết quả | Bằng chứng local được ignore |
|---|---|---|
| Backend đầy đủ — Maven/MySQL Testcontainers | **361/361 PASS**, 38 suite, 0 failure/error/skip, BUILD SUCCESS, 18m15s; đã cộng và đối chiếu XML Surefire | `.test-state/session-step5-backend.log`, `session-step5-surefire/` |
| Browser đầy đủ, loại riêng ca timeout thật | **137/137 PASS**, một lượt, 7.0 phút | `.test-state/session-step5-browser-full.log` |
| Idle/recovery với timeout 20s, warning 5s | **8/8 PASS**, một lượt, 2.8 phút; sau đó ca audio bổ sung **1/1 PASS**, 24.2s. Tổng 9 ca khác nhau qua các lượt, không phải một lượt 9/9 | `.test-state/session-step5-idle.log`, `session-step5-audio.log` |
| Timeout mặc định 300s thật, warning 30s | **1/1 PASS**, một lượt, 5.1 phút | `.test-state/session-step5-default.log`, `session-default-warning.png` |
| Restart/recreate/replay/media và khóa REVIEW | **3/3 chặng PASS** trong lượt script cuối | `.test-state/session-acceptance.json`, `session-step5-recovery-final.log`, `session-acceptance-docker.log` |
| Connection / session / IndexedDB-journal harness | **11/11**, **10/10**, **6/6 PASS** | Output các lệnh npm trong lượt nghiệm thu |
| TypeScript/Vite production build | PASS; cảnh báo chunk >500 kB đã có, không phải lỗi | Output `npm run build` |
| Diff whitespace, phạm vi Git và dữ liệu nhạy cảm | PASS; không thay `.env`, backend hoặc file báo cáo của người dùng | `git diff --check`, `git status --short` |

Browser có **147 ca khác nhau qua bốn lượt** (137 hồi quy + 8 idle/recovery + 1 audio + 1 default 300s), không phải một lượt 147/147. Không cộng các chặng Node hoặc harness vào số browser. Không lấy 350/350, 61/61 hoặc 23/23 từ các bước cũ thay bằng kết quả source này.

## Ma trận nghiệm thu

| Nhóm | Bằng chứng và giới hạn |
|---|---|
| Ranh giới thời gian/deadline | Registry Clock và session harness; browser 20s + 300s thật, kiểm tra cấu hình 300000 ms và warning 30000 ms |
| Không gia hạn giả | Default browser vẫn có ít nhất 10 heartbeat, 50 GET bổ sung, hơn 10 activity context-only và không interaction; vẫn về auth/401 sau hạn. Ca audio phát lặp có hơn 10 timeupdate thực vẫn về auth/401. Không dùng fake browser clock cho ca 300s |
| Tương tác/coalesce/seq | Session harness 10 ca, registry/API; trusted input giữ phiên, context/visibility không là interaction, tab hết watermark đăng ký lại không replay tín hiệu cũ |
| Cảnh báo/Continue | Browser 20s/5s có ACK gia hạn và desktop/mobile; default 300s/30s đã nhìn ảnh warning, sau đó không thao tác và hết phiên |
| Nhiều tab/cùng cookie | Browser thực tế tab A tương tác, B dùng chung deadline; presence/dedup và khung chi tiết trong full suite |
| Thiết bị độc lập | Browser thực tế hai context cùng user; một context tương tác vẫn 200 trong khi context kia về auth và 401 |
| Dirty draft + idle | Offline nhập đáp án, đợi server hết hạn, nối lại/login cùng user; dữ liệu local phục hồi và autosave server |
| Submit mất ACK + idle | Route đầu thực sự commit submit rồi bỏ response, giữ pending. Sau idle/login replay đúng key và payload; Result bằng trước/sau reload, không tạo kết quả mới |
| Restart + draft mất ACK | Dừng/restart backend thật khi pending tồn tại; login mới và replay sâu bằng payload gốc; snapshot, các bảng nghiệp vụ và receipt fingerprint không đổi |
| Recreate + seed/media | Force recreate cùng volume; ID container thay; Result, progress, M04 dashboard/ledger/counter, số câu/bài giữ nguyên; audio SHA-256 bằng nhau và Range 16 byte trả 206 |
| Khóa LEARN/REVIEW | Full browser khóa LEARN trên hai thiết bị, account khác không ảnh hưởng; script riêng xác minh context REVIEW từ API giám sát rồi khóa, user về auth/401, admin vẫn 200. Mở khóa không phục sinh cookie; login mới resume REVIEW và kết quả LEARN cũ bất biến |
| Quyền/rollback/đồng thời | Nhóm M01/API/registry/receipt trong full backend; browser tự khóa bị disable và M01 role/lock/unlock/delete. Không thay guard/actor lock/AFTER_COMMIT |
| Realtime/đổi bộ lọc | Full suite: context đổi dù count giữ nguyên, ACK/reconnect GET, fallback khi mất socket, poll khi socket sống; GET lỗi giữ stale, một request + một queued, bỏ response filter cũ |
| Callback auth/lỗi local | Full suite và harness: callback auth/CSRF/command cũ được fence; quota/lỗi IDB không thông báo lưu giả; transaction local treo không giữ UI hết phiên vô hạn |
| Giao diện | Full suite chụp desktop/mobile, không overflow toàn trang; dialog Tab/Shift+Tab/Escape/focus restore, 404 báo phiên kết thúc, độc lập filter CRUD/monitor. Đã quan sát ảnh render; thao tác smoke bằng browser automation |
| Restart/presence | Session và registry RAM mất khi restart; cần login lại, không phục sinh authentication từ snapshot IndexedDB. GET sau login/reconnect đối soát nguồn server |

## Lệnh và môi trường

Hướng dẫn tái lập đầy đủ: [bàn giao](../../docs/ADMIN_SESSION_MONITORING_HANDOFF.md). Backend build `docker build --target test -t english-learning-session-step5 ./source/server`; `docker run` mount Docker socket, `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`, chạy `mvn -B -ntp test`. Runner shell local được chuẩn hóa LF và chép XML Surefire sang thư mục evidence riêng sau khi Maven kết thúc.

Các stack độc lập:

| Project | Cổng web/API | Cấu hình |
|---|---|---|
| english-learning-e2e-session-step5-full | 5182 / 8092 | timeout mặc định, suite không gồm @idle-short/@idle-default |
| english-learning-e2e-session-default | 5183 / 8093 | 300s/30s thật, gate E2E_DEFAULT_IDLE_TESTS=1 |
| english-learning-session-acceptance | 5184 / 8094 | restart/recreate, cùng volume, user mới cho mỗi lượt |
| english-learning-e2e-session-step5-idle | 5185 / 8095 | 20s/5s/sweep 1000ms, gate E2E_IDLE_TESTS=1 |
| english-learning-e2e-session-step5-audio | 5186 / 8096 | chạy riêng ca audio bổ sung với cùng cấu hình 20s/5s |

Chrome local qua PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH. Các runner stop stack sau chạy, không `down -v` hoặc xóa dữ liệu demo. Không đổi cấu hình timeout của demo để chạy test. `.env` chỉ được đọc nội bộ; không in/commit credentials, artifact, trace hoặc snapshot IndexedDB.

## Điều chỉnh trong quá trình kiểm tra

- Lượt gọi runner backend đầu chưa chạy test do file shell CRLF làm Maven nhận `test\r`; chuẩn hóa LF rồi mới chạy lượt đầy đủ được ghi nhận. Không ghi lượt lỗi launcher thành lỗi nghiệp vụ.
- Script durability đầu so tổng mọi request_receipts và gặp tăng một receipt hợp lệ do M04 sync khi kết quả được hydrate lại sau login. Sửa phép đối chiếu: các bảng nghiệp vụ/ledger/seed giữ tổng, riêng command đang replay kiểm tra đúng requestId, đúng một receipt và fingerprint operation/hash/status/body bất biến. Không bỏ kiểm tra receipt và không sửa runtime để né sync. Lượt script cuối qua cả ba chặng.
- Sau lượt idle 8/8, bổ sung ca audio. Build đầu bắt lỗi kiểu DOM của test, đã cast HTMLAudioElement. Lượt chín ca sau đó có 8 qua, riêng audio thực sự đã hết phiên nhưng assertion yêu cầu request draft khi playback liên tục/debounce chưa gửi (saves=0) nên fail. Đổi sang đo hơn 10 timeupdate thực và 401, chạy lại riêng ca audio đạt 1/1; không đổi runtime hoặc bỏ kiểm tra hết phiên. Các lượt trùng không cộng vào tổng 147 ca.
- Log backend có thể có SQL lỗi cố ý ở fixture rollback/seed hoặc cảnh báo pool của test context cũ sau Testcontainer đóng; kết luận theo Surefire/Maven, không dựa vào việc log hoàn toàn không có chữ ERROR. PowerShell wrapper cũng có thể gắn NativeCommandError cho warning stderr của JVM/Docker.

## Giới hạn còn lại

NOT RUN: sleep/resume OS/thiết bị vật lý, trình duyệt khác Chrome, load/soak lớn, nhiều backend và thử chống bot. Các test clock/throttle/visibility mô phỏng không thay thành bằng chứng thiết bị ngủ thật. Chưa có PWA/login offline/cache toàn media, registry lịch sử bền vững hoặc giám sát sự chú ý. Hệ thống theo một backend; invalidation tối đa một lần/giây, polling visible 15s và heartbeat window 75s có độ trễ quan sát. Nháp chưa commit IndexedDB vẫn có thể mất; lỗi local không được báo là đã lưu. Các giới hạn này được giữ trong bàn giao, không công bố realtime tuyệt đối.
