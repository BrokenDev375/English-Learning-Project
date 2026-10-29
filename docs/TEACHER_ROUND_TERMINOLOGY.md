# Quản lý vòng đấu

Unit là dữ liệu của **Vòng đấu**, chứa Lesson. Giữ định danh Unit/unitId/units và các endpoint hiện tại. Chủ đề vẫn qua topics và units.topic_id; câu hỏi tiếp tục dùng question_versions.topic_id.

## Số thứ tự lưu trong database

V12 thêm units.round_number, INT dương duy nhất toàn hệ thống. Unit và TeacherUnit trả roundNumber; RoundProgress.number dùng cùng giá trị. Form ADMIN có **Số thứ tự vòng** và **Giáo viên phụ trách** để quản lý trực tiếp. Khi tạo mới có thể để trống để lấy số tiếp theo. Vòng chưa công bố cũng có số riêng; danh sách lọc, Của tôi và phân trang giữ nguyên số.

Số nhỏ được học trước. ADMIN sửa cấu trúc/số vòng và phân công. Giáo viên xem vòng được giao để biên soạn bài. Số đã dùng báo lỗi và giữ form để sửa; không tự đổi các vòng khác. Số có thể có khoảng trống; muốn đổi chỗ hai số đã có thì dùng một số tạm còn trống. Chưa có thao tác kéo thả/hoán đổi nhiều vòng một lần.

stage/sortOrder cũ được giữ để tương thích payload, nhưng bỏ khỏi form và không quyết định thứ tự vòng. roundNumbering.tsx chỉ định dạng nhãn từ giá trị API, không tải toàn bộ danh mục để tính số nữa. Áp dụng đồng nhất ở thẻ, chọn vòng của bài, duyệt công bố, thứ tự bài và thống kê.

## Dữ liệu cũ và tiến độ

Migration gán số cho các vòng đang công bố theo lộ trình cũ, rồi đến vòng chưa công bố. Không thay Unit/Lesson ID, tên nội dung, bài/câu hỏi, Attempt, kết quả hay XP. Khóa vòng theo số mới trước mọi bộ lọc; vòng đã hoàn thành vẫn mở và tiến độ theo Lesson ID được giữ. Seed chạy lại không ghi đè số đã sửa.

Kiểm tra hiện tại: [số vòng lưu cố định](../statics/results/persistent-round-number-validation.md). Bằng chứng giao diện trước V12: [thuật ngữ](../statics/results/teacher-round-terminology-validation.md), [số vòng tính động](../statics/results/teacher-round-numbering-validation.md).

V13 thay quyền quản lý theo người tạo bằng phân công hiện tại cho Lesson; xem [nghiệp vụ](CONTENT_GOVERNANCE.md).
