# M02 — tiến độ triển khai

Cập nhật 17/10/2026. **M02 đã hoàn thành bốn bước**, có UI, API, dữ liệu demo và kiểm tra đi kèm. Các mục bước 1–3 bên dưới ghi phạm vi tại thời điểm triển khai; bộ mẫu ban đầu được mở rộng ở bước 4. Hợp đồng nghiệp vụ vẫn là [bàn giao M02–M03](M02_M03_HANDOFF.md).

## Bước 1 — danh mục biên soạn

Đã triển khai:

- Màn **Quản lý học liệu** cho TEACHER và ADMIN: các tab Chủ đề, Mục tiêu học, Unit; tạo, chỉnh sửa, tìm kiếm, lọc, phân trang; giao diện desktop/mobile.
- API GET/POST `/api/author/topics`, `/api/author/objectives`, `/api/author/units` và PUT tương ứng `/{id}`.
- Scope OWN/ALL đúng hợp đồng. Chủ đề/mục tiêu là danh mục chung; chỉ chủ sở hữu hoặc ADMIN sửa. Unit ALL chỉ ADMIN.
- Code chủ đề/mục tiêu và Topic của mục tiêu bất biến; Unit đã có bài không đổi Topic. Input không nhận ownerId hoặc trường ngoài DTO.
- `requestId` và receipt cùng transaction với dữ liệu; retry trả đúng status/body cũ, key khác payload trả 409. `baseRevision` + khóa hàng ngăn ghi đè từ tab cũ. Giữ AccountGuard khi ghi.
- Flyway V3 theo DDL đã chốt tạo đủ 13 bảng M02. Entity/API bước 1 dùng danh mục và receipt; bước 2 dùng thêm các bảng file/tài liệu. V1/V2/V3 đã chạy được giữ nguyên.
- Demo có 3 chủ đề, 3 mục tiêu, 3 Unit ban đầu. Seed chạy lại không nhân bản hoặc đặt lại nội dung đã sửa.

`objectiveIds` của Unit được suy từ phiên bản bài ở bước 3, không chọn trực tiếp khi tạo Unit. Unit chưa có bài có danh sách mục tiêu rỗng.

## Bước 2 — file và tài liệu đọc/nghe/ảnh

Đã triển khai:

- Khu vực **Tài liệu**: danh sách, tìm kiếm, lọc loại/trạng thái/phạm vi, phân trang; tạo READING, AUDIO hoặc IMAGE, nhập nguồn/giấy phép và tải file.
- POST `/api/assets` nhận multipart đúng file + requestId; GET `/api/assets/{id}` kiểm tra quyền, trả MIME thật, hỗ trợ audio Range (200/206/416). Không lộ storage key.
- File tối đa 5 MiB; PNG/JPEG qua chữ ký và giải mã ảnh, MP3/WAV/OGG qua chữ ký + ffprobe. Không tin phần mở rộng hoặc Content-Type client. Ảnh giới hạn 20 triệu pixel, probe có timeout 8 giây và giới hạn đầu ra; Docker đã cài ffprobe/ffmpeg.
- API `/api/author/materials`: danh sách/chi tiết/lấy version, tạo và sửa nháp, bỏ nháp, xem trước, xuất bản và ngừng cấp mới. DTO nghiêm theo từng loại, chặn trường lạ/coercion và sai loại file.
- Lưu nháp chưa đủ nội dung; xem trước chỉ ra lỗi trước xuất bản. Audio có transcript và đoạn phát start/end chính xác mili giây, UI nghe thử/tua trong đoạn đã chọn; transcript được ẩn trong phần trình bày cho người học. Ảnh có mô tả và chú thích.
- Xuất bản cần previewToken còn hiệu lực. Chỉnh sửa bản đã công bố tạo version mới, giữ nguyên version cũ; xem được phiên bản qua API. Bỏ nháp trả 204 và không tái dùng số version. Root đã bỏ nháp mà chưa từng công bố được ẩn khỏi danh sách.
- TEACHER sửa nội dung mình sở hữu, đọc bản công bố của tác giả khác; ADMIN quản lý mọi nội dung; nháp riêng tư. File chỉ cấp theo quyền sở hữu/tài liệu; STUDENT chờ grant theo lượt làm ở M03. AssetReadGrant đã có điểm mở rộng cho M03.
- Receipt cùng transaction với dữ liệu, revision và khóa hàng chống ghi đè. UI giữ requestId khi thử lại thao tác mất phản hồi; không báo lưu thành công trước ACK. Rollback gỡ file vừa ghi; bản công bố/file lịch sử được giữ lại. Chặn bỏ nháp hoặc ngừng cấp tài liệu đang có tham chiếu bảo vệ.
- Demo có ba tài liệu đã công bố và hai file thật, thuộc TEACHER demo. SeedKey/requestId ổn định, không nhân bản hoặc đặt lại sửa đổi khi khởi động lại. Nguồn tại [dataset](../statics/dataset/README.md).

