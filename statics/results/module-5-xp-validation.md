# M05 — Kiểm tra XP, streak, cấp độ, huy hiệu và giao diện demo

Ngày 21/10/2026, nền Git `57f2951` cộng thay đổi M05 chưa commit. Đây là bằng chứng cho phần XP của M05; **chưa nghiệm thu Giải đấu tuần hoặc toàn M05**.

## Phạm vi đã triển khai

V6 giữ nguyên; V7 thêm outbox. Đã nối ORM/repository/service cho claims, XP ledger, daily/profile, badge, processed decision, failure metadata. Worker xử lý Submission đã commit; manual sync dùng RequestReceiptService. Toàn bộ quyết định thưởng, projection, outbox và receipt cùng transaction dưới khóa actor. M03 không cần worker chạy mới nộp được bài.

Đã có 4 endpoint riêng tư cho STUDENT: GET `/api/me/gamification`, POST `/api/me/gamification/sync`, GET `/api/me/gamification/activities`, GET `/api/me/badges`. GET chỉ đọc; không có API cho client truyền XP hoặc owner. Socket GAMIFICATION chỉ báo thay đổi cho cùng user; client GET đối soát lại.

UI có thanh XP ở trang chủ, phần thưởng sau nộp, trang cấp độ/streak/giới hạn ngày/3 huy hiệu/lịch sử/lý do/phân trang và nút cập nhật. Không tạo thưởng giả hoặc đồng hồ tăng ngày cho demo.

## Kết quả thực thi

| Kiểm tra | Kết quả |
|---|---|
| Backend compile và frontend production build | PASS; image test và runtime build từ source hiện tại |
| Nhóm mới GamificationApiTest + GamificationCycleKeyTest | **13/13**, 0 fail/error/skip, lượt riêng trước hồi quy |
| Toàn bộ backend regression | **187/187**, 18 lớp, 0 fail/error/skip, Maven BUILD SUCCESS, 23 phút 33 giây |
| Browser M05 riêng | **4/4**, Playwright trên Chrome, database test riêng |
| Toàn bộ browser M01–M05 | **59/59**, 5,7 phút; gồm 4 ca M05, không cộng dồn thành 63 ca |
| Hợp đồng OpenAPI | **8 operation, 31 schema, 10 example, 50 fixture** (18 valid, 32 invalid) đúng mong đợi |
| JSON thực tế so với OpenAPI | PASS cho summary, sync và activities lưu từ bài nộp thật của kiểm tra persistence |
| Persistence qua restart server | PASS: 15 XP/1 ngày hoạt động, giữ streak/history/revision; replay submit và sync trả receipt cũ, không cộng thêm |
| Giao diện desktop/mobile | Đã xem ảnh desktop và 390px; không tràn ngang; breadcrumb phần thưởng đã sửa và được build trước lượt 59/59 |
| Bản demo thật 5176/8083 | Health/UI 200, đăng nhập và GET summary/badges/history thành công, worker xử lý 2 nguồn trước activation về trạng thái terminal 0 XP |
| Shortcut Windows | `start-main.ps1` parse thành công; cấu hình dùng project/cổng/WS origins của bản demo |

12 ca API mới kiểm tra read-only/role/field lạ/owner privacy; fresh XP và replay; worker và chữa không tăng XP; cap 200 trên nguồn thật và claim ôn bị cap; rollback khi lỗi outbox; hai session ghi đồng thời; activation; ngày đến lệch thứ tự/streak 7 ngày; nửa đêm Việt Nam; thưởng ôn cùng XP câu mới; huy hiệu bài đầu; facts schema không hỗ trợ rollback/defer rồi retry. Các ca nền/context/migration vẫn nằm trong hồi quy toàn backend.

4 ca browser mới kiểm tra trang trống/rules/3 huy hiệu, worker cộng XP và tự cập nhật giao diện không bấm sync, mất ACK sync rồi F5 không cộng trùng, đổi tài khoản và layout mobile. Backend fault injection có thể in stack trace dự kiến; đánh giá theo tổng kết JUnit, không chỉ theo việc thấy dòng ERROR.

Lượt backend có cảnh báo Hikari ở các Spring context còn giữ pool sau khi Testcontainers của lớp cũ dừng; cuối lượt Surefire buộc kết thúc fork JVM sau thời gian chờ shutdown 30 giây. Maven kết thúc mã 0/BUILD SUCCESS với đủ 187 kết quả; không bỏ qua cảnh báo này và không coi đó là bằng chứng kiểm tra shutdown sạch hoặc tải.

## Cách tái hiện và môi trường

```powershell
docker build --target test -t english-learning-m05-test ./source/server
docker run --rm --name english-learning-m05-regression -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-m05-test mvn -B -ntp test
python scripts/verify-m05-contract.py
```

Java 17, MySQL 8.4 Testcontainers. `src/test/resources/application.properties` tắt worker scheduler để kiểm tra API xác định; runtime worker bật mặc định. Integration test gọi consumer thật; browser chạy worker scheduler thật.

Browser chạy trên Docker project `english-learning-m05-e2e`, UI 15176/API 18083, volume riêng; `E2E_BASE_URL=http://127.0.0.1:15176`, `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` trỏ Chrome cài sẵn, `npm run test:e2e` từ `source/client`. Không trỏ test ghi dữ liệu vào bản demo.

Kiểm tra restart trên môi trường test đã chạy/seed:

```powershell
node scripts/verify-m05-persistence.mjs prepare
docker compose -p english-learning-m05-e2e restart server
node scripts/verify-m05-persistence.mjs verify
```

Script chặn URL ngoài localhost:15176, dùng tài khoản test riêng, làm bài thật và lưu snapshot vào `.test-state/` đã ignore. Sau restart đăng nhập lại rồi replay cùng key/payload; không giữ session RAM làm bằng chứng bền vững.

Log lượt này: `.test-state/m05-xp/{backend-tests,backend-regression,browser-tests,browser-regression,build,local-start}.log`. Ảnh trong `source/client/test-results/` được ignore. Backup DB demo trước V7 nằm trong `.test-state/m05-local-before-v7/`; không đưa dữ liệu/credentials vào Git.

## Vận hành và giới hạn

Bản demo `english-learning-main` nâng V6→V7 sau sao lưu, giữ volume và `.env`. Dữ liệu học liệu vẫn 6 bài/90 câu/13 materials, 3 users và 2 submissions cũ trước activation tại thời điểm triển khai. Không tự sửa checksum V1–V7. Container server cũ của project `english-learning-project` bị vòng restart vì lệch checksum V4 đã được dừng; không thay database cũ hoặc xóa volume.

Chưa có season/join/group/ranking/tie/cutoff/finalizer, UI/subscription League hoặc kết quả tuần. XP hiện không được tính vào League. Chưa kiểm tra tải, nhiều backend, crash ở từng điểm của dispatcher, backlog rất lớn, hoặc mọi tổ hợp commit nguồn đảo ID. Quét NOT EXISTS giúp không bỏ vĩnh viễn nguồn đến muộn; chưa coi đó là bằng chứng thử mọi lịch tranh chấp.

Policy hiện được code hỗ trợ ở phiên bản `m05-p0-v1`; không có màn quản trị policy. Các ca checklist chưa được mô tả trong bằng chứng trên vẫn cần chạy ở chặng nghiệm thu toàn M05.
