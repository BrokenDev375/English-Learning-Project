# M05 — Nghiệm thu P0 và bàn giao

Ngày 22/10/2026. Source nền `57f2951`; nghiệm thu trên working tree M05 trước commit, bàn giao qua nhánh `feature/m05-gamification-league`. Bổ sung và kết thúc chặng nghiệm thu sau [XP](module-5-xp-validation.md) và [Giải đấu tuần](module-5-league-validation.md). [Hướng dẫn bàn giao/demo](../../docs/M05_HANDOFF.md).

## Kết quả lượt cuối

| Kiểm tra | Kết quả thực thi |
|---|---|
| MySQL acceptance bổ sung + League/rank | **41/41**, 3 phút 51 giây, BUILD SUCCESS: 20 acceptance +14 League +7 ranking |
| 7 ca review/multipart/cap/context thêm sau đó | **7/7**, 1 phút 29 giây, BUILD SUCCESS; đã compile image dùng cho hồi quy |
| Toàn backend | **235/235**, 21 lớp, 23 phút 33 giây, BUILD SUCCESS; 0 lỗi/bỏ qua |
| Bổ sung quyền ADMIN / V5 giữ lịch ôn / quy tắc ngày | **4/4**, 3 phút 46 giây, BUILD SUCCESS; không đổi runtime so với image hồi quy |
| Toàn browser M01–M05 | **68/68**, 5,1 phút; gồm phản hồi pending cũ, summary cũ/thông báo lặp, backlog UI/keyboard, reconnect và chi tiết UI chặng 5 |
| Socket thật ngắt/kết nối lại | Có trong lượt 68/68; lượt kiểm tra riêng trước đó **1/1**, 10,8 giây; XP tự đối soát không manual sync |
| Production frontend build/deploy | PASS; cập nhật riêng client demo, giữ backend/database/session |
| OpenAPI/schema/fixture | **8 operation,31 schema,10 example,50 fixture**, đúng mong đợi; không coi là test runtime |
| Durability / backlog / SQL audit | **PASS** qua worker disabled→enabled, restart server, force recreate cả Compose/seed và replay |
| Đối soát sau toàn browser | **PASS**, 6 nhóm kiểm tra, 0 sai lệch; chỉ đọc, không repair/phát lại thưởng |

Không cộng các lượt trùng thành một lượt test lớn. Số test mới thấp hơn tổng các lượt vì fixture XP được tách ra để không kế thừa/chạy lại 12 ca XP ở lớp League. Source test bổ sung sau khi image hồi quy đã tạo được chạy riêng, không thay đổi code runtime.

## Các kiểm tra còn thiếu trước đây đã bổ sung

- Claim/ledger/day/profile/badge/processed/receipt lỗi: rollback toàn bộ và retry cùng key. Nguồn ID nhỏ giữ transaction mở, nguồn ID lớn của user khác commit/xét trước; worker sau đó vẫn tìm và xử lý nguồn nhỏ.
- Tài khoản khóa/đổi vai trò được trì hoãn, user khác không bị chặn, khôi phục quyền rồi xử lý nguồn còn thiếu. ADMIN/TEACHER không tạo thành tích học viên.
- Activation bằng mốc và sau 1 microsecond; profile 0/99/100/199/200; matching/fill nhiều phần chỉ thưởng theo câu, partial không nhận XP/ngày hoạt động.
- Cấp rồi bỏ, copy cùng origin, hai context cùng cycle và objective/day, REVIEW thiếu context, MISTAKES chưa đến hạn; ôn 2 objective qua nửa đêm/resume/replay giữ context, không bỏ câu trống khỏi mẫu số bonus. Nộp ôn trống đổi lastSubmission nhưng không tạo cycle mới.
- Daily195 + nguồn mới/bonus dùng chung 5 XP còn lại, claim bonus bị cap vẫn tiêu thụ/trao huy hiệu, không chuyển bonus sang hôm sau.
- Hai nhóm 25 người đầy tranh cấp nhóm mới; nhóm thứ3 chỉ tạo một lần. N=9/10, active4/5, quota nhóm25, hòa ở vị trí5–6 và20–21, khối hòa trọn vùng và bậc đầu/cuối.
- Nguồn trước endAt1 microsecond và đúng endAt thuộc đúng tuần; trước/đúng cutoff; giao dịch nhận điểm trước cutoff commit sau cutoff phải được finalizer chờ và giữ điểm. Nhóm đầu đã chốt, nhóm sau lỗi, worker mới tiếp tục nhóm sau mà không thay kết quả đầu.
- WebSocket có cookie session thật: subscription sửa scope bị từ chối; user khác không nhận invalidation cá nhân. Gửi event rồi lỗi trước đánh dấu delivered: retry cùng eventId, GET/ledger vẫn một phần thưởng.
- UI nhận GET pending cũ sau terminal từng có thể che mất XP. Đã sửa chỉ chấp nhận terminal tiến lên; có browser test trả response đảo thứ tự. Summary revision cũ, invalidation lặp, disconnect/reconnect, F5, mất ACK và đổi tài khoản không nhân XP.
- Hoàn tất chi tiết UI chặng 5: trần XP ngày/liên kết giải đấu ở trang chủ, thời gian tuần còn lại dựa trên serverTime và đồng hồ đơn điệu (test sửa Date.now vẫn đúng), diễn giải điều kiện thưởng ôn tại M04. Kiểm tra ảnh desktop 1280px/mobile 390px đạt, không tràn ngang.

Rollback dùng fault injection, commit đảo thứ tự dùng hai transaction/MySQL thật. Các fixture UI được đặt tên rõ, không dùng như bằng chứng backend. Hai câu multipart và các giá trị XP99/199 là fixture boundary trong DB test; không được thêm vào demo.

