# Bài viết cuối vòng

## Phạm vi

Bài viết là hoạt động riêng ở cuối mỗi Unit, ngoài bộ 15–20 câu tự chấm của Lesson. Chỉ vòng có đề đang công bố mới yêu cầu nộp bài viết. Không tự tạo đề, thêm dữ liệu học sinh hoặc đổi điểm/XP/M04/League.

Giáo viên hiện được phân công Unit hoặc ADMIN thiết lập đề và chấm bài. Đây là luồng bài viết được người dùng đồng ý riêng: giáo viên công bố đề viết trực tiếp; quy trình ADMIN duyệt Question/Lesson của V13 giữ nguyên. Chuyển phân công thu hồi ngay quyền đọc/chấm của giáo viên cũ và cho người mới tiếp quản. Hàng đợi chỉ có tên học sinh và bài nộp thuộc vòng được giao, không trả email hay thống kê học viên ngoài phạm vi.

## Thiết lập và học

- Quản lý học liệu → **Bài viết cuối vòng** → chọn **Tạo bài viết** hoặc **Chấm bài**. Trang lựa chọn chỉ có hai chức năng; mỗi chức năng có màn hình và nút quay lại riêng.
- **Tạo bài viết** → chọn vòng → nhập tên đề, hướng dẫn, số từ gợi ý và 1–20 tiêu chí. Mỗi tiêu chí có ID ổn định, nội dung và điểm nguyên dương; tổng đúng 10. Màn hình này chỉ tải thiết lập đề, không tải danh sách bài nộp.
- **Chấm bài** → chọn vòng → lọc bài chờ chấm/đã chấm → mở bài và gửi kết quả. Màn hình này chỉ tải bài nộp, không hiển thị biểu mẫu tạo đề. Trở về danh sách sau khi chấm; nút Chọn chức năng bài viết quay lại trang lựa chọn.
- URL giữ `writingMode=create|grade`, Unit ID và `writingSubmission` khi mở chi tiết, nên tải lại/Back/Forward giữ màn hình. Không đưa nội dung đề/bài viết vào URL. Rời chức năng hoặc đổi vòng khi có đề/điểm chưa gửi cần xác nhận bỏ thay đổi; pending chưa rõ kết quả chặn chuyển chức năng qua các nút này.
- **Lưu đề viết** chỉ đổi bản nháp. **Công bố đề viết** tạo phiên bản bất biến. Ngừng cấp đề loại yêu cầu khỏi lộ trình mới, giữ mọi bài viết và điểm cũ.
- Học sinh hoàn thành các Lesson trong vòng đang mở mới được bắt đầu viết. Các tiêu chí hiển thị trước khi viết. Số từ là gợi ý; bài nộp phải có nội dung, tối đa 30.000 ký tự.
- Khi bắt đầu, chốt WritingVersion. Sửa/công bố lại đề chỉ áp dụng người chưa bắt đầu. Một học sinh có một hồ sơ viết mỗi Unit; các bản sửa tiếp tục dùng đề và tiêu chí đã chốt.
- Lưu tại máy sau 250 ms; lưu server sau 1.500 ms hoặc nút lưu ngay. IndexedDB dùng kho M06 workingDrafts, khóa riêng writing/user/unit/writer. Không chứa bài viết trong URL/localStorage/sessionStorage. Logout xóa dữ liệu riêng và fence callback; reload xác minh phiên trước khôi phục. Bản chưa commit IndexedDB vẫn có thể mất.
- Khi mở lại có bản local, học sinh chọn khôi phục hoặc bản máy chủ; lệnh chưa rõ kết quả cần replay trước khi bỏ. Pending giữ nguyên action/requestId/baseRevision/text và được commit local trước gửi. ACK được GET đối soát trước xóa pending. Không sửa nội dung trong lúc pending. 409 hiển thị hai bản, không tự tăng revision rồi ghi đè; giữ local chỉ chuẩn bị để người học lưu rõ ràng.

## Nộp, chấm và sửa

`DRAFT → SUBMITTED → GRADED` hoặc `SUBMITTED → RETURNED → SUBMITTED`.

Nộp nhận toàn bộ text mới nhất, không lệ thuộc autosave; tạo Submission bất biến cùng revision/state/receipt trong một transaction. Có ít nhất một Submission thì bài viết được tính đã nộp cho lộ trình; không phải đợi chấm. Trả bài để sửa không khóa lại vòng sau. Vòng hoàn thành khi đủ Lesson và đã nộp bài viết nếu có yêu cầu; vòng không có đề giữ hành vi cũ.

Giáo viên đọc bản nộp và đánh dấu từng tiêu chí đạt/chưa đạt, ghi góp ý riêng và nhận xét chung. Server đối chiếu đủ ID tiêu chí theo đúng bản đề đã chốt, cộng điểm mục đạt; không nhận tổng điểm do client gửi. Không có chấm ngữ nghĩa tự động hoặc mức đạt một phần. Chọn cho sửa phải có nhận xét hướng dẫn. Chỉ bản nộp mới nhất chưa chấm được chấm một lần; điểm và nhận xét đã gửi không bị viết lại. Nộp lại tạo Submission mới, giữ bản nộp và kết quả cũ.

## Dữ liệu và đồng thời

V15 thêm unit_writing_tasks/versions/works/submissions/grades. Không sửa V1–V14. Task có draft và con trỏ published; Version giữ định nghĩa; Work có text nháp, revision nghiệp vụ và con trỏ bản nộp gần nhất; Submission/Grade bất biến. FK, unique học sinh+Unit và unique grade+submission bảo vệ tính toàn vẹn.

Luồng ghi: AccountGuard actor lock → receipt/replay → (content_control đối với thiết lập/chấm) → khóa Unit → kiểm tra phân công/state/revision → ghi nghiệp vụ+receipt → commit. Mọi ghi bài viết cùng Unit dùng cùng khóa Unit; không khóa tài khoản học sinh từ giao dịch giáo viên. Request trùng key khác payload/operation trả REQUEST_ID_REUSED; replay trước revision/state. GET chỉ đọc và không tạo Work, nộp, chấm, XP hay progress M03.

## Kiểm tra

API: [hợp đồng](../source/common/UNIT_WRITING_API.md). Các ca kiểm tra: WritingApiTest và unit-writing.spec.ts. Kết quả thực tế và giới hạn cập nhật tại [biên bản](../statics/results/unit-writing-validation.md).
