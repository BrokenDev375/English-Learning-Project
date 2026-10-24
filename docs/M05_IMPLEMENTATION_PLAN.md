# M05 — XP, cấp độ hoạt động, streak, huy hiệu và Giải đấu tuần

Ngày cập nhật: 22/10/2026. Trạng thái: **hoàn tất các chặng 0–6 của M05 P0: XP/cấp độ/streak/huy hiệu và Giải đấu tuần, worker/API/sync/UI/outbox/socket; V6–V8.** Baseline `m05-p0-v1`; xem [quyết định P0](M05_DECISIONS.md), [tiến độ](M05_PROGRESS.md), [nghiệm thu cuối](../statics/results/module-5-validation.md) và [bàn giao](M05_HANDOFF.md). Phần dưới giữ thiết kế/tiêu chí; biên bản cuối ghi rõ bằng chứng và giới hạn kiểm tra. Thay đổi luật cần version mới, không sửa phần thưởng đã commit.

## 1. Nền mã đã đọc và điều kiện bắt đầu

| Phần | Nguồn đã đối chiếu | Ý nghĩa với M05 |
|---|---|---|
| M02 | V3, hợp đồng bàn giao M02–M03, PublishedContentReader | Nội dung có phiên bản; copy giữ originKey. Không lấy version mới làm nguồn thưởng mới |
| M03 | nền `be92240`, hiện được giữ trong main `57f2951`; AttemptServiceImpl, AttemptCompletionServiceImpl, AttemptViews, SubmissionEntity | FIRST và Result schema 1 được lưu bền vững; có mode, originKey, hỗ trợ, câu cũ/mới, submittedAt và lessonCompleted |
| M04 | main/origin/main tại `57f2951` | V5, ôn theo mục tiêu, lịch 1–3–7 ngày, ledger xử lý Submission và provider REVIEW |
| Dùng chung | RequestReceiptServiceImpl, AccountGuard, AccountSocketHandler, LiveConnections | Receipt khóa actor trước mutation; socket hiện chỉ có CONNECTED/HELLO/HEARTBEAT/ACK/ERROR |

Ở lượt lập kế hoạch, main local còn `be92240` nên M04 được đọc qua nhánh remote. Khi bắt đầu chặng 0, fetch thấy origin/main đã có `57f2951`; đã fast-forward local main lên cùng commit, giữ nguyên kế hoạch M05 chưa commit. Không tạo branch/commit/push và không đổi database demo.

Biên bản M04 ghi 157 backend test và 55 ca trình duyệt khác nhau đã qua, trong đó có một ca fixture được sửa rồi chạy lại. Đây là bằng chứng ghi trong nhánh M04, không phải test vừa chạy lại trong lượt lập kế hoạch M05.

Tài liệu hiện có: [kế hoạch tổng](../PLAN_ENGLISH_LEARNING_SYSTEM.md), [bàn giao](M02_M03_HANDOFF.md), [tiến độ M03](M03_PROGRESS.md), [tiến độ M04](M04_PROGRESS.md), [API M04](../source/common/M04_API.md), [kiểm tra M04](../statics/results/module-4-validation.md).

## 2. Phạm vi và nguyên tắc

M05 có đủ năm phần: XP, cấp độ hoạt động, streak, huy hiệu và Giải đấu tuần. XP biểu thị hoạt động học đủ điều kiện; không suy ra trình độ tiếng Anh, không ghi vào gợi ý độ khó của M04.

- Server quyết định phần thưởng từ Submission đã commit. Client không gửi số XP, ngày học, hạng hoặc kết quả tự chấm.
- Nộp lại request, đổi requestId, đổi phiên bản/copy câu, hai tab, restart và chạy lại job không tạo phần thưởng trùng.
- Tổng XP, XP trong ngày và XP dùng cho giải đấu phải giải thích được từ cùng sổ sự kiện. Chốt tuần không trừ tổng XP hoặc reset tiến độ M03/M04.
- Việc học/nộp bài M03 không phụ thuộc M05 đang xử lý nhanh hay chậm. Khi phần thưởng chưa cập nhật, UI nói rõ đang xử lý; không hiển thị mất XP như một kết quả cuối cùng.
- Giữ cấu trúc Controller → Service interface/impl → Repository interface/impl → JPA. Không sửa V1–V5, không ghi trực tiếp vào database để cộng điểm thủ công.
- M06 tiếp tục chịu trách nhiệm offline/outbox toàn ứng dụng; M05 phải có phục hồi server và chống trùng riêng ngay từ P0. M08 solo không nằm trong kế hoạch này.

