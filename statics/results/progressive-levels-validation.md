# Kiểm tra level tăng dần — 26/10/2026

## Phạm vi

Policy m05-p0-v3/V11, ngưỡng cấp L là 5×L×(L−1), mỗi bước lên cấp cần 10×L XP. UI phần thưởng hiển thị chi phí của cấp hiện tại và tổng XP cần đạt. Giữ XP, FIRST, claim, cap 200/ngày và luật League. Bắt đầu từ commit 4fdf64a trên feature/m07-profile-statistics. [Luật/giới hạn](../../docs/M05_PROGRESSIVE_LEVELS.md).

## Backend

Maven chạy source hiện tại qua bind mount trong container test Java 17, MySQL 8.4 Testcontainers riêng. Package chỉ được dùng cho môi trường chạy thử sau khi container kiểm thử kết thúc với mã 0. Không dùng image test cũ như bằng chứng source mới.

- Lượt tập trung: **109/109 PASS**, 10 lớp, không skip: ActivityLevelsTest (17), GamificationRulesTest (2), GamificationApiTest (14), GamificationAcceptanceTest (33), GamificationContextApiTest (16), EngagementMigrationTest (1), OrderingMigrationTest (1), AttemptPersistenceTest (7), StatisticsRewardsIntegrationTest (3), LeagueApiTest (15).
- Lượt bổ sung LeagueRankingTest: **9/9 PASS**. Tổng 118 ca khác nhau đã qua qua hai lượt, không phải một lượt 118/118.
- Kiểm tra mốc ngay trước/đúng ngưỡng 10/30/60/100/150/210/450/550, chi phí tăng dần đến cấp 100, XP lớn đến MAX_SAFE_INTEGER, XP ngoài miền bị từ chối và v1/v2 vẫn giữ bước cố định.
- API chứng minh nguồn v2 được xử lý sau lúc v3 có hiệu lực vẫn ghi v2; Summary hiện tại trả v3. 25 XP → cấp 2 (floor 10, next 30); 50 XP → cấp 3 (floor 30, next 60). Đọc/sync không tạo thêm ledger/XP.
- Migration V9→V11 giữ v1, thêm v2/v3 đúng activation, giữ claim/ledger/Submission/frozen result; chỉ V10 điều chỉnh tuần OPEN theo luật cũ của nó. V11 chỉ INSERT policy mới. Empty DB và nâng từ V3 qua V11 được kiểm tra cùng Hibernate validate.
- Các ca còn lại kiểm tra rollback, concurrency, receipt, due review, nguồn trễ, hồ sơ M07 và League vẫn qua. Không chạy lại toàn bộ test backend ngoài phạm vi này.

## Frontend và hợp đồng

- Docker client build chạy tsc -b và Vite production: PASS. Typecheck cuối sau chỉnh test hồ sơ: PASS.
- M05 JSON Schema/format checker: **58/58 fixture hợp lệ/không hợp lệ PASS**, gồm sáu mẫu Summary v3; vẫn giữ mẫu v1/v2 và ngưỡng tối thiểu tương ứng.
- Trình duyệt Chrome/Playwright trên Docker project riêng english-learning-progressive-e2e, UI 15193/API 18103, DB mới. Không tạo tài khoản hoặc chạy E2E trên preview người dùng 15183.
- Lượt 4 file rewards/league/profile/gameplay: **24 passed, 1 failed / 25**. Ca hồ sơ còn mong cấp 1 ở 15 XP theo luật cố định cũ; giao diện thực tế đã hiển thị đúng cấp 2. Cập nhật assertion để đối chiếu level mới với API.
- Chạy lại toàn bộ file hồ sơ: **6/6 PASS**. Tổng 25 kịch bản trình duyệt khác nhau đã qua qua các lượt, không phải một lượt 25/25.
- Bài demo đầu cho 25 XP/cấp 2, hai bài cho 50 XP/cấp 3. UI hiển thị Cấp 3 → 4: 30 XP, thanh tiến độ 20/30, tổng mốc 60; đọc/sync không nhân XP. Viewport 390×844 không tràn ngang, có tạo ảnh progressive-level-mobile.png trong test-results.
- Game, rương kết quả, tải lại, socket cập nhật phần thưởng, cap, nhóm nhỏ và dữ liệu hồ sơ riêng tư vẫn hoạt động trong các ca đã chạy.

## Vận hành

Khi đưa lên DB đang dùng: sao lưu trước V11, giữ MySQL/assets volume, đối chiếu hash dữ liệu cũ. So sánh cả policy v1/v2, tổng XP/revision hiện có, tất cả season rule snapshots, frozen results, Submission và ledger. Cấp hiện tại được suy ra từ tổng XP theo ngưỡng mới; không ghi thêm XP để đổi cấp.