Bước này dùng các bảng `assets`, `materials`, `material_versions` của V3 đã có. **MySQL lưu metadata/nội dung/phiên bản; byte ảnh/audio ở volume asset_data.** Bước 3 gắn phiên bản tài liệu vào câu hỏi.

## Bước 3 — ngân hàng câu hỏi và bài học

Đã triển khai:

- Hai khu vực **Câu hỏi** và **Bài học** trong Quản lý học liệu; danh sách/tìm kiếm/lọc/phân trang và quyền OWN/PUBLISHED/ALL như kho tài liệu.
- Câu hỏi SINGLE_CHOICE (2–6 lựa chọn), FILL_BLANK (1–5 ô, nhiều đáp án chấp nhận, tùy chọn hoa/thường và khoảng trắng), MATCHING (2–6 cặp). Form nhập nội dung, đáp án, gợi ý, giải thích, Topic/Objective, độ khó, nguồn và chọn phiên bản tài liệu; không cần nhập JSON/ID kỹ thuật.
- API `/api/author/questions` và `/api/author/lessons`: tạo/chi tiết/version, mở/lưu/bỏ nháp, xem trước, xuất bản, ngừng cấp. Nháp được thiếu nội dung nhưng vẫn đúng schema/FK. Preview kiểm tra đáp án; publish giữ phiên bản cũ bất biến. Sao chép câu qua `POST /api/author/questions/{id}/copy` giữ originKey.
- Bài học chọn Unit, mục tiêu cùng Topic, nhiều khối TEXT/EXAMPLE, câu hỏi và nhóm GUIDED/INDEPENDENT/REVIEW_VARIANT. Có sắp xếp câu trong từng nhóm. Xuất bản cần tên, hướng dẫn, mục tiêu, đủ ba nhóm; GUIDED cần hint. Không ép bài bất kỳ phải đúng 15 câu; 5/5/5 là yêu cầu bộ seed bước 4.
- Chặn sai Topic/Objective, đáp án không hợp lệ, câu trùng origin (kể cả bản sao/phiên bản), vị trí trùng hoặc không liên tiếp, nháp riêng của tác giả khác và phụ thuộc ngừng cấp. Sửa bài/câu/tài liệu sau preview làm token cũ hết hiệu lực.
- Xuất bản khóa closure Material → Question → Lesson, ID tăng dần. Kiểm tra lại token/phụ thuộc rồi tự công bố nháp liên quan được phép sửa trong cùng transaction. Có test lỗi DB xảy ra sau khi đã ghi phụ thuộc để chứng minh rollback toàn bộ và không có receipt thành công.
- Ngừng cấp câu/tài liệu đang được bài công bố dùng trả CONTENT_IN_USE, kể cả bài dùng phiên bản câu cũ. Các thao tác ngừng cấp và xuất bản được kiểm tra đồng thời; không để bài mới công bố trỏ vào tài liệu vừa ngừng cấp.
- GET `/api/author/units/{id}` và PUT `/{id}/lesson-order`. Tạo/sắp xếp bài dùng Unit revision; gửi đủ toàn bộ bài, không trùng/thiếu. Root bài đã bỏ nháp vẫn có trong chi tiết Unit để giữ đủ cấu trúc sắp xếp, và được ẩn khỏi bank thông thường. Mục tiêu Unit suy từ các phiên bản bài được phép xem.
- Danh mục học viên GET `/api/topics`, `/api/objectives`, `/api/units`, `/api/units/{id}`, `/api/lessons/{id}` chỉ dùng bài active published. Menu **Khám phá bài học** cho xem Unit, giới thiệu, hướng dẫn và mục tiêu; không trả ngân hàng/khóa đáp án/transcript. Chưa có nút làm bài hoạt động.
- `PublishedContentReader` server-only cho M03: bài đang công bố, danh sách bài theo Topic và các snapshot Lesson/Question/Material published theo Version ID. Đọc lịch sử không thay bằng phiên bản mới; các DTO nội bộ có keys/transcript không được dùng làm response học viên.
- Demo thêm ba câu hỏi và một bài mẫu **A day with Linh — Bài mẫu**, dùng ba tài liệu đã có. SeedKey ổn định, không đặt lại sửa đổi của người dùng. Đây là bài minh họa ba câu, chưa phải seed 6 bài/90 câu.