## 3. Bộ luật thưởng P0

### 3.1. XP

| Hoạt động | XP P0 | Điều kiện và khóa nguồn |
|---|---:|---|
| Tự làm đúng câu mới | 5/câu | FIRST complete, CORRECT, không GUIDED, không hỗ trợ, wasSeenBefore=false; duy nhất user + originKey + loại thưởng |
| Ôn đúng mốc | 5/mục tiêu/chu kỳ | Có bằng chứng mốc đã tới khi cấp lượt; phần câu của mục tiêu trong lượt được tự làm đúng đầy đủ; duy nhất user + objective + cycleKey |
| Hoàn thành bài mới | XP từ các câu đủ điều kiện | Chưa thêm thưởng cố định theo lần bấm nộp/bản Lesson mới; hoàn thành bài đầu có huy hiệu |
| Sửa lỗi bằng câu biến thể mới | Như câu mới | Nhận 5 XP theo origin mới đủ điều kiện; không cộng thêm lần nữa chỉ vì gắn nhãn sửa lỗi |
| GUIDED/hint/check, CORRECTION sau nộp, bỏ trống hoặc sai | 0 | Vẫn giữ kết quả và tiến độ học theo M03/M04 |
| Làm lại câu cũ ngoài chu kỳ ôn đủ điều kiện | 0 | Không cấm luyện; giải thích rõ lý do không có XP |

Hai loại thưởng đầu được cộng cùng nhau nếu cùng thỏa điều kiện. Ví dụ câu biến thể mới đúng trong một chu kỳ ôn: có XP câu mới và bonus ôn theo mục tiêu, mỗi nguồn chỉ nhận một lần. Không thưởng theo từng ô điền/cặp matching.

Đề xuất trần **200 XP/ngày/người**, áp dụng cả tổng XP nhận mới và điểm đưa vào giải đấu; không có một loại XP không giới hạn khác để lách trần. Ngày theo Asia/Ho_Chi_Minh. Trong batch các nguồn đã commit, xét Submission ID rồi item position, bonus mục tiêu theo objectiveId. Nguồn ID nhỏ commit muộn có thể được xét sau nguồn ID lớn; quyền nhận phần trần còn lại theo thứ tự quyết định đã commit và lưu trong ledger, không tính lại theo một thứ tự giả định khi rebuild. Khi chạm trần, ghi requested/awarded/reason; phần bị cắt không được dồn qua ngày sau. Claim đã xét hợp lệ nhưng bị trần chặn được ghi nhận đã tiêu thụ để không nhận lại bằng request khác.

**Đánh đổi P0 đã chọn:** M03 đánh dấu seen ngay khi cấp câu, kể cả người học bỏ lượt. Với luật câu mới nghiêm ngặt này, mở rồi bỏ cũng mất điều kiện XP câu mới; vẫn có thể nhận thưởng ôn hợp lệ sau này. Không đổi nghĩa wasSeenBefore hoặc thêm first-answer evidence trong P0. Ngân hàng seed hữu hạn nên phải có thưởng ôn lặp theo chu kỳ; không hứa luôn còn câu mới để kiếm XP.

### 3.2. Cấp độ, ngày hoạt động, streak và huy hiệu

- Cấp độ hoạt động đề xuất: `1 + floor(totalXp / 100)`. UI ghi rõ là cấp độ hoạt động, không dùng A1/B1 hoặc nhãn trình độ.
- Một Submission được tính ngày hoạt động khi có ít nhất `min(3, số câu không GUIDED được cấp)` câu FIRST trả lời đầy đủ, tự làm, và ít nhất một câu FIRST đúng tự làm. Nếu không có câu độc lập thì không đủ điều kiện. Không cần có XP mới: đạt trần hoặc luyện lại nghiêm túc vẫn có thể giữ streak.
- Nhiều Submission/ngày chỉ tạo một activity day. Ngày dùng submittedAt của server, không dùng đồng hồ client. Streak hiện tại bằng chuỗi liên tiếp kết thúc hôm nay hoặc hôm qua; nếu ngày hoạt động cuối cũ hơn thì hiển thị 0. Lưu riêng bestStreak.
- Đề xuất không freeze, không mua ngày, không tự ghi lùi từ timestamp client. Với backlog server, submittedAt gốc vẫn là ngày học; có thể cần tính lại chuỗi từ các activity day thay vì tăng một bộ đếm theo thứ tự worker chạy.
- Ba huy hiệu P0: **Bài học đầu tiên** (lessonCompleted và Submission đủ ngày hoạt động), **Ôn đến hạn** (có claim ôn hợp lệ, kể cả XP bị trần chặn), **Bảy ngày liên tiếp** (bestStreak ≥ 7). Duy nhất user + badgeCode; chưa cộng XP cho huy hiệu để tránh vòng lặp thưởng.

