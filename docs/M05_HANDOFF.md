# Bàn giao M05 P0

Ngày 22/10/2026. Nền Git `57f2951`, nhánh bàn giao M05: `feature/m05-gamification-league`. Người dùng yêu cầu commit và đẩy code lên nhánh riêng; chưa yêu cầu merge vào main. [Kết quả nghiệm thu](../statics/results/module-5-validation.md), [luật P0](M05_DECISIONS.md), [API](../source/common/M05_API.md).

## Chạy và demo

Mở Docker Desktop, chạy `start-main.cmd`, vào http://localhost:5176. Project `english-learning-main`, API 8083; `.env`, volume MySQL/asset hiện có được giữ. `start.cmd` dùng project/cổng mặc định khác, không dùng lẫn. Đăng nhập lại nếu backend vừa restart vì session nằm trong RAM.

Với học viên mới:

1. Mở **Giải đấu tuần**, bấm **Tham gia tuần này** trước khi làm bài. Nhóm được chọn tự động cùng bậc, tối đa 25 người. Hai tài khoản cần hai browser profile hoặc cửa sổ thường/ẩn danh.
2. Tự làm đúng câu độc lập mới để nhận 5 XP/câu; GUIDED, hỗ trợ, sai/thiếu và chữa lại không nhận XP câu mới. Trang kết quả giải thích cả XP cá nhân và điểm giải đấu. Câu được xem từ lúc cấp lượt nên mở rồi bỏ cũng tính đã gặp.
3. Hoàn thành LEARN đủ điều kiện nhận huy hiệu bài đầu. Ôn mục tiêu đến hạn và tự làm đúng đầy đủ phần được giao nhận bonus 5 XP/mục tiêu; tổng mọi nguồn tối đa 200/ngày Việt Nam. Huy hiệu không có XP riêng.
4. Ngày hoạt động cần đủ `min(3, số câu độc lập được giao)` FIRST tự làm đầy đủ, có ít nhất một câu đúng. Câu cũ có thể giữ streak. Level hoạt động = 1 + floor(totalXP/100), không phải trình độ tiếng Anh.
5. Bảng nhóm và tổng XP tự cập nhật; có thể dùng nút cập nhật khi cần đối soát. XP trước join không cộng ngược vào tuần. Gửi lại, F5 hoặc mất ACK không cộng trùng.

Nhóm nhỏ vẫn có thứ hạng nhưng giữ bậc: cần ít nhất 10 thành viên/5 người có XP mới xét lên/xuống. Bằng XP cùng hạng; khối hòa vắt quota giữ bậc. Đồng không xuống thấp hơn, Kim Cương không lên cao hơn. Không có tài khoản/điểm giả được thêm vào demo để đủ nhóm.

Tuần bắt đầu thứ Hai 00:00 Việt Nam. Sau hết tuần có 120 giây xét nguồn thuộc tuần cũ; đúng cutoff hoặc muộn hơn không đổi bảng tuần cũ. XP cá nhân hợp lệ vẫn được ghi. Job chốt từng nhóm, kết quả có trong **Lịch sử tuần của bạn**; tuần sau phải tham gia lại. Không có API đổi giờ/chốt sớm trong app thật.

## Điểm vào mã nguồn

- `GamificationServiceImpl`: nguồn immutable FIRST/Submission, activation, claims/cap, profile/day/streak/badge, processed decision và sync receipt. `GamificationRules`: điều kiện thuần.
- `GamificationWorker`: quét NOT EXISTS, luân phiên user; `GamificationFailureTracker`: hoãn nguồn theo tài khoản/schema, không đánh dấu thành công giả.
- `LeagueServiceImpl`: overview/join/board/history, pre-lock season, điểm tuần và lý do loại. `LeagueRanking`: thuật toán chung cho bảng tạm và frozen result.
- `LeagueLifecycle`/`LeagueWorker`: tạo tuần, đóng và chốt từng nhóm; tiếp tục nhóm chưa xong. Finalizer không khóa actor hoặc tạo FK user mới.
- Hai dispatcher/outbox: invalidation sau commit. `AccountSocketHandler` chỉ nhận resource GAMIFICATION/LEAGUE, scope do server xác định. Client luôn GET đối soát.
- `Rewards.tsx`, `League.tsx`: UI theo user. Kết quả terminal không bị GET pending cũ che mất; summary không lùi revision. Đóng socket và bỏ component cache khi đổi tài khoản.

