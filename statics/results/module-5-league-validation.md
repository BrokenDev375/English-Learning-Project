# M05 — Giải đấu tuần, chốt nhóm và tích hợp demo

Ngày 21/10/2026. Source nền `57f2951` cộng working tree M05 chưa commit. Tiếp nối [kiểm tra XP](module-5-xp-validation.md); số liệu ở biên bản này thuộc lượt tích hợp League. Không cộng dồn các lượt test trùng thành số ca riêng biệt.

## Thay đổi và hành vi

- V8 thêm season/allocator/group/member/outbox và FK ledger→season. Không sửa V1–V7, không hồi tố XP đã quyết định.
- 4 endpoint League chỉ cho STUDENT, cookie/CSRF, no-store/private, GET không ghi, POST chỉ nhận requestId. Join có receipt, UNIQUE season/user, season/allocator lock; nhóm tối đa 25, ưu tiên groupNo nhỏ nhất còn chỗ.
- Điểm tuần chỉ từ awarded XP của nguồn thuộc tuần `[startAt,endAt)` và submittedAt >= joinedAt. Quyết định cutoff kiểm tra dưới khóa season; claim/XP/member/day/profile/badge/processed/outbox/receipt cùng transaction. XP trước join hoặc quá cutoff vẫn được giải thích trong lịch sử, không tự chuyển sang tuần khác.
- Dùng chung LeagueRanking cho bảng tạm và kết quả frozen. Competition rank, khối hòa vắt quota giữ bậc, nhóm ít thành viên/ít người có XP giữ bậc, Đồng/Kim Cương chặn dịch chuyển ngoài bậc.
- LeagueLifecycle tạo tuần hiện tại; sau endAt đóng tham gia, nhận nguồn tuần cũ đến trước cutoff=endAt+120s. Chốt từng group trong transaction riêng, kết quả ngay trên membership có sẵn; group marker và kết quả cùng commit. Job chạy lại tiếp tục nhóm còn thiếu, không chốt lại nhóm đã xong.
- Finalizer không quay lại khóa actor hoặc ghi FK users mới. Membership và hai loại outbox dùng DynamicUpdate để không cập nhật lại cột FK không đổi. Các season của batch nguồn được pre-lock theo thứ tự trước khi ghi projection.
- UI có join/retry key, điểm tuần/rank/dự kiến, bảng phân trang với self ngoài trang, trạng thái đợi tuần trước, luật và lịch sử. Outbox gửi invalidation đúng user/thành viên nhóm qua subscription LEAGUE; reconnect/poll/GET đối soát, không cộng điểm theo event.

## Bằng chứng kiểm tra

| Nhóm | Kết quả |
|---|---|
| Build backend/test image, runtime server/client | PASS từ source triển khai; frontend 57 modules |
| LeagueApiTest | **22/22** trong hồi quy, gồm **10 ca League + 12 ca XP kế thừa**; không coi là 22 ca mới |
| 4 ca chọn riêng trước deploy | **4/4**, 0 lỗi/bỏ qua, BUILD SUCCESS: before-join, rollback/freeze, promote/chốt đồng thời, FK/actor race |
| Toàn backend | **214/214**, 20 lớp, 0 fail/error/skip, BUILD SUCCESS, mã thoát 0, **22 phút 44 giây** |
| Ca bổ sung nhận điểm trước cutoff, commit sau cutoff | **1/1**, BUILD SUCCESS, 2 phút 16 giây; finalizer chờ rồi frozen XP khớp SUM ledger, chạy riêng sau khi thêm test này |
| Browser League đầu tiên | **3/3**, backend/worker thật trên môi trường riêng |
| Hồi quy toàn browser | **62/62**, 5,5 phút, trước bổ sung đoạn giải thích XP tuần và fixture UI lịch sử |
| Browser bản cuối | **8/8**, 50,3 giây; 7 ca backend thật và 1 ca fixture UI được ghi rõ bên dưới |
| Contract | **8 operation / 31 schema / 10 example / 50 fixture**, đúng mong đợi |
| JSON thực tế so với OpenAPI | PASS summary, sync, activities, join, overview từ bài nộp thật của kiểm tra persistence |
| V7→V8 và dữ liệu cũ | Kiểm tra snapshot XP/streak/history/receipt trước V8 trên DB test vẫn đạt sau nâng; nguồn cũ không được đưa vào League |
| Restart/recreate server test | PASS, 15 XP cá nhân và 15 XP tuần, giữ memberKey/group/tier/joinedAt/weeklyXp; replay join/submit/sync nguyên receipt, không thêm membership/điểm |
| Demo 5176 sau V8 | Health/UI/đăng nhập/GET M05 thành công; XP/revision khớp backup trước V8; season OPEN, học viên chưa tham gia vẫn NOT_JOINED |
| Windows shortcut | start-main.cmd đã chạy thật thành công, giữ cổng 5176/8083/WS origins và volume; xử lý stderr progress của Compose khi redirect log |

10 ca League HTTP/MySQL trong lượt toàn backend: GET không tạo season/đúng role/query/owner; replay/cùng user hai session; XP trước join và cộng nguồn mới đúng một lần; hai user tranh chỗ cuối nhóm; grace/cutoff; rollback finalizer rồi chạy lại giữ frozen result; chỉ nhóm cũ của chính mình chặn join và vắng tuần giữ bậc; thời gian được đọc sau khi chờ season lock; promotion tuần sau cùng hai finalizer; finalizer và writer giữ actor không tạo vòng khóa ngầm FK. Các điểm ranking trong ca promotion là fixture cố ý trong DB test, không phải XP thật từ bài nộp; ca khác kiểm tra ledger nguồn thật.