## 4. Bằng chứng ôn đúng mốc

Không thể tính bonus đúng lịch bằng cách đọc dashboard M04 hiện tại sau khi sync: mốc có thể đã đổi. Result schema 1 cũng chưa chứa dueAt hoặc cycleKey.

Đề xuất thêm bảng `review_reward_context` với khóa attempt + objective. Trong transaction cấp lượt REVIEW, sau khi M04 chọn câu và M03 tạo Attempt, lưu bằng chứng cho các mục tiêu thực sự được cấp: user, objective, selectedAt, nextReviewAt, lastSubmissionId để audit, cycleKey, stage, trạng thái cần luyện/lỗi sai và policyVersion. Có hook service nội bộ; không đưa dependency ngược từ M04 sang M03 và không nhét dữ liệu tạm vào biến RAM.

- Chỉ chu kỳ đã có lịch từ một lần tự làm tốt, stage > 0, không needsPractice/hasMistakes và nextReviewAt <= selectedAt mới đủ điều kiện bonus. M04 hiển thị DUE khi mốc tới; không yêu cầu nhãn UI vẫn là SCHEDULED. Bonus này nghĩa là ôn từ lúc đến hạn, kể cả quá hạn, không phải chứng minh ôn chính xác trong ngày đến hạn. Cần luyện ngay do trả lời sai không đồng nghĩa đã đến một mốc ôn có thưởng.
- cycleKey là định danh ổn định của lịch: hash có schema của user + objective + nextReviewAt UTC chính xác + stage. Không dùng lastSubmissionId làm cycleKey: M04 cập nhật trường đó cả khi nộp trống và giữ nguyên mốc. Hai lượt cùng lịch chỉ có một claim; thêm unique một bonus/mục tiêu/ngày để phòng nhiều context cũ. Test nộp trống không được tạo chu kỳ thưởng mới.
- Tại nộp, mọi item được cấp cho mục tiêu đó phải có FIRST đầy đủ, không hỗ trợ, không GUIDED và đúng. Không dùng finalOutcome sau sửa, không bỏ câu sai/trống ra khỏi mẫu số.
- Context được lưu cùng transaction tạo Attempt; resume/replay không tạo context mới và không tính lại selectedAt.
- Lượt đã tồn tại trước M05 không có context: vẫn xử lý XP câu đủ điều kiện nhưng không bịa bonus đúng mốc. Ghi reason `NO_REVIEW_CONTEXT`.
- M04 vẫn cập nhật lịch theo hợp đồng đã có. Context M05 là bằng chứng bất biến cho thưởng, không ghi đè lịch và không thay stage.

## 5. Luật Giải đấu tuần P0

### 5.1. Tuần, tham gia và điểm

- Tuần: thứ Hai 00:00 đến thứ Hai kế tiếp 00:00, khoảng `[startAt, endAt)`, theo Asia/Ho_Chi_Minh; lưu Instant UTC.
- Bậc: Đồng → Bạc → Vàng → Bạch Kim → Kim Cương. Người mới bắt đầu Đồng.
- Người học chủ động tham gia từng tuần; tuần không tham gia không tự bị xuống hạng. Một user chỉ có một membership trong một tuần. P0 không chuyển nhóm/rời rồi tham gia lại trong tuần.
- Điểm giải đấu chỉ gồm awarded XP từ Submission có submittedAt trong tuần và không sớm hơn joinedAt. XP trước khi tham gia vẫn thuộc tổng XP nhưng không được cộng ngược vào bảng tuần.
- Người không online vẫn có vị trí. TEACHER/ADMIN không tham gia bằng luồng preview; tài khoản STUDENT test có đánh dấu và chỉ dùng trong môi trường demo/test.
- Mỗi bậc chia nhóm tối đa **25 người**; chọn nhóm còn chỗ ổn định bằng allocator có khóa theo season + tier. Không chuyển người giữa nhóm để cân lại sau khi họ đã có điểm.

### 5.2. Thứ hạng, lên/xuống và hòa điểm

Đề xuất chỉ áp dụng lên/xuống khi nhóm có ít nhất **10 thành viên và 5 người có XP > 0**; nếu thiếu thì giữ bậc và vẫn lưu thứ hạng. Nhóm nhỏ demo có cấu hình riêng, phải ghi rõ luật demo khác luật mặc định.