Mọi ghi thưởng/claim/ledger/projection/member/processed/outbox/receipt sync cùng transaction. Join có receipt riêng. Thứ tự actor → season theo thời gian → allocator/group → dữ liệu thưởng; finalizer không quay về actor. Không sửa FIRST, Submission, context hoặc phần thưởng đã quyết định để thay luật cũ.

## Database và vận hành

V1–V8 đã áp dụng trên demo; thay đổi schema mới cần V9 trở đi. V6 lưu policy/context/thưởng, V7 outbox cá nhân, V8 League. Giữ Flyway và Hibernate validate; không repair checksum hoặc dùng create/update. Backup trước V6/V7/V8 nằm trong `.test-state/` đã ignore.

Policy hiện hỗ trợ `m05-p0-v1`. Chưa có màn quản trị policy hoặc reset mùa. Thay luật cần version/mốc ngày hoặc tuần, cập nhật đồng bộ code/API/schema và kiểm tra migration; không sửa trực tiếp policy đang dùng.

Worker mặc định bật, poll 3 giây; test properties tắt scheduler để điều khiển Clock. PENDING còn nguồn đang chờ; DEFERRED có retry metadata; BEFORE_ACTIVATION là quyết định cuối 0 XP. Trước activation không nhận XP/streak/badge hồi tố, nhưng revision có thể tăng khi lịch sử được ghi.

Nếu chưa thấy điểm: kiểm tra đã nộp thật, lý do FIRST/seen/cap, backlog và đúng tài khoản/port. Nếu điểm cá nhân có nhưng điểm tuần 0: kiểm tra join, submittedAt và cutoff; không sửa ledger để cộng bù qua tuần đã chốt. Nếu API báo LEAGUE_SETTLEMENT_PENDING: chỉ nhóm cũ của chính học viên cần chốt, không phải đợi toàn bộ season.

## Tái hiện nghiệm thu

Docker Desktop, `.env` hợp lệ ở root và dependency frontend đã cài:

```powershell
node scripts/verify-m05-acceptance.mjs
python scripts/verify-m05-contract.py
```

Script Node chỉ dùng project `english-learning-m05-acceptance`, UI15177/API18084, volume riêng. Nó tạo dữ liệu học thật, một membership lịch sử **0 điểm** làm fixture cho job chốt thật, rồi kiểm tra backlog, restart, recreate/seed, replay, context/huy hiệu/frozen history và 6 truy vấn đối soát. Cuối cùng đo 20 học viên/40 WebSocket/120 bài nộp và dừng container, giữ volume. Không nhận URL/container demo, không xóa/reset dữ liệu.

Đối soát chỉ đọc khi môi trường test đang chạy: `node scripts/verify-m05-acceptance.mjs audit`. Script kiểm tra profile/day/member/frozen result/claim-ledger/group count; không phát thưởng lại hoặc tự sửa projection. Log/snapshot/chìa request/tài khoản test nằm trong `.test-state/m05-final/` đã ignore.

Backend:

```powershell
docker build --target test -t english-learning-m05-final-test ./source/server
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-m05-final-test mvn -B -ntp test
```

Frontend dùng runner mặc định ở project riêng, hoặc trỏ E2E_BASE_URL vào môi trường **test** đã chạy. Không trỏ browser test có ghi dữ liệu vào demo. Xem biên bản cho lệnh và số ca chính xác từng lượt; không cộng các lượt trùng thành số test mới.

## Phạm vi bàn giao

P0 gồm XP/cấp độ/streak/3 huy hiệu/Giải đấu tuần, UI và khôi phục từ dữ liệu đã commit. Chưa gồm offline/outbox client M06, thống kê nâng cao M07, solo M08, nhiều backend hoặc SLA tải production. Phép đo local chỉ chứng minh workload đã ghi trong biên bản; không suy ra mọi kiểu outage hay mọi interleaving đều được mô phỏng.
