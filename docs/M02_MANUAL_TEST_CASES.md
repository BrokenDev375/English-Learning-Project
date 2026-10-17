# M02 — toàn bộ chức năng và ca kiểm thử

Đối chiếu với code hiện tại ngày 17/10/2026. Bộ này có **98 ca kiểm thử**, gồm thao tác giao diện, API và kiểm tra hệ thống. Đây là checklist để thực hiện và ghi nhận kết quả; không mặc định đánh dấu PASS cho một lượt kiểm thử thủ công mới.

Bổ sung 18/10/2026: điều hướng đã hỗ trợ Back/Forward, tải lại và mở link màn con. Có năm ca tự động riêng và hướng dẫn thử tay tại [kiểm tra điều hướng](../statics/results/navigation-validation.md).

## 1. M02 có những chức năng gì?

| Nhóm | Chức năng hiện có | Tác dụng |
|---|---|---|
| Danh mục | Tạo/sửa/tìm kiếm chủ đề, mục tiêu học, Unit; lọc/phân trang, chặng và thứ tự Unit | Tổ chức học liệu theo chủ đề, mục tiêu và lộ trình |
| Tài liệu | Soạn đoạn đọc, audio, ảnh; nguồn/điều kiện sử dụng; upload ảnh/audio, chọn đoạn audio, nghe/xem trước | Dùng một học liệu cho nhiều câu hỏi |
| Ngân hàng câu | Chọn một đáp án, điền chỗ trống, ghép cặp; chủ đề/mục tiêu/độ khó, tài liệu, đáp án/gợi ý/giải thích/nguồn | Biên soạn nội dung câu hỏi có cấu trúc và kiểm tra tính hợp lệ |
| Bài học | Chọn Unit/mục tiêu, hướng dẫn/ví dụ/bản dịch, chọn phiên bản câu, chia ba nhóm, sắp xếp câu và bài trong Unit | Ghép các câu và tài liệu thành bài học |
| Vòng đời học liệu | Lưu/sửa/bỏ nháp, xem trước, xuất bản, mở bản chỉnh sửa, ngừng cấp mới | Cho phép biên soạn dần và công bố nội dung hợp lệ |
| Phiên bản | Giữ bản công bố cũ, liên kết theo Version ID, xem bản nháp/bản công bố hiện tại; API đọc phiên bản lịch sử | Sửa nội dung mà không làm biến đổi bài đã dùng phiên bản cũ |
| Sao chép câu | Sao chép câu được phép đọc thành câu của mình, giữ originKey | Tái sử dụng nội dung và phát hiện hai bản cùng nguồn trong một bài |
| Kho và phân quyền | Kho của tôi/kho đã xuất bản; ADMIN xem mọi tác giả; nháp riêng tư, quyền đọc file | Tách quyền STUDENT, TEACHER, ADMIN |
| Khám phá bài học | Học viên tìm/lọc Unit, xem bài, giới thiệu, hướng dẫn và mục tiêu | Xem nội dung đã công bố, không nhận ngân hàng/khóa đáp án |
| Toàn vẹn dữ liệu | Chống ghi đè từ tab cũ, chống thao tác lặp, phát hiện preview cũ, công bố phụ thuộc cùng transaction, bảo vệ nội dung đang dùng | Giữ dữ liệu nhất quán khi nhiều người sửa hoặc mất phản hồi mạng |
| Demo/seed | 3 chủ đề, 3 Unit, 6 mục tiêu, 6 bài, 90 câu, 13 tài liệu, 7 file; manifest/nguồn/hash; seed chạy lại, nâng mẫu cũ | Có bộ nội dung đầy đủ để demo và tích hợp M03 |
| Giao diện | Desktop/mobile, trạng thái tải/lỗi/rỗng, thử lại thao tác, giữ nội dung khi lỗi | Dùng được trên nhiều kích thước màn hình và khi kết nối có vấn đề |

**Ngoài M02:** bắt đầu lượt làm, kiểm tra đúng/sai, chấm điểm, nộp bài, lưu tiến độ học, ôn thích nghi, XP, huy hiệu, bảng xếp hạng. Preview M02 cho tương tác thử nhưng không chấm hoặc sinh lịch sử học. Chưa có màn liệt kê toàn bộ lịch sử phiên bản hoặc xóa vĩnh viễn root/file công bố. Không có chức năng xóa danh mục trong UI hiện tại.

## 2. Chuẩn bị và cách ghi nhận

Mở **http://localhost:5173**, tải lại trang. Các tài khoản mặc định khi bật demo:

| Người thử | Tài khoản | Mục đích |
|---|---|---|
| Giáo viên A | `teacher@english.local` / `Teacher@12345` | Thử biên soạn và kho OWN |
| Học viên | `student@english.local` / `Student@12345` | Thử danh mục học và giới hạn quyền |
| Quản trị | Email/mật khẩu bootstrap trong `.env` | Xem/sửa nội dung của mọi tác giả |
| Giáo viên B | Đăng ký một tài khoản thử, ADMIN đổi vai trò thành TEACHER | Thử quyền giữa hai tác giả; dùng cửa sổ ẩn danh hoặc trình duyệt khác |

Ký hiệu **UI**: thực hiện trên giao diện. **API**: Postman/DevTools theo mục 5. **AUTO**: chạy bộ test trên DB riêng; không cần gây lỗi trực tiếp vào DB đang dùng.

Mỗi ca ghi: `ID | PASS/FAIL/BLOCKED | kết quả thực tế | ảnh hoặc HTTP status/body | ghi chú`. Chỉ đánh dấu PASS khi đạt đủ kết quả mong đợi. Một ca có nhiều biến thể nên ghi kết quả từng biến thể.

