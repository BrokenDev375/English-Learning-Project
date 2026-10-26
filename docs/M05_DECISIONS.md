# M05 — Bộ luật P0 và quyết định triển khai

Ngày 21/10/2026. Policy: **m05-p0-v1**. Người dùng đã yêu cầu bắt đầu triển khai từng chặng sau khi review kế hoạch. Dùng bộ mặc định trong tài liệu này làm baseline P0; không hỏi lại quyền triển khai ở mỗi chặng. Thay đổi luật sau này cần version mới, không sửa thưởng/kết quả tuần đã lưu.

> Cập nhật 26/10: luật hiện hành v2 giảm còn 50 XP/cấp, League 2 thành viên/2 người có XP và tối thiểu một suất. Xem [quyết định v2](M05_ENGAGEMENT_V2.md). Phần dưới giữ nguyên làm baseline v1.

## 1. Nền và phạm vi

Đã fetch và fast-forward local main từ be92240 lên **57f2951**, cùng origin/main và có đầy đủ M04/V5. Không tạo branch, commit hoặc push. Các file kế hoạch M05 chưa commit được sao lưu và giữ lại khi cập nhật.

Chặng 0 hoàn thiện hợp đồng và kiểm tra nền; chưa mở endpoint M05, chạy worker thưởng hoặc nâng database demo. Chặng 1 thêm schema và bằng chứng; các tính năng chỉ được ghi hoàn thành sau kiểm tra thực tế theo [tiến độ](M05_PROGRESS.md).

## 2. Bảng thông số chuẩn

| Thông số | P0 v1 |
|---|---|
| zone | Asia/Ho_Chi_Minh |
| freshQuestionXp | 5 |
| dueObjectiveXp | 5 |
| dailyXpCap | 200 |
| xpPerActivityLevel | 100 |
| qualifiedMinimumIndependent | min(3, số item không GUIDED của Attempt), phải > 0 |
| qualifiedMinimumCorrect | Ít nhất 1 FIRST đúng, complete, không GUIDED, không hỗ trợ |
| syncBatchSize | Tối đa 100 nguồn đã commit/người/request |
| leagueGroupCapacity | 25 |
| leagueMinimumMembers | 10 |
| leagueMinimumActive | 5 membership có weeklyXp > 0 |
| leaguePromotionPercent / leagueDemotionPercent | 20 / 20 |
| leagueGraceSeconds | 120 |
| weekStart | Thứ Hai 00:00 Việt Nam, tuần [startAt, endAt) |
| badges | FIRST_LESSON, DUE_REVIEW, SEVEN_DAY_STREAK |

Đây là cấp độ **hoạt động**, không phải chứng nhận năng lực tiếng Anh. Không có hạng solo M08, multiplier độ khó, freeze/mua streak hoặc XP cho huy hiệu trong P0.

## 3. Quyết định thưởng

Q01. Câu mới chỉ có thưởng khi FIRST complete + CORRECT, role khác GUIDED, firstSupportUsed=false và wasSeenBefore=false. Claim duy nhất theo user + NEW_QUESTION + originKey. Một matching/fill là một câu. Đổi QuestionVersion hoặc copy cùng origin không sinh nguồn mới. Không dùng tổng điểm cuối làm dữ kiện thay cho FIRST.

Q02. Giữ nghĩa exposure hiện tại của M03: seen từ lúc cấp lượt. Mở rồi bỏ không lấy lại điều kiện câu mới ở lượt sau. Đây là đánh đổi P0 chống luyện lại câu đã thấy đáp án; người học vẫn nhận XP ôn đến hạn ở chu kỳ đủ điều kiện. Không tạo first-answer evidence mới trong P0.

Q03. GUIDED/hint/check/CORRECTION/sai/trống không sinh XP. Sửa lỗi bằng biến thể mới đủ Q01 nhận XP câu mới, không thưởng thêm theo nhãn sửa lỗi. Hoàn thành bài không có bonus cố định; có thể đạt huy hiệu FIRST_LESSON.

Q04. Context REVIEW được lưu cùng transaction cấp Attempt, chỉ cho objective có item thực sự được cấp. Nguồn: stage > 0, needsPractice=false, hasMistakes=false và nextReviewAt <= selectedAt. “Ôn đến hạn” bao gồm ôn quá hạn, không hứa đúng ngày tới hạn. Mọi FIRST item của objective trong lượt phải complete, đúng, không hỗ trợ và không GUIDED.

Q05. Định danh chu kỳ: SHA-256 hex của UTF-8 `m05-review-cycle-v1\n{userId}\n{objectiveId}\n{dueEpochMicros}\n{stage}\n`. dueEpochMicros lấy từ mốc lưu database, chuẩn hóa đến microsecond. Không đưa lastSubmissionId vào hash vì M04 đổi trường đó khi nộp trống mà lịch không đổi. Unique user + DUE_REVIEW + cycleKey và thêm unique user + DUE_REVIEW + objective + activityDate. Context cũ/resume không đổi mốc; Attempt trước M05 không có context thì không bonus ôn.

