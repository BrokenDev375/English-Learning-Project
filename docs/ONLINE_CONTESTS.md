# Cuộc thi online và kho bộ đề

Nhánh `feature/online-contests`, nền `9d3b743`. V17 thay luồng soạn câu ngay trong cuộc thi bằng **kho bộ đề riêng có thể dùng lại**, tách quyền ra đề và giám sát theo yêu cầu ngày 30/10. Không phải Giải đấu tuần M05 hoặc thử thách solo M08.

## Luồng sử dụng

**Admin phân quyền → giáo viên soạn bộ đề → admin duyệt → tạo cuộc thi và chọn một bộ đề → phân công giám thị → học viên đăng ký/thi.**

1. ADMIN vào **Cuộc thi → Phân quyền**. Mỗi giáo viên có hai checkbox riêng: **Ra đề** và **Giám sát**. Có thể cấp một hoặc cả hai quyền. Thu hồi quyền có hiệu lực trên API, cập nhật giao diện qua WebSocket và không đăng xuất tài khoản website.
2. Giáo viên có quyền Ra đề mở **Kho bộ đề → Tạo bộ đề**. Đặt tên, mô tả, thêm/sắp xếp các câu. Danh sách câu nằm bên trái, nội dung câu đang soạn bên phải; các ô nhập và khoảng cách được thu gọn. Một giáo viên chỉ đọc/sửa bộ đề của mình, ADMIN quản lý toàn bộ.
3. **Lưu bộ đề → Gửi duyệt**. ADMIN mở bộ đề chờ duyệt, chọn **Duyệt bộ đề** hoặc **Yêu cầu sửa** kèm nhận xét. Bộ đề đã duyệt giữ nội dung cố định; muốn tạo bản khác dùng **Nhân bản bộ đề**, sửa và gửi duyệt lại. Lưu trữ ngừng cho chọn vào cuộc thi mới, vẫn giữ dữ liệu và các cuộc thi đã công bố.
4. ADMIN **Tạo cuộc thi** chỉ nhập thông tin, chọn **một bộ đề đã duyệt**, thời gian và giám thị. Ô chọn đề mở danh sách có tìm tên bộ đề/người soạn và phân trang; không dùng dropdown tải toàn bộ kho. Tab Kho bộ đề cũng tìm theo tên/người soạn. Không nhập từng câu ở bước này. Có thể lưu nháp chưa chọn đề; cần chọn bộ đề hợp lệ trước khi công bố. Một bộ đề có thể dùng cho nhiều cuộc thi; mỗi cuộc thi có bản chốt riêng.
5. Danh sách **Giám thị cuộc thi** chỉ có giáo viên đã được cấp quyền Giám sát. Cấp quyền là điều kiện; ADMIN vẫn cần tick giáo viên vào từng cuộc thi. Có tìm theo họ tên/email, phân trang và danh sách đã chọn; đổi từ khóa/trang không bỏ các giám thị đã tick. Có thể bỏ từng người bằng nút ×. Giám thị không được sửa đề hoặc đọc đáp án qua API quản lý cuộc thi. Người chỉ có quyền giám sát không thấy tab Kho bộ đề.
6. Học viên đăng ký trước hạn; ADMIN duyệt trước giờ bắt đầu. Học viên đọc quy định, xác nhận và **Vào phòng chờ trước giờ thi**. Thi chung giờ; người chưa check-in không được bắt đầu muộn. Người đã check-in được kết nối lại, thời gian vẫn trôi.
7. Khi đến giờ, đề mở cho người đủ quyền. Đáp án tự lưu tại máy và gửi server, có trạng thái xác nhận. Nộp chủ động có bước xác nhận; hết giờ tự chốt đáp án đã lưu trên server. Đáp án chỉ lưu tại máy không được gửi bù sau hạn.
8. Giám thị xem danh sách thí sinh và nhật ký. Cột Trạng thái thi tách nhãn chờ duyệt/không được duyệt, chưa vào phòng, chờ thi, đang làm bài, đã nộp/chốt, chờ chốt bài, vắng thi và thu hồi. Trong giờ làm bài còn có nhãn **Trong tab thi / Ngoài tab thi**, đổi theo tín hiệu rời/quay lại đã được server chấp nhận. Mất kết nối hiển thị **Chưa rõ trạng thái tab**; không dùng trạng thái tab cũ để khẳng định hiện tại. Người bị thu hồi vẫn có trong danh sách Tất cả và Đã thu hồi, không mất khỏi lịch sử. Kết nối phòng thi hiển thị riêng phía dưới; còn kết nối không có nghĩa là đang làm bài. Trên điện thoại, mỗi thí sinh thành một thẻ để không bị khuất cột trạng thái. Các bộ lọc hiện có vẫn dùng trạng thái đăng ký/đã chốt/cảnh báo; nhãn tiến trình chỉ hiển thị, không đổi quyền hoặc hạn thi. Rời màn lần 1 cảnh báo, lần 2 trừ mặc định 10 phút đúng một lần, từ lần 3 báo giám thị. Giáo viên ghi nhận đã xử lý; **ADMIN quyết định thu hồi quyền thi**. Không tự loại ở lần ba.
9. ADMIN có thể đóng thi sớm kèm lý do; hệ thống cũng tự đóng đúng giờ. Đóng/thu hồi chỉ dừng quyền làm bài trong contest, không khóa tài khoản website. Khi chốt toàn cuộc thi, mở kết quả/đáp án/BXH; đúng giảm dần, thời gian thực tế tăng dần, bằng cả hai đồng hạng. Lưu trữ giữ lịch sử.

