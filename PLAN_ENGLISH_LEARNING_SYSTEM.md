# Kế hoạch module — ứng dụng học và ôn tập tiếng Anh có gamification và Giải đấu tuần

Cập nhật: 22/10/2026. Đây là kế hoạch cho hướng đề tài mới; trạng thái triển khai tra cứu trong tiến độ và biên bản từng module. Phạm vi hiện tại gồm 7 module M01–M07, giữ Giải đấu tuần đo độ chăm học. Danh sách module được đánh lại số theo phạm vi hiện tại. Hạng thử thách solo được chuyển riêng sang M08 mở rộng, chờ người phụ trách xác nhận trước khi triển khai. Quy định unit, độ khó, seed tại M02.1/M02.2 và cấp câu tại M03.1 giữ theo trao đổi. Các đề xuất XP/League ban đầu được chốt bởi policy M05 `m05-p0-v1`; các đề xuất mở rộng ngoài policy chưa được coi là đã duyệt.

Tên đề xuất: **Ứng dụng học và ôn tập tiếng Anh tích hợp gamification theo mô hình client–server.**

Plan cho app tiếng Anh mới tại english-learning-project/. M01–M04 và chức năng P0 của M05 đã triển khai; xem tiến độ/biên bản từng module cho phạm vi nghiệm thu. M06–M07 cùng những quyết định mở rộng ghi “đề xuất” chưa được coi là hoàn thành. Plan IELTS cũ được lưu để đối chiếu trong docs/archive/.

Tài liệu này được sao chép từ plan ở thư mục gốc LTM; mã nguồn app mới nằm cùng thư mục dự án. Các file môn học được giữ nguyên ở INSTRUCTION.md, TOPICS.md và SUBMISSION.md.

### Đặc tả M02–M03 đã chốt để giao việc — 16/10/2026

- [Phân công, database, API và nghiệm thu](docs/M02_M03_HANDOFF.md).
- [Quy tắc API](source/common/M02_M03_API.md), [OpenAPI 1.0.0](source/common/openapi-m02-m03.json), [ví dụ request/response](source/common/examples-m02-m03.json).
- DDL dự kiến: [V3 của M02](docs/database/planned/V3__learning_content.sql), [V4 của M03](docs/database/planned/V4__learning_attempts.sql).
- Đây là hợp đồng triển khai; M02 có V3/API học liệu và seed, M03 có V4/runner cùng kết quả bất biến, M04 có V5 để tổng hợp kết quả và chọn bài ôn. Xem tiến độ và biên bản của từng module để biết phạm vi đã kiểm tra.

## 1. Phạm vi và mục tiêu

### 1.1. Những nội dung đã trao đổi

- Học tiếng Anh thông thường, trước mắt tập trung từ vựng, ngữ pháp, đọc ngắn và nghe ngắn có đáp án xác định.
- Có học bài mới, luyện vận dụng, ôn theo lịch, ôn lỗi sai và kiểm tra lại; XP là phần thưởng đi kèm hoạt động.
- Có **Giải đấu tuần** dùng XP tuần để thi đua chăm học, tham khảo league Duolingo; có XP, cấp độ hoạt động, huy hiệu và chuỗi ngày học.
- **Hạng thử thách solo để sau, chờ xác nhận**. Giữ thiết kế và bộ danh hiệu đã trao đổi tại M08 để có thể phát triển tiếp; chưa đưa vào phạm vi triển khai hiện tại.
- Server quản lý tài khoản, tài nguyên, câu hỏi và các client online/offline. Có quản trị tài khoản, gồm khóa/xóa.
- Thể hiện giao tiếp socket, phục vụ nhiều client, timeout, mất mạng, gửi trùng và khôi phục dữ liệu.
- Năm dạng tương tác: chọn một, chọn nhiều, điền từ/cụm từ, ghép cặp, sắp xếp từ. Ưu tiên hoàn thiện chọn một, điền từ và ghép cặp trước.
- Bài học được trộn câu văn bản, câu có đoạn đọc và câu có audio. Một tài liệu có thể dùng chung cho nhiều câu.
- Tách riêng độ khó bài học, cấp độ hoạt động từ XP và bậc Giải đấu tuần. Các bậc hoạt động/thi đua không là chứng nhận trình độ CEFR/IELTS.

### 1.2. Phạm vi demo đề xuất

- Một ứng dụng web, một backend, một MySQL, một kho file; dùng lại nền tảng ORM/Docker đã có.
- Vai trò: người học, người biên soạn, quản trị viên. Ánh xạ quyền từ STUDENT/TEACHER hiện tại và bổ sung ADMIN bằng migration khi triển khai; đăng ký công khai chỉ tạo người học.
- Nội dung ban đầu theo vài chủ đề đời sống, có tham khảo học liệu A1–A2; chưa xây chương trình đầy đủ A1–C2.
- Giải đấu tuần có nhóm nhỏ trong cùng bậc. Demo hiện tại chưa yêu cầu Hạng thử thách solo; điều kiện mở rộng được lưu tại M08.
- AI hỗ trợ soạn nháp là tùy chọn, không là điều kiện để luồng chính chạy. Không đưa chấm bài luận, chấm phát âm hoặc suy band IELTS vào MVP.

## 2. Kiến trúc và phân chia công việc

Giữ stack đã thống nhất: React + TypeScript + Vite; Java 17 + Spring Boot 3.5; Spring Data JPA/Hibernate; MySQL 8.4; Flyway; Docker Compose. Giữ cổng 5173/8080 và cấu trúc controller → service interface → service.impl → repository interface/implementation → JPA. Không chuyển về JDBC tự viết hoặc tự đổi stack.

Đề xuất bổ sung Spring WebSocket cho một kênh sự kiện JSON có phiên bản. HTTP đảm nhiệm API nghiệp vụ và truyền file; WebSocket đảm nhiệm thông báo, presence, đăng ký theo dõi bảng Giải đấu tuần. Hai kênh dùng chung service, kiểm tra quyền và trạng thái server. Giao thức ứng dụng tự thiết kế trên WebSocket không đồng nghĩa tự viết TCP/WebSocket từ đầu.

~~~mermaid
flowchart LR
    C["Các trình duyệt React"] <-->|"HTTP API / file"| S["Spring Boot"]
    C <-->|"WebSocket / heartbeat / sự kiện"| S
    S --> D[("MySQL")]
    S --> F["Kho file audio / ảnh"]
    C --> L["IndexedDB: nháp tại thiết bị"]
~~~

| Công việc | Client | Server |
|---|---|---|
| Học/luyện | Dựng câu hỏi, nhận đáp án, hiển thị hướng dẫn và phản hồi | Cấp đúng nội dung/phiên bản và dữ liệu phản hồi được phép |
| Tính toán cục bộ | Đếm câu, hiển thị kết quả luyện tạm, xử lý gợi ý và nháp | Kiểm tra tính hợp lệ; xác nhận kết quả được công nhận |
| Bài làm | Lưu nháp tại máy, gửi snapshot đáp án, thử lại cùng yêu cầu | Lưu lần làm/bài nộp; xác nhận đã nhận; kiểm soát trạng thái |
| Ôn tập | Hiển thị hàng đợi, trình diễn bài luyện | Lưu lịch sử và lịch ôn dùng chung giữa các thiết bị |
| Thi đua | Hiển thị Giải đấu tuần và thông báo | Đối chiếu kết quả học tập, xác nhận XP; chốt tuần đúng phạm vi |
| Kết nối | Heartbeat, reconnect, tải lại snapshot | Quản lý phiên/kết nối, phân quyền sự kiện, phát hiện timeout |

**Điểm cần thống nhất khi bảo vệ:** với kết quả học tập dùng để cộng XP/xếp Giải đấu tuần, server cần xác nhận kết quả từ đáp án đã nhận, không chỉ tin số điểm client tự khai. Đây là xử lý nhẹ cho câu hỏi có đáp án xác định. Nếu giảng viên yêu cầu toàn bộ việc chấm chỉ nằm ở client, cần chốt lại cách xác nhận XP trước khi triển khai. Không trình bày giải pháp này như chống được mọi hành vi gian lận trên web.

Các hướng dẫn môn học cho phép HTTP/WebSocket và định dạng message phù hợp; không thấy yêu cầu chung bắt buộc protocol nhị phân. Nếu giảng viên yêu cầu TCP socket thuần hoặc binary riêng thì cần chốt lại thiết kế kết nối, không tự đổi client web sang desktop.

## 3. Tổng hợp 7 module chính và phần mở rộng chờ xác nhận

P0: luồng demo bắt buộc. P1: hoàn thiện sau khi P0 chạy. M01–M07 là phạm vi hiện tại; M08 là phần mở rộng lưu thiết kế, chỉ bắt đầu khi có xác nhận thêm. Mã module trong plan này thuộc đề tài mới, không dùng lẫn với số module IELTS cũ.