### Dữ liệu thử dùng chung

Tạo nội dung riêng có tiền tố `UAT`, code ví dụ `uat-20261017-01` (đổi hậu tố cho mỗi lượt). Các ca sửa/bỏ nháp/ngừng cấp nên dùng nội dung UAT để giữ bộ demo thuận tiện cho các lần thử sau.

| Mã trong checklist | Dữ liệu cần chuẩn bị |
|---|---|
| T1/T2 | Hai chủ đề UAT khác nhau, code khác nhau |
| O1/O2 | O1 thuộc T1, O2 thuộc T2 |
| U1/U2 | U1 thuộc T1, U2 thuộc T2; chặng 1, thứ tự 0 |
| R1 | READING, tên `UAT — Mai’s morning`; đoạn `Mai gets up at six. She goes to school by bus.`; nguồn `Tự biên soạn để kiểm thử` |
| A1 | AUDIO, dùng [daily-routine.wav](../source/server/src/main/resources/demo/materials/daily-routine.wav); bản chép lời đúng file; bắt đầu 1 giây, kết thúc 5 giây |
| I1 | IMAGE, dùng [daily-routine.png](../source/server/src/main/resources/demo/materials/daily-routine.png); có mô tả và chú thích |
| Q1 | SINGLE_CHOICE, T1/O1, Dễ; `What time does Mai get up?`; lựa chọn `At six.` / `At seven.` / `At eight.`; đúng `At six.`; gắn R1; hint `Read the first sentence.`; giải thích `The passage says that Mai gets up at six.` |
| Q2 | FILL_BLANK, T1/O1; đoạn chữ `She ` + một ô trống + đoạn chữ ` to school by bus.`; đáp án `goes`; có giải thích/nguồn |
| Q3 | MATCHING, T1/O1; `seven → 7`, `eight → 8`; có giải thích/nguồn |
| L1 | Bài `UAT — Morning practice`, U1/O1; một khối hướng dẫn và một khối ví dụ/bản dịch; Q1 ở Có hướng dẫn, Q2 ở Tự làm, Q3 ở Biến thể ôn tập |

Q1/Q2/Q3 đều nên nhập hint để dễ chuyển giữa các nhóm. Vòng đời hợp lệ: **Lưu bản nháp → Xem trước → Xuất bản**. Để sửa bản công bố: **Tạo bản chỉnh sửa → sửa → Lưu bản nháp → Xem trước → Xuất bản**. Nháp cần đúng cấu trúc và tham chiếu dù được thiếu một phần nội dung.

## 3. Các ca kiểm thử

### A. Truy cập và phân quyền — 8 ca

| ID | Kênh | Thao tác | Kết quả mong đợi |
|---|---|---|---|
| M02-A01 | UI | Đăng nhập giáo viên A → Quản lý học liệu → lần lượt Danh mục/Tài liệu/Câu hỏi/Bài học. | Có đủ bốn khu vực; tải được dữ liệu được phép xem. |
| M02-A02 | UI/API | Đăng nhập học viên; kiểm tra menu; gọi GET `/api/author/topics`, `/api/author/materials`, `/api/author/questions`, `/api/author/lessons`. | Không có menu Quản lý học liệu; author API trả 403. |
| M02-A03 | API | Khi chưa đăng nhập, gọi GET `/api/author/topics`. | 401, không trả danh mục tác giả. |
| M02-A04 | UI | Giáo viên A tạo một nháp riêng từng loại tài liệu/câu/bài; giáo viên B tìm trong kho và thử GET theo ID. | B không thấy nháp riêng; GET nội dung chỉ có nháp của A trả 404. |
| M02-A05 | UI/API | A công bố R1/Q1/L1; B xem Kho đã xuất bản, mở nội dung và thử sửa trực tiếp bản của A. | B đọc bản công bố; không có quyền sửa bản gốc; API sửa bị từ chối. |
| M02-A06 | UI/API | A mở nháp chỉnh sửa R1/Q1 đã công bố, đặt nội dung riêng dễ nhận ra; B đọc root và thử GET version nháp đó. | B chỉ nhận bản công bố; draft/draftVersionId bị ẩn, không chứa nội dung riêng; GET version nháp trả 404. |
| M02-A07 | UI | ADMIN chọn Tất cả tác giả; mở và sửa nội dung UAT của A. | ADMIN được sửa; chủ sở hữu vẫn là A. |
| M02-A08 | UI/API | B xem Danh mục dùng chung của Chủ đề/Mục tiêu; thử sửa mục của A; thử scope ALL của Unit/Tài liệu/Câu/Bài. | Đọc danh mục dùng chung được, không sửa mục của A; scope ALL các kho còn lại dành cho ADMIN, B bị từ chối. |

### B. Chủ đề, mục tiêu, Unit — 12 ca

Đường dẫn UI: **Quản lý học liệu → Danh mục**.

