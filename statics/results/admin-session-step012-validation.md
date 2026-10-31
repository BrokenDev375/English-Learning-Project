# Kiểm tra giám sát phiên bước 0–2

Ngày 29/10/2026. Nền Git `764d846`, working tree nhánh `feature/admin-session-monitoring`, chưa commit. Không dùng DB demo; Java 17/MySQL Testcontainers trong Docker, frontend Node/Chrome qua Playwright.

## Baseline và kiểm tra đã có

| Nhóm | Kết quả |
|---|---|
| Baseline frontend | Build PASS, 84 modules; manager 10/10 |
| Baseline backend M01/socket | 21/21 PASS, 0 failure/error/skip, 41.5s |
| Frontend source cuối | Build PASS, 87 modules; manager 10/10, session harness 10/10; storage M06 6/6 |
| Browser idle rút ngắn lượt đầu | 4/4 PASS, 1.5 phút; timeout server thật 20s, warning 5s, sweep 1s |
| Screenshot warning | Đã xem desktop 1280×720 và mobile 390×844; không tràn ngang, nút Continue hoạt động |
| Detector UI | Không có findings ở IdleSession.tsx/session.css |
| Backend toàn bộ | **350/350 PASS**, 37 suite XML, 0 failure/error/skip, BUILD SUCCESS, **19m27s** |
| Browser hồi quy mặc định 300s | **128/128 PASS**, không failed/skipped, **6.2 phút**, project/volume mới |
| Browser sau bổ sung bounded flush | 5/5 PASS, 1.9 phút; gồm callback IndexedDB completion bị chặn |
| API/registry sau sửa filter | 13/13 PASS, 51s; thêm xử lý session có thể null rồi nhóm API/registry/M01 24/24 PASS, 1m32s |
| Browser idle source cuối | **6/6 PASS**, không failed/skipped, **1.9 phút**, timeout thật 20s; gồm TAB_EXPIRED fixture và bounded flush |

Baseline frontend bị sandbox chặn spawn EPERM, chạy lại với quyền phù hợp thì build đạt. Docker CLI ghi tiến độ/warning vào stderr khiến PowerShell có thể trả exit code khác 0 dù Maven BUILD SUCCESS; dùng kết quả Maven, XML và Playwright summary làm bằng chứng.

Lượt backend tập trung đầu đạt 79/80. Test ranh giới phát hiện HTTP idle gate có thể đứng trước SecurityContextHolderFilter do hai filter có cùng order. Đã sửa chuỗi thành load context → idle → account guard; 13/13 API/registry xác nhận lại. Fixture Clock được đổi volatile và căn trực tiếp deadline trả về, không nới ranh giới 300000ms. Lượt full sớm được dừng có chủ đích để tách clock ngày/tuần của fixture XP/League khỏi clock idle; fixture nghiệp vụ vẫn dùng fake Clock cũ, registry phiên dùng Clock.systemUTC với timeout thật 300s. Không tắt idle hoặc tăng timeout để né test.

Browser hồi quy lượt đầu 127/128: context Attempt của user khác bị 404 nhưng tracker diễn giải thành lỗi phiên và thêm alert lặp. Đã cho context không được cấp chuyển OTHER/không gửi ID, giữ 404 nghiệp vụ; notice kết nối là status. Lượt sau 127/128, ca M03 đã đạt, lỗi còn lại ở fixture League đòi groupCount=2 trong volume test dùng lại đã có 10 người. Không đổi assertion hoặc xóa volume. Runner bổ sung E2E_PROJECT_NAME với prefix riêng để chạy trên project/volume mới; lượt này **128/128 PASS**.

Phạm vi source: full backend chạy trên image sau sửa filter. Sau đó controller được thêm guard trường hợp session đã bị sweep hủy giữa filter/controller; bản compile mới chạy nhóm API/registry/M01 **24/24**, không tính là 24 ca mới ngoài 350. Browser full gồm guard này và route/context từ Runner. Cuối cùng tracker tự đăng ký tab mới ngay sau TAB_EXPIRED, không chờ poll 15s hoặc replay tương tác cũ: xác nhận bằng harness **10/10**, build và browser idle **6/6** trên source cuối. Đây không phải một lượt ghép 350+24 hoặc 128+5+6 ca khác nhau.