Mỗi phía có quota `floor(N * 20%)`, với N là số membership của nhóm lúc chốt. Top đủ điều kiện lên một bậc, bottom xuống một bậc, còn lại giữ. Đồng không xuống thấp hơn; Kim Cương không lên cao hơn. Không phạt bằng cách xóa totalXp, streak hoặc tiến độ.

Người bằng XP có cùng competition rank, ví dụ 1, 1, 3. Nếu một khối hòa điểm nằm vắt qua ranh giới quota thì cả khối giữ bậc, không bốc thăm hoặc dùng ID/thời điểm đăng nhập để quyết định thưởng. Không mở rộng quota hoặc chuyển suất thừa cho người kế tiếp. Người 0 XP không được lên hạng. Thứ tự ID chỉ giúp hiển thị ổn định bên trong một khối hòa, không phá hòa về quyền lợi. UI dùng cùng hàm phân vùng với job chốt.

Ví dụ nhóm đủ 25 người: tối đa 5 lên, 5 xuống. Nếu hạng 5 và 6 hòa điểm và khối hòa đi qua ranh giới top 5, cả khối giữ bậc; chỉ khối phía trên được lên. Nếu cả nhóm bằng 0 thì giữ toàn bộ vì không đủ số người có hoạt động.

### 5.3. Chốt tuần, backlog và thời điểm biên

Season đi qua OPEN → CLOSING → FINALIZED. Sau endAt đóng nhận tham gia và mở cửa sổ xử lý muộn đề xuất **2 phút**, chỉ nhận Submission có submittedAt thuộc tuần vừa hết. Tuần mới có thể tồn tại ngay nhưng người có kết quả bậc tuần trước chưa chốt sẽ nhận `LEAGUE_SETTLEMENT_PENDING` khi tham gia, thử lại sau.

Khi hết cửa sổ: khóa điểm tuần trước. Worker chốt từng nhóm bằng transaction riêng, lưu outcome ngay trên membership cùng marker group finalized, rank, điểm chốt, bậc cũ/mới và policyVersion. Chỉ một kết quả cho season + group; job chạy lại hoặc chết giữa các nhóm tiếp tục phần chưa xong. FINALIZED chỉ khi mọi nhóm đã chốt. Không xóa/bắt đầu lại membership cũ khi sang tuần mới.

Submission xử lý sau cutoff vẫn nhận lifetime XP/ngày hoạt động đúng nguồn nếu đủ điều kiện, nhưng không sửa bảng tuần đã khóa và không đẩy XP cũ sang tuần mới. Ghi `LATE_AFTER_CUTOFF` và giải thích được trên lịch sử. Đây là lựa chọn công bằng cần được người dùng chốt: ưu tiên kết quả tuần ổn định, đổi lại có giới hạn thời gian xử lý trễ. Không gọi cửa sổ 2 phút là bảo đảm xử lý mọi outage.

Tách `submittedAt` (server ghi khi nộp), `processedAt` (worker xét), `leagueCountedAt` và `activityDate`. Chưa coi submittedAt là thời điểm commit vật lý chính xác. Ca transaction bắt đầu trước endAt nhưng commit sau phải kiểm thử theo đúng chính sách cửa sổ trên.

## 6. Nguồn dữ liệu, ledger và phục hồi

### 6.1. Pipeline

1. M03 commit Submission như hiện tại; không bắt transaction submit phụ thuộc worker M05.
2. Worker bền vững tìm Submission chưa có ledger M05 bằng NOT EXISTS, join Attempt để lấy owner. Không chỉ dùng callback AFTER_COMMIT hoặc con trỏ max ID vì ID lớn có thể commit trước ID nhỏ.
3. Trong transaction một user/batch nhỏ: khóa actor, kiểm tra trạng thái quyền hiện hành, đọc nguồn immutable, xác định policy/activation và khóa ngày/season liên quan theo thứ tự cố định.
4. Tính quyết định, claim nguồn, trần ngày, XP ledger, total/day projections, activity day, badge, membership points, outbox và processed ledger trong cùng transaction. Receipt cũng cùng transaction nếu gọi bằng POST sync.
5. Sau commit phát thông báo; UI GET lại trạng thái đã lưu. Rollback bất kỳ bước nào không để lại XP một nửa hoặc marker đã xử lý giả.