| ID | Kênh | Thao tác | Kết quả mong đợi |
|---|---|---|---|
| M02-B01 | UI | Tạo T1 với code hợp lệ, tên/mô tả; lưu → F5 → tìm lại. | Tạo đúng một chủ đề; dữ liệu đã lưu còn nguyên. |
| M02-B02 | UI | Sửa tên/mô tả T1 rồi lưu; mở lại. | Hiện giá trị mới; code cũ giữ nguyên. |
| M02-B03 | UI/API | Tạo chủ đề có code trùng T1 bằng thao tác mới/requestId mới. | Bị từ chối; API 409; không tạo bản trùng. |
| M02-B04 | UI/API | Lần lượt thử code `BAD CODE`, tên chỉ có khoảng trắng, tên quá giới hạn; API bỏ trường bắt buộc. | Không lưu dữ liệu sai; báo lỗi; không có hàng tạo dở. |
| M02-B05 | UI | Tạo O1 thuộc T1, tên/mô tả/code hợp lệ → lưu → sửa tên/mô tả. | O1 được lưu và cập nhật, vẫn thuộc T1. |
| M02-B06 | API | PUT mục tiêu để đổi `code` hoặc `topicId`; PUT chủ đề thêm `code` mới. | 400 vì các trường này không được sửa; dữ liệu cũ giữ nguyên. |
| M02-B07 | UI | Tạo U1 chọn T1, chặng 1, thứ tự 0; sửa tên/mô tả/chặng/thứ tự → F5. | Lưu và hiển thị đúng cấu hình; Unit mới chưa có mục tiêu suy ra từ bài. |
| M02-B08 | UI | Khi Unit UAT chưa có bài, đổi sang chủ đề khác rồi đổi lại. | Đổi Topic được khi Unit còn trống. |
| M02-B09 | UI/API | Thêm một bài vào U1, sau đó thử đổi Topic của U1. | Bị từ chối, Unit vẫn thuộc T1, liên kết bài không hỏng. |
| M02-B10 | UI | Tìm tên/code riêng; lọc mục tiêu/Unit theo T1; đổi phạm vi được phép; chuyển Trước/Sau khi đủ dữ liệu. | Kết quả đúng điều kiện, phân trang không mất bộ lọc; tìm tên không tồn tại cho trạng thái rỗng. |
| M02-B11 | UI/API | Tạo các Unit cùng Topic với `(chặng, thứ tự)` là `(2,0)`, `(1,1)`, `(1,0)`; xem danh sách; thử chặng 0, thứ tự -1, API chặng 1.5. | Danh sách theo chặng rồi thứ tự; đầu vào sai bị từ chối. |
| M02-B12 | API | Tạo mục tiêu/Unit trỏ Topic không tồn tại; thử page=-1, size=51, topicId=0. | Tham chiếu không tồn tại bị 404; tham số truy vấn không hợp lệ bị 400; không lưu dữ liệu dở. |

### C. File và tài liệu đọc/nghe/ảnh — 18 ca

Đường dẫn UI: **Quản lý học liệu → Tài liệu**.

| ID | Kênh | Thao tác | Kết quả mong đợi |
|---|---|---|---|
| M02-C01 | UI | Tạo R1, điền đoạn đọc/nguồn → lưu nháp → xem trước → xuất bản → F5. | Nội dung đọc đúng; trạng thái Đã xuất bản; mở lại được. |
| M02-C02 | UI | Tạo READING có ảnh minh họa và mô tả; lưu/xem trước; mở nháp rồi gỡ ảnh, lưu lại. | Ảnh hiện khi gắn, biến mất khỏi nháp khi gỡ; không làm hỏng file hoặc bản công bố cũ. |
| M02-C03 | UI | Tạo A1, upload WAV, nhập transcript và đoạn 1–5 giây → lưu → nghe/xem trước → xuất bản. | Upload thành công, phát được audio; chỉ phát/tua trong đoạn chọn, transcript không hiện trong phần trình bày nghe cho người học. |
| M02-C04 | UI/API | Lần lượt upload PNG, JPEG, WAV, MP3, OGG hợp lệ; API thử một PNG thật nhưng filename/MIME khai sai. | Định dạng thật được nhận diện; API không tin MIME/phần mở rộng do client khai. UI có thể lọc phần mở rộng ngay hộp chọn file. |
| M02-C05 | UI | Tạo I1 có mô tả và chú thích → lưu → xem trước → xuất bản. | Hiện ảnh, mô tả/chú thích đúng; mở lại được. |
| M02-C06 | UI/API | Upload file lớn hơn 5 MiB, ví dụ 5.242.881 byte. | Bị từ chối; API 413; không tạo asset/file thành công. |
| M02-C07 | API | Upload file rỗng; file text đổi tên `.png`; file có chữ ký ảnh/audio nhưng nội dung hỏng. | 400 với file không hợp lệ; không lưu file giả thành học liệu hợp lệ. |
| M02-C08 | API/AUTO | Upload tên `../../escape.png`; ảnh trên 20 triệu pixel; multipart có hai file hoặc trường thừa. | Bị từ chối; không ghi ra ngoài kho file hoặc nhận multipart sai hợp đồng. |
| M02-C09 | UI/API | AUDIO: thử start âm, end nhỏ hơn/bằng start, end vượt thời lượng file; thử để end trống hợp lệ. | Mốc sai bị chặn; end trống dùng đến cuối file. |
| M02-C10 | API | Tạo IMAGE/READING gắn Asset audio, hoặc AUDIO gắn Asset ảnh. | 400 vì không khớp loại tài liệu/file. |
| M02-C11 | UI | Lưu tài liệu UAT thiếu tên/đoạn đọc hoặc thiếu file/transcript theo loại → Xem trước. | Nháp đúng cấu trúc lưu được khi cho phép; preview liệt kê nội dung thiếu; không xuất bản được. |
| M02-C12 | UI | Tạo bản chỉnh sửa tài liệu đã công bố; đổi nội dung → lưu; chuyển Bản nháp/Bản đã xuất bản. | Nháp chứa nội dung mới; bản công bố vẫn nội dung cũ. |
| M02-C13 | UI/API | Xuất bản nháp chỉnh sửa C12; đọc Version ID cũ qua API. | Bản hiện tại đổi sang version mới; API version cũ còn nguyên nội dung/file. |
| M02-C14 | UI/API | Bỏ nháp chưa được tham chiếu: thử cả tài liệu chỉ có nháp và tài liệu đã có bản công bố. | Chỉ có nháp thì ẩn khỏi bank thường; đã công bố thì giữ bản công bố. DELETE thành công 204; không xóa lịch sử/file. |
| M02-C15 | UI | Ngừng cấp một tài liệu UAT đã công bố nhưng chưa có tham chiếu bảo vệ. | Trạng thái Ngừng cấp; không còn trong kho công bố để tái sử dụng mới; chủ sở hữu vẫn xem lịch sử được. |
| M02-C16 | UI/API | Tạo câu/bài tham chiếu tài liệu; thử bỏ nháp được bảo vệ hoặc ngừng cấp tài liệu đang được bài công bố dùng. | Bị chặn với 409/CONTENT_IN_USE; nội dung đang dùng vẫn nguyên vẹn. |
| M02-C17 | UI | Tìm tên tài liệu; lọc lần lượt loại, trạng thái, Của tôi/Kho đã xuất bản; chuyển trang. | Kết quả đúng tổ hợp bộ lọc, không lẫn nháp riêng của tác giả khác. |
| M02-C18 | API | Chủ sở hữu GET asset bình thường, với Range `bytes=0-15`, rồi `bytes=999999999-`; B/học viên thử file chỉ thuộc nháp riêng của A. | Lần lượt 200, 206 đúng 16 byte, 416; người không được cấp quyền nhận 404; response không lộ storageKey. |

