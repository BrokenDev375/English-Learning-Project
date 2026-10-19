# Nghiệm thu M03 — học, luyện tập và bài nộp

Ngày chạy: **19/10/2026**. Nhánh `feature/m03-learning-attempts`, nền source `97a881d` (bước 0–5 đã commit). Bước 6 bổ sung script nghiệm thu và tài liệu, không đổi mã ứng dụng, migration hoặc dependency. PR do người dùng tạo trên web sau; chưa commit/push trong lượt nghiệm thu này.

## Môi trường và kết quả

Windows PowerShell, Node **24.15.0**, npm **11.14.0**, Playwright **1.64.0** dùng Chrome cài sẵn. Backend test build mới từ Dockerfile target `test`: Java 17/Maven 3.9, MySQL 8.4 qua Testcontainers, ffprobe/ffmpeg. UI gọi Spring Boot/MySQL/nginx thật qua Compose riêng.

| Kiểm tra | Kết quả |
|---|---|
| Backend toàn bộ từ source hiện tại | **136/136 PASS**, 0 failure/error/skipped, BUILD SUCCESS, 8 phút 9 giây; XML 12 suite |
| `npm run build` | **PASS**, TypeScript/Vite production, 53 modules |
| Toàn bộ browser E2E | **47/47 PASS**, 0 failed/skipped, 2.0 phút; **14 M03 + 33 hồi quy** |
| Restart backend và seed lại | **PASS**, phiên cũ 401, đăng nhập lại khôi phục đúng dữ liệu |
| Force recreate mysql/server/client giữ volume và seed lại | **PASS**, cả ba container đổi ID; dữ liệu/file còn nguyên |
| Replay draft/submit sau mỗi lần khởi động lại | **PASS**, header replay và ACK/Result gốc, không tăng hàng |
| Rà tài liệu và script | **PASS**, `git diff --check`, `node --check`, 119 liên kết local và whitespace/fence các file thay đổi |

Backend gồm 70 ca M01/M02, 5 receipt, 25 nền M03 và 36 HTTP AttemptApiTest. Những dòng ERROR do CHECK constraint trong test lỗi/rollback là lỗi được chủ động ép; kết quả XML và Maven cuối lượt xác nhận không có test failure/error.

Danh sách file thay đổi không có `.env`, credentials thật hoặc artifact build/test. Sau nghiệm thu, cả hai project test đã dừng; ba container demo vẫn chạy, server/MySQL healthy.

Build/E2E và script Docker chạy với quyền truy cập Docker/tiến trình con phù hợp vì sandbox Windows chặn pipe Docker hoặc tiến trình Vite/browser. Không sửa ứng dụng để né giới hạn môi trường.

## Bằng chứng bền vững

[Script kiểm tra](../../source/client/scripts/verify-m03-persistence.mjs) luôn dùng project **english-learning-m03-acceptance**, UI/API **5175/8082**, volume riêng. Tạo STUDENT mới, hoàn thành LEARN sau CHECK đủ GUIDED, thêm CORRECTION, rồi lưu một bài khác còn dở với đáp án/câu hiện tại/vị trí audio. Baseline chụp sau khi các mutation đã commit.

Hai checkpoint restart và recreate đều so sánh **toàn bộ JSON snapshot của hai lượt, Result và trang progress** với baseline. Kiểm tra COMPLETED/STARTED; session cũ nhận 401; đăng nhập mới đọc lại dữ liệu. File audio có SHA-256 giữ nguyên và Range `bytes=0-15` trả 206 đúng 16 byte. Không lưu đáp án/cookie/mật khẩu vào hồ sơ commit.

Số hàng tại cả hai checkpoint bằng baseline của lượt chạy đạt:

| Nhóm | Số hàng không đổi |
|---|---|
| Học liệu | 3 Topic, 3 Unit, 6 Objective, 6 Lesson/6 Version, 90 Question/90 Version, 13 Material/13 Version, 7 Asset |
| Lượt và đáp án | 4 Attempt, 40 item, 40 answer |
| Kết quả và lịch sử | 22 trial, 2 Submission, 40 exposure, 4 progress |
| Receipt | 155 |

DB test còn dữ liệu từ lượt phát triển script trước đó; các con số là tổng của project test tại baseline, không phải số hàng tạo riêng cho một user. Script tạo user mới mỗi lần và so sánh trước/sau, không cần xóa volume để chạy lại.

Mỗi lần backend khởi động lại ghi seed report: `createdMaterials=0, adoptedMaterials=0, createdQuestions=0, adoptedQuestions=0, createdLessons=0, upgradedLessons=0, preservedLessons=6`. Nháp/kết quả/tiến độ và số hàng giữ nguyên sau seed và sau replay. Đây là bằng chứng restart/recreate với volume được giữ, không phải khôi phục khi ổ đĩa bị mất.

Lượt đầu phát triển harness dừng ở truy vấn nhầm tên bảng `objectives`; đã sửa thành `learning_objectives` rồi chạy lại toàn bộ script đạt hai checkpoint. Không thay schema hoặc mã nghiệp vụ để sửa lỗi harness.

