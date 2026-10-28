# Vòng học, trang chủ học viên và bài tập tương tác

Phạm vi được thống nhất ngày 25/10/2026, nền main `69fb0d1`. Một Unit tương ứng một vòng học; Lesson là bài trong vòng. Lịch thi và hệ thống hạng solo M08 tiếp tục để sau.

## Quy tắc đã triển khai

- [x] STUDENT xem Unit thành vòng học, có số bài đã hoàn thành/tổng bài và nút mở bài chưa hoàn thành tiếp theo theo thứ tự.
- [x] Vòng có roundNumber lưu cố định trong DB; sắp theo roundNumber/id trên toàn bộ nội dung đang công bố. Chỉ mở vòng chưa hoàn thành đầu tiên; hoàn thành tất cả Lesson trong vòng sẽ mở vòng sau. Vòng đã hoàn thành vẫn chơi lại được; không khóa theo điểm hoặc lịch.
- [x] Vòng hoàn thành khi mọi Lesson đang công bố trong vòng có tiến độ COMPLETED của học viên. Bài retire không nằm trong mẫu số; bài mới công bố có thể làm tăng tổng bài. Lịch sử hoàn thành từng bài vẫn giữ nguyên. Unit không có bài công bố không hiện như một vòng đã hoàn thành.
- [x] Khi cùng một Lesson có phiên bản mới, giữ COMPLETED theo luật M03 và hiển thị thông báo có bản cập nhật; không bắt học viên mất mốc hoàn thành cũ.
- [x] GET `/api/me/rounds` phân trang/lọc q/topicId, chỉ STUDENT, kiểm tra tài khoản hiện hành, transaction chỉ đọc REPEATABLE_READ. Đọc toàn bộ lộ trình và bulk Lesson/progress trước lọc để xác định khóa; danh sách lọc giữ thứ tự và hành vi catalog hiện hành.
- [x] Trang chủ ưu tiên lượt đang làm gần nhất → vòng chưa hoàn thành đang mở. Ôn tập cá nhân có lối vào riêng. Gợi ý vẫn tìm qua các trang nếu sáu vòng đầu đã hoàn thành. Có loading/lỗi/thử lại, thành tích M05 và tổng quan M07.
- [x] Trang chủ chỉ đọc dữ liệu; không tự sync M04, xử lý thưởng hay tham gia League. Không tạo mục tiêu ngày hoặc chính sách XP mới.
- [x] Chọn đáp án dạng thẻ; ghép cặp bằng bấm/chạm, bàn phím hoặc kéo thả. Ghép vào thẻ đã dùng sẽ chuyển cặp tương ứng; có nút bỏ ghép.
- [x] Dạng ORDERING hỗ trợ biên soạn, lưu nháp, xem trước, xuất bản, LEARN/PRACTICE/REVIEW, chấm, kết quả và chữa bài qua cơ chế chung.
- [x] ORDERING có 2–20 thẻ khi xuất bản, 1–10 thứ tự đúng. Mỗi thứ tự chứa đủ mỗi ID một lần. Các ID có cùng nội dung chữ được chấm tương đương; so nội dung NFC, không tự bỏ dấu/case hoặc thêm fuzzy matching.
- [x] Đảo thẻ được lưu một lần trong Display.optionIds của AttemptQuestion. F5/resume giữ cùng bộ và thứ tự. Đáp án lưu bằng tokenIds, tham gia autosave/journal/IndexedDB M06.
- [x] Snapshot không chứa acceptedOrders. Khóa và giải thích chỉ có ở feedback/result đúng điều kiện M03; chữa không thay FIRST/Submission.
- [x] Migration V9 chỉ mở rộng CHECK question_versions.type; giữ nguyên V1–V8 và dữ liệu seed 3 Unit/6 Lesson/90 Question hiện có.

## Kiểm tra

Build frontend PASS; focused backend 57/57, hợp đồng API 25/25, manager 9/9, IndexedDB/journal 6/6. Browser toàn bộ 95/95 trong một lượt; bổ sung kéo thả ORDERING mobile 1/1. Backend lượt đầy đủ 270/273, ba assertion version migration cũ đã sửa và nhóm migration 2/2 + persistence 7/7 qua. Tổng cộng 274 ca backend khác nhau đã qua qua các lượt, không phải một lượt 274/274. Kết quả cuối và giới hạn ghi tại [biên bản](../statics/results/learning-journey-validation.md).

## Thử chức năng

