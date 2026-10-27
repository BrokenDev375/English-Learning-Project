# Kiểm tra bảng xếp hạng chung — 27/10/2026

## Phạm vi

Thêm GET /api/me/leaderboard, mục Bảng xếp hạng và hai tab Toàn hệ thống/Nhóm thi đấu. Có top 3, đồng hạng, phân trang, self ngoài trang và trạng thái chưa có XP. Bảng chung đọc daily stats đã commit, không cần join, không thêm migration hoặc đổi luật thưởng/level/League.

Bắt đầu từ working tree trên feature/m07-profile-statistics, HEAD 4fdf64a, đã bao gồm phần level v3 chưa commit. Giữ nguyên các thay đổi level đó. [Nghiệp vụ và giới hạn](../../docs/M05_GLOBAL_LEADERBOARD.md).

## Backend và hợp đồng

- Current source được bind mount vào Maven/Java 17; MySQL 8.4 Testcontainers riêng. Chạy package với WeeklyLeaderboardApiTest, GamificationApiTest, LeagueApiTest và StatisticsRewardsIntegrationTest: **37/37 PASS**, không skip, một lượt. Backend jar chỉ được đưa vào image sau khi test container thoát mã 0.
- Năm ca API mới kiểm tra 401/403, Cache-Control private/no-store, tham số ngoài hợp đồng, trang/size sai, đọc không tạo profile/XP/membership; đồng hạng 1/1/3/3/5, top và self ngoài trang, offset lớn không tràn số; tuần theo thứ Hai giờ Việt Nam; chỉ ACTIVE STUDENT có XP dương; XP thật trước khi join và tuần mới giữ tổng XP/level.
- API trả tên/cấp/XP/rank/position/isSelf, không có account ID/email/securityVersion/password. Truy vấn page/top/self/count ở cùng REPEATABLE_READ snapshot; rank được tính trước phân trang.
- JSON Schema và format checker M05: **66/66 PASS**, gồm mẫu bảng rỗng, đồng hạng và self ngoài trang; từ chối ID tài khoản, rank giả ở 0 XP, top quá ba dòng, XP âm và self sai.
- Không chạy lại toàn bộ backend ngoài phạm vi 37 ca trên; kiểm tra level v3 trước đó có biên bản riêng.

## Frontend

- Docker production client build (tsc -b + Vite) và typecheck cuối: PASS.
- Lượt trình duyệt đầu tại 15195 bị gián đoạn khi Docker Desktop dừng, kết nối ECONNREFUSED. Không dùng lượt đó làm bằng chứng PASS. Khởi động Docker lại, giữ dữ liệu cũ và tạo project/DB mới english-learning-leaderboard-final-e2e, UI 15197/API 18107.
- Lượt tổng thể trên DB thử mới: **113 passed, 1 failed / 114**, 6 phút. Toàn bộ 108 ca cũ qua. Ca mới đổi tài khoản gửi đăng ký qua API khi logout chưa xong, dẫn đến CSRF_INVALID. Sửa test chờ form đăng nhập xuất hiện trước khi tạo tài khoản tiếp theo; không thay code xác thực.
- Chạy lại file leaderboard.spec.ts sau sửa: **6/6 PASS**, 15 giây. Tổng 114 kịch bản khác nhau đã qua qua các lượt; không ghi là một lượt 114/114.
- Kiểm tra: sidebar/tab/legacy URL và bàn phím, nhận 25 XP từ bài nộp thật không cần join, GET/F5 không thêm XP, phân trang với self hạng 35, hai học viên đồng hạng 1, lỗi refresh giữ dữ liệu cũ và retry, đổi tài khoản không giữ nhầm self, bảng rỗng không tạo người thắng giả và có lối vào chơi.
- Ảnh desktop và mobile 390×844 đã được xem, không tràn ngang. Ảnh dữ liệu 35 học viên dùng fixture HTTP phục vụ kiểm tra UI, không ghi các tên/điểm đó vào DB. Artifact leaderboard-desktop.png/leaderboard-mobile.png nằm trong test-results và không commit.
- Các ca cũ gồm game, hoàn thành/mở vòng, level/XP, nhóm nhỏ/chốt tuần, M06 offline/receipt/socket, biên soạn, quyền và hồ sơ vẫn qua.

## Vận hành

Không chạy E2E hoặc tạo dữ liệu kiểm thử trên preview người dùng 15183. Bản deploy dùng cùng server/client image đã thử ở project riêng. Sao lưu DB trước cập nhật, giữ MySQL/assets volumes; đối chiếu Submission, ledger, profile XP/revision, daily stats, mọi policy, season rule snapshot và frozen result trước/sau. Schema vẫn V11, không thêm leaderboard seed, tài khoản hay điểm giả. Không benchmark tải hoặc broadcast thứ hạng toàn hệ thống.