Đề xuất tối đa 100 Submission/batch, giao dịch riêng mỗi user; worker chạy định kỳ với giới hạn và luân phiên user. POST sync là đường đối soát theo yêu cầu, không phải điều kiện duy nhất để XP/giải đấu được cập nhật. UI kết quả không phải mở trang M05 mới có điểm. Phân trang lịch sử, index pending và khả năng worker theo kịp phải đo trong môi trường test.

Tài khoản bị khóa/đổi vai trò: worker không giả principal bỏ qua AccountGuard. Lưu trạng thái tạm hoãn/quarantine để xử lý rõ ràng, không cộng âm thầm, không để một nguồn lỗi chặn toàn bộ người khác. Khôi phục quyền xử lý lại nguồn chưa hoàn tất theo cùng cutoff; không sửa lại tuần đã khóa. Facts schema không hỗ trợ ghi lỗi có thể quan sát và không đánh dấu nguồn thành công.

### 6.2. Thời điểm bật và policy version

Lưu `activatedAt` và snapshot luật một lần trong database, không lấy thời điểm mỗi lần restart làm mốc. Đề xuất không thưởng hồi tố dữ liệu trước activatedAt; đánh dấu `BEFORE_ACTIVATION` để không quét lại mãi. Nếu sau này nhập lịch sử thì dùng thao tác có kiểm soát, không tính vào giải đấu đã chốt.

Các Submission mới chưa xử lý sau restart được đọc bù. Rule version của nguồn phải chọn ổn định theo activatedAt/effectiveAt, không theo lúc worker tình cờ chạy. Policy đang dùng và policy của season được lưu bền vững; đổi cấu hình không được đổi phần thưởng đã commit hoặc quota giữa tuần. Thay đổi policy thưởng chốt theo mốc ngày; luật giải đấu chốt theo mốc tuần. Không sửa ledger XP cũ để tính lại số đẹp hơn.

## 7. Schema dự kiến và ràng buộc

M04 dùng V5. M05 có V6 nền thưởng/context, V7 outbox và V8 Giải đấu tuần. Không sửa V1–V8 đã áp dụng; schema tiếp theo cần migration mới.

| Bảng dự kiến | Vai trò / ràng buộc quan trọng |
|---|---|
| gamification_policies | Snapshot luật, activatedAt/effectiveAt, version; không sửa version đã áp dụng |
| gamification_processed_submissions | Một marker hoàn tất mỗi submission, owner/policy/disposition; index pending/đối soát |
| review_reward_context | PK attempt + objective, owner/cycle/dueAt/selectedAt; nguồn server tạo, không client nhập |
| gamification_reward_claims | UNIQUE user + rewardType + sourceKey, khóa chống thưởng cùng origin/chu kỳ; bonus có thêm unique user + rewardType + objectiveId + activityDate; objectiveId của claim câu mới để NULL; giữ quyết định bị cap |
| xp_ledger | Source submission/claim, requestedXp, awardedXp, reason, policy, submittedAt/activityDate, season hoặc league exclusion; không thưởng âm trong P0 |
| gamification_profiles | PK user, totalXp BIGINT, revision; projection đối soát được bằng SUM ledger |
| gamification_daily_stats | PK user + localDate, awardedXp, qualified count; trần ngày và rebuild streak |
| user_badges | UNIQUE user + badgeCode, earnedAt, nguồn chứng minh |
| league_seasons | UNIQUE startAt, end/cutoff/status, snapshot timezone/quota và revision |
| league_group_allocators | PK season + tier; khóa cấp nhóm/chỗ, không chỉ COUNT rồi INSERT không khóa |
| league_groups | season/tier/groupNo, capacity/memberCount, trạng thái chốt/revision; unique danh tính nhóm |
| league_members | UNIQUE season + user, group/joinedAt/weeklyXp; kết quả frozen rank, tierBefore/tierAfter/outcome ngay trên row |
| gamification_outbox | Event ID duy nhất, resource/revision/payload tối thiểu, delivery/retry state; không chứa đáp án/token |

CHECK tổng/điểm không âm; FK owner/source; các index actor/time, group/points và pending phải có trong DDL. GET không tạo profile/membership ngầm. Profile rỗng trả dữ liệu mặc định; POST activation/join/sync hoặc worker là các đường ghi rõ ràng.

Bậc tuần kế tiếp lấy từ membership gần nhất đã finalized; tuần vắng không tự đổi bậc. Không cần job chốt khóa users để cập nhật một trường rank trong user. TotalXp và các projection phải có truy vấn đối soát; sửa projection không đồng nghĩa phát lại claim/thưởng mới.

## 8. Transaction và tranh chấp