### D. Ngân hàng câu hỏi — 16 ca

Đường dẫn UI: **Quản lý học liệu → Câu hỏi**.

| ID | Kênh | Thao tác | Kết quả mong đợi |
|---|---|---|---|
| M02-D01 | UI | Tạo Q1, đủ Topic/mục tiêu/độ khó/nguồn/hint/giải thích → lưu → xem trước → chọn thử → xuất bản. | Lưu và công bố đúng câu SINGLE_CHOICE; chọn thử được, không tự hiện đáp án hoặc điểm. |
| M02-D02 | UI/API | SINGLE_CHOICE: thử 1 lựa chọn, lựa chọn rỗng, chưa chọn đáp án; API thêm lựa chọn thứ 7 hoặc correctOptionId không tồn tại. | Không công bố; preview/API chỉ ra dữ liệu sai. UI không cho thêm quá 6 lựa chọn. |
| M02-D03 | UI | Tạo Q2 bằng các đoạn chữ và ô trống, đáp án `goes` → lưu → preview → nhập thử → xuất bản. | Ô trống nằm đúng vị trí, nhập được; không lộ đáp án/không chấm. |
| M02-D04 | UI/API | Một ô điền có nhiều đáp án, mỗi dòng một đáp án; bật/tắt phân biệt hoa thường/gộp khoảng trắng → lưu → đọc lại. | Danh sách và hai tùy chọn được lưu đúng. Việc chấm theo cấu hình thuộc M03, không kỳ vọng preview M02 chấm đúng/sai. |
| M02-D05 | UI/API | FILL_BLANK: thử không có ô, ô không có đáp án, hơn 5 ô, blankId trùng hoặc bộ đáp án không khớp ô. | Không công bố; UI chặn thêm ô thứ 6, API kiểm tra cấu trúc/đáp án sai. |
| M02-D06 | UI | Tạo Q3 gồm hai cột và hai cặp đúng → lưu → preview → ghép thử → xuất bản. | Hai cột/điều khiển ghép hiện đúng; tương tác được; không lộ cặp đáp án và không chấm. |
| M02-D07 | UI/API | MATCHING: thử hai cột lệch số mục, mục rỗng, thiếu cặp, hai mục trái cùng ghép một mục phải; API hơn 6 mục. | Không công bố; mỗi cột cần cùng 2–6 mục, mỗi mục được ghép đúng một lần. |
| M02-D08 | UI/API | Đổi Topic của câu từ T1 sang T2; thử cố gửi O1 vẫn thuộc T1 bằng API. | UI chỉ cho chọn mục tiêu cùng Topic; API từ chối cặp Topic/Objective sai. |
| M02-D09 | UI | Gắn phiên bản R1 vào nhiều câu; mở preview; thử câu không dùng tài liệu. | Câu có tài liệu hiển thị đúng bản đã chọn; câu text độc lập vẫn lưu/công bố được. |
| M02-D10 | UI/API | Lưu câu thiếu prompt/giải thích/đáp án nhưng đúng schema, FK → preview/publish. | Có thể lưu nháp theo điều kiện; chưa đủ nội dung thì preview báo lỗi và publish bị chặn. |
| M02-D11 | UI | Tìm prompt; lọc Topic, mục tiêu, dạng, độ khó, trạng thái, phạm vi; chuyển trang. | Danh sách khớp mọi bộ lọc; đổi Topic cập nhật danh sách mục tiêu phù hợp. |
| M02-D12 | UI/API | Mở chỉnh sửa câu đã công bố, đổi prompt/đáp án → lưu/công bố; đọc Version ID cũ. | Root/origin giữ nguyên, có version mới; phiên bản cũ bất biến. |
| M02-D13 | UI/API | B mở câu công bố của A → Sao chép thành câu của tôi. | Có root nháp mới thuộc B; originKey bằng câu nguồn; bản gốc không đổi. |
| M02-D14 | UI/API | Bỏ nháp câu UAT chưa được bài tham chiếu; thử lại khi nháp đang nằm trong bài nháp. | Trường hợp không bị bảo vệ bỏ được; trường hợp có tham chiếu bảo vệ bị chặn. |
| M02-D15 | UI/API | Ngừng cấp câu chưa được bài công bố dùng; lặp lại với câu đang dùng, kể cả bài giữ version cũ. | Câu tự do ngừng cấp được; câu đang dùng trả CONTENT_IN_USE, không lọt qua chỉ vì đã có version mới. |
| M02-D16 | API | Thử type `MULTIPLE_CHOICE`, ownerId do client khai, thiếu nullable bắt buộc hoặc sai kiểu dữ liệu. | 400; không tự đổi thành một trong ba dạng hoặc nhận owner giả. |