## Phục hồi dữ liệu bền vững

`node scripts/verify-m05-acceptance.mjs` chạy trên project `english-learning-m05-acceptance` tại 15177/18084, volume riêng. Không sửa `.env`, không xóa volume hoặc ghi vào demo.

Prepare làm LEARN đủ 5 GUIDED/5 independent và REVIEW bằng câu seed thật; tổng **40 XP**, hai huy hiệu, context ôn và membership lịch sử 0 điểm do job thật chốt. Một bài mới commit khi tắt worker, tổng còn 40; bật lại tự đọc bù thành **50 XP cá nhân/tuần**, không cần mở màn thưởng hoặc gọi sync. Kỳ vọng lấy từ FIRST facts thật, không giả định mọi câu ôn đều mới/đúng.

Sau restart và force recreate/seed: giữ badge timestamps, context cycle/selectedAt/dueAt/stage/eligibility, frozen history, memberKey/group/tier/joinedAt/weeklyXp, kết quả M03 và receipt join/submit/sync nguyên bản. Replay không thêm điểm hoặc membership.

SQL audit đối chiếu profile↔SUM ledger, day↔SUM ledger/cap, member↔SUM league ledger, weeklyXp↔frozen result, claim↔ledger và group.memberCount↔membership thực; **0 sai lệch**. Kiểm tra lại sau browser regression vẫn đạt.

## Phép đo workload local

Một backend, Docker được cấp **12 CPU /8.011.960.320 byte RAM**. Workload: 20 học viên,2 WebSocket/người,6 bài nộp/người = **40 socket/120 Submission**, câu seed thật. Worker bật mặc định poll 3 giây; không có manual sync trong phần đo. Điểm kỳ vọng từ origin/FIRST facts, so sánh API tổng/tuần và SQL audit.

| Số đo | Kết quả |
|---|---:|
| P50 HTTP submit ACK | 503ms |
| P95 HTTP submit ACK | 951ms |
| Từ bắt đầu nộp đến tất cả submit ACK | 10.764ms |
| Từ submit ACK cuối đến kiểm tra tất cả backlog rỗng | 3.970ms |
| Invalidation nhận trong cửa sổ đo | 1.040 |
| Sai lệch ledger | 0 |

Đây là workload hữu hạn trên máy local khi có tác vụ kiểm tra khác, không phải capacity test hoặc SLA production. Số event có thể thay đổi theo batch/nhóm/lần chạy; không dùng số event làm điểm hoặc khẳng định phát đúng một lần.

## Đối chiếu ma trận P0

| Tiêu chí | Nguồn bằng chứng |
|---|---|
| C01–C03 | verify-m05-contract.py,50 fixture; API/Socket runtime scope và trường riêng tư |
| D01–D02 | GamificationMigrationTest/AttemptPersistenceTest; V5 có Submission/lịch ôn nâng V8; durability recreate/seed |
| D03–D06 | GamificationContextApiTest + Acceptance:2objective, context rollback, midnight/resume/replay, blank-cycle |
| X01–X06 | LEARN seed thật25XP, wrong/correction, multipart partial/full, copy origin và cấp rồi bỏ |
| X07–X12 | DUE/MISTAKES/missingcontext/multiobjective/cycle/day/cap195 acceptance |
| X13–X19 | receipt/transaction faults, concurrent sync, late ID, disabled worker catch-up, role/schema deferral, activation equality |
| S01–S08 | GamificationApiTest, GamificationRulesTest, profile boundary fixture,3 badge tests và durability2 huy hiệu |
| L01–L13 | LeagueApiTest/LeagueRankingTest; allocator/25 quotas/ties/endAt/cutoff/finalizer resume và tier history |
| U01–U06 | Browser M05, Java socket thật, UI fixture trạng thái/keyboard/mobile; delayed reads, duplicate events, lost ACK và reconnect |
| A01–A04 | verify-m05-acceptance.mjs,6 SQL audits, backend/browser regressions và workload20/40/120 |

Đối chiếu là tổ hợp test tự động/fixture/runtime theo phạm vi trên, không tuyên bố đã thao tác tay toàn bộ biến thể hoặc mô phỏng mọi lịch tranh chấp/crash. Những giới hạn tải production, nhiều backend và mọi điểm crash ngoài phạm vi P0 không phải các chặng còn bỏ dở.

## Vận hành và artifact

Demo vẫn http://localhost:5176, `start-main.cmd`, schema V8. Source runtime/backend không đổi trong lượt nghiệm thu này; UI sửa phản hồi terminal đến muộn và hoàn tất chi tiết chặng 5. Đã build/deploy riêng client mới, giữ nguyên backend/database. Tài liệu API làm rõ revision vẫn tăng khi ghi BEFORE_ACTIVATION dù không tăng thành tích.

Test context được đóng AFTER_CLASS; fixture XP dùng chung, không nhân bản ca kế thừa. Log hồi quy 235 ca có 0 lần buộc dừng JVM và 0 cảnh báo pool kết nối cũ; không đánh đồng warning cũ với kết quả mới.

Log/snapshot/metrics nằm trong `.test-state/m05-final/`, ảnh/traces trong `source/client/test-results/` đã ignore. Script/test nguồn được giữ để tái hiện; không đưa account data/log/secret vào Git. Việc bàn giao Git lên nhánh riêng được thực hiện sau nghiệm thu theo yêu cầu người dùng. Đọc [bàn giao](../../docs/M05_HANDOFF.md) để tiếp tục bảo trì; migration tiếp theo phải là V9 trở đi.