| Module | Nội dung | Phụ thuộc chính |
|---|---|---|
| M01. Nền tảng, tài khoản và quyền | ORM/Docker, đăng nhập, vai trò, quản trị tài khoản | Nền hiện có |
| M02. Học liệu, câu hỏi và seed | Chủ đề, mục tiêu học, tài liệu, câu hỏi nhiều dạng, bài học có phiên bản | M01 |
| M03. Học bài, luyện tập và bài nộp | Trình làm bài dùng chung cho học/luyện/ôn, nháp, nộp, phản hồi | M02; hợp đồng lỗi M06 |
| M04. Ôn tập cá nhân | Ôn đến hạn, lỗi sai, tiến độ theo từng nội dung, gợi ý mức luyện | M03 |
| M05. Gamification và Giải đấu tuần | XP/cấp độ/huy hiệu/streak; nhóm thi đua theo XP tuần | M02–M04 |
| M06. Kết nối và phục hồi lỗi | Socket, online/offline, reconnect, đối soát, chống gửi trùng | M01; xây nền sớm cùng M03 |
| M07. Hồ sơ, quản trị và thống kê | Tiến độ, lịch sử, báo cáo học tập/Giải đấu tuần, dashboard kết nối | M03–M06 |

Phần mở rộng: **M08. Hạng thử thách solo — để sau, chờ xác nhận**; không là điều kiện hoàn thành 7 module chính.

### M01. Nền tảng, tài khoản và quyền — P0

**Trạng thái 16/10/2026:** đã triển khai trong dự án mới. Xem [hướng dẫn chạy](README.md) và [kiểm tra M01](statics/results/module-1-validation.md): 22 test backend, 5 test giao diện và kiểm tra giữ dữ liệu qua recreate Docker. M02 đang được triển khai từng bước theo tiến độ bên dưới.

- Tận dụng tài khoản, BCrypt, cookie session HttpOnly, CSRF, DTO và kiểm tra quyền đã có. Không mặc định các tính năng mới đã tồn tại.
- Bổ sung quyền quản trị và biên soạn bằng quy trình cấp quyền của admin; người học không tự đổi vai trò.
- Quản trị tìm/xem tài khoản, khóa/mở khóa; hành động “xóa” MVP đề xuất là xóa logic, thu hồi phiên và ẩn hồ sơ. Giữ bản ghi lịch sử học tập/XP/Giải đấu tuần; xóa vật lý là quy trình khác cần đặc tả riêng.
- Khi khóa/xóa: chặn HTTP mới, đóng WebSocket đang hoạt động, kiểm tra lại quyền với request đang chạy trước khi ghi dữ liệu; không chỉ ẩn nút.
- Giữ dữ liệu lớp học cũ; lớp học không là điều kiện bắt buộc để học/luyện trong hướng mới.

**Hoàn thành khi:** hai tài khoản chỉ xem/sửa dữ liệu được phép; tài khoản bị khóa không tiếp tục nộp/cộng XP bằng session hoặc socket cũ. Docker mở lại giữ tài khoản và dữ liệu.

### M02. Học liệu, câu hỏi và seed — P0, hai dạng mở rộng P1

**Tiến độ 17/10/2026:** đã hoàn thành bước 1–4: Flyway V3, danh mục, kho file/tài liệu, ngân hàng câu ba dạng và bài học với nháp/xem trước/xuất bản/ngừng cấp, copy giữ origin, phiên bản bất biến, sắp xếp và receipt/revision. Learner catalog và PublishedContentReader đã có; seed 3 Unit/6 bài/90 câu (5/5/5 mỗi bài), 6 mục tiêu, 13 tài liệu và 7 file thật đã hoàn thành. Có manifest, nguồn và kiểm tra seed chạy lại/nâng dữ liệu cũ. Lượt làm/chấm bài thuộc M03. Xem [tiến độ M02](docs/M02_PROGRESS.md).

- Quản lý Topic và LearningObjective: ví dụ “sinh hoạt hằng ngày”, mục tiêu là một từ/cụm hoặc cấu trúc cụ thể như “ngôi thứ ba hiện tại đơn”. Mỗi câu MVP gắn một mục tiêu chính để xác định nội dung cần ôn.
- Tổ chức lộ trình → unit theo chủ đề/mục tiêu và chặng nội dung → bài học nhỏ → câu hỏi. Mỗi unit có thứ tự trong lộ trình; cùng một chủ đề có thể có unit ở chặng sau với yêu cầu cao hơn. Tham khảo chi tiết M02.1.
- Soạn bài ngắn gồm từ/cấu trúc, giải thích, ví dụ, câu có hướng dẫn, câu tự làm và câu vận dụng mới. Nội dung và đáp án được duyệt trước công bố. Quy mô demo tại M02.2.
- Ngân hàng câu hỏi có type, chủ đề, mục tiêu chính, độ khó nội bộ EASY/MEDIUM/HARD, yêu cầu, nội dung, đáp án, giải thích và nguồn. Người biên soạn gắn độ khó theo ngữ liệu, cấu trúc, bước suy luận và phương án gây nhầm; mức gợi ý/hỗ trợ lưu riêng.
- Ba dạng P0: SINGLE_CHOICE, FILL_BLANK, MATCHING. Hai dạng P1: MULTIPLE_CHOICE, WORD_ORDER. Đọc/nghe là tài liệu đi kèm, không tạo bản sao kiểu câu hỏi cho mỗi kỹ năng.
- Material lưu đoạn đọc hoặc tham chiếu audio/ảnh; transcript, đáp án và giải thích có chính sách mở riêng.
- Audio/ảnh thật nằm trong kho file/volume asset_data; Asset metadata ở MySQL. M02 bước 2 đã bổ sung API upload/đọc file có phân quyền và kiểm tra loại/kích thước; giới hạn P0 chốt 5 MiB/file, PNG/JPEG và MP3/WAV/OGG.
- Bài/câu đã công bố có phiên bản bất biến. Sửa tạo phiên bản mới; bài đang làm giữ đúng phiên bản được cấp.
- Có form biên soạn, xem trước, lưu nháp, xuất bản/ngừng cấp mới; seed có mã định danh để chạy lại không nhân bản.

#### M02.1. Chủ đề, unit và cách tăng độ khó

**Cấu trúc thống nhất:** lộ trình học → unit theo chủ đề/mục tiêu → bài học nhỏ → câu hỏi. Topic là nhãn nội dung, Unit là vị trí học theo lộ trình; không dùng tên chủ đề để suy độ khó.

- Màn “Học bài” gợi ý bài tiếp theo trong lộ trình do người biên soạn sắp. Người học có mục riêng để chọn chủ đề/mức khó khi luyện và xem hàng đợi ôn.
- Một chủ đề có thể xuất hiện ở nhiều chặng: ví dụ “Ăn uống” ban đầu học tên món và gọi món; chặng sau dùng hội thoại nhiều thông tin hơn. Các unit cùng chặng có thể ngang độ khó, không bắt buộc mỗi chủ đề sau khó hơn mọi chủ đề trước.
- Trong bài/unit, tăng thử thách theo các bước bên dưới. Đây là các bước hoạt động, không bắt buộc tách thành bốn bài học riêng.
- Tách các dữ liệu: chặng/thứ tự của Unit, difficulty của câu hỏi, mức hỗ trợ và rank/XP của người học. Người học cùng bậc rank vẫn có thể học nội dung ở mức khác nhau.

| Bước hoạt động | Yêu cầu và mức hỗ trợ | Ví dụ chủ đề “Sinh hoạt hằng ngày” |
|---|---|---|
| Nhận biết | Giới thiệu từ/cấu trúc, có ví dụ và gợi ý | Chọn nghĩa của “wake up” |
| Vận dụng | Dùng kiến thức vừa học trong câu mới, giảm gợi ý | Chọn dạng hiện tại đơn: “She ___ to school every day.” |
| Tổng hợp | Kết hợp nội dung đã được học trong đoạn đọc/audio ngắn | Đọc/nghe lịch sinh hoạt rồi trả lời câu hỏi |
| Ôn lại | Tự làm câu biến thể sau một khoảng thời gian | Đổi nhân vật, hoạt động hoặc ngữ cảnh nhưng kiểm tra cùng mục tiêu |

Người biên soạn gắn EASY/MEDIUM/HARD dựa trên từ/cấu trúc cần biết, số thông tin phải kết hợp, ngữ cảnh và độ gây nhầm của lựa chọn. Tăng độ dài hoặc giảm thời gian đơn thuần không đủ làm căn cứ gắn nhãn khó. Chọn một đáp án vẫn có thể là câu dễ hoặc khó; audio cũng có thể có câu rất cơ bản.

Quy mô seed chỉ bao phủ chặng ban đầu; các chặng cao hơn cần học liệu, mục tiêu và câu biến thể được biên soạn/duyệt bổ sung. Kết quả luyện theo mục tiêu dùng để gợi ý mức tiếp theo; không tự cấp trình độ CEFR/IELTS. Bậc Giải đấu tuần không quyết định Unit phải học. Việc gợi ý bài học dựa trên tiến độ/mục tiêu đã ghi nhận.

#### M02.2. Quy định số bài và số câu của bản demo

Đơn vị đếm là **một chủ đề ở chặng nội dung ban đầu**, tổ chức thành một Unit. Đây là quy mô đã thống nhất cho demo, không giới hạn mỗi chủ đề mãi mãi chỉ có hai bài.