1. Đăng nhập STUDENT, mở Trang chủ, dùng gợi ý bài tiếp theo hoặc tiếp tục lượt đang làm.
2. Mở Vào chơi, chọn vòng đang mở, xem tiến độ và mở bài. Hoàn thành đủ bài theo điều kiện M03 rồi trở lại kiểm tra vòng sau đã mở; tải lại trang để đối chiếu dữ liệu server.
3. Đăng nhập TEACHER, mở Quản lý học liệu → Câu hỏi → Thêm câu hỏi → Sắp xếp từ thành câu. Nhập thẻ, các thứ tự đúng, giải thích và nguồn. Lưu, xem trước và xuất bản; thêm vào Lesson theo các nhóm GUIDED/INDEPENDENT/REVIEW_VARIANT như những dạng hiện có. Câu GUIDED cần gợi ý.
4. STUDENT làm bài có ORDERING, chọn một phần rồi lưu/tải lại, thử đổi vị trí bằng nút mũi tên. Kiểm tra câu GUIDED, nộp và chữa sau nộp. Thử ghép cặp bằng bàn phím hoặc kéo thả trên câu MATCHING.

Các ví dụ ORDERING do kiểm thử tạo trong môi trường riêng rồi retire; không ghi câu hỏi hoặc kết quả giả vào demo. Ngân hàng demo cũ không tự bị viết lại để đổi dạng câu.

## Cập nhật cách hoàn thành — 25/10 theo phản hồi người dùng

Nộp đủ câu thì tính hoàn thành, CHECK GUIDED là tùy chọn. Sau nộp hiển thị toàn bộ đáp án/giải thích và một nút xác nhận để về vòng. Bài đã nộp đủ theo luật cũ có thể xác nhận ngay để tăng tiến độ, không làm lại. Không thêm migration hoặc tự điều chỉnh điểm/thưởng cũ. Kiểm tra phiên bản này tại [biên bản đổi luồng](../statics/results/lesson-completion-flow-validation.md).


## Bản đồ vòng chơi và tên điều hướng — 26/10/2026

- Sidebar STUDENT hiện chỉ có hai lối vào chính: **Vào chơi**, **Ôn tập cá nhân**. Trong khu vực học có tab **Chơi theo vòng** và **Lịch sử làm bài**. TEACHER/ADMIN giữ luồng duyệt học liệu.
- Trang chủ có CTA chơi/tiếp tục, hai lối vào theo mục đích và tổng quan; bỏ danh sách vòng lặp. **Vào chơi** mở bản đồ: nút tròn có số vòng, đường nối, tiến độ, trạng thái mở/hoàn thành/khóa và lý do khóa.
- Số vòng và khóa được tính trước lọc/phân trang. Tìm chủ đề hoặc dán URL không vượt khóa. POST LEARN mới trả 409 ROUND_LOCKED khi có vòng trước chưa hoàn thành. GET /api/me/rounds/{id} dùng để hiển thị đúng trạng thái khi mở đường dẫn trực tiếp.
- Lượt đang làm từ trước chính sách mới vẫn tiếp tục được qua Lịch sử làm bài/trang chủ; receipt replay giữ hành vi cũ. Vòng đã COMPLETED luôn mở để chơi lại dù có khoảng trống ở vòng trước. Không tự sửa Submission, FIRST, điểm, phần thưởng hoặc dữ liệu hoàn thành cũ.
- PRACTICE và REVIEW độc lập điều kiện mở vòng; không tăng tiến độ hoàn thành vòng. Bài mới công bố có thể tăng mẫu số và khóa lại vòng chưa hoàn thành phía sau; bài retire được loại khỏi lộ trình. Số vòng là thứ tự hiển thị hiện tại, không phải ID vĩnh viễn.
- Không thêm migration. RoundJourneyReader dùng chung quy tắc cho API tiến độ và AttemptSelector. Mọi ghi vẫn theo transaction/actor lock/receipt M03.

Kết quả kiểm tra lần này tại [biên bản bản đồ vòng chơi](../statics/results/round-map-validation.md). Các số kiểm thử phía trên là bằng chứng của phiên bản trước.

## Chốt hai luồng học — 26/10/2026