### E. Biên soạn bài học và thứ tự — 16 ca

Đường dẫn UI: **Quản lý học liệu → Bài học**.

| ID | Kênh | Thao tác | Kết quả mong đợi |
|---|---|---|---|
| M02-E01 | UI | Tạo L1: chọn U1/O1, tên/mô tả; thêm Q1/Q2/Q3 vào ba nhóm → lưu. | Lưu đúng Unit, mục tiêu và các phiên bản câu được chọn. |
| M02-E02 | UI | Thêm khối Hướng dẫn, khối Ví dụ và bản dịch; sửa nội dung, bỏ một khối rồi thêm lại; lưu/mở lại. | Giữ đúng các khối theo thứ tự đã nhập; preview thể hiện nội dung/bản dịch. UI hiện có thêm/bỏ khối, không có nút riêng để di chuyển khối. |
| M02-E03 | UI | Trong picker, tìm câu; thêm câu vào Có hướng dẫn/Tự làm/Biến thể ôn tập; thêm câu thứ hai cùng nhóm rồi ↑/↓ và bỏ một câu. | Thêm đúng version và nhóm; thứ tự trong nhóm đổi đúng; bỏ khỏi bài không xóa câu ngân hàng. |
| M02-E04 | UI | Xem trước L1 có tài liệu đọc/nghe/ảnh và ba dạng câu. | Hướng dẫn/câu/tài liệu hiện đúng; tương tác được, không hiện khóa đáp án/hint/giải thích/transcript trong phần trình bày cho người học. |
| M02-E05 | UI | Chỉ để một câu mỗi nhóm, đủ điều kiện nội dung → xuất bản L1. | Xuất bản được bài ba câu; 15 câu là quy mô seed, không phải điều kiện bắt buộc cho mọi bài. |
| M02-E06 | UI | Tạo các bản thử lần lượt thiếu tên, hướng dẫn, mục tiêu hoặc một trong ba nhóm → preview. | Báo phần thiếu; không xuất bản được. |
| M02-E07 | UI | Đưa một câu không có hint vào GUIDED → preview; bổ sung hint và thử lại. | Lần đầu báo thiếu hint; sau khi sửa/lưu/preview lại thì lỗi tương ứng hết. |
| M02-E08 | UI/API | Chọn mục tiêu/câu khác Topic của Unit; chọn câu có mục tiêu không nằm trong mục tiêu bài. | Không thể công bố cấu trúc sai; không tự đổi Topic/mục tiêu để nhận đầu vào sai. |
| M02-E09 | UI/API | Đưa cùng câu hai lần hoặc câu gốc và bản sao giữ cùng origin vào một bài. | UI ngăn chọn trùng khi có thể; preview/API từ chối DUPLICATE_ORIGIN, kể cả khác root/version. |
| M02-E10 | UI/API | Thử thêm nháp riêng của B hoặc phiên bản nội dung đã ngừng cấp vào bài của A. | Nháp B không có trong lựa chọn của A; gửi trực tiếp bị từ chối; không tạo bài công bố có phụ thuộc không hợp lệ. |
| M02-E11 | UI/API | Chuẩn bị R1 và ba câu còn ở nháp, A sở hữu; cho vào một bài hợp lệ → preview → công bố bài. | Bài, câu và tài liệu nháp liên quan được công bố cùng transaction; không cần công bố thủ công từng câu trước. |
| M02-E12 | UI/API | Bài ở E11 thiếu một nhóm hoặc một câu không hợp lệ → thử công bố. | Toàn bộ thao tác thất bại; không công bố dở câu/tài liệu và không có receipt thành công của publish. |
| M02-E13 | UI/API | Bài công bố v1 → tạo chỉnh sửa, đổi hướng dẫn/slot → lưu/công bố v2; đọc v1. | Bài có version mới; v1 còn nguyên hướng dẫn, mục tiêu, slot/version câu cũ. |
| M02-E14 | UI | Bỏ nháp bài UAT; sau đó kiểm tra ngân hàng câu và tài liệu. | Chỉ bỏ nháp bài; câu/tài liệu độc lập và bản bài đã công bố vẫn được giữ. |
| M02-E15 | UI | Ngừng cấp một bài UAT công bố → học viên tìm Unit/bài và GET introduction. | Bài không được cấp mới; học viên không mở introduction đó được; chủ sở hữu còn lịch sử. |
| M02-E16 | UI/API | Tạo hai bài cùng U1; lọc U1 → Sắp xếp bài trong Unit đã chọn → đổi vị trí → Lưu thứ tự bài → F5. API thử list thiếu/trùng ID hoặc revision cũ. | UI lưu thứ tự đầy đủ, học viên thấy thứ tự các bài công bố tương ứng; list thiếu/trùng bị 400, revision cũ bị 409. |

