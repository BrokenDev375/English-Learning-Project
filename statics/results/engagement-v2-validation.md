# Kiểm tra nhịp level và giải đấu nhóm nhỏ v2 — 26/10/2026

## Phạm vi

Policy m05-p0-v2 giảm ngưỡng từ 100 xuống 50 XP/cấp; League xét từ 2 thành viên và 2 người có XP, tối thiểu một suất mỗi phía khi đủ điều kiện. Giữ 5 XP/câu mới, 5 XP/ôn đủ điều kiện, trần 200 XP/ngày, claim chống lặp, FIRST, streak/huy hiệu và tier/tie rules.

V10 thêm policy v2 và chuyển đúng tuần hiện tại còn OPEN/chưa hết hạn sang luật mới. Giữ nguyên v1, tuần kết thúc/CLOSING/FINALIZED, điểm và kết quả đã chốt. Nguồn thưởng chọn policy theo submittedAt; level hiển thị theo luật hiệu lực lúc đọc. Rule snapshot cũ thiếu minimumMovementSlots đọc thành 0, giữ hành vi cũ.

## Bằng chứng

| Kiểm tra | Kết quả |
|---|---|
| Backend một lượt | **79/79**, không failure/error/skip, BUILD SUCCESS và JAR package thành công |
| Browser toàn bộ một lượt | **104/104**, 5.2 phút, môi trường riêng 15189/18099 |
| Build frontend | TypeScript/Vite PASS, 75 modules; image dùng asset `index-vidz8JfQ.js` |
| Hợp đồng M05 | 8 operations, 31 schemas, mọi local ref, 10 embedded examples; **52 fixtures** (20 valid, 32 invalid) |

Backend gồm StatisticsApiTest 15, AttemptPersistenceTest 7, LeagueApiTest 15, OrderingMigrationTest 1, GamificationContextApiTest 16, EngagementMigrationTest 1, GamificationApiTest 13, LeagueRankingTest 9, GamificationRulesTest 2.

Các ca mới xác minh:

- 25 XP chưa đủ cấp 2 theo v2; đủ 50 XP đạt cấp 2, floor=50/next=100. Cùng 50 XP ở v1 vẫn cấp 1. GET/replay không tạo thêm ledger hoặc XP.
- Bài nộp trước cutover nhưng worker xử lý sau vẫn ghi Activity/ledger v1. Context REVIEW sau nâng cấp dùng v2, resume/replay giữ bằng chứng và chu kỳ đã chốt.
- Nhóm hai người có XP khác nhau có một suất; một người hoặc chỉ một người có XP chưa xét. Nhóm 2–4 không bị quota làm tròn thành 0; nhóm 25 giữ 5 suất. Khối hòa và giới hạn Đồng/Kim Cương được giữ; JSON luật cũ vẫn dùng ngưỡng 10/5.
- League hai người đi qua API thật, dự kiến thăng, chốt một lần, lịch sử bất biến và tuần sau vào đúng bậc mới mà tổng XP không đổi.
- Migration từ V9 giữ hash policy v1, Submission và kết quả tuần đã chốt; giữ ledger/claim/profile XP; đổi đúng OPEN hiện tại, không đổi CLOSING/FINALIZED; chạy lại Flyway không áp dụng lại.

Browser mới làm đúng hai Lesson seed rồi kiểm tra 50 XP/cấp 2 trên giao diện thật, đọc/sync lại không cộng thêm. Một nhóm mới đúng hai thành viên có 15/10 XP được kiểm tra promotionQuota=1/outcome=PROMOTE và luật 2/2/minimum slot hiển thị đúng. Hồi quy bao gồm M01–M07, auth/quyền, ledger/socket, ôn, lưu nháp/mất ACK/timeout, bản đồ/khóa vòng/chơi lại, authoring/media và mobile.

Kiểm thử chạy trong project `english-learning-engagement-e2e` và MySQL Testcontainers riêng, không ghi test vào dữ liệu người dùng 15183. Trước cập nhật bản người dùng, công cụ triển khai tạo mysqldump riêng ở `.test-state`, so sánh hash các Submission, ledger, kết quả đã chốt, luật tuần đã kết thúc và policy v1 trước/sau; giữ MySQL/asset volumes. Không in credentials hoặc nội dung đáp án trong log.

Log tạm trong thư mục kiểm tra: `.test-state/engagement-backend.log`, `engagement-client-build.log`, `engagement-stack.log`, `engagement-browser.log`. Các biên bản trước là lịch sử riêng. Luật và cutover đầy đủ ở [quyết định v2](../../docs/M05_ENGAGEMENT_V2.md).

[Đề xuất lồng game](../../docs/GAMEPLAY_INTEGRATION_PROPOSAL.md) là thiết kế cho ORDERING/MATCHING cùng các dạng còn lại, chưa phải hoạt cảnh đã triển khai trong bản v2 này.