Ca League thứ 11 được thêm/chạy riêng: worker xét và flush điểm trước cutoff trong transaction chưa commit, finalizer bắt đầu sau cutoff phải chờ season lock; khi writer commit muộn, kết quả frozen và SUM league ledger vẫn 15 XP. Chỉ thêm test, không đổi code runtime so với lượt 214/214. Không mô tả như đã chạy một lượt 215 ca.

5 unit LeagueRanking kiểm tra competition rank/khối hòa phía trên, khối hòa phía dưới, nhóm nhỏ/ít active, clamp bậc và biên thứ Hai Việt Nam. Hai lỗi ở lượt test đầu là fixture tuần giữa các ca đụng nhau và kỳ vọng sai kiểu exception đã được Spring dịch; đã cách ly mỗi ca cách nhau 10 tuần và kiểm tra đúng exception. Không thay luật production để làm test qua.

Lượt toàn backend có cảnh báo pool Hikari của Spring context cũ sau khi Testcontainers tương ứng dừng. Cuối lượt Surefire buộc dừng fork JVM sau thời gian chờ shutdown 30 giây; Maven vẫn trả mã 0/BUILD SUCCESS với đủ 214 kết quả. Đây chưa phải bằng chứng shutdown test JVM hoàn toàn sạch; không sửa code ứng dụng để che cảnh báo môi trường này.

Các browser thật kiểm tra mất ACK join/replay/F5, hai tài khoản trong hai browser context thấy XP nhóm cập nhật, XP trước join không vào tuần, query nhóm tùy ý bị từ chối và mobile 390px. Khi DB test tích lũy hơn 10 thành viên, kiểm tra đã điều hướng trang thay vì giả định mọi người nằm trang đầu. Một ca fixture UI trả overview WAITING_PREVIOUS_SETTLEMENT và history frozen để kiểm tra render/nút join; **ca này không phải bằng chứng finalizer chạy thật**. Finalizer được kiểm tra bằng HTTP/MySQL ở trên. Không ghi fixture lịch sử hoặc XP giả vào DB demo.

Đã xem ảnh desktop/mobile của màn League. Ảnh fixture lịch sử được đặt tên rõ ràng. Screenshot/log trong `.test-state/` và `source/client/test-results/` bị Git ignore, không đưa dữ liệu tài khoản vào Git.

## Tái hiện

```powershell
docker build --target test -t english-learning-m05-test ./source/server
docker run --rm --name english-learning-m05-league-regression-final -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-m05-test mvn -B -ntp test
python scripts/verify-m05-contract.py
```

Java 17/MySQL 8.4 Testcontainers. Test tắt scheduler bằng test properties, gọi consumer/lifecycle thật và Clock inject; không đổi giờ máy. Integration dùng MySQL riêng, không DB demo.

Browser ở `english-learning-m05-e2e`, UI 15176/API 18083, volume riêng, worker scheduler thật. Từ `source/client`, đặt `E2E_BASE_URL=http://127.0.0.1:15176`, `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` trỏ Chrome và chạy `npm run test:e2e` (toàn bộ) hoặc `npm run test:e2e -- module-five-league.spec.ts module-five-rewards.spec.ts` (M05).

```powershell
node scripts/verify-m05-persistence.mjs prepare
docker compose -p english-learning-m05-e2e restart server
node scripts/verify-m05-persistence.mjs verify
```

prepare đăng ký user test, join, nộp 3 câu thật và lưu snapshot/keys trong `.test-state/`; verify đăng nhập lại, replay và GET đối soát. Script chỉ chấp nhận localhost:15176. Đã verify lại sau recreate server bằng image cuối; không coi session RAM là bằng chứng bền vững.

Log lượt này trong `.test-state/m05-league/`: build-regression, backend-regression, boundaries, accepted-cutoff, browser-regression, browser-final, local-start. File backend-tests là lượt đầu có 2 ca fixture lỗi, không dùng thay kết quả hồi quy cuối. Backup demo trước V8: `.test-state/m05-local-before-v8/database.sql` (ignore). Không sửa `.env`, không xóa volume, không commit/push.

## Giới hạn

Chưa đo tải/latency, chạy nhiều backend, backlog lớn hoặc thử crash thật tại mọi điểm trước/sau commit/delivery. Rollback có fault injection; persistence có restart/recreate, không gọi đó là mô phỏng mọi kiểu outage. Chưa thử toàn bộ lịch commit nguồn đảo ID, tài khoản bị thu hồi quyền trong từng bước finalizer/dispatcher hoặc chỉnh sửa tên trong lúc phân trang.

P0 cố ý khóa bảng tuần sau cutoff; nguồn đến quá muộn không sửa kết quả cũ. Demo theo giờ thật nên lịch sử chỉ có sau khi một tuần đã chốt, không có API đổi giờ/chốt sớm. Nhóm nhỏ vẫn xếp hạng nhưng giữ bậc; không hạ ngưỡng 10 thành viên/5 active để tạo kết quả đẹp.

Toàn bộ chức năng P0 chính đã được nối vào runtime; các ca chưa có bằng chứng ở ma trận vẫn là phạm vi nghiệm thu bổ sung, không tự ghi PASS.