| Đơn vị | Số lượng | Ý nghĩa |
|---|---:|---|
| Một chủ đề/Unit ban đầu | 2 bài học nhỏ | Các bài có mục tiêu cụ thể và thứ tự học |
| Ngân hàng cho mỗi bài | 15 câu khác nhau | Chia thành ba nhóm bên dưới |
| Nhóm GUIDED | 5 câu/bài | Luyện có hướng dẫn, được dùng gợi ý |
| Nhóm INDEPENDENT | 5 câu/bài | Tự làm câu mới; giữ kết quả trước khi mở gợi ý/đáp án |
| Nhóm REVIEW_VARIANT | 5 câu/bài | Biến thể cho lượt ôn/kiểm tra vận dụng sau đó |
| Lượt học đầu của một bài | 10 câu | 5 GUIDED + 5 INDEPENDENT, ngoài phần đọc hướng dẫn/ví dụ |
| Ngân hàng một chủ đề | 30 câu | 2 bài × 15 câu |
| Ngân hàng học tập của ba chủ đề demo | 90 câu | 3 chủ đề × 2 bài × 15 câu |

Quy mô trên áp dụng học bài/luyện/ôn; nhóm REVIEW_VARIANT dành cho ôn tập.

Ví dụ một Unit “Sinh hoạt hằng ngày”: bài 1 tập trung từ/cụm và mẫu câu ngắn; bài 2 vận dụng nội dung đã học trong câu mới, đoạn đọc hoặc audio ngắn. Cả hai bài vẫn có ba nhóm 5/5/5; các bước tăng thử thách được phân bố trong và giữa hai bài, không tăng số câu của seed chỉ vì thêm một bước hướng dẫn.

- Năm câu REVIEW_VARIANT được chuẩn bị sẵn trong ngân hàng, không bắt làm hết ngay sau mười câu của lượt học đầu. Lượt ôn lấy câu theo mục tiêu đến hạn/còn sai; một lượt có thể chỉ dùng một phần trong nhóm này.
- Một bản ghi câu hỏi là một đơn vị đếm: một câu ghép ba cặp tính một câu; một câu có nhiều ô trống vẫn tính một câu. Một audio/đoạn đọc dùng cho năm câu thì tính năm câu hỏi, một tài liệu.
- Thẻ giới thiệu từ, ví dụ minh họa, màn giải thích và thao tác chữa lại một câu không được cộng thành câu hỏi mới.
- Đảo vị trí lựa chọn hoặc tạo version sửa lỗi không tự tạo “câu biến thể mới” để chứng minh người học đã vận dụng. Biến thể cần thay tình huống/ngữ cảnh phù hợp và được duyệt đáp án.
- Nếu cần thêm lượt luyện, người biên soạn bổ sung ngân hàng. Khi chưa đủ câu mới theo mục tiêu, có thể cho ôn câu cũ nhưng phải ghi nhận là làm lại; không báo đó là bộ kiểm tra mới.
- Phần hướng dẫn, nghe/đọc và các dạng P0 được phân bố phù hợp giữa hai bài. Không yêu cầu cả 15 câu của mọi bài đều có audio hoặc đủ tất cả dạng.

#### M02.3. Quy trình biên soạn Unit, bài học và câu hỏi

**Cách hiểu thống nhất:** giáo viên/người biên soạn xây bài học bên trong Unit. “Biên soạn” là quá trình tạo nội dung hướng dẫn, thêm câu hỏi, kiểm tra và xuất bản bài. Tất cả người học dùng chung ngân hàng học liệu đã xuất bản; tiến độ, lịch sử làm bài và bộ câu ôn được chọn riêng cho từng người.

Phần dưới mô tả toàn bộ luồng M02. Danh mục, tài liệu và câu hỏi/bài học đã có ở bước 1–3; bộ seed đầy đủ đã hoàn thành ở bước 4. Luồng giao diện chính:

    Quản lý học liệu → chọn/tạo Unit → chọn/thêm bài học
                    → soạn hướng dẫn và câu hỏi → xem trước → xuất bản

Ngân hàng câu hỏi và thư viện tài liệu là các màn hình hỗ trợ tìm, quản lý và chọn lại nội dung. Khi giáo viên tạo câu hoặc tài liệu ngay trong bài, hệ thống lưu vào ngân hàng/thư viện tương ứng.

Ví dụ xuyên suốt:

    Chủ đề: Sinh hoạt hằng ngày
    └── Unit: Thói quen hằng ngày — cơ bản
        ├── Bài 1: Từ vựng về hoạt động thường ngày
        └── Bài 2: Miêu tả một ngày bằng hiện tại đơn

##### Bước 1. Tạo hoặc chọn Unit

Tại “Quản lý học liệu”, người biên soạn xem danh sách Unit, tìm theo tên/chủ đề, mở Unit có sẵn hoặc bấm “Thêm Unit”.

| Thông tin nhập | Ý nghĩa / ví dụ |
|---|---|
| Tên Unit | “Thói quen hằng ngày — cơ bản” |
| Chủ đề | Chọn Topic “Sinh hoạt hằng ngày”; dùng danh mục chung |
| Mô tả | Nội dung và mục tiêu chung của nhóm bài |
| Chặng và thứ tự | Vị trí Unit trong lộ trình do người biên soạn sắp |
| Mục tiêu học liên quan | Tự tổng hợp từ LearningObjective của các bài trong Unit; chọn mục tiêu ở từng bài, không có bảng UnitObjective riêng |

Topic là nhãn nội dung; Unit tổ chức nội dung vào một vị trí trong lộ trình. Một chủ đề có thể xuất hiện ở nhiều Unit/chặng khác nhau theo M02.1. Tạo Unit xong mở màn chi tiết để thêm bài; Unit chưa có bài đã xuất bản chưa xuất hiện như một Unit có thể học.

##### Bước 2. Thêm bài học vào Unit

Trong chi tiết Unit, bấm “Thêm bài học”. Biểu mẫu gồm tên bài, mô tả/mục tiêu, thứ tự trong Unit, các LearningObjective liên quan và phần nội dung hướng dẫn.

Phần hướng dẫn cho phép nhập từ/cụm, cấu trúc, giải thích và ví dụ. Ví dụ bài 2 giới thiệu quy tắc thêm s/es, cách dùng does/doesn't và một vài câu mẫu. Giáo viên chọn hoặc thêm mục tiêu cụ thể như “hiện tại đơn — ngôi thứ ba”; các câu hỏi về nội dung này dùng lại mục tiêu đó.

Bài mới được lưu ở trạng thái nháp. Giáo viên có thể lưu bài chưa hoàn thiện rồi quay lại sửa; chỉ bản đã xuất bản mới được cấp cho người học. Nội dung giới thiệu/ví dụ là phần hướng dẫn, không tính thêm vào số câu hỏi.

##### Bước 3. Gắn đoạn đọc hoặc audio khi cần

Trong bài hoặc khi tạo câu hỏi, giáo viên có thể:
- Nhập một đoạn đọc mới hoặc chọn đoạn đọc đã có trong thư viện.
- Tải audio lên hoặc chọn audio đã có, điền tiêu đề, transcript và nguồn/quyền sử dụng.
- Chọn tài liệu đó cho các câu hỏi cần dùng chung.

Một đoạn đọc/audio có thể phục vụ nhiều câu; file được lưu một lần và các câu tham chiếu đúng phiên bản Material. Ví dụ một audio kể lịch sinh hoạt đi kèm ba câu chọn đáp án thì có một tài liệu và ba câu hỏi.

Giáo viên thao tác bằng tên tài liệu trên giao diện; hệ thống tự lưu liên kết và phiên bản. File nằm trong kho backend/volume asset_data, metadata ở MySQL. M02 bước 2 đã triển khai upload, đọc file có phân quyền và kiểm tra loại/kích thước; bước 3 đã cho chọn phiên bản tài liệu vào câu hỏi/bài học. Giới hạn P0 chốt 5 MiB/file; cấu trúc metadata/quyền đọc theo bàn giao M02–M03.

Transcript, đáp án và giải thích có quy tắc cấp riêng. Bản xem trước phải thể hiện đúng thời điểm người học được phép mở các phần này; chúng không tự đi kèm dữ liệu câu hỏi ban đầu khi chưa được phép.

##### Bước 4. Thêm và sắp xếp câu hỏi trong bài

Màn soạn bài có ba nhóm để giáo viên đặt câu:
- **Có hướng dẫn — GUIDED:** dùng để luyện với gợi ý.
- **Tự làm — INDEPENDENT:** dùng cho lần trả lời độc lập trước khi mở hỗ trợ.
- **Ôn sau — REVIEW_VARIANT:** câu biến thể dành cho ôn/kiểm tra vận dụng ở lượt sau.

Mỗi nhóm có “Thêm câu hỏi” và “Chọn từ ngân hàng”. Giáo viên có thể sắp thứ tự câu trong nhóm. Lượt học đầu cấp nhóm GUIDED rồi INDEPENDENT; nhóm REVIEW_VARIANT được lưu để M04 chọn khi ôn. Nhóm sử dụng nằm trên liên kết câu–bài; cùng một câu được chọn lại vẫn giữ lịch sử nguồn để theo dõi đã gặp.

Thông tin chung khi tạo câu:

| Trường | Giáo viên thao tác | Hệ thống xử lý |
|---|---|---|
| Dạng câu | Chọn một đáp án / điền từ / ghép cặp ở P0 | Hiện biểu mẫu đúng dạng; kiểm tra cấu trúc |
| Yêu cầu và nội dung | Nhập câu hỏi, ngữ cảnh, lựa chọn hoặc các cặp | Lưu nội dung và mã định danh ổn định |
| Đáp án và giải thích | Chọn/nhập đáp án đúng, biến thể được chấp nhận và lời giải | Lưu riêng khóa đáp án; chỉ cấp khi được phép |
| Mục tiêu chính | Chọn một LearningObjective cụ thể | Dùng để tổng hợp nội dung yếu và chọn câu ôn |
| Chủ đề | Mặc định theo Unit, giáo viên kiểm tra lại | Lưu nhãn dùng để tìm/lọc câu |
| Độ khó | Chọn EASY/MEDIUM/HARD theo M02.1 | Lưu riêng với nhóm sử dụng và mức hỗ trợ |
| Tài liệu đi kèm | Chọn đoạn đọc/audio nếu cần | Tham chiếu phiên bản Material, dùng chung được |
| Nguồn và gợi ý | Ghi nguồn, nhập gợi ý nếu hoạt động cho phép | Lưu nguồn và kiểm soát lúc mở gợi ý |
| Nhóm sử dụng | Đặt vào GUIDED/INDEPENDENT/REVIEW_VARIANT | Lưu practiceRole trong LessonQuestion |
| Thứ tự | Sắp xếp câu trong nhóm | Cấp đúng thứ tự đã được chốt cho lượt làm |

Biểu mẫu riêng của ba dạng P0:
- **Chọn một đáp án:** nhập các lựa chọn, đánh dấu đúng một lựa chọn đúng và viết giải thích.
- **Điền từ/cụm từ:** nhập câu có ô trống, đáp án/biến thể chấp nhận cho từng ô và quy tắc chữ hoa/khoảng trắng; hệ thống chấm theo quy tắc đã đặc tả.
- **Ghép cặp:** nhập các cặp tương ứng; khi cấp cho người học, hệ thống giữ mã item và sắp hai phía theo cấu hình, không cấp sẵn khóa ghép đúng.

Hai dạng chọn nhiều và sắp xếp từ thuộc P1, bổ sung sau; không bắt người biên soạn nhập các trường của dạng chưa triển khai.

Ví dụ câu tự làm trong bài 2:

| Trường | Giá trị minh họa |
|---|---|
| Câu hỏi | She ___ to school every day. |
| Dạng | Chọn một đáp án |
| Lựa chọn | go / goes / going |
| Đáp án | goes |
| Giải thích | Chủ ngữ she dùng động từ thêm s/es trong câu khẳng định hiện tại đơn |
| Mục tiêu chính | Hiện tại đơn — ngôi thứ ba |
| Độ khó | EASY |
| Nhóm trong bài | INDEPENDENT |

Một câu ôn mới có thể là “He ___ football every Sunday.” với đáp án “plays”, kiểm tra cùng mục tiêu trong ngữ cảnh khác. Chỉ đảo lựa chọn của câu cũ không được coi là câu mới. Giáo viên tự kiểm tra mục tiêu/đáp án của biến thể; AI nếu dùng chỉ hỗ trợ soạn nháp.

Quy mô **5 GUIDED + 5 INDEPENDENT + 5 REVIEW_VARIANT** là yêu cầu của bộ seed demo ở M02.2. Form có thể lưu nháp khi chưa đủ câu; không biến con số 15 thành giới hạn cố định của mọi bài trong tương lai. Khi xuất bản cần bảo đảm mỗi nhóm phục vụ luồng học/ôn đã cấu hình có nội dung hợp lệ; trường hợp thiếu câu mới khi học/ôn xử lý theo M03.1.

Khi chọn câu có sẵn, hệ thống liên kết đúng phiên bản câu vào bài. Sửa câu dùng chung tạo bản nháp/phiên bản mới; các bài đã xuất bản khác vẫn giữ phiên bản đã chọn. Lịch sử đã gặp câu được giữ để M04 không coi việc tái sử dụng là nội dung hoàn toàn mới.

##### Bước 5. Xem trước và xuất bản

Bấm “Xem trước” để kiểm tra cách người học nhìn thấy nội dung hướng dẫn, câu hỏi, audio và các nút gợi ý/chữa bài. Giáo viên có thể làm thử; lượt xem trước không ghi tiến độ, lịch ôn hoặc XP thật của học viên.

Trước khi xuất bản, hệ thống kiểm tra:
- Bài thuộc Unit hợp lệ; có tên, mục tiêu, nội dung cần học và thứ tự.
- Câu có type/nội dung đúng cấu trúc, mục tiêu chính, độ khó và đáp án hợp lệ.
- Các nhóm câu và thứ tự phù hợp cấu hình học/ôn; dữ liệu seed đáp ứng riêng M02.2.
- Tài liệu được tham chiếu tồn tại, file hợp lệ, có transcript/nguồn khi cần; bản xem trước phát audio được.
- Không cấp sớm đáp án/transcript hoặc các trường nội bộ chứa khóa chấm.
- Người thực hiện còn quyền xuất bản nội dung này.

Lỗi hiển thị tại bài/câu/trường liên quan để sửa; bài vẫn ở nháp. Người biên soạn kiểm tra chuyên môn và bấm “Xuất bản”. P0 là tự kiểm tra trước khi xuất bản, chưa yêu cầu thêm một người duyệt hay một module phê duyệt riêng.

Sau khi xuất bản, người học được cấp bài qua lộ trình chung và ngân hàng được phép. Hệ thống chốt LessonVersion, các QuestionVersion/MaterialVersion và thứ tự tương ứng. Thao tác xuất bản phải nhất quán, không để lộ bài đang hoàn tất dở hoặc nhân bản phiên bản chỉ vì bấm/gửi lại.

##### Bước 6. Sửa bài và quản lý nội dung đã có

| Thao tác | Hành vi |
|---|---|
| Sửa Unit | Chỉnh thông tin tổ chức, mô tả và thứ tự trong phạm vi được cấp quyền; giữ liên kết lịch sử |
| Sửa bài/câu nháp | Lưu trực tiếp vào bản nháp, xem trước và tiếp tục hoàn thiện |
| Sửa bài/câu đã xuất bản | Tạo bản nháp sửa đổi; bản đã công bố vẫn được giữ cho tới khi công bố bản mới |
| Xuất bản bản sửa | Lượt làm mới nhận bản đã công bố mới; lượt đang làm giữ đúng bộ phiên bản đã cấp |
| Ngừng cấp mới | Không cấp bài/câu đó cho lượt mới; giữ dữ liệu để đọc kết quả và xử lý lượt đã được cấp theo chính sách M03/M06 |
| Xóa nháp | Chỉ xóa khi không làm hỏng tham chiếu; tài liệu/câu dùng chung hoặc có lịch sử không bị xóa cứng tùy tiện |

Giáo viên bấm “Sửa bài”, chỉnh nội dung rồi xem trước/xuất bản; hệ thống quản lý mã phiên bản. Việc tạo phiên bản sửa lỗi không tự biến nội dung thành câu mới, không tự reset tiến độ hay tạo thêm phần thưởng.

**Phân quyền P0 đã chốt:** TEACHER biên soạn/xuất bản nội dung mình sở hữu, đọc/tái sử dụng bản công bố của người khác; ADMIN quản lý mọi nội dung; STUDENT chỉ nhận nội dung đã được cấp. Nháp riêng tư. Copy câu giữ originKey, sửa bản công bố tạo version mới. Quy tắc chi tiết theo bàn giao M02–M03.

**Đầu ra và phối hợp module:**
- M02 cung cấp Unit/bài đã xuất bản, nội dung hướng dẫn, phiên bản câu/tài liệu, mục tiêu, độ khó, nhóm sử dụng và thứ tự.
- M03 dùng dữ liệu đó để hiển thị và xử lý lượt học/luyện/ôn, lưu nháp và bài nộp.
- M04 dùng mục tiêu, nhóm câu và kết quả từ M03 để chọn nội dung cần ôn cho từng người; giáo viên không phải tạo một bản tài liệu riêng cho mỗi học viên.
- API/DTO vẫn phải đặc tả trước khi chia code; biểu mẫu trên là yêu cầu nghiệp vụ/UI, chưa thay cho schema request/response đã kiểm thử.

**Kiểm tra riêng luồng biên soạn:** tạo Unit → thêm bài nháp → soạn hướng dẫn → thêm câu thuộc ba nhóm → gắn chung đoạn đọc/audio → xem trước → sửa lỗi → xuất bản → người học mở được; sửa bài đã công bố không đổi lượt đang làm; người học không xem nháp/đáp án sớm; gửi lại thao tác không nhân bản nội dung. Người biên soạn chỉ sửa nội dung thuộc phạm vi được cấp quyền.

**Hoàn thành khi:** soạn được một bài trộn ba dạng P0, đoạn đọc và audio dùng chung cho nhiều câu; seed sáu bài có đủ ba nhóm 5/5/5 và mỗi lượt học đầu cấp đúng 10 câu. Client học viên không nhận đáp án/transcript trước điều kiện cho phép. File vẫn đọc được sau khi tạo lại container.

### M03. Học bài, luyện tập và bài nộp — P0

