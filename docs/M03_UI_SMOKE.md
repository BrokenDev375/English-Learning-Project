# Thử giao diện M03 — học bài và luyện tập

Từ root, mở Docker Desktop rồi chạy `start.cmd` để build/chạy mã mới. Mở http://localhost:5173 và đăng nhập STUDENT. Dữ liệu demo hiện có được giữ; không xóa volume để nâng cấp. TEACHER/ADMIN chỉ khám phá giới thiệu hoặc xem trước trong quản lý học liệu, không có nút tạo lượt thật.

## Học một bài

1. Chọn **Vào chơi** → vòng đang mở → chọn bài. Trang tóm tắt số câu, câu có hướng dẫn/tự làm và trạng thái hoàn thành. Nút chính là **Bắt đầu chơi** khi mới học, **Tiếp tục làm** khi còn lượt dở hoặc **Chơi lại bài** khi đã nộp. Bài từng hoàn thành vẫn hiện **Tiếp tục làm** nếu đang học lại dở. **Xem kết quả gần nhất** mở lần nộp mới nhất của đúng bài, kể cả đang có lượt mới chưa nộp. Server quyết định tạo/tiếp tục, giữ cố định phiên bản và bộ câu của lượt.
2. Dùng các nút số hoặc **Câu trước/Câu tiếp theo**. Có bốn dạng: chọn một đáp án, điền từng ô (tối đa 200 ký tự/ô), ghép cặp theo ID ổn định và sắp xếp từ thành câu. Mỗi mục bên phải chỉ được ghép một lần; chọn lại mục đang được dùng sẽ bỏ cặp trước. **Xóa câu trả lời** đưa câu về chưa trả lời.
3. Đọc đoạn văn, xem ảnh hoặc nghe audio đi kèm. Audio dùng chung giữa các câu của cùng học liệu; vị trí nghe là số giây tuyệt đối, giới hạn theo đoạn nghe và thời lượng thực của file.
4. Chỉ câu **Có hướng dẫn** có **Mở gợi ý** và **Kiểm tra đáp án**. Kiểm tra cần trả lời đủ; hiện đáp án/giải thích cho câu đó, chưa hiện transcript chung audio. Mở lại hint không tăng thêm lần hỗ trợ. Khi sửa sau một lần kiểm tra, phản hồi cũ ghi rõ nó áp dụng cho đáp án đã kiểm tra.
5. M06 tự lưu toàn bộ đáp án/câu đang xem/vị trí nghe tại máy, rồi autosave server khi đủ điều kiện. Nút **Lưu nháp lên máy chủ** gửi ngay. **Đã lưu tại máy** không đồng nghĩa **Đã lưu trên máy chủ**. Reload xác minh auth trước khi nhận lại local draft hoặc pending để đối soát; xem [phục hồi M06](M06_MANUAL_TEST_CASES.md).
6. Bấm **Nộp bài** và đọc xác nhận số câu chưa đủ. Có thể nộp sớm. Submit gửi toàn bộ bản nhập mới nhất, kể cả chưa bấm lưu nháp. Bài LEARN hoàn thành khi nộp đủ đáp án; không cần kiểm tra từng câu hướng dẫn hoặc đúng hết. Sau nộp, xem toàn bộ lời giải rồi bấm Xác nhận hoàn thành bài học để về vòng. Bài cũ đủ đáp án vẫn xác nhận được ngay.
7. Trang kết quả giữ điểm, câu trả lời lần đầu/khi nộp, dấu hỗ trợ, đáp án, giải thích và transcript. Chọn câu, nhập lại đáp án và bấm **Kiểm tra bài chữa**. Chữa bài không đổi điểm, đáp án khi nộp hoặc tiến độ đã chốt.
8. **Về danh sách lượt** mở lịch sử. Tab **Chơi theo vòng** hiển thị trạng thái hoàn thành trong từng Unit, và báo khi có phiên bản bài mới.

## Luyện tập và lịch sử

- Không có mục tạo Luyện tự chọn trên giao diện. Dùng **Chơi lại bài**/**Chơi lại vòng** trong bản đồ để làm lại; **Ôn tập cá nhân** chọn nội dung theo kết quả. Lượt PRACTICE cũ vẫn mở từ lịch sử/URL và phục hồi thao tác chưa nhận ACK; không đánh dấu hoàn thành Lesson.
- Tab **Lượt làm của tôi**: lọc chế độ/trạng thái, phân trang, tiếp tục lượt đang làm hoặc xem kết quả lượt đã nộp. F5/back/forward/deep link dùng Attempt ID có sẵn, không POST tạo lượt để thay bộ câu.
- REVIEW được tạo từ tab **Ôn tập cá nhân** M04, dùng chung runner và cơ chế phục hồi M06. Điều kiện XP M05 được giữ theo các luật thưởng hiện hành.

## Khi kết nối hoặc phiên đăng nhập gặp lỗi

- Timeout/mất phản hồi/503: giữ nguyên bản nhập và requestId/payload đang chờ; khóa thao tác ghi kế tiếp. **Gửi lại thao tác và đối soát** replay đúng thao tác cũ rồi GET dữ liệu mới nhất. Yêu cầu tạo lượt cũng có nút gửi lại riêng. Không chọn một lượt mới để thay thao tác chưa rõ kết quả.
- Bạn vẫn có thể sửa đáp án trong lúc lưu nháp. ACK cũ không thay các chỉnh sửa mới hơn; trạng thái tiếp tục báo chưa lưu, cần bấm lưu lần nữa. Trong lúc submit chưa rõ kết quả, phần trả lời bị khóa.
- 409: màn **Đối chiếu bài làm** đặt bản đang nhập cạnh bản server. Chỉ sau lựa chọn và xác nhận rõ ràng mới dùng bản server hoặc giữ toàn bộ bản nhập cho lần lưu tiếp. Nếu server đã nộp, chỉ có lựa chọn dùng kết quả server. Không tự tăng revision rồi ghi đè.
- 401: dừng ghi, chọn **Đăng nhập lại**. Cùng user nhận lại local draft/pending sau xác minh phiên; logout chủ động xóa dữ liệu riêng và đổi user không nhận bản của người trước.
- Dữ liệu đã báo lưu tại máy nằm trong IndexedDB; phần chưa commit vẫn có thể mất khi đóng tab. Không có login offline hoặc service worker. Không Web Locks thì có nút khôi phục bản cũ; record sai/storage lỗi được cảnh báo, không gửi command tùy ý. Xem [hợp đồng M06](../source/common/M06_API.md).

## Kiểm thử tự động

Từ `source/client`: `npm ci`, `npm run build`, `npm run test:e2e -- module-three-learning.spec.ts`. E2E tự build môi trường Docker riêng `english-learning-e2e` ở 5174/8081, giữ nguyên database demo; không cấu hình E2E_BASE_URL vào cổng demo 5173. Xem [báo cáo bước 5](../statics/results/module-3-step5-validation.md) để biết môi trường, lệnh và kết quả thực tế.