Q06. XP câu mới và bonus ôn được cộng khi cùng hợp lệ, rồi dùng chung trần 200. Thứ tự nguồn: các Submission đã commit theo ID trong batch; trong một nguồn xét item position rồi objectiveId. Nguồn ID nhỏ commit muộn được xét sau phần đã quyết định, không sửa ledger cũ. Trần thuộc activityDate từ submittedAt của nguồn. Lưu requestedXp/awardedXp/reason. Claim hợp lệ bị cap chặn vẫn tiêu thụ, không chuyển ngày hoặc cấp lại bằng key mới.

Q07. Quyết định nguồn không hợp lệ phải có lý do trong kết quả xử lý đã lưu, dù không tạo claim. Các reason: AWARDED, DAILY_CAP_REACHED, ALREADY_CLAIMED, ALREADY_REWARDED_TODAY, SEEN_BEFORE, GUIDED, SUPPORTED, FIRST_NOT_CORRECT, INCOMPLETE_FIRST, NO_REVIEW_CONTEXT, REVIEW_NOT_DUE, REVIEW_OBJECTIVE_NOT_PASSED, BEFORE_ACTIVATION. Không sinh claim chỉ để chiếm nguồn khi câu chưa đủ điều kiện.

Q08. activatedAt được ghi một lần; nguồn submittedAt < activatedAt nhận disposition BEFORE_ACTIVATION, 0 XP và không sinh ngày hoạt động/huy hiệu. Nguồn bằng mốc được xét. Policy thưởng của nguồn lấy theo submittedAt, không theo lúc worker chạy. Đổi luật có mốc ngày, luật league có mốc tuần; không thay version hiện hành trong cùng ngày/tuần đang dùng.

## 4. Streak, huy hiệu và độ trễ

S01. Ngày hợp lệ cần đủ ngưỡng FIRST tự làm complete trong bảng thông số và ít nhất một FIRST đúng. Sai vẫn được tính số đã tự làm nhưng phải có ít nhất một câu đúng. Câu cũ có thể giữ streak, dù không sinh XP. Trần XP không chặn ngày hoạt động.

S02. qualified ngày là UNIQUE user + activityDate; nhiều bài cùng ngày không nhân ngày. currentStreak là chuỗi kết thúc hôm nay hoặc hôm qua; lâu hơn thì 0. bestStreak xét mọi ngày đã ghi. Worker nhận ngày cũ sau ngày mới phải nối lại chuỗi đúng, không tăng bộ đếm theo thứ tự xử lý. Không nhận ngày client tự gửi.

S03. FIRST_LESSON: mode LEARN, lessonCompleted và nguồn đủ ngày hoạt động. DUE_REVIEW: có claim ôn hợp lệ, kể cả awardedXp=0 vì trần. SEVEN_DAY_STREAK: bestStreak >= 7. Unique user + badgeCode. earnedAt là lúc xét đạt; evidenceSourceAt là thời điểm nguồn chứng minh, để backlog không làm giả thời điểm trao thực tế.

S04. API phân biệt PENDING, DEFERRED và đã xử lý. Một Submission chưa xét không được hiển thị như kết quả thưởng 0. Khi lọc activities theo submissionId, trả requestedSubmission.state; source không tồn tại/khác user trả 404. Nguồn bị hoãn quyền hoặc schema lạ không được đánh dấu thành công. Hoãn phải có retry metadata và không chặn worker của user khác.

## 5. Giải đấu

L01. Tham gia chủ động mỗi tuần, bắt đầu Đồng; một membership/user/season. Không tự đăng ký tuần sau, không đổi nhóm hoặc hủy/đăng ký lại trong tuần. Vắng tuần giữ bậc từ membership đã chốt gần nhất. Chưa có kết quả nào thì Đồng.

L02. Điểm nhóm là SUM awardedXp nhận vào league của nguồn có startAt <= submittedAt < endAt, submittedAt >= joinedAt và được xét trước cutoff dưới khóa season. Lifetime trước khi join không được chuyển sang điểm nhóm. Mỗi ledger ghi countedXp và reason: COUNTED, NOT_JOINED, BEFORE_JOIN, LATE_AFTER_CUTOFF, NO_AWARDED_XP hoặc BEFORE_ACTIVATION. Tổng lifetime không thay đổi khi join/chốt tuần.

L03. Nhóm cùng season/tier tối đa 25, xếp vào nhóm còn chỗ có groupNo nhỏ nhất; allocator khóa season + tier. Kiểm tra COUNT không khóa rồi INSERT là không đủ. Khóa unique season + user chống join nhiều lần. RequestId mới trên membership hiện có trả 200 created=false; replay cùng key trả nguyên response cũ.