- Trình câu hỏi hỗ trợ học có hướng dẫn, luyện theo chủ đề/mức khó, ôn tập và kiểm tra ngắn. Attempt phân biệt mode theo hoạt động.
- Học bài: xem nội dung → luyện có hỗ trợ → tự làm câu mới → xem phần cần ôn. Luyện chủ đề: chọn nội dung/mức khó và làm bộ câu khác.
- Mỗi lần làm có attemptId, mode, danh sách phiên bản câu hỏi đã chốt và trạng thái IN_PROGRESS/SUBMITTED.
- Đáp án là dữ liệu có cấu trúc: chọn optionId, điền theo blankId, ghép theo itemId. Không chấm dựa trên vị trí hiển thị A/B/C sau khi đảo lựa chọn.
- Quy tắc chấm P0: chọn một đúng mã; điền so với các biến thể đã duyệt theo quy tắc chữ hoa/khoảng trắng; ghép đúng đủ các cặp. Không tự bỏ dấu/từ hoặc dùng “gần giống” thiếu đặc tả.
- Lưu riêng lần trả lời đầu, gợi ý đã mở và luyện sửa sau phản hồi. Kết quả có hỗ trợ không ghi đè kết quả tự làm.
- M03 lưu/tải nháp server bằng full snapshot, requestId và revision; nháp không tính là trial. Submit cũng gửi đáp án cuối trong cùng transaction. M06 bổ sung IndexedDB theo userId+attemptId/reconnect; chỉ báo đã lưu trên server sau ACK.
- Client hiển thị phản hồi được server cho phép; không tải toàn bộ answerKey để tự chấm trước nộp. Hint/check chỉ cho GUIDED, transcript sau submit. M03 ghi kết quả bất biến; M05 dùng kết quả để thưởng theo chính sách riêng.
- Với audio: player, phát lại, vị trí đang nghe; mất mạng chỉ dùng tiếp phần tài liệu thực sự đã tải. Chưa hứa ứng dụng mở lần đầu hoàn toàn offline.

#### M03.1. Cách cấp bài và chọn câu

Chọn phạm vi nội dung trước rồi mới lấy câu phù hợp. Không bốc ngẫu nhiên từ toàn bộ ngân hàng; học mới, luyện tự chọn và ôn tập có quy tắc riêng.

| Luồng | Quy tắc cấp câu |
|---|---|
| Học bài mới | Đúng phiên bản bài: toàn bộ GUIDED rồi INDEPENDENT; seed 5+5=10, bài khác theo số câu đã biên soạn |
| Luyện theo chủ đề | Chọn Topic/difficulty/count 1–10; lấy INDEPENDENT của bài đang công bố, ưu tiên chưa gặp rồi ít/lâu gặp, không trùng origin |
| Ôn tập hôm nay | Chọn mục tiêu đến hạn/còn sai trước, sau đó ưu tiên REVIEW_VARIANT chưa làm của cùng mục tiêu |
| Ôn lỗi sai | Xem lại câu cũ để hiểu lỗi, sau đó dùng câu biến thể cho kiểm tra vận dụng; lưu hai kết quả riêng |

- Nhóm câu mô tả mục đích cấp, không thay type/difficulty. REVIEW_VARIANT dành ôn M04, không lấy cho PRACTICE thường. REVIEW dùng ReviewSelectionProvider; chưa nối M04 trả REVIEW_NOT_READY, không tạo bộ ôn ngẫu nhiên tạm.
- P0 lượt luyện/ôn lấy tối đa 10 câu hợp lệ trong phạm vi được chọn; nếu chỉ có ít hơn thì cấp số thực có và hiển thị đúng số lượng, không tự nhân bản câu để đủ 10. Không có câu phù hợp thì thông báo và đề nghị chọn nội dung khác/biên soạn bổ sung.
- Câu thường và câu có tài liệu được lấy theo nhóm tương thích. Câu cần đoạn đọc/audio phải đi kèm đúng MaterialVersion; giữ thứ tự khi các câu phụ thuộc ngữ cảnh chung.
- Server chốt và lưu danh sách questionVersionId, thứ tự câu/lựa chọn và trạng thái từng câu vào Attempt. F5/reconnect trả lại cùng lượt, không bốc bộ mới hoặc reset kết quả.
- Chấm theo mã câu/lựa chọn, không theo thứ tự A/B/C nhìn thấy. Mở gợi ý/giải thích ghi vào lịch sử hỗ trợ trước khi cấp nội dung tương ứng.
- Kết quả GUIDED, trả lời đầu độc lập, làm lại sau chữa và REVIEW phải phân biệt để M04 gợi ý đúng và M05 áp dụng luật thưởng phù hợp.

**Hoàn thành khi:** mở bài, trả lời, lưu, F5/đóng mở, tiếp tục và nộp được; giữ nguyên bộ câu khi khôi phục, không lẫn nhóm câu có hướng dẫn với kết quả tự làm; nộp lặp không sinh hai kết quả hoặc hai phần thưởng; dữ liệu người khác bị chặn.

### M04. Ôn tập cá nhân và gợi ý mức luyện — P0 đơn giản, P1 điều chỉnh sâu

**Triển khai P0 ngày 21/10/2026:** [kế hoạch chi tiết](docs/M04_IMPLEMENTATION_PLAN.md), [tiến độ](docs/M04_PROGRESS.md), [API](source/common/M04_API.md), [kiểm tra](statics/results/module-4-validation.md).

- Tab “Ôn tập cá nhân” có nội dung cần ôn và mục tiêu còn lỗi sai; luyện chủ đề tiếp tục dùng M03. Mỗi mục tiêu đã có kết quả có lịch sử, lý do ưu tiên và mốc nhắc.
- Giảng viên chuẩn bị câu ở M02, phân nhóm khi đưa vào Lesson trong Unit. M04 chọn REVIEW_VARIANT chưa gặp trước, sau đó câu INDEPENDENT mới/câu cũ cùng mục tiêu và mức ưu tiên; không tự sinh học liệu.
- Tính theo mục tiêu, không lập lịch tối ưu riêng cho từng câu. Sai ưu tiên ngay; tự làm đúng tăng stage tối đa một lần mỗi ngày Việt Nam, nhắc lại sau 1 → 3 → 7 ngày. Lịch có cấu hình, chưa có bằng chứng tối ưu.
- Bản P0 dùng cấu hình backend chung. Chưa có màn hình/API cho giảng viên chỉnh quy tắc theo Unit; đây là phần bổ sung đã trao đổi, chưa triển khai. Mốc nhắc hiện hiển thị trong ứng dụng, chưa có email/thông báo đẩy.
- Dùng FIRST bất biến của Submission; GUIDED/hỗ trợ/bỏ trống/câu từng gặp được phân biệt. Chữa sau nộp không sửa bằng chứng. Trả lời đúng nhờ hướng dẫn chưa chứng minh tự làm.
- Bài nhiều mức khó: ưu tiên mức thấp nhất còn sai; nếu không sai thì mức cao nhất đã tự làm có trả lời. Đây là mức luyện nội bộ, không phải chứng chỉ trình độ.
- Gợi ý tăng mức: tối thiểu hai bộ tốt mới liên tiếp đủ câu cùng mục tiêu/mức; mặc định ít nhất 3 câu mới/bộ, đạt 80%. Chỉ đề xuất khi không còn ưu tiên cần luyện và mức kế tiếp có đủ câu mới. Học viên tự chọn mức.
- Thiếu câu cấp số thực có; thiếu bằng chứng hoặc học liệu hiển thị lý do, không lặp origin để tạo bộ “mới”.
- Flyway V5 lưu progress/evidence/processed Submission ledger. POST sync backfill theo batch và receipt, GET chỉ đọc, provider REVIEW nối runner/lưu/nộp M03. Không dùng callback RAM làm nguồn duy nhất.

**Hoàn thành khi:** hai người học cùng bài nhưng sai mục tiêu khác nhau nhận bộ ôn khác nhau; ôn đúng/sai cập nhật đúng lịch; tài khoản khác trên cùng máy không nhận nháp/tiến độ của nhau.

### M05. Gamification và Giải đấu tuần — P0 cơ bản, P1 mở rộng

#### M05.1. XP, cấp độ hoạt động, huy hiệu và streak

[M05 — triển khai, tính công bằng và nghiệm thu](docs/M05_IMPLEMENTATION_PLAN.md) cập nhật ngày 22/10/2026: đã hoàn tất chặng 0–6 P0. Có [bộ luật P0](docs/M05_DECISIONS.md), [API](source/common/M05_API.md), V6–V8, worker/sync/ledger, XP/cấp độ/streak/ba huy hiệu và Giải đấu tuần (join/allocator/ranking/tie/chốt/history/UI/socket) trên nền main `57f2951` đã có M04/V5. Xem [nghiệm thu cuối](statics/results/module-5-validation.md) và [bàn giao](docs/M05_HANDOFF.md) cho phạm vi đã kiểm tra. Luật dưới đây theo policy `m05-p0-v1`.