Bước 3 dùng các bảng Question/Lesson/version/join của V3 đã áp dụng; không thêm/sửa migration. UI giữ thao tác/requestId khi mất ACK, giữ nội dung nhập khi xung đột, cho tải lại rõ ràng; preview tương tác ẩn đáp án/gợi ý/giải thích/transcript.

## Dùng thử

1. Mở `http://localhost:5173`, tải lại trang để nhận frontend mới.
2. Đăng nhập `teacher@english.local` / `Teacher@12345` khi dùng cấu hình demo mặc định.
3. Chọn **Quản lý học liệu** → **Danh mục** để xem Unit/Chủ đề/Mục tiêu học.
4. Chọn **Tài liệu** để mở 13 tài liệu mẫu. Bấm **+ Thêm tài liệu**, chọn loại, nhập nội dung hoặc tải file, ghi nguồn → **Lưu bản nháp** → **Xem trước** → **Xuất bản tài liệu**.
5. Mở tài liệu đã xuất bản → **Tạo bản chỉnh sửa**. Sau khi lưu nháp, chuyển giữa tab bản nháp/bản đã xuất bản để thấy nội dung cũ được giữ nguyên. Có thể bỏ nháp hoặc ngừng cấp mới khi nội dung chưa được sử dụng.
6. Chọn **Câu hỏi** để xem ba dạng câu mẫu hoặc **+ Thêm câu hỏi**. Chọn Topic/Mục tiêu, soạn nội dung/đáp án/giải thích, gắn tài liệu nếu cần → lưu nháp → xem trước → xuất bản.
7. Chọn **Bài học** để mở một trong 6 bài mẫu hoặc tạo bài mới: chọn Unit/mục tiêu, nhập hướng dẫn, thêm câu vào đủ ba nhóm → lưu → xem trước → xuất bản. Nháp câu/tài liệu liên quan hợp lệ sẽ được công bố cùng bài. Để đổi thứ tự bài, lọc một Unit rồi chọn **Sắp xếp bài trong Unit đã chọn**.
8. Đăng nhập học viên hoặc chọn **Khám phá bài học** để xem giới thiệu các bài đã công bố. Bắt đầu lượt làm/chấm bài sẽ triển khai ở M03.

ADMIN dùng tài khoản bootstrap trong `.env`; mặc định màn danh mục xem ALL. STUDENT không có menu hoặc quyền gọi author API.

## Bước 4 — seed đầy đủ và nghiệm thu M02

Đã triển khai:

- 3 Topic, 3 Unit, 6 mục tiêu; mỗi Unit 2 bài, tổng 6 bài × 15 câu = 90 Question root độc lập. Mỗi bài đúng 5 GUIDED + 5 INDEPENDENT + 5 REVIEW_VARIANT; toàn bộ có 30 SINGLE_CHOICE, 30 FILL_BLANK, 30 MATCHING.
- 6 đoạn đọc, 6 tài liệu nghe và 1 ảnh; 7 file thật (6 WAV, 1 PNG). Có câu text độc lập, nhiều câu dùng chung đoạn đọc/audio, đáp án, gợi ý và giải thích.
- Dataset JSON, manifest 90 câu/13 tài liệu, SHA-256 media và bảng duyệt nội dung tại [dataset](../statics/dataset/README.md). Có script sinh lại dữ liệu/audio và kiểm tra schema, số lượng, liên kết, đáp án nghe.
- Seed qua các service thật, chạy lại không thêm root/version/file/receipt. Mỗi bài cùng câu/phụ thuộc được công bố nguyên tử; lỗi giữa chừng có thể chạy lại từ checkpoint tài liệu.
- Mẫu cũ chưa sửa được nhận lại ID và origin; bài cũ được nâng từ 3 lên 15 câu bằng version mới, bản công bố cũ giữ nguyên. Mẫu đã sửa/nháp/ngừng cấp được giữ và bộ chuẩn tạo riêng; tổng dữ liệu có thể lớn hơn 6/90 nếu có nội dung riêng.
- Không thêm hoặc sửa migration. Metadata/nội dung ở MySQL; byte media ở volume asset_data. Đã kiểm tra dữ liệu cũ trước/sau triển khai và sau recreate server.