- Bỏ sidebar/tab/thẻ trang chủ và form Luyện tự chọn. Không thay bằng mục Luyện thêm. Chơi lại bài/vòng vẫn nằm trong lộ trình, không xóa progress/kết quả cũ.
- Ôn tập cá nhân giải thích ba bước: nộp bài → xác định kiến thức cần ôn → thử câu hỏi cùng kiến thức. Mỗi mục tiêu hiển thị rõ lý do; tài khoản mới có nút về bản đồ để bắt đầu. Bỏ gợi ý tăng độ khó dẫn sang chức năng đã bỏ.
- GUIDED và INDEPENDENT vẫn nằm trong bài chơi; REVIEW_VARIANT ưu tiên cho ôn cá nhân theo M04. Giữ nguyên schema, ngân hàng, lựa chọn câu hỏi, điểm, XP và luật mở vòng.
- API PRACTICE và dữ liệu cũ giữ để tương thích. URL tab=practice không kèm attempt chuyển bằng replace về catalog; URL có attempt vẫn mở đúng lượt. Trang chủ/lịch sử tiếp tục mở được lượt cũ. Journal cũ còn PREPARED vẫn có nút gửi lại với cùng requestId/payload.
- Thay đổi chỉ frontend/tài liệu, không thay backend hoặc thêm migration. Bằng chứng lần này ở [biên bản hai luồng](../statics/results/two-learning-modes-validation.md); các số kiểm thử trước là lịch sử riêng.

## Game Săn kho báu — 26/10/2026

- [x] Cảnh đảo/nhân vật/rương trong lượt học và ôn, lối vào có hình rương trên trang chủ/giới thiệu bài.
- [x] Ghép thẻ có đường nối, mảnh chìa khóa và hoạt cảnh mở rương; xếp từ lắp ván và qua cầu; trắc nghiệm chọn cổng, điền từ mở khóa.
- [x] Nút chọn trò lấy đúng dạng câu có trong lượt, cuộn tới khu vực chơi. Lưu/khôi phục bằng draft M06 hiện có.
- [x] Chỉ feedback đúng cho đáp án hiện tại hoặc kết quả nộp mới mở rương. Không lộ khóa INDEPENDENT/REVIEW_VARIANT trước nộp.
- [x] Kho báu kết quả theo Submission, sao minh họa và xem lại hoạt cảnh không thêm XP hoặc sửa điểm.

Thiết kế/giới hạn tại [gameplay](GAMEPLAY_INTEGRATION_PROPOSAL.md), bằng chứng tại [biên bản](../statics/results/gameplay-validation.md). Demo chưa có ORDERING nên trò xây cầu chỉ hiện khi bài có dạng câu đó.

## Tên vòng đấu ở giao diện giáo viên — 27/10/2026

Thống nhất nhãn Unit → Vòng đấu trong quản lý học liệu, chọn vòng cho bài, sắp xếp bài, danh mục công bố và thống kê giáo viên. Bỏ badge Chặng; gom stage/sortOrder vào tùy chọn sắp xếp. Giữ API, dữ liệu và số vòng toàn lộ trình của học viên. Xem [quy ước](TEACHER_ROUND_TERMINOLOGY.md) và [kiểm tra](../statics/results/teacher-round-terminology-validation.md).

Bổ sung số vòng giáo viên ngày 27/10: thẻ và ô chọn vòng hiện số theo toàn bộ danh mục công bố, khớp lộ trình học viên và giữ số khi lọc riêng/tìm kiếm/phân trang. Vòng chưa trong lộ trình hiện Chưa có số; lỗi tải không đoán số. Dùng API công bố hiện có, không đổi backend/DB. Xem [kiểm tra](../statics/results/teacher-round-numbering-validation.md).

Số vòng lưu cố định ngày 27/10 (V12): units.round_number dương/duy nhất; thêm/sửa trực tiếp ở quản lý vòng, tạo mới tự cấp khi để trống. API danh mục, học viên và thống kê cùng dùng roundNumber, bỏ map số tính động. Backfill bảo toàn số công bố hiện tại; draft có số riêng phía sau. Giữ ID/tiến độ/XP/kết quả. Các mô tả không đổi backend/DB ở hai mốc giao diện ngay trên là bằng chứng lịch sử trước V12. Xem [quy ước](TEACHER_ROUND_TERMINOLOGY.md) và [kiểm tra](../statics/results/persistent-round-number-validation.md).

V13 ngày 27/10: ADMIN điều hành cấu trúc lộ trình, số vòng, phân công và duyệt bài/câu hỏi. Giáo viên biên soạn trong vòng được giao, gửi/rút yêu cầu; bản cũ giữ tới khi bản sửa được duyệt. Quyền và thống kê chuyển theo assignedTeacherId, ownerId giữ người tạo. Các mô tả giáo viên tự tạo/sửa vòng trước đây là mốc trước V13. Xem [quy tắc](CONTENT_GOVERNANCE.md), [kiểm tra](../statics/results/content-governance-validation.md).