- XP chỉ sinh từ hoạt động hợp lệ đã được server xác nhận; lưu sổ sự kiện có khóa chống trùng theo người học + nguồn hoạt động.
- Tách tổng XP để tính cấp độ hoạt động và XP hợp lệ trong tuần để xếp Giải đấu tuần. Lặp lại request không thưởng lần hai.
- Thưởng 5 XP cho FIRST câu độc lập mới, đủ/đúng, không hỗ trợ; 5 XP/mục tiêu/chu kỳ ôn đến hạn đủ điều kiện, tối đa một bonus/mục tiêu/ngày. Trần chung 200 XP/ngày; cấp độ hoạt động = 1 + floor(totalXP/100). Chữa sau nộp không tạo thưởng câu mới.
- Huy hiệu ban đầu: hoàn thành bài đầu, ôn đúng lịch, duy trì chuỗi ngày. Chốt điều kiện có thể kiểm tra từ sự kiện đã lưu.
- Streak: ngày Việt Nam có lượt FIRST tự làm đầy đủ ít nhất min(3, số câu độc lập được giao), có ít nhất một câu đúng; nhiều bài cùng ngày chỉ tính một ngày. Không dùng giờ client để ghi lùi ngày.

#### M05.2. Giải đấu tuần — thi đua chăm học

- Tên UI: **Giải đấu tuần**. Thước đo là XP hợp lệ của tuần, nhằm khuyến khích duy trì học/luyện; không dùng XP để chứng nhận trình độ tiếng Anh.
- Các bậc: **Đồng → Bạc → Vàng → Bạch Kim → Kim Cương**.
- Người học chủ động tham gia mỗi tuần. Mỗi bậc chia nhóm tối đa 25 người; mỗi người chỉ cạnh tranh trong nhóm được xếp. Chỉ XP từ bài nộp sau joinedAt thuộc tuần mới được xét vào bảng.
- Nhóm không phụ thuộc tất cả thành viên đang online. Có vùng lên hạng, giữ hạng và xuống hạng; không lọt top lên hạng vẫn có thể giữ bậc.
- Chốt tuần đúng một lần: lưu thứ hạng, bậc tuần sau và lịch sử. Tuần mới bắt đầu điểm tuần mới từ 0; bậc được giữ/lên/xuống theo kết quả, không đưa tất cả về Đồng.
- Việc chốt tuần giữ nguyên tổng XP, cấp độ hoạt động, huy hiệu, streak, tiến độ học tập.
- Cần ít nhất 10 thành viên/5 người có XP mới xét lên/xuống; quota mỗi phía floor(20% số thành viên). Cùng XP cùng hạng; khối hòa vắt ranh giới giữ bậc. Nhóm nhỏ giữ bậc; Đồng/Kim Cương chặn ở đầu/cuối.
- Demo có thể dùng nhóm nhỏ với tài khoản thử nghiệm được ghi rõ; không tạo người học giả để báo thành tích sử dụng. Dữ liệu đến muộn không tự sửa tuần đã chốt; áp dụng chính sách thời điểm server xác nhận.

**Hoàn thành khi:** XP/streak/huy hiệu không tăng trùng khi gửi lại/reconnect; job chốt tuần chạy lại không tạo thêm kết quả; tuần mới bắt đầu XP tuần từ 0 và cập nhật bậc theo luật, giữ nguyên tổng XP và tiến độ học tập.

### M06. Kết nối, online/offline và phục hồi — P0, làm xuyên suốt

- HTTP phục vụ đăng nhập, tải nội dung/file, lưu/nộp và tải snapshot. WebSocket kết nối sau đăng nhập, có xác thực, kiểm tra origin và quyền theo dõi tài nguyên.
- Bản MVP đề xuất dùng Spring WebSocketHandler với message JSON tự đặc tả; chưa cần STOMP/broker, Redis hoặc nhiều backend.
- Socket có heartbeat, timeout và reconnect với khoảng chờ tăng dần, có độ lệch ngẫu nhiên. Tham số demo được ghi trong cấu hình, không xem là tiêu chuẩn bắt buộc.
- Presence: người dùng online khi còn ít nhất một kết nối đã xác thực và còn heartbeat hợp lệ. Đóng một tab không làm offline nếu tab khác còn hoạt động; trạng thái có độ trễ phát hiện.
- Sau reconnect: xác thực lại nếu cần, đăng ký lại các luồng được phép, GET snapshot có phiên bản. Socket là kênh thông báo thay đổi; dữ liệu nghiệp vụ vẫn khôi phục từ server.
- IndexedDB giữ nháp tại thiết bị; hiển thị “đã lưu tại máy”, “đã lưu trên server” và “đã nộp” là các trạng thái khác nhau. Lỗi lưu cục bộ phải hiện cảnh báo.
- Request timeout có thể đã được server xử lý: tra trạng thái theo attempt/request trước khi thử lại cùng mã. Không sinh mã yêu cầu mới chỉ vì chưa nhận ACK.
- Nháp server có revision. Client gửi baseRevision; xung đột hai tab/thiết bị trả 409 để đối soát, không ghi đè âm thầm bằng bản cũ.
- Mất mạng luyện cá nhân: giữ nháp, tiếp tục với nội dung đã tải, đồng bộ khi trở lại và còn quyền.
- DB lỗi: rollback và báo lỗi có mã; không ACK/XP trước commit. File upload thất bại thì gửi lại cả file; chưa cần upload tiếp từng phần.
- Server restart: dữ liệu bền vững được khôi phục từ DB/file; session RAM có thể hết, người dùng đăng nhập lại và nhận trạng thái đúng.

**Hoàn thành khi:** demo được tắt mạng/bật lại, timeout sau commit, gửi trùng, hai tab và server restart; bài nộp/XP không nhân đôi và nháp đã lưu vẫn truy xuất được theo phạm vi được phép.

### M07. Hồ sơ và thống kê — P0 gọn, P1 xuất báo cáo

- Hồ sơ: bài đã học, độ chính xác lần đầu theo chủ đề/mục tiêu, nội dung cần ôn, lịch sử học/luyện/ôn, tổng XP, streak và huy hiệu. Hiển thị Giải đấu tuần gồm bậc, vị trí nhóm, XP tuần và lịch sử tuần.
- Tách kết quả có hỗ trợ, tự làm và làm lại; hiển thị cỡ mẫu. Không kết luận thành thạo từ một câu.
- Quản trị: câu hỏi nào thường sai, phân bố độ khó, lượt học/luyện/ôn, số tài khoản online hiện tại. Dữ liệu cá nhân chỉ cấp theo quyền.
- P1: xuất dữ liệu thống kê phù hợp và màn hình xem số liệu thử nghiệm mạng; mã request/log kỹ thuật không đưa vào luồng học.
- Không ghi số liệu hiệu quả học tập khi chưa có thực nghiệm.

**Hoàn thành khi:** người học xem đúng lịch sử của mình, admin xem tổng hợp có nguồn dữ liệu; số liệu khớp kết quả/bản ghi thực tế.

### M08. Hạng thử thách solo — mở rộng để sau, chờ xác nhận

**Trạng thái:** giữ lại phương án đã trao đổi để có thể phát triển tiếp; chưa triển khai cho tới khi người phụ trách xác nhận thêm. M08 không nằm trong 7 module chính hoặc điều kiện bàn giao hiện tại. Phần dưới là thiết kế tham khảo, chưa yêu cầu thêm migration, API, UI, seed hay kiểm thử solo vào M01–M07.

#### M08.1. Hạng thử thách — leo hạng solo

- Tên UI của hệ thống: **Hạng thử thách**. Tên mục để bắt đầu làm bài: **Thử thách cá nhân**.
- Người học tự làm bộ câu hỏi theo bậc/điều kiện đã mở, không cần đối thủ, phòng đấu hoặc tất cả người chơi bắt đầu cùng giờ.
- Kết quả bài thử thách độc lập, không gợi ý trước nộp, là căn cứ tính điểm solo. Đây là mức chinh phục nội dung trong app, không phải chứng nhận trình độ ngôn ngữ.
- Điểm/bậc solo được giữ qua các tuần. Chốt Giải đấu tuần hoặc nghỉ học một tuần không tự đưa điểm solo về 0 hay tự hạ danh hiệu.
- Server cấp và lưu đúng phiên bản thử thách, danh sách câu/thứ tự và luật chấm tại lúc bắt đầu; reconnect quay lại cùng Attempt. Nếu thử thách có thời hạn, deadline cũng do server quản lý.
- Dùng câu/bộ biến thể phù hợp chưa được chữa trước đó; kiểm soát việc lặp lại thử thách đã vượt. Đảo lựa chọn hoặc sửa version không tự cho phép nhận lại điểm lên hạng.
- Server xác nhận kết quả và ghi điểm solo chống trùng. Một ACK/nộp lặp hoặc hoàn tất cùng mốc đã được công nhận không tạo thêm điểm.
- Công thức điểm cộng/trừ, ngưỡng từng danh hiệu, điều kiện vượt bậc, số câu, thời lượng nếu có và luật làm lại còn cần chốt. Ví dụ “8/10 câu để vượt” trong trao đổi chỉ là minh họa, không hard-code thành luật đã duyệt.

Bộ danh hiệu đã thống nhất, theo thứ tự tăng dần:

| Thứ tự | Danh hiệu Hạng thử thách |
|---|---|
| 1 | Tân binh |
| 2 | Học giả |
| 3 | Tinh anh |
| 4 | Cao thủ |
| 5 | Bậc thầy |
| 6 | Huyền thoại |

#### M08.2. Quy tắc tách điểm và hiển thị

