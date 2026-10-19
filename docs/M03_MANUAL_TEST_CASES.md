# M03 — ma trận nghiệm thu và ca thao tác

Phạm vi: LEARN/PRACTICE, nháp, hint/check, nộp, kết quả/chữa và tiến độ. REVIEW thật, lịch ôn, XP và offline bền vững thuộc các module sau. Đây là quy trình cùng kết quả mong đợi; trạng thái chạy thực tế ghi riêng tại [biên bản nghiệm thu](../statics/results/module-3-validation.md).

## Chuẩn bị

- Docker Desktop, Node theo `source/client/package.json`, dependency đã cài bằng `npm ci`. Backend test chạy trong Docker với Java 17/MySQL 8.4/ffmpeg.
- Dùng STUDENT mới, TEACHER và ADMIN trong môi trường test; không ghi mật khẩu thật vào báo cáo. Seed chuẩn: 3 Unit, 6 Lesson, 90 Question, 13 Material, 7 Asset. LEARN cấp 10 câu gồm GUIDED và INDEPENDENT.
- Thao tác UI theo [hướng dẫn giao diện](M03_UI_SMOKE.md); gọi API với session/CSRF theo [hướng dẫn API](M03_API_SMOKE.md). Chỉ truy cập lượt do tài khoản đang đăng nhập sở hữu.
- E2E mặc định dùng project `english-learning-e2e`, UI/API 5174/8081. Kiểm tra bền vững dùng `english-learning-m03-acceptance`, 5175/8082. Hai môi trường có volume riêng với demo 5173/8080; không dùng `down -v` hoặc xóa volume.

## Ca thao tác trên web

| ID | Dữ liệu và bước thực hiện | Kết quả mong đợi |
|---|---|---|
| UI-01 | STUDENT → Khám phá → Unit → giới thiệu bài chưa học | Mô tả không trùng, nút Bắt đầu học, số câu và mục tiêu rõ; chưa lộ đáp án |
| UI-02 | Bắt đầu bài seed; xem từng câu, điều hướng qua lại | 10 câu, đủ chọn/điền/ghép theo bài; giữ đáp án nhập; không lộ keys/transcript |
| UI-03 | Nhập một câu, để câu khác trống; Lưu nháp; F5, Back/Forward, mở URL trong tab cùng tài khoản | Đáp án và câu đang xem khớp bản đã ACK; Attempt ID/bộ câu không đổi |
| UI-04 | Câu GUIDED: mở hint, check đáp án đầy đủ, sửa và check lại; sang INDEPENDENT | GUIDED có hỗ trợ/feedback đúng; không transcript trước nộp; INDEPENDENT không có hint/check |
| UI-05 | Nhập đủ, CHECK mọi GUIDED rồi nộp, kể cả có đáp án sai | Xác nhận nộp; bài COMPLETED; kết quả có FIRST/final và giải thích/transcript |
| UI-06 | Nộp khi còn trống/partial hoặc chưa CHECK đủ GUIDED | Cảnh báo nộp sớm; nộp được, không hoàn thành Lesson; số chưa trả lời/điểm đúng |
| UI-07 | Chữa một câu sau nộp rồi tải lại kết quả | Phản hồi CORRECTION; Submission ID, FIRST, final, điểm và tiến độ gốc giữ nguyên |
| UI-08 | Bài đã hoàn thành → học lại → nhập dở → trở về giới thiệu | Nút Tiếp tục làm mở đúng lượt dở; trạng thái COMPLETED không bị hạ; xem được lần nộp gần nhất |
| UI-09 | PRACTICE chọn Topic/độ khó/số câu, nộp và mở từ lịch sử | Đúng mode/bộ lọc, không hint/check; thiếu câu không lặp để đủ số; không hoàn thành Lesson |
| UI-10 | Hai tab cùng revision, tab A lưu rồi tab B lưu bản khác | B nhận 409 và giữ bản nhập; chỉ ghi lại sau khi đối chiếu, chọn rõ và bấm lưu |
| UI-11 | Interception: cho PUT/POST commit thật rồi bỏ phản hồi; nhập thêm nếu UI cho phép; gửi lại | Không báo đã lưu/nộp giả; replay đúng key/payload, GET đối soát; ACK cũ không thay bản nhập mới |
| UI-12 | Trì hoãn response 17 giây sau commit để vượt timeout fetch 15 giây | Hiện chưa rõ kết quả, chặn mutation tiếp theo; gửi lại không tạo hiệu ứng lần hai |
| UI-13 | Fixture 401/503 khi đang ghi; đăng nhập lại cùng user rồi gửi lại | 401 dừng ghi; bản nhập/pending giữ trong phiên trang; 503 không báo thành công; replay đúng payload |
| UI-14 | Đăng xuất chủ động khi còn bản chưa lưu; đăng nhập user khác và mở URL cũ | Có xác nhận bỏ bản nhập; bộ nhớ cũ bị xóa, lượt người khác 404 |
| UI-15 | TEACHER/ADMIN mở danh mục/giới thiệu | Được khám phá; không nút tạo lượt thật, không gọi attempt/progress |
| UI-16 | Audio: nghe, tua, lưu, F5; thử đoạn nghe có metadata fixture; viewport 390px | Lưu giây tuyệt đối, khôi phục đúng, clamp theo đoạn/thời lượng; không tràn ngang |
| UI-17 | Dùng bàn phím chọn radio/nhập/điều hướng; prompt dài, input fill quá 200 ký tự | Điều khiển có nhãn/focus; mobile không tràn; fill giới hạn 200 ký tự |