## Bằng chứng và giới hạn

- Registry unit: ranh giới 299999/300000ms và warning, context/GET/replay không gia hạn, session độc lập, stale tab, sweep/tombstone TTL, cap, touch/expire đồng thời và cấu hình sai.
- API integration: cookie/CSRF thật, strict JSON/cache/privacy, hết hạn và login lại với cookie cũ, hai context độc lập, socket thật nhận close 1008 IDLE_TIMEOUT.
- Client harness dùng code thật transpile với clock/transport điều khiển: warning/ACK, coalesce, offline không tự kết luận auth, hết hạn, auth generation và response/CSRF cũ.
- Browser 20s dùng server thật, không page.clock để làm backend hết hạn. Ca M06 giữ đáp án offline, chờ server hết phiên, nối mạng/login và nhận lại cùng lượt/đáp án.
- Ca stalled IndexedDB chặn callback completion trong browser: kiểm tra giới hạn chờ client, không mô tả là lỗi đĩa thật. Ca TAB_EXPIRED fixture trả HTTP 409 một lần để xác nhận client đăng ký context-only bằng tabId mới rồi chỉ nhận tương tác mới; mốc 75s thật ở server được kiểm bằng Clock unit, không phải browser chờ 75s.
- Chưa chờ 300s bằng thời gian thực trong browser; ranh giới mặc định được kiểm bằng fake Clock backend. Browser hồi quy dùng policy mặc định nhưng không phải ca chờ đủ 5 phút. Nghiệm thu mặc định 300s thật và restart/recreate riêng thuộc bước 5.
- Chưa có API/UI giám sát hoạt động admin của bước 3–4. Không có benchmark tải mới, PWA/login offline hoặc deployment nhiều backend.

## Lệnh tái hiện

Từ root, backend source mới phải được build trước test:

```powershell
docker build --target test -t english-learning-session-test ./source/server
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-session-test mvn -B -ntp test
```

Từ `source/client`:

```powershell
npm run build
npm run test:connection
npm run test:session
$env:PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
# Hồi quy mặc định; nhóm idle ngắn được chạy riêng:
$env:E2E_PROJECT_NAME = 'english-learning-e2e-session-' + (Get-Date -Format yyyyMMddHHmmss)
npm run test:e2e -- --grep-invert '@idle-short'
# Chỉ stack E2E riêng đọc các biến này; không sửa .env demo:
$env:SESSION_IDLE_TIMEOUT_SECONDS = '20'
$env:SESSION_IDLE_WARNING_SECONDS = '5'
$env:SESSION_IDLE_SWEEP_MS = '1000'
$env:E2E_IDLE_TESTS = '1'
npm run test:e2e -- session-idle.spec.ts
# Trả lại mặc định cho lần chạy tiếp theo trong cùng terminal:
Remove-Item Env:SESSION_IDLE_TIMEOUT_SECONDS, Env:SESSION_IDLE_WARNING_SECONDS, Env:SESSION_IDLE_SWEEP_MS, Env:E2E_IDLE_TESTS, Env:E2E_PROJECT_NAME
```

Runner mặc định dùng project `english-learning-e2e` cổng 5174/8081; có thể chọn E2E_PROJECT_NAME bắt đầu bằng `english-learning-e2e-` để tạo volume test mới, tránh dữ liệu League của các lượt trước. Project cuối là `english-learning-e2e-session-20261029`. Runner đợi seed và stop khi xong, giữ volumes cũ; không down -v. Không dùng E2E_BASE_URL trỏ demo. Artifact ignored trong `.test-state/session-*`; log chốt: `session-backend-final.log` + XML `session-surefire` (350), `session-backend-check.log` (24), `session-e2e-fresh.log` (128), `session-e2e-idle-release.log` (6). Không in credentials/cookie/đáp án vào tài liệu commit.

Trong log backend có constraint errors từ test ép rollback và cảnh báo pool của context cũ khi DB test đã dừng; kết quả Maven và 37 suite XML đều không có failure/error/skip. Không sửa pool ứng dụng chỉ để giấu cảnh báo của fixture lịch sử. Không restart/rebuild project demo `english-learning-main` trong lượt kiểm tra này.