| Dữ liệu | Căn cứ | Khi sang tuần mới |
|---|---|---|
| Tổng XP/cấp độ hoạt động | Các sự kiện học tập đủ điều kiện | Giữ nguyên |
| XP Giải đấu tuần | XP hợp lệ ghi nhận trong tuần | Tuần mới bắt đầu từ 0, lưu kết quả tuần cũ |
| Bậc Giải đấu tuần | Kết quả nhóm tuần vừa chốt | Giữ/lên/xuống theo luật đã cấu hình |
| Điểm/danh hiệu Hạng thử thách | Kết quả thử thách solo được xác nhận | Giữ nguyên, cập nhật khi có kết quả solo hợp lệ |

- Không chuyển tổng XP hay XP tuần thành điểm solo. Nếu sau này hoàn thành thử thách có thưởng thêm XP thì đó là sự kiện thưởng riêng theo luật cần chốt, không là phép đổi hai loại điểm.
- Hồ sơ hiển thị hai mục riêng. Ví dụ minh họa: “Giải đấu tuần: Vàng — vị trí 8/25”; “Hạng thử thách: Tinh anh — 240/300 điểm”. Con số là ví dụ UI, chưa là ngưỡng thực.
- Logic chốt tuần chỉ cập nhật LeagueSeason/Group/Member; không ghi vào SoloRankProgress hoặc SoloRankPointEvent.

#### M08.3. Tích hợp dự kiến sau khi xác nhận

- Tận dụng trình làm bài M03 và khôi phục M06: thêm mode SOLO_CHALLENGE, cấp bộ đã duyệt theo bậc/điều kiện, không mở gợi ý trước nộp; reconnect giữ cùng Attempt và deadline server nếu có.
- Mô hình dự kiến: SoloChallenge, SoloChallengeVersion, SoloChallengeQuestion; Attempt tham chiếu soloChallengeVersionId. Điểm solo dùng sổ SoloRankPointEvent có nguồn kết quả/mốc thử thách và khóa chống cộng lại cho cùng người học; không lưu chung vào XpEvent. SoloRankProgress giữ điểm/danh hiệu hiện tại và liên kết luật đã áp dụng. Bậc kim loại của tuần thuộc LeagueMember/kết quả tuần; sáu danh hiệu solo thuộc cấu hình Hạng thử thách.
- API dự kiến: GET /api/solo-challenges, POST /api/solo-challenges/{id}/attempts; nộp qua API Attempt chung; GET /api/me/solo-rank. Sự kiện SOLO_RANK_CHANGED chỉ gửi tới người học có quyền.
- UI dự kiến: người biên soạn tạo/xuất bản bộ thử thách; người học vào Thử thách cá nhân, xem danh hiệu, điểm và lịch sử trên hồ sơ. Những màn hình này chưa nằm trong luồng chính hiện tại.
- Seed bổ sung phải có bộ riêng được duyệt theo bậc, luật và phiên bản; không mặc định tái dùng 90 câu học đã chữa làm nguồn điểm solo. Số bộ/số câu chưa chốt; 90 câu là seed của phạm vi chính. Chỉ mở bậc đã đủ nội dung kiểm tra.
- Khi triển khai, cần unique constraint/transaction để hai lượt đồng thời vượt cùng mốc không nhận điểm hai lần; xác nhận kết quả, điểm solo và trạng thái nộp nhất quán.

**Nghiệm thu sau khi được xác nhận:** nộp lặp/reconnect/hai lượt đồng thời không cộng điểm solo trùng; XP học thông thường không tự tăng điểm solo; chốt tuần giữ nguyên điểm/danh hiệu solo; khôi phục đúng bộ/lượt và kiểm tra đúng quyền. Công thức điểm, thời hạn, điều kiện không đạt/làm lại và ngưỡng danh hiệu phải được chốt trước khi phát triển phần tương ứng.

## 4. Hợp đồng dữ liệu và API M02–M03

Hợp đồng **1.0.0 ngày 16/10/2026** đã chốt trong [bàn giao](docs/M02_M03_HANDOFF.md), [quy tắc API](source/common/M02_M03_API.md) và [OpenAPI](source/common/openapi-m02-m03.json). Không tự tạo schema JSON/API khác khi chia việc.