Ca UI-11–13 cần interception có kiểm soát như [Playwright M03](../source/client/e2e/module-three-learning.spec.ts), không dùng ngắt mạng tùy ý để kết luận server đã commit. Fixture 401/503 và metadata không thay thế kiểm tra quyền/media thật ở backend.

## Ma trận API, dữ liệu và đồng thời

| ID | Thao tác hoặc lỗi được ép trong test | Kết quả mong đợi | Điểm kiểm tra tự động |
|---|---|---|---|
| API-01 | DB trống lên V4, DB V3 có user lên V4; ghi JSON và vi phạm FK/unique/CHECK | Giữ dữ liệu cũ, Hibernate validate, rollback khi sai | AttemptPersistenceTest |
| API-02 | STUDENT/TEACHER/ADMIN, không CSRF, owner khác, actor bị khóa khi save chờ | Quyền/status đúng, không ghi trái phép | AttemptApiTest, AccountAdministrationApiTest |
| API-03 | Tạo/resume/replay LEARN; PRACTICE đủ/thiếu/rỗng/copy origin; exposure ít/cũ | Pin Version/slot/permutation, không lặp origin, exposure tăng đúng một lần | AttemptApiTest |
| API-04 | REVIEW chưa provider; provider giả lập trả Version hợp lệ | REVIEW_NOT_READY ở runtime; contract provider được test riêng | AttemptApiTest |
| API-05 | Publish keys/audio mới, retire học liệu sau khi đã tạo lượt hoặc nộp | Lượt cũ giữ bản chốt, quyền media lịch sử và kết quả cũ | AttemptApiTest, AudioUpdateApiTest |
| API-06 | Draft full item có null/partial; thiếu/trùng/lạ item; playerState sai | Nháp không tạo trial/chấm; lỗi không ghi dở hoặc receipt thành công | AttemptApiTest, AttemptRequestsTest |
| API-07 | Ba dạng câu, ID giả, partial, NFC/case/whitespace/Locale | Outcome/completeness đúng hợp đồng; không fuzzy matching | AnswerGraderTest |
| API-08 | Hint/check, FIRST sai rồi final khác, dùng hỗ trợ trước/sau FIRST | FIRST bất biến; support ghi trước mở feedback; không transcript qua check | AttemptApiTest |
| API-09 | Submit full bản chưa lưu, null/partial, đủ câu nhưng thiếu CHECK | Một Submission; denominator theo câu thực cấp; lessonCompleted đúng | AttemptApiTest |
| API-10 | Hai create/save, submit cùng/khác key; save/check/submit cạnh tranh | Không nhân bản, không ghi đè revision; có một thao tác thắng hoặc replay | AttemptApiTest |
| API-11 | Bỏ body sau commit, replay key cũ sau mutation mới; đổi payload với cùng key | Replay status/body gốc trước kiểm tra revision/state; REQUEST_ID_REUSED khi khác payload | AttemptApiTest, RequestReceiptServiceTest |
| API-12 | Ép lỗi DB ở receipt/progress sau ghi answer/Submission | Rollback toàn bộ, không ACK thành công hoặc dữ liệu nửa chừng | AttemptApiTest |
| API-13 | CORRECTION, học lại dở, công bố version mới, bài retire/draft, phân trang owner | Điểm/FIRST không đổi; COMPLETED không hạ; updateAvailable/lịch sử/NOT_STARTED đúng | AttemptApiTest |
| API-14 | Media nguyên file, Range hợp lệ/sai, file vật lý mất, owner khác | 200/206/416/503 và quyền đúng; không storageKey | MaterialApiTest, AttemptApiTest |
| API-15 | Chạy lại các luồng auth/admin/catalog/material/authoring/audio/seed | M01/M02 vẫn đạt, không phá publication/thu hồi phiên | Toàn bộ JUnit và E2E |