### F. Khám phá bài học của học viên — 7 ca

| ID | Kênh | Thao tác | Kết quả mong đợi |
|---|---|---|---|
| M02-F01 | UI | Đăng nhập học viên → Khám phá bài học → Tìm Unit `Bắt đầu`. | Có ba Unit demo; mỗi Unit mở ra hai bài chuẩn. Nội dung UAT/e2e khác có thể xuất hiện khi bỏ bộ lọc. |
| M02-F02 | UI | Tìm Unit bằng tên; lọc chủ đề; dùng từ khóa không tồn tại; chuyển trang nếu có. | Đúng tập kết quả; có trạng thái rỗng rõ ràng, không lỗi màn hình. |
| M02-F03 | UI | Mở một Unit → xem giới thiệu bài → quay về danh sách bài → quay về Unit. | Đúng tên/mô tả/mục tiêu/hướng dẫn; điều hướng trở lại được. |
| M02-F04 | UI/API | Trong Unit UAT, có một bài nháp và một bài công bố; xem bằng học viên. | Chỉ bài active published xuất hiện; Unit/Topic/Objective không có bài công bố phù hợp không được cấp như nội dung học. |
| M02-F05 | API | Học viên GET `/api/lessons/{id}` và xem JSON response, không chỉ nhìn màn hình. | Không chứa answerKey, correctOptionId, transcript, explanation hoặc ngân hàng QuestionVersion/MaterialVersion. |
| M02-F06 | API | Học viên gọi author API hoặc GET một file demo khi chưa có grant lượt làm. | Author API 403; file không được cấp quyền trả 404. Có introduction không đồng nghĩa được đọc mọi media/ngân hàng. |
| M02-F07 | UI | Xem giới thiệu và thử tìm chức năng bắt đầu/nộp/chấm điểm. | Chỉ có giới thiệu và thông báo chức năng làm bài đang hoàn thiện; không sinh điểm/tiến độ. Đây là phạm vi M03, không đánh lỗi M02 vì chưa có runner. |

### G. Đồng thời, mạng và toàn vẹn dữ liệu — 9 ca

| ID | Kênh | Thao tác | Kết quả mong đợi |
|---|---|---|---|
| M02-G01 | UI/API | Mở cùng nội dung ở hai tab. Tab 1 sửa/lưu; tab 2 dùng bản cũ sửa/lưu. Lặp với danh mục, tài liệu, câu và bài. | Tab cũ báo xung đột, API 409 REVISION_CONFLICT; không ghi đè tab 1; form tab 2 giữ nội dung để đối chiếu. |
| M02-G02 | UI/API | Preview một nháp rồi sửa/lưu chính nháp đó; gửi publish với token preview cũ. | UI yêu cầu preview lại; gửi token cũ bằng API nhận PREVIEW_STALE/409; nội dung chưa được xem trước không bị công bố. |
| M02-G03 | API/AUTO | Preview câu gắn Material draft rồi sửa Material; hoặc preview bài rồi sửa Question/Material draft liên quan → publish bằng token cũ. | 409 PREVIEW_STALE; xem trước mới rồi publish mới có thể thành công. |
| M02-G04 | API/AUTO | Gửi lại cùng requestId và payload của thao tác đã commit: create/update/upload/copy/publish/discard/order. | Replay đúng status/body cũ; có Idempotent-Replayed; không tạo thêm nội dung/file/version. Discard replay vẫn 204 không body. |
| M02-G05 | API | Dùng lại requestId của thao tác đã thành công nhưng sửa payload hoặc đổi thao tác. | 409 REQUEST_ID_REUSED; không nhận thay đổi mới. |
| M02-G06 | UI/AUTO | Ngắt phản hồi sau khi server commit, rồi bấm Thử lại thao tác. Dùng Playwright để mô phỏng chính xác; Offline của DevTools chỉ mô phỏng mất mạng trước/gần lúc gửi. | Giữ dữ liệu và requestId; retry không tạo bản sao; chưa nhận ACK thì không báo lưu thành công. |
| M02-G07 | API/AUTO | Bỏ CSRF header; thêm ownerId/trường lạ vào DTO; gửi số dạng chuỗi hoặc enum sai; ADMIN khóa/đổi quyền người đang thao tác rồi người đó gửi tiếp. | Request bị chặn tương ứng; không nhận giả chủ sở hữu/sai schema hoặc quyền đã bị thu hồi. |
| M02-G08 | AUTO | Gây lỗi DB tại cuối publish sau khi đã ghi phụ thuộc; gây rollback sau upload; mô phỏng file vật lý bị thiếu trong fixture riêng. | Publish rollback mọi phụ thuộc/receipt; rollback upload dọn file vừa ghi; file bị thiếu trả lỗi 503, không 200 rỗng. Dùng Testcontainers, không xóa file/đổi CHECK trên DB đang dùng. |
| M02-G09 | AUTO | Chạy đồng thời sửa phụ thuộc/publish, hoặc retire Material/publish Lesson, hoặc tạo/sửa cùng mã/revision. | Không ghi đè âm thầm, không có bài công bố trỏ phụ thuộc vừa ngừng cấp, không sinh bản trùng; một thao tác xung đột bị từ chối phù hợp. |

### H. Seed demo và lưu bền vững — 8 ca

