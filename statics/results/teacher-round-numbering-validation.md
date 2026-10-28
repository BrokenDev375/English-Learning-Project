# Kiểm tra số vòng giáo viên — 27/10/2026

## Phạm vi

Bổ sung số Vòng đấu 1, 2, 3… vào thẻ quản lý/duyệt công bố, chọn vòng cho bài, sắp xếp bài và bộ lọc thống kê. Bắt đầu từ working tree có phần đổi tên giáo viên chưa commit, HEAD cb31f80 trên feature/m07-profile-statistics. Giữ phần đổi tên đó.

roundNumbering.tsx tải đủ public /api/units, cùng điều kiện công bố và thứ tự stage/sortOrder/id của RoundJourneyReader, rồi lập map trước lọc của tác giả. Vòng ngoài public journey hiện Chưa có số; không lấy chỉ số của trang hoặc danh sách riêng để gán số. Lỗi tải có retry và không công bố map thiếu. Không thêm API, backend hoặc migration. [Quy ước](../../docs/TEACHER_ROUND_TERMINOLOGY.md).

## Kết quả

- Typecheck và Docker production build tsc -b/Vite: PASS.
- Chrome/Playwright trên project riêng english-learning-round-numbers-e2e, UI 15201/API 18111, DB mới và backend hiện có. Không chạy E2E hoặc tạo dữ liệu trên preview 15183.
- Lượt tập trung: **29/29 PASS**, 1,2 phút, một lượt: learning-journey, module-two-catalog, module-two-authoring, module-seven-teacher, navigation và hai ca readonly giáo viên/admin của M03.
- Ca mới tạo vòng riêng có bài công bố sau các vòng của tác giả khác; số trong quản lý giáo viên khớp GET /api/me/rounds của học viên. Lọc chỉ còn một thẻ vẫn giữ số toàn lộ trình. Vòng chưa có bài công bố không xuất hiện trong lộ trình và hiện Chưa có số. Picker bài và thống kê cùng số.
- Ca fixture 51 vòng bắt buộc đọc hai trang public catalog. Trang thứ hai lỗi thì giữ nhãn chưa xác định, không gán số 1; retry đọc đủ mới hiển thị Vòng đấu 51. Truy vấn metadata chỉ dùng size/page, không mang bộ lọc của tác giả. 51 vòng này là fixture HTTP, không ghi DB.
- Các ca cũ kiểm tra tạo/sửa vòng, sắp xếp bài, học viên tiếp tục/game, đầy đủ câu/nộp, preview giáo viên/admin không tạo lượt thật, quyền thống kê, mobile và Back/Forward/direct URL vẫn qua.
- Không chạy backend test vì không thay backend; đã đối chiếu publicUnits/allPublicUnits dùng chung điều kiện công bố và cách sort với nguồn lộ trình.

## Triển khai

Chỉ thay client image, giữ backend process và volume. Đối chiếu số user/attempt/submission và hash kết quả cũ. Không thay stage/sortOrder, tên nội dung hoặc điểm/XP đã lưu. Cập nhật lại danh sách sau khi công bố/ngừng công bố/sắp xếp để nhận số theo lộ trình hiện tại.