Kết quả chi tiết và hướng dẫn tái hiện: [nghiệm thu M02 bước 4](../statics/results/module-2-seed-validation.md).

Danh sách toàn bộ chức năng, dữ liệu thử và 98 ca kiểm thử UI/API/hệ thống: [checklist kiểm thử M02](M02_MANUAL_TEST_CASES.md).

Cập nhật 18/10: đã nối các màn/tab và chi tiết học liệu với lịch sử trình duyệt; Back/Forward và tải lại giữ đúng màn. [Kết quả sửa điều hướng: 31/31 test giao diện](../statics/results/navigation-validation.md).

Cập nhật 18/10: đã ngừng cấp 11 bài fixture từng sót trong 6 Unit E2E trên demo; danh mục học viên còn 3 Unit/6 bài chuẩn. Lệnh E2E mặc định dùng DB/file volume riêng, có dọn publication sau test. [Xác minh nguyên nhân và kết quả cách ly E2E](../statics/results/e2e-data-isolation-validation.md).

## Phần tiếp theo

### Cập nhật 19/10 — quyền và đổi audio đồng loạt

- Lesson tạo trong Unit luôn thuộc chủ Unit, kể cả khi ADMIN tạo giúp. Chủ Unit tiếp tục sửa/sắp xếp được bài.
- Quyền đọc file tài liệu dùng chung bao gồm các phiên bản PUBLISHED cũ trên root còn active published; không mở quyền cho nháp riêng hoặc root đã ngừng cấp.
- Tài liệu AUDIO đã công bố có nút **Cập nhật audio cho câu hỏi/bài học**: liệt kê nội dung, chọn câu/bài, xác nhận đáp án/transcript rồi tạo/xuất bản phiên bản mới nguyên tử. Giữ nguyên bản cũ, quyền sở hữu, Unit và origin; chặn nội dung đang có nháp hoặc không đủ quyền sửa.
- API bổ sung GET/POST `/api/author/materials/{id}/audio-update`, có previewToken/revision và receipt chống tạo trùng khi mất ACK. Không thay migration. M03 tiếp tục dùng snapshot version đã cấp.
- Đã chạy **30/30 test backend liên quan**, **2/2 test Playwright** và build backend/frontend. Chi tiết, giới hạn và lệnh tái hiện: [báo cáo cập nhật audio](../statics/results/module-2-audio-update-validation.md).

M02 hoàn thành phạm vi biên soạn và cung cấp học liệu. M03 sẽ triển khai bắt đầu lượt làm, trả câu an toàn, lưu câu trả lời, chấm bài và tiến độ. Hiện học viên xem được Unit/giới thiệu/hướng dẫn; preview của giáo viên không sinh điểm hoặc lịch sử làm bài.

Kết quả chạy kiểm tra được ghi riêng tại [bước 1](../statics/results/module-2-catalog-validation.md), [bước 2](../statics/results/module-2-materials-validation.md), [bước 3](../statics/results/module-2-authoring-validation.md) và [bước 4](../statics/results/module-2-seed-validation.md).

## Mở rộng vòng học và tương tác — 25/10/2026

Thêm ORDERING xuyên suốt biên soạn/xem trước/xuất bản; V9 mở rộng CHECK loại câu, giữ nguyên V1–V8 và seed 90 câu. Unit được trình bày thành vòng học ở khu vực STUDENT. Xem [tiến độ chung](LEARNING_JOURNEY_PROGRESS.md) và [biên bản kiểm tra](../statics/results/learning-journey-validation.md). Các số kiểm thử ở phần trước thuộc mốc lịch sử, không thay kết quả của lượt mới.