L04. Lên/xuống chỉ khi N >= 10 và ít nhất 5 người có XP > 0. Quota mỗi phía floor(N*20/100). Competition rank, các khối bằng XP cùng quyền lợi. Khối chỉ lên nếu toàn khối nằm trong top quota và XP > 0; chỉ xuống nếu toàn khối nằm trong bottom quota. Khối vắt ranh giới giữ bậc, không chuyển suất thừa. Đồng/Kim Cương chặn dịch chuyển ngoài bậc, hiển thị reason MINIMUM_TIER/MAXIMUM_TIER. ID chỉ dùng ổn định vị trí hiển thị.

L05. OPEN hết endAt đi vào CLOSING; nhận nguồn tuần cũ đến cutoff=endAt+120s. Tại cutoff hoặc muộn hơn không nhận thêm điểm cũ. Linearization là lúc kiểm tra thời gian dưới khóa season; không gọi submittedAt hoặc processedAt là thời điểm commit vật lý chính xác. Finalizer phải chờ transaction đã giữ khóa trước cutoff hoàn tất.

L06. Sau cutoff, chốt từng group trong transaction riêng; update kết quả ngay trên membership có sẵn. Group finalized là marker chống chạy lại; không insert bảng kết quả có FK users từ finalizer. Khi mọi group xong mới FINALIZED season. Chết giữa các group tiếp tục phần chưa xong. Nguồn đến muộn vẫn nhận lifetime theo luật nhưng bị LATE_AFTER_CUTOFF, không sửa bảng cũ hoặc chuyển sang tuần mới.

L07. Chỉ chặn join tuần mới nếu chính user còn membership tuần trước chưa được chốt; trả 409 LEAGUE_SETTLEMENT_PENDING. Nhóm của user chốt xong có thể join dù nhóm khác còn chạy. GET không tự tạo season, membership hoặc profile; nếu worker chưa tạo season, state UNAVAILABLE và canJoin=false.

L08. Tên hiển thị và public memberKey đủ để xem bảng cùng nhóm. Không trả email, userId nội bộ, dữ liệu đáp án, toàn lịch sử học hoặc bảng nhóm khác. P0 không tự xóa điểm người bị khóa khỏi tuần đang chạy; chỉ dừng xử lý thưởng mới và thu hồi phiên. Can thiệp chống gian lận/hủy kết quả cần thiết kế riêng, không âm thầm viết lại ledger.

## 6. Hợp đồng kỹ thuật và các trường hợp tranh chấp

T01. User mutation: actor → season tăng dần → allocator/group tăng dần → membership → claim → profile/day/badge → outbox/processed/receipt. Worker cùng thứ tự, transaction một user; batch không trộn nhiều user. Sources có thể ở nhiều ngày/season thì pre-read để khóa tài nguyên theo thứ tự trước khi xét. Finalizer: season → group → membership đã có; không quay về actor. Kiểm thử MySQL phải xét cả FK implicit lock.

T02. POST dùng receipt hiện tại, requestId UUID chữ thường, body chỉ requestId, không nhận userId/XP/date/tier/season/group. Cùng UUID khác payload/operation trả REQUEST_ID_REUSED. Replay không ghi lại claim/outbox. Response replay là snapshot cũ, GET là đối soát mới.

T03. Consumer dùng nguồn đã commit và NOT EXISTS terminal decision, không dùng một cursor MAX(id) bỏ qua ID commit muộn. Callback RAM chỉ đánh thức worker; periodic scan là đường khôi phục. processed decision/claims/XP/projections/badge/member/outbox cùng transaction. Lỗi phải rollback toàn bộ; retry transaction ngoài transaction hỏng, tối đa hữu hạn với cùng nguồn/key.

T04. M03 submit không phải chờ worker M05. M04 sync không phải điều kiện nhận XP thường. Snapshot context cần M04 đã sync trước cấp REVIEW như hợp đồng hiện tại; hook chạy trong transaction M03, không mở REQUIRES_NEW, không đổi public Result schema 1 hoặc sửa Submission sau commit.

T05. Dùng Clock được inject cho logic ngày/tuần/cutoff/worker; không đổi giờ host, không mở test-clock API ở runtime thật. JSON XP/ID trong miền số nguyên JavaScript an toàn; DB tổng dùng BIGINT và kiểm tra tràn. Không dùng số thực để cộng XP hoặc tính quota.

T06. Outbox ghi cùng commit; event chỉ làm invalidation. Client nhận event revision cũ/lặp thì không ghi đè dữ liệu mới, GET sau reconnect. Scope user hoặc membership được xác thực server; không tin groupId từ SUBSCRIBE. Không trả đáp án hoặc token trong event.

Các sơ đồ và checklist thi công nằm trong [kế hoạch](M05_IMPLEMENTATION_PLAN.md), hợp đồng payload trong [API M05](../source/common/M05_API.md), các ca nghiệm thu trong [ma trận](M05_MANUAL_TEST_CASES.md).
