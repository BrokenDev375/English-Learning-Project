# Kiểm tra bài viết cuối vòng — 29/10/2026

Phạm vi: source working tree trên b2b9f26; không dùng số kiểm thử lịch sử để mô tả thay đổi này.

## Kết quả

| Kiểm tra | Kết quả thực tế |
|---|---|
| TypeScript/Vite và build Docker server/client từ source mới | PASS; cảnh báo bundle >500 kB vẫn còn |
| WritingApiTest, MySQL Testcontainers riêng | **5/5 PASS**, 0 failure/error/skip; Maven BUILD SUCCESS, 2:18 phút gồm clean/compile |
| Hồi quy backend 10 lớp liên quan | **98/98 PASS** trong một lượt, 0 failure/error/skip; BUILD SUCCESS, 11:00 phút |
| Browser bài viết mới | **3/3 PASS** trong một lượt cuối, 37,3 giây |
| Browser hồi quy activity-variety, learning-journey, module-six-recovery | **25/26 PASS**, 4,1 phút; một kỳ vọng câu thông báo cũ được cập nhật rồi chạy riêng **1/1 PASS**, 25,6 giây |
| Manager kết nối | **10/10** tình huống deterministic PASS |
| IndexedDB/journal M06 trên Edge | **6/6 PASS** |
| JSON/OpenAPI refs, migration duy nhất V1–V15, UTF-8/whitespace/marker và liên kết tài liệu mới | PASS |

Tổng **103 ca backend khác nhau** đã qua qua hai lượt và **29 ca browser khác nhau** đã qua qua các lượt. Không phải một lượt 103 backend hay 29 browser toàn bộ cùng lúc. Không chạy toàn bộ suite backend/browser của repository trong lượt này.

Backend hồi quy gồm AttemptActivityProjectionTest (1), AttemptApiTest (47), AttemptPersistenceTest (7), AuthoringApiTest (17), ContentGovernanceApiTest (13), ContentGovernanceMigrationTest (1), EngagementMigrationTest (1), LessonActivitiesTest (9), OrderingMigrationTest (1), RoundNumberMigrationTest (1).

## Hành vi đã kiểm tra

WritingApiTest dùng HTTP thật, session/CSRF, database MySQL riêng:

- Chỉ mở viết sau khi hoàn thành Lesson; nộp bài mở vòng sau trước khi chấm; trả bài để sửa không khóa lại vòng.
- Chốt đề và tiêu chí khi bắt đầu; công bố lại không đổi bản chốt. Nộp nhận text cuối đầy đủ, độc lập autosave. Sửa/nộp lại giữ nguyên Submission/Grade cũ.
- Lưu, bắt đầu, nộp và chấm replay đúng status/body; cùng key khác payload bị từ chối; revision cũ không ghi đè.
- STUDENT không vào API giáo viên, TEACHER/ADMIN không tạo bài viết học viên; giáo viên khác không đọc/chấm vòng riêng. Đổi phân công thu hồi quyền cũ, người mới tiếp quản.
- Chấm phải đủ tiêu chí theo ID đã chốt; sai rubric không đổi trạng thái. Server cộng điểm từ checkbox, không nhận score client.
- Hai request nộp cùng key đồng thời chỉ có một Submission; hai quyết định chấm khác nhau chỉ một thành công. Các quyền và ràng buộc định nghĩa/unknown field được kiểm tra.

Điều kiện hoàn thành Lesson trong WritingApiTest được tạo bằng fixture DB, không mô tả như đã làm bài qua UI; riêng các thao tác bài viết đều gọi API thật. Browser tạo tiến độ bằng nộp LEARN qua API trên môi trường E2E, không bỏ qua khóa vòng.

Browser mới kiểm tra giáo viên sửa/công bố đề, học sinh lưu/reload/nộp, giáo viên tích tiêu chí chấm 6/10 và cho sửa, học sinh xem phản hồi rồi nộp bản thứ hai. Ca mất ACK chủ động bỏ phản hồi **sau commit**, reload/khôi phục IndexedDB rồi replay byte-for-byte cùng payload, chỉ có một Submission. Ca hai tab kiểm tra so sánh hai văn bản và lựa chọn bản máy chủ. Đây là bỏ phản hồi có kiểm soát, không phải mô phỏng toàn bộ sự cố mạng.

Ảnh QA: `.test-state/writing-mobile.png`, viewport 390×844; đã xem ảnh và kiểm tra không tràn ngang. Các log bị Git ignore: `.test-state/writing-backend.log`, `writing-regression.log`, `writing-browser-final.log`, `writing-browser-regression.log`, `writing-journey-final.log`.