- Mutation do người học: actor → các season theo startAt tăng dần → allocator/group theo ID → membership/claim/profile/day theo thứ tự quy định. Nếu có khóa dùng chung/read cho season phải kiểm chứng với MySQL thực; không giả định đọc status không khóa là đủ.
- Worker giữ cùng thứ tự với POST sync. Mỗi transaction một user, không khóa toàn bộ user cùng lúc. Nếu lock actor làm cần tài khoản hiện hành thì dùng cơ chế dành cho worker có kiểm tra trạng thái; không tạo UserPrincipal từ dữ liệu client.
- Finalizer khóa season/group rồi cập nhật membership đã có, không gọi AccountGuard hoặc quay lại khóa actor. Không INSERT kết quả có FK users trong lúc chốt nếu có thể tạo chu trình khóa ngầm; kết quả dùng row membership đã có. Kiểm tra cả khóa ngầm FK khi integration test.
- Các thao tác cấp REVIEW cần snapshot context tuân theo actor lock của M03; không mở transaction riêng ghi context sau khi đã trả Attempt thành công.
- Nếu hai request tranh suất cuối nhóm, cả hai không được cùng thấy còn chỗ rồi vượt capacity. Unique membership chặn join trùng; allocator chặn vượt nhóm.
- Chốt tuần và cộng điểm phải có điểm tuần tự hóa trên season/group. Một khoản XP hoặc được nhận trước cutoff và nằm trong snapshot chốt, hoặc có reason loại khỏi tuần; không có khoản nằm nửa trong/nửa ngoài.
- Deadlock/lock timeout: retry transaction toàn bộ với số lần giới hạn và cùng nguồn/key; không tiếp tục trong transaction đã hỏng. Kiểm tra fault injection rollback, không chỉ mock method được gọi.

## 9. API dự kiến

Cookie session + CSRF; principal quyết định user; no-store/private cho dữ liệu cá nhân. Hợp đồng trong [M05_API.md](../source/common/M05_API.md) và [openapi-m05.json](../source/common/openapi-m05.json); cả 8 endpoint đã có runtime.

| Endpoint | Hành vi chính |
|---|---|
| GET /api/me/gamification | totalXp, level/next threshold, dailyXp/cap, streak/best, badge summary, pendingSubmissions, revision, serverTime/zone |
| POST /api/me/gamification/sync | requestId; xử lý batch nguồn của mình, trả processed/remaining, replay receipt; không client truyền điểm |
| GET /api/me/gamification/activities | Phân trang nguồn XP, breakdown/reasons; lọc submissionId để trang kết quả xem phần thưởng |
| GET /api/me/badges | Đã đạt/chưa đạt, điều kiện và tiến độ từ dữ liệu của mình |
| GET /api/me/league | Trạng thái chưa tham gia/đang chờ/đang thi/đã chốt, season/group/tier và snapshot luật |
| POST /api/me/league/join | requestId; tham gia season hiện tại, receipt + unique membership; không chọn group/tier/userId tùy ý |
| GET /api/me/league/leaderboard | Chỉ nhóm của mình, phân trang, competition rank/vùng kết quả/revision; không lộ email hoặc dữ liệu học riêng |
| GET /api/me/league/history | Phân trang membership đã chốt của chính mình |

Học viên xem bảng nhóm chỉ thấy tên hiển thị được phép và mã công khai tối thiểu, không email. Không cấp API đổi XP hoặc endpoint chốt tuần công khai. Job/test clock không được mở trong production profile.

Mã lỗi dự kiến: VALIDATION_FAILED, UNAUTHENTICATED, FORBIDDEN, REQUEST_ID_REUSED, LEAGUE_SETTLEMENT_PENDING, LEAGUE_NOT_OPEN, GAMIFICATION_SCHEMA_UNSUPPORTED, DATABASE_UNAVAILABLE. Không tham gia là trạng thái UI bình thường, không phải lỗi kết nối. Sync trả `remainingSubmissions` và snapshot revision; replay receipt cũ không thay thế GET trạng thái mới nhất.

## 10. UI và cập nhật trực tiếp