| ID | Kênh | Thao tác | Kết quả mong đợi |
|---|---|---|---|
| M02-H01 | UI/API | Giáo viên demo chọn OWN; kiểm tra bộ chuẩn theo tên/seed manifest; đếm hết các trang hoặc dùng test seed. | Bộ chuẩn có 3 Topic/3 Unit/6 mục tiêu/6 bài/90 câu; tổng toàn DB có thể lớn hơn do UAT/e2e/nội dung riêng. |
| M02-H02 | UI/API | Mở từng bài chuẩn và đối chiếu slot/type với manifest. | Mỗi bài 5 GUIDED + 5 INDEPENDENT + 5 REVIEW_VARIANT, 15 câu khác origin; tổng 90 root và 30 câu mỗi dạng. |
| M02-H03 | UI/API | Mở 13 tài liệu, phát/xem 7 file; đối chiếu manifest/SHA-256 bằng verifier hoặc test. | 6 READING/6 AUDIO/1 IMAGE; 6 WAV/1 PNG; file thật khớp hash, nguồn và transcript hiện đúng cho tác giả. |
| M02-H04 | Hệ thống | Ghi ID/revision/version/slot, số asset/receipt; recreate riêng server, đăng nhập lại rồi đối chiếu. | Không thêm bản sao, không reset nội dung; file vẫn đọc được sau recreate; migration không đổi. |
| M02-H05 | AUTO | Trong fixture riêng, sửa một câu chuẩn, mở nháp tài liệu, retire bài, tạo nội dung riêng; seed lại. | Giữ nguyên tất cả thay đổi/trạng thái/nội dung riêng; không tự phục hồi nội dung đã ngừng cấp. |
| M02-H06 | AUTO | Fixture có mẫu cũ 3 câu/1 bài chưa sửa; chạy seed mới. | Đủ bộ chuẩn 6/90; ba Material/Question giữ ID/version, Question giữ origin; bài Linh giữ root và có version mới 15 câu, bản 3 câu cũ vẫn nguyên. |
| M02-H07 | AUTO | Fixture mẫu cũ đã tùy chỉnh; chạy seed mới. | Mẫu tùy chỉnh được giữ; tạo bộ chuẩn riêng; tổng root có thể vượt 6/90. |
| M02-H08 | AUTO | Chạy hai lượt seed đồng thời; hoặc gây lỗi bước tạo bài rồi chạy lại. | Không trùng root/file; bài lỗi rollback nguyên tử; lần sau tiếp tục từ checkpoint và hoàn tất bộ chuẩn. |

### I. Giao diện và phản hồi thao tác — 4 ca

| ID | Kênh | Thao tác | Kết quả mong đợi |
|---|---|---|---|
| M02-I01 | UI | Desktop 1440×1000: mở bốn khu tác giả và learner catalog; dùng các bộ lọc, form, preview. | Nhãn/nút/nội dung dễ đọc, chọn được đủ điều kiện, không chồng nút hoặc mất điều khiển. |
| M02-I02 | UI | Mobile 390×844: tạo/sửa ba dạng câu, tài liệu, bài; đọc giới thiệu và dùng điều hướng. | Không tràn ngang toàn trang; nhập/chọn/lưu/preview và quay lại được. |
| M02-I03 | UI | Làm chậm mạng và chặn một GET danh sách; thử tải lại. | Có trạng thái tải/lỗi và cách thử lại; không báo thành công giả hoặc coi lỗi tải là đã xóa dữ liệu. |
| M02-I04 | UI | Nhập thay đổi chưa lưu; kiểm tra nút Xem trước/Xuất bản. Khi xung đột, thử hủy rồi xác nhận tải lại nội dung mới nhất. | Không công bố dữ liệu chưa lưu; giữ nội dung khi lỗi; tải lại có cảnh báo/xác nhận theo màn hình và lấy bản server đúng. |

## 4. Một luồng thử nhanh từ đầu đến cuối

1. **A01 → B01 → B05 → B07:** đăng nhập giáo viên, tạo Topic → mục tiêu → Unit UAT.
2. **C01/C03/C05:** tạo đoạn đọc/audio/ảnh; thử lưu, preview và xuất bản.
3. **D01/D03/D06:** tạo ba dạng câu cùng Topic/mục tiêu, gắn tài liệu; xem trước và xuất bản.
4. **E01–E05:** tạo bài, thêm hướng dẫn/ví dụ và ba câu chia ba nhóm; xuất bản.
5. **F03/F05:** học viên thấy bài/giới thiệu; JSON không lộ đáp án.
6. **C12/D12/E13:** tạo phiên bản chỉnh sửa; đối chiếu bản cũ còn nguyên.
7. **G01:** hai tab sửa cùng nội dung để thử chống ghi đè.
8. **E15 → D15/C16:** retire bài UAT, kiểm tra khả năng ngừng cấp phụ thuộc sau khi hết tham chiếu bảo vệ.

Các ca lỗi có thể thay đổi fixture đang dùng. Nếu cần kiểm tra một ca độc lập, tạo thêm nội dung UAT mới hoặc ghi lại rõ trạng thái trước khi chạy. Bỏ nháp/ngừng cấp không phải xóa vĩnh viễn.

## 5. Cách thử bằng API

Base URL: `http://localhost:5173` qua proxy, hoặc `http://localhost:8080` trực tiếp; dùng thống nhất một base để cookie phiên khớp. Không cần Bearer token.

1. GET `/api/auth/csrf`; giữ cookie và lấy `token`, `headerName` từ response.
2. POST `/api/auth/login`, JSON `{ "email": "teacher@english.local", "password": "Teacher@12345" }`, kèm header CSRF ở bước 1.
3. Sau đăng nhập lấy lại CSRF để dùng token hiện tại; giữ cookie phiên. Mọi request ghi tiếp theo gửi CSRF; GET thường không cần header này.
4. Dùng requestId UUID mới cho thao tác mới. Ca retry dùng lại chính requestId/payload cũ. Khi update không tự đoán revision: lấy lại root/draft hoặc dùng preview response.