## Lỗi đã đối soát

Lượt backend đầu bị trùng migration V12 trong `target/classes` từ artifact cũ trước khi đổi V12 activities sang V14. Chạy `mvn clean` rồi kiểm tra lại đạt; không sửa/repair lịch sử migration trong DB.

Lượt browser mới đầu 1/3 PASS: fixture đổi danh tính qua API cần reload ứng dụng, và nhãn textarea cần aria-label ổn định khi React khôi phục văn bản. Đã sửa fixture/nhãn, build image lại và lượt cuối 3/3 PASS. Hồi quy cũ chỉ thất bại tại chuỗi thông báo hoàn thành vòng đã đổi; kiểm tra số bài/submit/state trước đó đạt. Đã đổi kỳ vọng sang câu mới và ca đó chạy riêng PASS; không bật retry hoặc bỏ qua ca.

## Bản chạy cho người dùng

Project `english-learning-integrated` tại **5176/8083** đã build/chạy code mới, Flyway **V14 → V15** thành công. Trước nâng sao lưu `.test-state/unit-writing/before-v15.sql`, không đưa vào Git. Đối chiếu ngay trước/sau giữ **3 tài khoản, 1 Attempt, 0 Submission M03, 1 lesson_progress**; seed báo preservedLessons=6, không tạo/đổi học liệu cũ. Sau nâng có **0 writing task**: giáo viên tự cấu hình và công bố đề, không nạp đề/bài nộp/điểm giả vào DB của người dùng.

MySQL/server healthy, frontend HTTP 200, đăng nhập STUDENT và GET 3 vòng đạt. E2E dùng project/volume riêng `english-learning-e2e-writing` tại 5174/8081, đã dừng khi xong; không ghi vào demo. Baseline/main cũ giữ nguyên volume và trạng thái dừng. Tại thời điểm kiểm tra ban đầu, code và tài liệu chưa commit/push; sau đó người dùng yêu cầu bàn giao lên nhánh `addquestionsystem`.

## Giới hạn

Chấm tay đạt/chưa đạt từng tiêu chí, không có điểm một phần, AI, file đính kèm hoặc sửa đè điểm đã gửi. Giáo viên có thể cho nộp bản mới để chấm tiếp. Không cấp XP hoặc đưa điểm viết vào M07. IndexedDB áp dụng nháp/pending của học sinh; bản chưa commit local vẫn có thể mất, không hỗ trợ đăng nhập offline. Chưa đo tải hoặc kiểm tra sự cố mất điện. Kiểm thử migration bao gồm các lớp hồi quy ở trên và nâng DB integrated thực tế; không chuyển/repair các DB local từng dùng V12 activities cũ.

## Tách tạo đề và chấm bài — 29/10/2026

Theo phản hồi giao diện, luồng mới: **Quản lý học liệu → Bài viết cuối vòng → Tạo bài viết / Chấm bài**. Trang lựa chọn có hai thẻ, không mở đồng thời biểu mẫu và hàng đợi. WritingTaskEditor chỉ đọc Task; WritingQueue chỉ đọc bài nộp. URL writingMode=create/grade giữ màn hình qua reload/Back/Forward; writingSubmission giữ chi tiết chấm. Nút quay lại chức năng và đổi vòng kiểm tra nội dung đề/điểm chưa gửi trước khi rời.

- `npm run build`: TypeScript/Vite PASS, còn cảnh báo chunk >500 kB.
- `npm run test:e2e -- unit-writing.spec.ts`: **4/4 PASS trong một lượt, 31,9 giây**, trên project/volume E2E riêng. Gồm vòng đời tạo/chấm/sửa, lựa chọn chức năng và kiểm tra không gọi API của chức năng còn lại, reload/Back/Forward, replay sau mất ACK và so sánh hai tab.
- Đã xem ảnh `.test-state/writing-hub-desktop.png` và `writing-hub-mobile.png`; hai thẻ nằm hai bên trên desktop và xếp dọc trên mobile, không tràn ngang.
- Đã cập nhật riêng client của `english-learning-integrated` tại 5176. Không có thay đổi backend/schema trong lượt tách giao diện; không chạy lại kiểm thử backend. Dữ liệu người dùng không được dùng làm fixture.
- Log: `.test-state/writing-split-browser.log`, `.test-state/writing-split-run.log`. Các kết quả backend và browser rộng phía trên thuộc lượt triển khai trước khi tách giao diện, không phải kiểm thử đã chạy lại trong lượt này.