- Trang chủ học viên: tổng XP, cấp độ, streak, tiến độ trần ngày và liên kết Giải đấu tuần. Không đặt bảng toàn hệ thống lên dashboard giảng viên.
- Trang kết quả M03: điểm bài và phần thưởng tách riêng; có breakdown “câu mới”, “ôn đúng mốc”, “không thưởng vì đã gặp/đạt trần”, hoặc đang xử lý. Hiện 0 chỉ khi có kết quả xét cuối cùng.
- Ôn tập M04: diễn giải mốc và khả năng nhận thưởng theo context, không hứa chắc số XP trước khi nộp/chốt điều kiện.
- Trang Giải đấu: nút chủ động tham gia, thời gian còn lại theo server, luật nhóm, bảng nhóm có dòng của mình, vùng lên/giữ/xuống, trạng thái nhóm nhỏ/hòa điểm/đang chốt, lịch sử tuần.
- Huy hiệu/streak có ngày và điều kiện rõ; không trình bày cấp độ hoạt động như trình độ tiếng Anh. Kiểm tra desktop/mobile, bàn phím và trạng thái rỗng/lỗi.

Socket có subscription GAMIFICATION/LEAGUE; gửi event theo user hoặc thành viên nhóm, không nhận groupId của client. Logout/đổi tài khoản đóng socket cũ. GAMIFICATION_CHANGED, LEADERBOARD_CHANGED, LEAGUE_CHANGED có eventId/serverTime/resource/revision; chỉ là tín hiệu GET snapshot, không cộng XP tại client. Reconnect luôn GET lại.

Outbox được ghi cùng transaction và phát sau commit, có retry. Duplicate/out-of-order event dùng revision để không ghi đè snapshot mới. Reconnect/F5 luôn GET lại; polling chậm và refresh thủ công là đường phục hồi nếu socket lỗi. Hủy quyền phải tiếp tục đóng socket như M01. ACK socket không thay cho ACK nộp bài hoặc nhận thưởng.

## 11. Các chặng triển khai và điểm dừng nghiệm thu

| Chặng | Công việc / đầu ra | Điều kiện qua chặng |
|---|---|---|
| 0. Chốt nền và hợp đồng | Tích hợp M04 theo quy trình được yêu cầu; review bộ luật mục 3–5; API/OpenAPI, decision log, fixtures, sơ đồ lock, test matrix | Không còn mơ hồ nguồn thưởng, mốc ngày/tuần, tie/cap/cutoff; build/test nền đúng source |
| 1. Nền dữ liệu và bằng chứng | Migration nền M05, ORM/repositories, policy/activation, context cấp REVIEW và consumer đọc nguồn | DB mới và nâng V5 giữ dữ liệu; context commit cùng Attempt, resume không nhân bản |
| 2. XP và phục hồi | Pure reward policy, claims/ledger, worker + sync, daily cap, totalXp/reasons và đối soát | Retry, hai tab, restart, rollback và nguồn commit đảo thứ tự không mất/nhân XP |
| 3. Streak, cấp độ và huy hiệu | Activity day, current/best streak, badge claims và API/fixture | Đúng biên ngày, backlog, đạt cap, nhiều bài/ngày và nguồn không hợp lệ |
| 4. Giải đấu và chốt tuần | Migration giải đấu, join/allocator, điểm nhóm, ranking/tie, close/cutoff/finalizer và history | Không vượt nhóm, join trùng, thưởng lọt tuần hoặc chốt trùng; restart giữa nhóm khôi phục |
| 5. UI và cập nhật | Tích hợp trang chủ/kết quả/M04, màn giải đấu/huy hiệu, outbox/socket/reconcile | Hai tài khoản ở hai phiên độc lập xem đúng bảng; mất socket/ACK vẫn đối soát đúng |
| 6. Nghiệm thu toàn module | Regression M01–M04, persistence, concurrency/fault tests, demo tuần và tài liệu bàn giao | Có bằng chứng chạy thật, không chỉ mock/UI; review số liệu XP/rank không trùng |

Mỗi chặng có commit review riêng khi người dùng yêu cầu commit; không gộp toàn bộ vào một lần thêm màn hình. Chưa ước lượng ngày công chính xác khi chưa chốt luật và môi trường test.

## 12. Ma trận nghiệm thu bắt buộc

