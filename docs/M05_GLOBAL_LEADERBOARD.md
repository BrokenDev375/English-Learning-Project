# Bảng xếp hạng chung — 27/10/2026

## Lối vào và hai bảng

STUDENT mở **Bảng xếp hạng** trên menu hoặc lối tắt ở thanh phần thưởng trang chủ. Tab **Toàn hệ thống** là mặc định; tab **Nhóm thi đấu** chứa toàn bộ Giải đấu tuần hiện có. URL mới `#/leaderboard`, tab nhóm `#/leaderboard?board=group`. URL cũ `#/league` và các nút Thi đua tuần vẫn mở tab nhóm.

Bảng chung gồm top ba dòng dẫn đầu, danh sách có phân trang và vị trí cá nhân độc lập với trang đang xem. Những người bằng XP đồng hạng, ví dụ 1, 1, 3. Top 3 là ba dòng đầu để trình bày, không phải ba bậc thưởng; đồng hạng không bị chia lại bằng tên/thời điểm tham gia. Không tạo phần thưởng top hoặc tự thăng bậc.

## XP tuần và người tham gia

- Tuần bắt đầu thứ Hai 00:00 giờ Việt Nam, kết thúc lúc bắt đầu thứ Hai kế tiếp; khoảng thời gian đầu bao gồm, cuối loại trừ.
- XP lấy từ tổng awarded_xp của gamification_daily_stats theo activity_date. Đây là projection M05 đã commit, cùng transaction với ledger/claim. Ngày hoạt động là ngày submittedAt theo giờ Việt Nam. Nguồn xử lý muộn vẫn thuộc ngày bài nộp; không chuyển XP sang tuần xử lý.
- Không cần tham gia nhóm. Học viên ACTIVE có XP tuần > 0 tự xuất hiện. TEACHER/ADMIN, LOCKED/DELETED và người có 0 XP tuần không nằm trong danh sách có hạng.
- Người chưa có XP tuần vẫn nhận self với rank/position null, weeklyXp 0 và level từ XP tích lũy. Giao diện ghi **Chưa xếp hạng**. Không dựng tên hoặc bục người thắng khi bảng rỗng.
- Sang tuần mới, truy vấn dùng khoảng ngày mới; tổng XP/level không bị đặt về 0. Bảng chung chỉ thể hiện tuần hiện tại, không lưu/chốt một bản kết quả quá khứ mới.
- Bảng nhóm giữ nguyên: chủ động tham gia, XP tính sau khi tham gia, ngưỡng thành viên, đồng hạng, chốt tuần, lên/xuống bậc và lịch sử. Bảng chung tính cả XP hợp lệ trước lúc tham gia nhóm nên số XP của hai bảng có thể khác.

## API và dữ liệu

`GET /api/me/leaderboard?page=0&size=20`, STUDENT đã đăng nhập, response `no-store, private`. Chỉ chấp nhận page/size; page là int không âm, size 1–50. Từ chối userId, ngày tùy chọn hoặc tham số ngoài hợp đồng.

Response có serverTime, zone, weekStartAt/weekEndAt, top, items, self và thông tin phân trang. Mỗi dòng chỉ có position, rank, displayName, activityLevel, weeklyXp, isSelf. Không cấp account ID, email, securityVersion hoặc đường dẫn hồ sơ học viên khác. position là vị trí trình bày khi bằng điểm, không thay rank.

WeeklyLeaderboardRepositoryImpl dùng SQL aggregation và window functions để tính rank/position trước phân trang; service đọc trong REPEATABLE_READ để page/top/self/count dùng một snapshot. Không tải toàn bộ người dùng sang Java để sắp hạng. Level dùng cùng policy hiện hành v1/v2/v3 của M05. AccountGuard và role check vẫn áp dụng.

Không thêm migration, bảng hoặc job ghi điểm. GET không sync phần thưởng, tạo profile, membership, receipt hay season. Các bài đang chờ worker chỉ xuất hiện sau khi xử lý thưởng thành công. Không thay giới hạn 200 XP/ngày, XP câu mới/ôn, FIRST hoặc điểm game.

## Cập nhật và giao diện

Giao diện đọc lại khoảng 15 giây và khi nhận gamification:changed của tài khoản đang dùng. Không thêm subscription/broadcast toàn hệ thống. Có nút cập nhật thủ công, tab dùng được bằng bàn phím, route giữ tab sau F5 và legacy link vẫn hoạt động.

Khi đọc lỗi, giữ dữ liệu lần tải thành công trước kèm thông báo; chưa có dữ liệu thì không biến lỗi thành bảng 0 XP. Khi đăng xuất/đổi tài khoản, self không được tái sử dụng từ tài khoản trước. Tên học viên trong bảng là thông tin hiển thị được chia sẻ; React escape nội dung và bố cục cho phép tên dài xuống dòng.

Nguồn chính: WeeklyLeaderboardController/Service/Repository và WeeklyLeaderboardViews; frontend Leaderboards.tsx, League.tsx và leaderboard.css. [Kiểm tra](../statics/results/global-leaderboard-validation.md).