## Quyền và bộ đề

| Vai trò/quyền | Được làm |
|---|---|
| ADMIN | Phân quyền, đọc/duyệt mọi bộ đề, tạo/công bố/đóng cuộc thi, duyệt học viên, thu hồi quyền thi |
| TEACHER + Ra đề | Tạo, lưu, gửi duyệt, nhân bản/lưu trữ và xem bộ đề của mình |
| TEACHER + Giám sát + được phân công | Theo dõi cuộc thi được giao, xem nhật ký và ghi nhận cảnh báo; không nhận câu hỏi/đáp án riêng từ API tác giả |
| STUDENT | Đăng ký, làm bài và xem kết quả cá nhân đúng thời điểm |

Bộ đề dùng bốn dạng tự chấm hiện có: chọn đáp án, điền từ, ghép cặp, sắp xếp từ; có context chữ. Câu hỏi contest tách khỏi ngân hàng học, không sinh XP/lịch ôn/tiến độ vòng. Bản này chưa nối các hoạt động có file nghe/ảnh hoặc tự luận vào kho bộ đề.

## Dữ liệu và nhất quán

- V16: contests, contest_participants, contest_submissions, contest_events. V17 thêm contest_teacher_permissions, contest_papers và nguồn bộ đề trên contest. Không sửa migration đã chạy.
- V17 chuyển đề cũ sang thư viện, giữ draft/paper/results và deadline cũ. Giáo viên từng được giao ở V16 được giữ hai quyền; bộ đề nháp chuyển cho giáo viên đầu tiên được giao, hoặc ADMIN nếu không có giáo viên phù hợp. Đề đã công bố chuyển thành bộ đề đã duyệt. ADMIN có thể điều chỉnh hai quyền sau nâng cấp.
- READY là nội dung bất biến; nhân bản tạo ID bộ đề và ID câu mới. Khi chọn/công bố, server chụp câu hỏi vào contest, lưu ID/revision/tên nguồn. Sửa bản sao hoặc lưu trữ nguồn không thay đề của cuộc thi đã công bố. Nguồn đã lưu trữ không được dùng để công bố cuộc thi nháp.
- HTTP mutation dùng session/CSRF, receipt/revision và transaction. Quyền ra đề được kiểm tra cả trước khi replay receipt cũ; thu hồi không cho tiếp tục đọc kết quả lệnh tác giả cũ. Request trùng không tạo thêm bộ đề/cuộc thi/bài nộp.
- WebSocket contest chỉ invalidation sau commit, không chứa câu/đáp án. Thu hồi Giám sát gỡ subscription contest của giáo viên, gửi CONTEST_PERMISSIONS_CHANGED; reconnect GET lại quyền. Lệnh ghi vẫn kiểm tra hạn/quyền khi socket mất.
- Server giữ UTC/deadline, worker tự chốt; submittedAt là hạn hiệu lực, processedAt là lúc xử lý. Một writer với lease 45 giây, heartbeat UI 15 giây. IndexedDB M06 giữ bản nhập/lệnh học viên, logout fence/xóa dữ liệu riêng.

## Môi trường và kiểm tra

Môi trường thử riêng `english-learning-e2e-online-contests-20261030`: **http://localhost:15223/#/contests**, API `18133`, DB V17. DB được sao lưu trước khi nâng; backup nằm trong `.test-state/` và không đưa vào Git. Bản demo 15183 và các volume khác giữ nguyên. Không tự seed cuộc thi/bộ đề sản phẩm.

Xem [API và OpenAPI](../source/common/ONLINE_CONTESTS_API.md), [kiểm tra thư viện](../statics/results/contest-paper-library-validation.md), [kiểm tra V16 trước khi tách thư viện](../statics/results/online-contests-validation.md). Chưa commit/push khi người dùng chưa yêu cầu.

## Giới hạn

Trình duyệt chỉ báo rời màn theo tín hiệu gửi được; không biết tab đích, không bảo đảm phát hiện mọi gian lận. Offline không dừng giờ hoặc nhận đáp án sau hạn. Session/socket hiện trên một backend; chưa đo tải contest/nhiều node. Form tác giả cảnh báo thay đổi chưa lưu khi đóng trang; chưa có journal offline cho việc soạn bộ đề. Bài làm học viên vẫn dùng cơ chế phục hồi riêng đã có.
