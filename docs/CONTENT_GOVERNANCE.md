# Phân công và duyệt học liệu — V13

Hệ thống giữ một lộ trình vòng học chung. Mỗi vòng có một giáo viên phụ trách chính. Chức năng này thay mô hình tác giả tự tạo vòng và tự công bố của các mốc trước.

## Trách nhiệm

| Vai trò | Được thực hiện |
|---|---|
| ADMIN | Tạo/sửa chủ đề, mục tiêu, vòng và số thứ tự; phân công hoặc bỏ phân công; xem mọi bản nháp; duyệt/trả lại; công bố và ngừng cấp nội dung; áp dụng cập nhật audio hàng loạt |
| TEACHER | Đọc danh mục chung; tạo/sửa/sắp xếp bài trong vòng đang được giao; quản lý ngân hàng câu hỏi và tài liệu của mình; gửi bài/câu hỏi chờ duyệt, rút yêu cầu để sửa; xem thống kê vòng được giao |
| STUDENT | Học nội dung đã công bố theo lộ trình; không truy cập bản nháp, hàng đợi hoặc bản chụp duyệt |

Tài liệu đọc/nghe/ảnh là nguyên liệu biên soạn. Giáo viên vẫn tự quản lý tài liệu của mình; việc công bố tài liệu không đưa một câu hỏi/bài học mới đến học viên. Bài học/câu hỏi và việc thay audio trong nội dung đã công bố cần quyền ADMIN. MaterialVersion/QuestionVersion đã công bố là bất biến.

## Người tạo và người phụ trách

units.owner_id và lessons.owner_id giữ người tạo, không đổi khi phân công. units.assigned_teacher_id nullable xác định người phụ trách. Unit tạo mới qua API do ADMIN tạo; có thể chưa phân công. Khi tạo bài, ownerId là người thực hiện, kể cả ADMIN; quyền biên soạn bài lấy theo phân công của vòng, không lấy ownerId cũ.

V13 gán giáo viên sở hữu vòng cũ làm người phụ trách ban đầu; vòng do ADMIN tạo trước đó để chưa phân công. Không sửa số vòng, tên, chủ đề, revision, phiên bản, lịch sử làm bài hoặc XP. Seed chỉ đặt phân công khi tạo Unit mới, không ghi đè thay đổi của quản trị viên.

Sau khi chuyển/bỏ phân công, người cũ mất quyền đọc nháp và sửa bài trong vòng, người mới được tiếp quản. Bài đã công bố vẫn đọc được theo quyền kho công bố. Người mới đọc được đúng các phiên bản câu hỏi/tài liệu riêng đã được bài trong vòng tham chiếu, và file tương ứng; không được sửa root câu hỏi/tài liệu của tác giả khác. Quyền đọc phát sinh này bị thu hồi khi không còn được phân công. Giữ quyền cá nhân trên câu hỏi/tài liệu do chính mình tạo.

## Gửi duyệt

Giáo viên lưu nháp → xem trước và sửa các lỗi kiểm tra → Gửi duyệt. Yêu cầu lưu version/revision/token và bản chụp đầy đủ bài, câu hỏi, tài liệu tại thời điểm gửi. Bản chụp không thay đổi khi tác giả sửa rồi gửi lại sau này.

| Trạng thái yêu cầu | Hành vi |
|---|---|
| PENDING — Chờ duyệt | Bản nháp gốc bị khóa sửa/bỏ nháp/xuất bản trực tiếp; bản đã công bố cũ vẫn phục vụ học viên |
| APPROVED — Đã duyệt | ADMIN công bố đúng version và các phụ thuộc đã kiểm tra trong một transaction, ghi người xử lý và thời gian |
| RETURNED — Cần chỉnh sửa | ADMIN phải ghi lý do; giáo viên sửa rồi gửi một yêu cầu mới, giữ lịch sử yêu cầu cũ |
| WITHDRAWN — Đã rút | Người có quyền quản lý hiện tại rút yêu cầu để tiếp tục sửa; bản chụp/lịch sử vẫn giữ |

Mỗi bài/câu hỏi chỉ có một yêu cầu PENDING. Giáo viên khác không được xem yêu cầu riêng, trừ bài thuộc vòng hiện được giao. ADMIN xem toàn bộ; không có tự động công bố khi gửi duyệt.

Nếu phụ thuộc thay đổi, ngừng cấp hoặc giáo viên phụ trách thay đổi sau khi gửi, bản duyệt được đánh dấu cần kiểm tra lại. Duyệt với token/revision cũ bị từ chối; rút/trả lại rồi xem trước và gửi lại. Duyệt bài đồng thời công bố chính xác các câu hỏi/tài liệu nháp được tham chiếu; yêu cầu riêng đang chờ của câu hỏi đó được ghi nhận duyệt cùng bài.

## Giao diện

- ADMIN: sidebar **Phân công biên soạn** → chọn vòng → Phân công → chọn **Giảng viên phụ trách** → **Lưu phân công**. Form này chỉ đổi phân công, giữ tên/chủ đề/số vòng và dùng cùng API/revision/receipt hiện có. Quản lý học liệu → Danh mục vẫn giữ form đầy đủ như trước.
- TEACHER: Quản lý học liệu → **Vòng được giao** → **Soạn bài**. Danh sách bài lọc sẵn đúng vòng qua URL unit, giữ khi tải lại; thêm bài mới chọn sẵn vòng nếu còn quyền truy cập. Sau khi lưu, **Xem trước → Gửi duyệt bài học/câu hỏi**; sidebar **Bài gửi duyệt** để xem phản hồi hoặc rút yêu cầu.
- ADMIN: **Xem bài chờ duyệt** ngay tại trang phân công hoặc sidebar **Duyệt học liệu** → mở yêu cầu → **Duyệt và công bố** hoặc trả lại kèm lý do. Hàng đợi chỉ có bài sau khi giáo viên gửi duyệt; nội dung seed đã công bố không tạo yêu cầu giả.
- Bản sửa đang chờ không thay bản học viên đang dùng. Lượt làm, kết quả và tiến độ cũ tiếp tục gắn với ID/Version đã chốt.

## Giao thức và đồng thời

RequestId/revision/receipt giữ cơ chế hiện có. Quyền mới được kiểm tra bên trong action sau bước đối soát receipt: yêu cầu thành công trước V13 vẫn replay đúng body cũ, không cấp quyền ghi mới. DTO UnitCreate/Update bỏ các trường phân công null khi băm để không đổi hash payload cũ. Root có canEdit/review từ server, UI không suy quyền quản lý bài chỉ từ người tạo.

Thứ tự khóa cho ghi học liệu: actor → content_control → Unit hoặc dependency roots; xuất bản vẫn khóa Material → Question → Lesson. content_control chỉ tuần tự hóa nhóm thao tác biên soạn/duyệt/phân công, không dùng cho GET hoặc giao dịch học/XP. Một quyết định và việc công bố cùng commit với receipt. Review snapshot không có FK đến bản nháp có thể bị bỏ; FK tới root và JSON chụp giữ lịch sử.

Bộ dữ liệu mẫu đóng gói là nhập liệu tin cậy trong bootstrap, dùng publisher nội bộ dưới transaction/khóa hiện có và giữ key/operation receipt cũ. Không tạo endpoint bỏ qua duyệt cho giáo viên. Khởi động/seed lại không đặt lại số vòng, phân công, nháp hoặc dữ liệu đã tùy chỉnh.

API và các mã lỗi được mô tả trong [hợp đồng M02/M03](../source/common/M02_M03_API.md). Kết quả kiểm tra tại [biên bản V13](../statics/results/content-governance-validation.md).
