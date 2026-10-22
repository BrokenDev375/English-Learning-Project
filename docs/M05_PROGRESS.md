# Tiến độ M05 — Gamification và Giải đấu tuần

Ngày cập nhật 22/10/2026. Người dùng yêu cầu hoàn tất M05 theo từng chặng trên nền main/origin/main `57f2951` đã có M02/M03/M04. P0 có **XP/cấp độ/streak/huy hiệu và Giải đấu tuần** với schema V8; đã bổ sung nghiệm thu, phục hồi/đối soát/workload và [bàn giao](M05_HANDOFF.md). Demo http://localhost:5176, project `english-learning-main`. Nhánh bàn giao M05 theo yêu cầu người dùng: `feature/m05-gamification-league`. Policy: [m05-p0-v1](M05_DECISIONS.md).

## Tiến độ từng chặng

| Chặng | Trạng thái |
|---|---|
| 0 — luật/hợp đồng | Đã có decision log, 8 endpoint, 31 schema, 50 fixture, ma trận và validator |
| 1 — schema/context | Đã có V6, activation, immutable REVIEW context; 25/25 kiểm tra riêng ở mốc này |
| 2 — XP/consumer | Đã có claims/ledger, cap, terminal decisions, worker, receipt sync, retry metadata và API đối soát |
| 3 — hoạt động | Đã có qualified day, current/best streak, level và ba huy hiệu |
| 4 — Giải đấu tuần | Đã có season/group/join/allocator/ranking/tie/cutoff/finalizer/history, V8 |
| 5 — giao diện/kết nối | Hoàn tất: XP/cấp độ/streak/huy hiệu/trần ngày trên trang chủ, kết quả và trang phần thưởng; giải đấu/history/đếm ngược theo server, điều kiện thưởng ôn M04, outbox/socket/reconnect |
| 6 — nghiệm thu | Hoàn tất P0: hồi quy backend/browser, concurrency/fault injection, worker catch-up, restart/recreate/seed/replay, SQL audit, workload local và bàn giao |

## Bản demo có gì

- Học viên thấy XP, cấp độ, streak và huy hiệu trên dữ liệu thật. Trang phần thưởng có lịch sử phân trang và lý do từng quyết định.
- FIRST độc lập, đủ/đúng, không hỗ trợ, chưa gặp trước đó: 5 XP/origin duy nhất. Ôn đúng mốc dùng context đã chốt, thưởng 5 XP/objective/cycle và tối đa một objective/ngày. Trần chung 200 XP/ngày; claim hợp lệ bị trần vẫn được ghi nhận.
- Ngày hoạt động cần tự làm đủ `min(3, số câu độc lập được giao)` và đúng ít nhất một câu. Câu cũ có thể giữ streak, không tạo lại XP câu mới. Level = 1 + floor(totalXP/100).
- Ba huy hiệu: hoàn thành bài đầu đủ điều kiện, ôn đúng mốc, streak 7 ngày. Huy hiệu không thưởng XP riêng.
- Nguồn trước activation được đánh dấu BEFORE_ACTIVATION, không hồi tố XP/streak. Worker quét nguồn đã commit, không dựa vào MAX ID; POST sync dùng receipt cùng transaction với dữ liệu thưởng.
- Outbox được commit cùng dữ liệu, gửi invalidation cho socket cùng user đã subscribe. Client đọc GET để đối soát, không cộng điểm cục bộ. Polling/reconnect vẫn lấy lại được trạng thái khi mất thông báo.
- Nguồn schema không hỗ trợ rollback toàn batch và được trì hoãn để kiểm tra/thử lại; không ghi thành công giả. Hiện code hỗ trợ policy `m05-p0-v1`, chưa có API quản trị/chuyển policy.

## Điểm vào mã nguồn

Backend: `GamificationServiceImpl`, `GamificationRules`, `GamificationRepositoryImpl`, `GamificationWorker`, `GamificationFailureTracker`, `GamificationEventDispatcher`, `GamificationController`. `ReviewRewardContextRecorder` vẫn chạy MANDATORY trong transaction tạo REVIEW mới; resume/replay không capture lại. Worker và sync khóa actor qua AccountGuard. GET chỉ đọc và owner-scoped.

Frontend: `features/gamification/Rewards.tsx` chứa RewardStrip, RewardsDashboard, AttemptRewards; App đăng ký GAMIFICATION và invalidation theo user. `AttemptResult` gắn phần thưởng vào kết quả. Không dùng localStorage làm nguồn XP.