Tên lớp kiểm tra nằm dưới `source/server/src/test/java/vn/edu/ptit/english/`. Chi tiết từng test và báo cáo XML là bằng chứng, bảng này không tự xác nhận PASS.

## Bền vững và seed chạy lại

Chạy từ `source/client`, sau `npm ci` và có `.env` hợp lệ ở root:

```powershell
node scripts/verify-m03-persistence.mjs
```

Script luôn dùng project riêng 5175/8082, tự build source rồi tạo tài khoản test mới. Nếu cổng đã bị ứng dụng khác chiếm, xử lý xung đột trước khi chạy; không đổi sang URL demo.

| ID | Quy trình | Kết quả mong đợi |
|---|---|---|
| DB-01 | Tạo LEARN hoàn thành và CORRECTION; bài thứ hai lưu một đáp án, vị trí câu/audio; chụp baseline qua API và số hàng DB | Có COMPLETED và STARTED; result gốc không đổi sau chữa; baseline có nháp và playerState |
| DB-02 | Restart riêng server, chờ seed mới xong; thử cookie cũ rồi đăng nhập lại | Cookie cũ 401; snapshot đầy đủ, nháp, result, progress và số hàng bằng baseline |
| DB-03 | Force recreate mysql/server/client, giữ cùng volume; kiểm tra ID container đều đổi; đăng nhập lại | Dữ liệu như DB-02; file audio còn nguyên SHA-256, Range 206 đúng 16 byte |
| DB-04 | Sau mỗi lần seed, replay đúng request save và submit đã commit | Header replay đúng, ACK/Result gốc giữ nguyên; không tăng số hàng học liệu/lượt/trial/receipt/exposure/progress |

Script dừng container test khi xong, giữ volume để chẩn đoán; không xóa dữ liệu demo. Kết quả chi tiết tại `.test-state/m03-step6-persistence.json`, log Docker riêng bị Git ignore. Không đưa bản đáp án, cookie hoặc mật khẩu vào hồ sơ commit.

## Giới hạn diễn giải

Restart/recreate giữ volume không phải thử mất ổ đĩa hoặc khôi phục backup. Các lỗi response dùng HTTP/browser interception, không phải benchmark mạng. Bản nhập chưa ACK chỉ ở bộ nhớ trang: reload/đóng tab sau khi bỏ cảnh báo có thể mất phần chưa commit. Biên bản ghi rõ kiểm tra tự động và phần chưa có phiên thao tác thủ công độc lập.