| Chức năng | Endpoint |
|---|---|
| Danh sách/tạo danh mục | GET/POST `/api/author/topics`, `/api/author/objectives`, `/api/author/units` |
| Sửa danh mục | PUT `/api/author/{topics|objectives|units}/{id}` |
| Upload | POST `/api/assets`, multipart `file` và `requestId` |
| Đọc media/Range | GET `/api/assets/{id}`, có thể thêm header `Range` |
| Danh sách/tạo học liệu | GET/POST `/api/author/{materials|questions|lessons}` |
| Chi tiết/lịch sử | GET `/api/author/{resource}/{id}` hoặc `/{id}/versions/{versionId}` |
| Mở nháp chỉnh sửa | POST `/api/author/{resource}/{id}/draft` |
| Lưu nháp | PUT `/api/author/{resource}/{id}/draft` |
| Bỏ nháp | DELETE `/api/author/{resource}/{id}/draft?requestId=...&baseRevision=...&draftVersionId=...` |
| Preview/publish/retire | GET `/{id}/preview`, POST `/{id}/publish`, POST `/{id}/retire` dưới author resource |
| Sao chép câu | POST `/api/author/questions/{id}/copy` |
| Chi tiết Unit/thứ tự bài | GET `/api/author/units/{id}`; PUT `/api/author/units/{id}/lesson-order` |
| Danh mục học viên | GET `/api/topics`, `/api/objectives`, `/api/units`, `/api/units/{id}`, `/api/lessons/{id}` |

`{resource}` ở bảng là `materials`, `questions` hoặc `lessons`, không gửi dấu ngoặc hoặc dấu `|` trong URL thật. Ví dụ đọc một câu: `/api/author/questions/123`.

Revision dùng đúng đối tượng: update danh mục dùng `revision`; update draft dùng `draft.revision`; start/retire dùng `rootRevision`; sắp xếp bài dùng `unit.revision`; publish dùng `baseRevision`, `draftVersionId`, `previewToken` từ preview mới nhất.

Payload publish mẫu (thay giá trị từ response thật):

```json
{
  "requestId": "4b14c181-1138-4dfc-8984-e904936a8227",
  "baseRevision": 0,
  "draftVersionId": 123,
  "previewToken": "TOKEN_TU_PREVIEW"
}
```

Import [OpenAPI](../source/common/openapi-m02-m03.json) vào công cụ API để xem DTO chính xác; dùng phần M02, không coi endpoint M03 trong hợp đồng là đã triển khai. [Ví dụ JSON](../source/common/examples-m02-m03.json) là fixture minh họa, phải thay FK/Version ID bằng dữ liệu thật của lượt thử. [Quy tắc API](../source/common/M02_M03_API.md) giải thích quyền/revision/receipt.

## 6. Các ca cần tự động hóa và bằng chứng đã có

Các bài test thực tế có nhiều assertion, không tương ứng 1:1 với 98 ca thủ công ở trên.

| Bộ test | Nội dung liên quan |
|---|---|
| [CatalogApiTest](../source/server/src/test/java/vn/edu/ptit/english/CatalogApiTest.java) | Danh mục, quyền, CSRF, input, receipt, revision, cạnh tranh ghi |
| [MaterialApiTest](../source/server/src/test/java/vn/edu/ptit/english/MaterialApiTest.java) | Định dạng/file/Range, nháp/phiên bản, quyền, rollback và bảo vệ tham chiếu |
| [AuthoringApiTest](../source/server/src/test/java/vn/edu/ptit/english/AuthoringApiTest.java) | Ba dạng câu, bài, origin, công bố phụ thuộc, token cũ, rollback/race, learner catalog |
| [CurriculumSeedTest](../source/server/src/test/java/vn/edu/ptit/english/CurriculumSeedTest.java) | Bộ chuẩn, seed lại, nâng mẫu cũ, giữ tùy chỉnh, lỗi giữa chừng và seed đồng thời |
| `source/client/e2e/module-two-*.spec.ts` | Luồng UI catalog/material/authoring/seed, mất ACK và desktop/mobile |

Kết quả lượt nghiệm thu trước: **62 backend PASS** (gồm 40 test M02 và 22 test nền M01/admin), **26 Playwright PASS** (21 M02, 5 M01). Đây là kết quả đã chạy trước khi lập checklist, không phải khẳng định đã chạy lại toàn bộ 98 ca thủ công trong lượt này. Xem [báo cáo nghiệm thu](../statics/results/module-2-seed-validation.md).

Chạy verifier tại root:

```powershell
python scripts/verify-demo-curriculum.py
```

Chạy backend trên DB Testcontainers riêng, Docker Desktop đang chạy:

```powershell
docker build --target test -t english-learning-project-test ./source/server
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-project-test mvn -B -ntp test
```

Chạy giao diện tại `source/client`, Docker Desktop đang chạy:

```powershell
npm run test:e2e
```

Cần Chromium của Playwright hoặc `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`. Từ 18/10, lệnh mặc định tự tạo/chạy/dừng stack Docker `english-learning-e2e` tại cổng 5174/8081, dùng DB/file volume riêng. Test không ghi dữ liệu vào demo 5173. Chỉ khi đặt `E2E_BASE_URL` mới sử dụng app có sẵn tại URL đó. Các ca cố ý gây lỗi DB/file chạy bằng bộ backend riêng; không cần reset hoặc xóa volume dữ liệu đang dùng.