V6 tạo 9 bảng nền, V7 thêm `gamification_outbox`, V8 thêm season/allocator/group/member/outbox League và FK XP→season. Không sửa migration đã áp dụng hoặc dùng Hibernate create/update.

## Kiểm tra và vận hành

Xem [nghiệm thu P0](../statics/results/module-5-validation.md) cho lượt cuối/đối chiếu ma trận/giới hạn. Bằng chứng từng chặng: [XP](../statics/results/module-5-xp-validation.md), [League](../statics/results/module-5-league-validation.md), [chặng 0](../statics/results/module-5-stage0-validation.md), [chặng 1](../statics/results/module-5-stage1-validation.md). Các số ở biên bản cũ không tự áp dụng cho source mới.

Lượt cuối ngày 22/10: backend **235/235** (21 lớp, 0 lỗi/bỏ qua, BUILD SUCCESS), bổ sung riêng **4/4** ca quyền ADMIN/migration V5/quy tắc ngày; browser toàn hệ thống **68/68**. Build/contract, worker catch-up và restart/recreate/seed/replay đạt; 6 nhóm SQL audit không sai lệch. Context test đóng sạch, không còn cảnh báo pool cũ hoặc buộc dừng JVM. Các lượt có ca trùng không cộng thành tổng test mới.

Các mốc lịch sử: trước League 187 backend/59 browser; tích hợp League 214 backend/62 browser và 8 ca UI cuối, thêm riêng 1 ca cutoff. Xem biên bản từng chặng để biết source/phạm vi của các số này; kết quả cuối ở biên bản P0 phía trên.

Demo dùng `start-main.cmd`, cổng 5176/8083, WS origins tương ứng. Đã sao lưu trước V7 ở `.test-state/m05-local-before-v7/` và trước V8 ở `.test-state/m05-local-before-v8/`, giữ MySQL/asset volumes và `.env`. V8 đã lên demo, login/health/GET M05 đạt; XP/revision khớp backup trước nâng. Số liệu 3 users/6 lessons/90 questions/13 materials/2 submissions là mốc trước V7, không phải tổng cố định vì người dùng tiếp tục học. Hai bài trước activation không được cộng hồi tố. Test có ghi dữ liệu chạy ở `english-learning-m05-e2e` cổng 15176/18083 hoặc Testcontainers, không chạy vào demo.

## Giải đấu tuần đã nối vào hệ thống

`LeagueServiceImpl` cung cấp 4 endpoint và tích hợp quyết định điểm vào transaction XP. Pre-lock season theo thứ tự trước khi xử lý batch nguồn; điểm chỉ tính từ submittedAt thuộc tuần và sau joinedAt, kiểm tra cutoff dưới khóa season. XP đã quyết định trước V8 không bị hồi tố.

`LeagueRanking` dùng chung thuật toán khối hòa cho bảng tạm và kết quả chốt. `LeagueLifecycle` tạo tuần hiện tại bằng khóa policy, đóng/chốt từng nhóm trong transaction riêng; cập nhật kết quả trên membership có sẵn. `LeagueWorker` tiếp tục nhóm chưa xong; nhóm đã chốt không đổi khi chạy lại. Finalizer không khóa actor hoặc ghi FK user mới; membership/outbox dùng DynamicUpdate để tránh cập nhật cột FK không thay đổi.

`LeagueDashboard` cho phép join, xem bảng nhóm, vị trí riêng ngoài trang, trạng thái chờ tuần trước và lịch sử. Socket chỉ gửi invalidation đến thành viên nhóm, không nhận groupId từ client; GET là dữ liệu đối soát. Đã đo workload local trên một backend; chưa kiểm chứng quy mô production hoặc nhiều backend.

Bằng chứng chặng League: [kiểm tra Giải đấu tuần](../statics/results/module-5-league-validation.md). Lượt chốt P0 bổ sung nguồn commit đảo thứ tự, lỗi từng projection/receipt, multipart/partial, nhiều objective/cap195, allocator hai nhóm đầy, exact endAt, finalizer tiếp tục nhóm còn thiếu, socket scope/outbox replay, phản hồi UI đảo thứ tự và reconnect thật. Runtime/backend giữ nguyên; UI chặn pending cũ thay terminal.

Bộ `verify-m05-acceptance.mjs` đã đạt qua worker tắt/bật, restart, force recreate/seed và replay, giữ huy hiệu/context/frozen history/điểm tuần; 6 nhóm SQL audit không sai lệch. Workload local 20 học viên/40 WebSocket/120 Submission đã đo và ghi cấu hình/thời gian trong biên bản. Đây là nghiệm thu P0 theo phạm vi test, không phải chứng minh mọi lịch crash/interleaving hoặc tải production.