## Đối chiếu ma trận bắt buộc của kế hoạch

| Nhóm | Bằng chứng và phạm vi |
|---|---|
| Migration/ORM | AttemptPersistenceTest: DB trống/V3→V4, giữ user cũ, validate, JSON, FK/unique/CHECK |
| Quyền | AttemptApiTest và hồi quy M01: role/owner/CSRF, actor bị khóa khi save chờ; E2E teacher/admin/user khác |
| Selector | AttemptApiTest: LEARN 10 câu, PRACTICE thiếu/rỗng/copy origin/ưu tiên exposure, resume/replay; REVIEW provider giả lập chỉ trong test |
| Snapshot/lịch sử | AttemptApiTest: pin version, publish keys/audio mới, retire, quyền file cũ; browser F5/Back/Forward |
| Draft | AttemptRequestsTest/AttemptApiTest: null/partial, full membership, playerState, không trial, lưu/khôi phục và rollback |
| Chấm | AnswerGraderTest: ba dạng, partial, Unicode NFC/case/whitespace/Locale, ID sai; FIRST/final qua HTTP |
| Submit | AttemptApiTest: full bản mới, nộp dở, thiếu CHECK, denominator thực, submit cạnh tranh, một Submission |
| Chữa/tiến độ | AttemptApiTest/E2E: CORRECTION không đổi result/FIRST; COMPLETED không hạ, version mới/retire, NOT_STARTED và phân trang |
| Đồng thời | CyclicBarrier khởi phát hai HTTP call; CountDownLatch ép actor lock khi khóa tài khoản; kiểm tra revision/số hàng sau tranh chấp |
| Lỗi/replay | HTTP đóng body sau status hoặc browser fetch commit rồi abort/delay; lỗi DB thực bằng CHECK constraint ở progress/receipt; replay sau revision mới |
| Media | MaterialApiTest/AttemptApiTest: quyền, 200/206/416/file mất 503; E2E audio thật với metadata clip fixture; script giữ SHA-256/Range qua recreate |
| UI/E2E | 14 ca M03: LEARN/PRACTICE, nháp, hint/check, nộp/chữa/tiến độ, 401/409/503, timeout, đổi user, responsive/bàn phím và giới thiệu bài |
| Bền vững/hồi quy | Hai checkpoint script; toàn bộ JUnit và 47 browser E2E gồm M01/M02/audio/authoring/navigation |

Quy trình dữ liệu/bước/kỳ vọng có tại [M03_MANUAL_TEST_CASES](../../docs/M03_MANUAL_TEST_CASES.md). Các ca đã tự động hóa được xác nhận bằng suite/script; chưa có một phiên kiểm thử thủ công độc lập chạy lại từng dòng checklist.

## Lệnh tái hiện và artifact

Từ root:

```powershell
docker build --target test -t english-learning-m03-acceptance-test ./source/server
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-m03-acceptance-test mvn -B -ntp test
```

Lượt nghiệm thu có thêm bind mount `.test-state` vào `/evidence` và copy `target/surefire-reports` trước khi container test kết thúc, để tổng hợp XML. Mỗi lượt build test image từ source hiện tại.

Từ `source/client`, dependency đã cài theo lockfile:

```powershell
npm run build
$env:PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
npm run test:e2e
node scripts/verify-m03-persistence.mjs
```

E2E dùng project **english-learning-e2e**, UI/API **5174/8081**, build nguồn mới và chờ seed trước khi chạy. Không đặt `E2E_BASE_URL` vào demo. Script bền vững tự quản lý project riêng và dừng container khi kết thúc, giữ volume. Demo 5173/8080 không được build/restart hoặc ghi dữ liệu trong nghiệm thu này.

Artifact ignored: `.test-state/m03-step6-backend-build.log`, `m03-step6-backend.log`, `m03-step6-surefire/`, `m03-step6-client-build.log`, `m03-step6-e2e.log`, `m03-step6-persistence.log`, `m03-step6-persistence-docker.log`, `m03-step6-persistence.json`. Playwright giữ report/ảnh/trace theo cấu hình trong thư mục ignored; không commit artifact hoặc credentials.

## Giới hạn và bàn giao

- Timeout browser dùng fetch 15 giây thật, response đã commit bị trì hoãn 17 giây. Abort/401/503 và metadata clip dùng interception có kiểm soát; không phải benchmark hoặc thử mất gói TCP vật lý.
- Thử lỗi DB bằng constraint trong Testcontainers chứng minh rollback transaction; không phải thử tắt ổ đĩa, failover hoặc backup/restore.
- Nháp chưa ACK và pending command ở bộ nhớ trang, chưa có IndexedDB/outbox; bỏ cảnh báo rồi reload có thể mất phần chưa commit. REVIEW thật chờ M04, XP/offline bền vững thuộc module sau.
- Nội dung PR được cung cấp trong cuộc trao đổi để sao chép, kèm migration V4, tác động dùng chung, kiểm tra và giới hạn. Người dùng sẽ mở PR trên web; chưa có PR/merge/deploy trong phạm vi này.
