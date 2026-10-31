# Kiểm tra tích hợp main và giám sát phiên — 31/10/2026

Nguồn `7c171f1` + main `fe3ab0c`, nhánh đích `feature/admin-session-monitoring`. Cách xử lý 13 conflict và các điểm ghép ngoài conflict tại [báo cáo tích hợp](../../docs/ADMIN_MAIN_INTEGRATION.md). Chỉ dùng các kết quả bên dưới cho source lần tích hợp này; các số 361/361, 117/117 hoặc nghiệm thu từng nhánh trước đó là lịch sử riêng.

## Kết quả

- TypeScript build, Docker build client/server PASS. Không còn marker conflict. Bản kiểm tra dùng project riêng `english-learning-e2e-admin-main-20261031`, cổng 15323/18233 và volume riêng; không nâng DB của bản contest 15223.
- **132 ca backend khác nhau đã qua qua các lượt**, không phải một lượt 132/132: lượt 16 suite/132 ca có 130 PASS và 2 FAIL do fixture contest nhảy đồng hồ vượt idle 300s. Sau khi fixture đăng nhập lại mà không tắt idle policy, chạy riêng hai ca đó **2/2 PASS**, BUILD SUCCESS, 38.893s. Lượt rộng 6:56 phút. Các lần chạy bị Docker/WSL hoặc thiếu pagefile chặn không tính là PASS.
- Các suite backend gồm AccountSocketHandler (9), AdminSessionService (8), AttemptApi (51, gồm audio hai lượt), AttemptAudioMigration (1), AttemptPersistence (7), ContentGovernanceMigration (1), ContestApi (13), ContestPaperApi (8), ContestPaperMigration (1), EngagementMigration (1), LessonActivities (9), OrderingMigration (1), RoundNumberMigration (1), SessionActivityApi (7), SessionActivityRegistry (9), WritingApi (5).
- Migration V17 → V18 chạy đúng một migration, giữ tài khoản và lịch sử V14 activities, tạo bảng bộ đếm audio, chạy lại không làm thêm. Kiểm tra byte nội dung sau chuẩn hóa newline xác nhận V1–V17 không đổi so với main; V18 giữ nguyên DDL audio của nhánh nguồn. Chuỗi 1–18 không trùng số.
- **33 ca browser khác nhau đã qua qua hai lượt**: lượt đầu 32/33 PASS trong 4.0 phút; một ca bài 18 câu còn kỳ vọng native audio hiển thị/cho tua theo main cũ. Đổi ca đó kiểm tra khung RestrictedAudio, nghe bằng nút và xác nhận playsUsed=1; chạy lại **1/1 PASS**, 10.3s. Không khôi phục cơ chế tua để chiều kiểm thử cũ.
- Nhóm 33 ca bao phủ bảy phần hoạt động, inline reading/cloze và chữa bài bất biến; giám sát phiên/khóa tài khoản; năm kịch bản audio gồm chống tua, đổi câu, replay, reload, mobile/kết quả và lỗi file; kho đề/quyền giám thị, bốn dạng đề thi, lưu/nộp/BXH/thu hồi/visibility/tìm kiếm; viết/chấm/trả lại, route, đồng thời và replay nộp.
- Hai ca browser mới PASS: hết phiên ngay sau khi gõ bài viết vẫn flush local trước debounce và khôi phục đúng user; hết phiên khi chưa autosave contest giữ URL/phòng và đáp án qua khôi phục/claim rồi lưu lên server.
- **9/9 browser timeout thật PASS** trên stack riêng với idle 20s/warning 5s. Gồm Continue/ACK, GET và heartbeat không gia hạn, audio không gia hạn, nháp offline qua hết phiên thật, nộp mất ACK qua login lại, nhiều tab cùng phiên, hai thiết bị độc lập, lỗi IndexedDB không giữ UI mãi và đăng ký lại tab hết metadata. Cấu hình test không ghi vào `.env` hoặc bản demo.
- Như vậy **42 ca browser khác nhau đã qua qua ba lượt**, không ghi thành một lượt 42/42.
- Harness: ConnectionManager **14/14**, tracker **11/11**, IndexedDB/journal bằng Chrome thật **6/6**. Giữ đồng thời ca gắn tab trước/sau HELLO ACK và ca contest/reconnect/permission.
- Kiểm tra whitespace và liên kết tài liệu PASS. Các file merge gồm mã nguồn, cấu hình khởi động, tài liệu, migration và kiểm thử; không chứa `.env`, backup SQL, log, trace, node_modules, target hoặc dist.

## Môi trường và giới hạn

Docker ban đầu chưa chạy, sau đó gặp hết pool subnet, lỗi WSL, thiếu bộ nhớ/pagefile và MySQL test khởi tạo dở. Override subnet/JVM/test timeout ở `.test-state` riêng; volume test khởi tạo dở được giữ, dùng volume test mới, không xóa dữ liệu. Người dùng đã cho phép tạm dừng hai demo `english-learning-rounds-e2e` và `english-learning-project` để giảm tải. JVM chạy xác nhận hai ca cuối dùng một tiến trình, CPU 2, SerialGC và heap giới hạn; không đổi cấu hình máy.

Sau kiểm thử đã dừng stack test riêng và khởi động lại sáu container của hai demo đã được tạm dừng theo phép người dùng. Bản contest 15223 được giữ nguyên. Volume test và volume demo vẫn giữ nguyên.

DB từng chạy **V14 audio của nhánh admin cũ** cần kế hoạch chuyển lịch sử trên bản sao trước khi chạy source sau merge. Lượt này không chuyển đổi DB nhánh cũ, không repair lịch sử hoặc reset volume. Không có bằng chứng đo tải, nhiều backend, ngủ máy vật lý hoặc nâng DB nhánh cũ trực tiếp. Tất cả artifact nhạy cảm/log/ảnh chỉ giữ local và đã ignore.

Tái hiện bộ rộng: Maven `-Dtest=AttemptApiTest,AttemptPersistenceTest,SessionActivityApiTest,SessionActivityRegistryTest,AdminSessionServiceTest,AccountSocketHandlerTest,ContestApiTest,ContestPaperApiTest,WritingApiTest,LessonActivitiesTest,ContentGovernanceMigrationTest,EngagementMigrationTest,OrderingMigrationTest,RoundNumberMigrationTest,ContestPaperMigrationTest,AttemptAudioMigrationTest test`.

Browser: Playwright các file `activity-variety`, `audio-listening`, `admin-session-monitoring`, `unit-writing`, `online-contests`; timeout thật chạy `session-idle.spec.ts` với `E2E_IDLE_TESTS=1` và stack test cấu hình riêng. Không trỏ bộ ghi dữ liệu này vào demo đang sử dụng.