| Nhóm | Ca cần kiểm tra / bất biến |
|---|---|
| Nguồn | LEARN/PRACTICE/REVIEW; FIRST khác final; GUIDED/support/CORRECTION/blank; matching/fill nhiều phần vẫn một câu |
| Origin | Hai version/copy cùng origin; câu đã được cấp rồi bỏ; hai Attempt có origin liên quan; không nhận XP câu mới lần hai |
| Ôn | Context đến mốc/chưa đến/needsPractice; nhiều mục tiêu; resume sau qua ngày; hai context cùng cycle; bỏ trống một item không được bonus |
| Trần | Nhiều nguồn chạm đúng/vượt cap, bonus bị cắt, không chuyển sang ngày sau; streak vẫn có thể được ghi |
| Retry | Cùng UUID replay, UUID khác cùng Submission, payload khác cùng key; mất ACK sau commit không nhân XP/badge/join |
| Rollback | Lỗi khi ghi claim/ledger/profile/day/member/outbox/receipt; mọi projection và marker rollback cùng nhau |
| Backlog | Worker chết, nguồn ID nhỏ commit muộn, schema lạ, user khóa/đổi vai trò, user nhiều nguồn không làm đói user khác |
| Ngày | 23:59:59/00:00 Việt Nam, client sửa giờ, nhiều bài/ngày, nghỉ ngày, catch-up nhiều ngày không theo thứ tự, hôm qua/hôm nay |
| Tuần | Chính xác endAt, trước/sau joinedAt, trong/sau grace, commit muộn, outage dài; lifetime không bị reset, tuần đã khóa không đổi |
| Nhóm | Hai user tranh chỗ cuối, tạo nhóm đồng thời, join hai tab, nhóm thiếu người/ít người có XP, tier đầu/cuối |
| Hòa điểm | Bằng XP và tie vắt ranh giới trên/dưới; rank chia sẻ, không phân xử bằng ID, UI và finalizer cùng kết quả |
| Chốt | Hai job chạy cùng lúc, chết giữa các nhóm, chạy lại sau finalized; tier tuần sau đúng, nghỉ tuần giữ bậc |
| Quyền | Không xem nhóm khác qua sửa ID; không lộ email/đáp án; TEACHER/ADMIN không tự tạo thưởng; đổi account không giữ cache/subscribe cũ |
| Socket | Mất socket, outbox retry, sự kiện lặp/đảo thứ tự, reconnect/F5, session bị thu hồi; GET vẫn là dữ liệu chuẩn |
| Dữ liệu | Restart/recreate/seed giữ claims/ledger/history/context; SUM ledger khớp total/day/weekly; migration không sửa checksum cũ |
| Hồi quy | Luồng học liệu M02, snapshot/save/submit/media M03 và lịch/mức/selector M04 vẫn đúng |

Unit test tập trung policy; integration dùng MySQL thật qua Testcontainers cho constraint/lock/rollback; E2E dùng môi trường Compose riêng. Không ghi test lên database demo hoặc xóa volume để vượt lỗi. Dùng Clock inject được cho ngày/tuần và worker, không đổi giờ máy hoặc mở API reset tuần trong app thật.

Demo cần tài khoản/nhóm nhỏ được ghi rõ, một tuần hiện tại và một tuần đã chốt trong fixture riêng; không giả số người sử dụng thật. Đo và ghi thời gian xử lý backlog/cập nhật bảng theo cấu hình máy; chưa tuyên bố quy mô hoặc latency khi chưa đo.

## 13. Quyết định P0 và quản lý thay đổi

1. Thưởng 5 XP/câu mới, 5 XP/mục tiêu/chu kỳ, trần 200/ngày; không thưởng cố định theo lần nộp; giữ seen-at-allocation hiện tại.
2. Điều kiện ngày hoạt động, cấp độ 100 XP/mức, ba huy hiệu và mốc activatedAt không thưởng hồi tố.
3. Nhóm 25, tối thiểu 10 thành viên/5 có XP, quota 20% mỗi phía và hòa ranh giới giữ bậc.
4. Tham gia theo từng tuần, chỉ tính XP sau joinedAt; cutoff 2 phút, khoản xử lý quá muộn không sửa bảng tuần.

Yêu cầu bắt đầu triển khai đã có. [Decision log](M05_DECISIONS.md), API và fixture chặng 0 là baseline thi công; không cần xin lại quyền ở mỗi chặng. Các chữ “đề xuất” trong phần diễn giải ban đầu được cụ thể hóa bởi `m05-p0-v1`. Nếu thay luật về sau, đổi đồng bộ policy/API/fixture và mốc áp dụng, không âm thầm đổi kết quả cũ.

## 14. Hồ sơ bàn giao khi hoàn thành

`docs/M05_PROGRESS.md`, `source/common/M05_API.md`, `source/common/openapi-m05.json`, migration runtime/ORM, `docs/M05_MANUAL_TEST_CASES.md`, `statics/results/module-5-validation.md`, hướng dẫn demo/persistence và cập nhật README/DATABASE_DESIGN/AGENTS theo trạng thái thực tế. Biên bản phải phân biệt ca đã chạy với checklist dự kiến, test source nào và dữ liệu/môi trường nào. Không commit .env hoặc đưa mật khẩu/log nhạy cảm vào tài liệu.