| Module | Bảng dự kiến | API thuộc module |
|---|---|---|
| M02/V3 | request_receipts, topics, learning_objectives, units, assets, materials, material_versions, questions, question_versions, lessons, lesson_versions, lesson_objectives, lesson_questions | /api/topics, /api/objectives, /api/units, /api/lessons/{id}; /api/author/**; /api/assets |
| M03/V4 | attempts, attempt_questions, attempt_answers, answer_trials, submissions, question_exposures, lesson_progress | /api/attempts/**; /api/me/learning-progress |
| M04/V5 | review_objective_progress, review_difficulty_evidence, review_processed_submissions | /api/me/review, /api/me/review/sync; tạo REVIEW qua /api/attempts |
| M05/V6–V8 | policy/context/claim/XP ledger/profile/day/badge/processed/failure, outbox, league season/allocator/group/member/outbox | /api/me/gamification/**, /api/me/badges, /api/me/league/**; socket invalidation theo user/nhóm |

Bản SQL hợp đồng gốc ở [V3](docs/database/planned/V3__learning_content.sql) và [V4](docs/database/planned/V4__learning_attempts.sql). **Migration runtime V1–V8 nằm trong source/server/src/main/resources/db/migration/**; M04 đã kiểm tra nâng V4 → V5, M05 kiểm tra nâng V5 → V8 giữ dữ liệu. Giữ migration đã chạy và ddl-auto=validate. M02 giao nền trước để M03 có FK/DTO/service đọc nội dung.

Dữ liệu định danh/lọc/quyền ở cột quan hệ; content/answer/source/instructions và snapshot linh hoạt dùng JSON có schemaVersion. Mỗi câu có một mục tiêu chính. LessonQuestion lưu Version ID + GUIDED/INDEPENDENT/REVIEW_VARIANT + thứ tự. MaterialVersion dùng chung file qua Asset, transcript không cấp trong snapshot làm bài.

P0 chốt SINGLE_CHOICE, FILL_BLANK, MATCHING. Chọn nhiều và sắp xếp từ là P1, chưa nhận ở API/schema P0. Question có originKey giữ qua copy/sửa version để theo dõi đã gặp. Published version bất biến; Attempt giữ đúng bộ và thứ tự đã cấp.

Không có API STUDENT tải toàn bộ ngân hàng kèm khóa đáp án. Học viên nhận PublicQuestion/PublicMaterial qua Attempt; hint/check chỉ cho GUIDED, transcript sau nộp. Nháp không tạo trial; submit gửi full snapshot, chốt một Submission bất biến và giữ riêng FIRST.

M02 cung cấp RequestReceiptService dùng chung; mọi mutation có requestId và revision theo đặc tả. Retry cùng key/payload trả kết quả đã commit, key khác payload trả 409. M04 cung cấp ReviewSelectionProvider, M03 dùng chung runner; trước M04 trả REVIEW_NOT_READY. M05 đọc Submission ID để thưởng có ledger riêng; M03 chưa cộng XP.

## 5. Message, concurrency và đối soát

Envelope đề xuất cho WebSocket:

~~~json
{
  "version": 1,
  "type": "SUBSCRIBE",
  "requestId": "uuid",
  "payload": {
    "stream": "LEAGUE",
    "id": 42
  }
}
~~~

Client gửi HELLO/HEARTBEAT/SUBSCRIBE/UNSUBSCRIBE. Server trả ACK/ERROR và sự kiện PRESENCE_CHANGED/LEADERBOARD_CHANGED/LEAGUE_CHANGED. LEAGUE_CHANGED thuộc Giải đấu tuần. Sự kiện có eventId, serverTime, resourceId và revision; ACK của socket xác nhận lệnh subscribe/heartbeat, không thay ACK lưu/nộp qua HTTP.

- Gửi sự kiện nghiệp vụ sau commit. Khi socket mất sự kiện, client tải snapshot lại; chưa cần hệ thống replay toàn bộ lịch sử sự kiện.
- Unique constraint giữ một kết quả cho cùng lần nộp, một XpEvent cho cùng nguồn thưởng và một kết quả chốt cho cùng tuần/nhóm.
- Khóa/transaction ở service cho ghi nháp–nộp, cộng XP và chốt tuần. Quy định thứ tự khóa thống nhất.
- Nộp và cập nhật điểm/XP liên quan phải commit nhất quán; nếu dùng tác vụ sau thì phải có bản ghi nguồn để phục hồi, không chỉ lưu trong RAM.
- Khi broadcast tới một WebSocketSession, đồng bộ việc gửi và có giới hạn hàng đợi; không để một client chậm làm chặn các client khác.
- Client reconnect tải snapshot, đăng ký theo dõi lại rồi đối soát revision lần nữa nếu có khoảng trống giữa hai bước.
- MVP một backend: registry kết nối ở RAM, nghiệp vụ ở DB/file. Thêm nhiều backend cần thiết kế chia sẻ presence/event riêng, ngoài phạm vi đầu.

## 6. UI, seed và thứ tự triển khai

### 6.1. Màn hình

Người học: trang chủ → học bài → luyện theo chủ đề → ôn hôm nay/lỗi sai → Giải đấu tuần → hồ sơ.

Người biên soạn: ngân hàng câu hỏi/tài liệu → soạn bài → xem trước/xuất bản → xem thống kê học tập.

Quản trị: quản lý tài khoản/quyền, nội dung và thống kê kết nối; không yêu cầu người học hiểu requestId hoặc cấu trúc protocol để sử dụng.

### 6.2. Seed đề xuất, làm cùng M02

- Ba chủ đề: sinh hoạt hằng ngày, ăn uống, đi lại. Mỗi chủ đề ở chặng ban đầu có một Unit gồm 2 bài: tổng 3 Unit, 6 bài.
- Mỗi bài chuẩn bị đúng 15 câu cho seed: 5 GUIDED + 5 INDEPENDENT + 5 REVIEW_VARIANT; tổng đúng 90 câu học tập. Lượt học đầu mỗi bài làm 10 câu; phần ôn lấy theo M03.1, không bắt làm cả 15 câu liên tục.
- Seed phạm vi chính gồm đúng 90 câu hỏi học tập; tài liệu/audio dùng chung không cộng thêm vào số câu.
- Có đủ ba type P0; ít nhất hai đoạn đọc tự biên soạn và ba audio ngắn có transcript khớp. Ghi nguồn/tác giả/quyền dùng; không chép ngân hàng Duolingo.
- Đáp án, biến thể được chấp nhận, giải thích, mục tiêu và mức khó phải được kiểm tra. AI nếu dùng chỉ hỗ trợ nháp, không tự công bố.
- Có dữ liệu tuần hiện tại và một tuần đã chốt để xem UI Giải đấu tuần; fixture/đồng hồ giả lập được đánh dấu, không dùng như kết quả thực nghiệm thật.
- Tài khoản thử đủ vai trò và vài người học để demo online/rank. Dùng định danh seed ổn định; không đưa mật khẩu thật vào Git.

### 6.3. Thứ tự làm và bàn giao

| Chặng | Việc cần xong | Điều kiện chuyển tiếp |
|---|---|---|
| 1 | M01 + chốt DTO/schema loại câu và quyền | Đăng nhập, quyền, migrations chạy trên DB thử |
| 2 | M02 + seed + nền socket/presence M06 | Một bài trộn đọc/nghe cấp được từ server |
| 3 | M03 + nháp/ACK/idempotency M06 | Học/luyện/nộp, tắt mạng rồi mở lại không mất nháp đã lưu |
| 4 | M04 + XP/streak cơ bản M05 | Sai mục tiêu nào được ôn mục tiêu đó, không thưởng trùng |
| 5 | Giải đấu tuần M05 + M07 + thử nghiệm/bàn giao | Chốt tuần đúng một lần, khôi phục, số liệu thật và hướng dẫn demo |

Có thể chia việc sau khi thống nhất hợp đồng chung: nền/quyền/socket; ngân hàng/seed; trình làm bài/nháp; ôn tập; gamification/Giải đấu tuần. Mỗi phần bàn giao UI + API + migration cần thiết + seed + kịch bản kiểm tra, không chỉ một màn hình. M08 chưa xếp vào các chặng hiện tại; chỉ lập lịch và chia việc sau khi được xác nhận.

## 7. Kiểm thử, contribution và yêu cầu môn học

Kịch bản bắt buộc: hai máy/tài khoản khác nhau; quyền truy cập; câu sai dữ liệu; mất mạng khi lưu/nộp; timeout sau commit; thử lại; hai tab ghi nháp; lưu nháp đồng thời nộp; server/DB lỗi; file thiếu; session hết; tài khoản bị khóa; job chốt tuần chạy lặp; chốt tuần giữ tổng XP và tiến độ học tập.

Kiểm tra học liệu/cấp câu: seed 3 Unit × 2 bài × 15 câu = 90, chia đúng ba nhóm 5/5/5; lượt đầu cấp đúng 10 câu; F5/reconnect không đổi bộ; chọn đúng chủ đề/mức/mục tiêu; thiếu câu không nhân bản; câu/audio ghép đúng material; đảo lựa chọn hoặc sửa version không tự tính là câu mới; dữ liệu tự làm tách khỏi hỗ trợ/chữa lại.

Đo thực tế ở quy mô phù hợp máy demo: số client/socket đồng thời, p50/p95 thời gian API, độ trễ cập nhật bảng Giải đấu tuần, thời gian phát hiện mất kết nối/khôi phục, tỷ lệ lỗi và số bản ghi/XP trùng. Ghi máy, dữ liệu, cấu hình, công cụ, số lần chạy; không tự điền kết quả chưa đo.

Đề xuất contribution để kiểm chứng, chưa tuyên bố novelty đã đạt:

| Hướng | Baseline | Phần đề xuất | Bằng chứng |
|---|---|---|---|
| C1. Bảo vệ tiến độ khi lỗi mạng | Chỉ gửi khi bấm nộp, không nháp bền vững/đối soát | IndexedDB + ACK + snapshot/revision + chống gửi trùng | Số câu khôi phục, số bản trùng, thời gian phục hồi dưới lỗi có kiểm soát |
| C2. Chọn bài ôn theo nội dung sai | Chọn câu ngẫu nhiên cùng chủ đề | Lịch ôn và câu biến thể theo mục tiêu/lịch sử hỗ trợ | Độ khớp mục tiêu, độ phủ, lượt lặp; muốn nói cải thiện ghi nhớ cần thực nghiệm người học riêng |

League/XP hoặc dùng WebSocket tự thân không chứng minh contribution mới. Tham khảo Duolingo cần ghi rõ là cơ chế kế thừa.

Tuân thủ hướng dẫn tại [Instruction](INSTRUCTION.md), [Topics](TOPICS.md) và [Submission](SUBMISSION.md): phân biệt baseline/phần mới, có sơ đồ/message flow/concurrency/error handling, số liệu thật, nguồn và báo cáo. Submission ghi hạn 23:59 ngày 31/10/2026, bàn giao source/report theo hướng dẫn riêng; kiểm tra lại thông báo giảng viên khi nộp. Lập kế hoạch/thiết kế không thay cho xác nhận đề tài nếu giảng viên yêu cầu.

## 8. Những điểm còn cần chốt

1. M02–M03 đã chốt quyền TEACHER own/ADMIN mọi nội dung, ba type P0, API và V3/V4 tại bàn giao. Tên quyền code giữ theo M01; không chờ mục này để triển khai.
2. Mức thưởng XP, giới hạn lặp, công thức cấp độ, kích thước nhóm/quota và luật lên/xuống/hòa điểm của Giải đấu tuần.
3. Múi giờ, ghi nhận học offline gửi muộn và tác động tới tuần/streak.
4. Socket WebSocket trên web có đúng yêu cầu cụ thể của giảng viên; phạm vi server xác nhận điểm/XP.
5. Khi xem xét mở rộng M08: xác nhận có triển khai Hạng thử thách solo hay không, rồi chốt công thức điểm, ngưỡng danh hiệu, điều kiện vượt bậc/làm lại và ngân hàng câu. Quyết định này không chặn M01–M07.
6. Dự án tiếng Anh được tách sang thư mục mới từ nền M01 ORM; dự án IELTS và các nhánh cũ được giữ riêng để đối chiếu. Dùng migration bổ sung cho app mới, không xóa dữ liệu/volume hoặc viết lại lịch sử của dự án cũ.

## 9. Nguồn tham khảo

- [Duolingo — Tổ chức khóa học](https://blog.duolingo.com/duolingo-101-how-to-learn-a-language-on-duolingo/) và [ôn cách quãng](https://blog.duolingo.com/spaced-repetition-for-learning/): tham khảo lộ trình và ôn theo lịch sử. Quy mô 2 bài/chủ đề, 15 câu/bài và quy tắc chọn câu trong plan là thiết kế của đồ án, không phải số liệu/công thức của Duolingo.
- [Duolingo — Leagues and leaderboards](https://blog.duolingo.com/duolingo-leagues-leaderboards/): tham khảo riêng Giải đấu tuần; không lấy quy mô/suất lên xuống của bài viết làm luật app. Hạng thử thách solo và bộ danh hiệu được lưu tại M08 chờ xác nhận là thiết kế riêng của đồ án.
- [British Council — A1–A2 vocabulary](https://learnenglish.britishcouncil.org/free-resources/vocabulary/a1-a2) và [A1–A2 grammar](https://learnenglish.britishcouncil.org/free-resources/grammar/a1-a2): tham khảo chủ đề/mục tiêu và cách tổ chức bài.
- [MySQL 8.4 — JSON](https://dev.mysql.com/doc/refman/8.4/en/json.html): cột JSON cho nội dung khác nhau theo type.
- [MDN — Using IndexedDB](https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API/Using_IndexedDB): nháp bền vững tại trình duyệt; vẫn cần xử lý lỗi/quota và mất dữ liệu cục bộ.
- [RFC 6455, mục 7.2.3](https://www.rfc-editor.org/rfc/rfc6455#section-7.2.3): khôi phục WebSocket sau đóng bất thường, backoff.
- [Spring Framework 6.2 — WebSocket server](https://docs.spring.io/spring-framework/reference/6.2/web/websocket/server.html): API WebSocket, handshake và đồng bộ gửi.
- [Spring Data JPA 3.5 — Locking](https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/locking.html): khóa trong tầng repository/service.
- [Cấu trúc backend hiện tại](docs/BACKEND_STRUCTURE.md) và [quy ước ORM](docs/ORM_ARCHITECTURE.md): nền tảng tái sử dụng, cần rà soát lại khi bắt đầu code.
