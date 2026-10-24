# English Learning

## Demo M05 hiện tại

Mở Docker Desktop rồi chạy **`start-main.cmd`** từ thư mục dự án. Script dùng project `english-learning-main`, giữ volume hiện có, giao diện **http://localhost:5176**, API **http://localhost:8083/api/health**. Đây là bản đang dùng cho lần demo M05 này; `start.cmd` bên dưới dùng project/cổng mặc định khác.

Đăng nhập `student@english.local` / `Student@12345` hoặc đăng ký học viên mới, mở **Phần thưởng học tập**. Trang chủ có tổng XP/cấp độ/streak/huy hiệu và trần XP hôm nay; kết quả bài có phần XP và lý do; trang phần thưởng có ba huy hiệu và lịch sử chi tiết. Ôn tập cá nhân giải thích điều kiện thưởng; giải đấu hiển thị thời gian tuần còn lại theo máy chủ.

1. Chọn bài/chủ đề chưa từng làm, tự trả lời đúng các câu độc lập rồi nộp: mỗi nguồn câu mới hợp lệ được **5 XP**, tối đa **200 XP/ngày**. Câu GUIDED, câu đã gặp, trả lời sai/thiếu hoặc chữa lại không tạo XP câu mới.
2. Nộp đủ ít nhất `min(3, số câu độc lập được giao)` câu độc lập tự làm và đúng ít nhất một câu để ghi nhận ngày hoạt động. Streak tính theo ngày Việt Nam; làm lại câu cũ vẫn có thể giữ streak.
3. Hoàn thành bài LEARN đủ điều kiện nhận huy hiệu bài đầu tiên. Hai huy hiệu còn lại cần ôn đúng mốc hợp lệ và chuỗi 7 ngày; giao diện hiển thị tiến độ thật, không trao sẵn để demo.
4. Mở lại trang hoặc gửi lại cùng thao tác không cộng trùng. Nếu cập nhật chậm, đợi worker vài giây hoặc bấm **Cập nhật phần thưởng**. Hai bài nộp cũ trước lúc bật M05 hiển thị lý do không thưởng, không được hồi tố.

Đã có XP/cấp độ/streak/huy hiệu, worker, outbox và cập nhật qua WebSocket. Xem [tiến độ M05](docs/M05_PROGRESS.md), [kiểm tra XP](statics/results/module-5-xp-validation.md) và [kiểm tra Giải đấu tuần](statics/results/module-5-league-validation.md).

Nghiệm thu/bàn giao P0: [hướng dẫn M05](docs/M05_HANDOFF.md), [kết quả lượt cuối](statics/results/module-5-validation.md). Từ root chạy `node scripts/verify-m05-acceptance.mjs` để tái hiện backlog/restart/recreate/replay/ledger audit và workload local 20 học viên/40 socket/120 bài; cần Docker Desktop, `.env` hợp lệ và dependency frontend đã cài. Script chỉ dùng project/volume test riêng `english-learning-m05-acceptance` tại 15177/18084, dừng container khi xong và giữ volume.

### Demo Giải đấu tuần

Đăng nhập học viên → **Giải đấu tuần** → **Tham gia tuần này** rồi làm bài mới đủ điều kiện nhận XP. Bảng nhóm tự cập nhật, hiển thị XP tuần, thứ hạng tạm thời và dự kiến lên/giữ/xuống bậc. Điểm trước khi tham gia không được cộng ngược vào tuần. Mỗi tuần bắt đầu thứ Hai 00:00 Việt Nam; tuần sau phải chủ động tham gia lại.

Để thử hai học viên, dùng hai hồ sơ trình duyệt hoặc một cửa sổ thường và một cửa sổ ẩn danh. Hai tab trong cùng hồ sơ dùng chung cookie đăng nhập. Đăng ký học viên mới nếu muốn thử câu chưa gặp; không sửa database để tạo XP giả.

Nhóm tối đa 25 người cùng bậc, chỉ xét lên/xuống khi có ít nhất 10 thành viên và 5 người có XP. Nhóm nhỏ demo vẫn xếp hạng nhưng giữ bậc; không tạo người giả cho đủ số. Người bằng điểm cùng hạng, khối hòa vắt ranh giới giữ bậc. Bậc từ Đồng đến Kim Cương.

Sau hết tuần có 2 phút xử lý bài nộp thuộc tuần cũ, rồi job tự chốt từng nhóm và lưu **Lịch sử tuần của bạn**. Nguồn xét quá mốc vẫn có thể nhận XP cá nhân nhưng không thay bảng tuần cũ. Không có nút đổi giờ/chốt tuần sớm trong app thật; kiểm tra biên tuần dùng Clock trong test riêng.

M04 bổ sung ôn tập cá nhân từ kết quả M03: ưu tiên mục tiêu còn sai/tới mốc, chọn câu biến thể và gợi ý mức luyện. Xem [tiến độ M04](docs/M04_PROGRESS.md), [cách thử](docs/M04_MANUAL_TEST_CASES.md) và [kiểm tra](statics/results/module-4-validation.md). Luồng làm bài/lưu/nộp/chữa M03 tiếp tục được dùng chung.

Ứng dụng học và ôn tập tiếng Anh theo mô hình client–server. M01 có tài khoản/quản trị và nền session/WebSocket; M02 có học liệu phiên bản và seed 6 bài/90 câu; M03 có lượt làm bài; M04 có ôn tập cá nhân. XP và Giải đấu tuần thuộc M05.

## M06 — kết nối và phục hồi

LEARN/PRACTICE/REVIEW tự lưu tại máy bằng IndexedDB rồi autosave server; journal giữ request qua reload, socket có reconnect/heartbeat và presence nhiều tab. Xem [tiến độ M06](docs/M06_PROGRESS.md), [cách thử](docs/M06_MANUAL_TEST_CASES.md), [kết quả kiểm tra](statics/results/module-6-validation.md). Chưa có PWA/login offline hoặc tải trước toàn bộ audio.

## M04 — ôn tập cá nhân

Đăng nhập STUDENT → Học bài → tab **Ôn tập cá nhân**, hoặc bấm **Xem nội dung cần ôn** sau khi nộp. Trang tự cập nhật kết quả đã nộp, hiển thị mục tiêu còn sai, mốc nhắc, số câu mới/cũ và gợi ý mức luyện. Chọn chủ đề/số câu rồi bắt đầu ôn nội dung cần ôn hoặc mục tiêu còn lỗi sai.

Câu ôn lấy từ ngân hàng M02; ưu tiên REVIEW_VARIANT chưa gặp, cùng mục tiêu và mức cần luyện. Có thể nhận ít câu hơn yêu cầu khi kho thiếu. Lịch nhắc mặc định 1–3–7 ngày là quy tắc demo, không phải mô hình đã chứng minh tối ưu. Gợi ý tăng mức cần ít nhất hai bộ câu mới đủ dữ liệu và nội dung ở mức kế tiếp. Xem [API M04](source/common/M04_API.md) và [kế hoạch chi tiết](docs/M04_IMPLEMENTATION_PLAN.md).

## M03 — học và luyện tập

Chạy `start.cmd`, mở http://localhost:5173, đăng nhập STUDENT rồi chọn **Khám phá bài học** → Unit → giới thiệu bài → **Bắt đầu học** (hoặc **Tiếp tục làm/Học lại** theo trạng thái). Có ba dạng câu, đọc/ảnh/audio, gợi ý và kiểm tra cho câu hướng dẫn. M06 tự lưu tại máy rồi đồng bộ khi đủ điều kiện; nút **Lưu nháp lên máy chủ** gửi ngay. Nộp bài gửi toàn bộ đáp án mới nhất và cho phép nộp sớm. Kết quả giữ điểm gốc khi chữa; tiến độ hoàn thành bài khác số câu trả lời đúng.

Tab **Luyện tập theo chủ đề** chọn độ khó/số câu; **Lượt làm của tôi** lọc và mở lại lượt dở/đã nộp. Khi chưa nhận được xác nhận, journal giữ nguyên thao tác để gửi lại và đối soát; 409 yêu cầu lựa chọn bản dữ liệu rồi lưu rõ ràng. “Đã lưu tại máy” khác “Đã lưu trên máy chủ”; bản chưa commit IndexedDB vẫn có thể mất khi đóng tab. REVIEW được cấp qua M04 và dùng chung runner. Xem [cách thử và phục hồi lỗi M06](docs/M06_MANUAL_TEST_CASES.md).

## M02 — biên soạn học liệu đã có thể dùng thử

Đăng nhập tài khoản demo `teacher@english.local` / `Teacher@12345`, chọn **Quản lý học liệu**. Khu vực **Danh mục** có ba Unit mẫu và các tab tạo/sửa/tìm kiếm chủ đề, mục tiêu học, Unit. Chọn **Tài liệu** để xem 13 tài liệu đọc/nghe/ảnh hoặc tự tạo tài liệu: lưu nháp → xem trước → xuất bản → tạo bản chỉnh sửa. Có tải file tối đa 5 MiB, phát/tua audio và ngừng cấp tài liệu. ADMIN quản lý mọi tác giả; TEACHER sửa nội dung mình sở hữu và xem tài liệu đã công bố của người khác.

Chọn **Câu hỏi** để soạn chọn một đáp án/điền chỗ trống/ghép cặp, gắn tài liệu và xuất bản. Chọn **Bài học** để thêm hướng dẫn/ví dụ, chọn mục tiêu, thêm câu theo ba nhóm và sắp xếp. Luồng lưu nháp → xem trước → xuất bản kiểm tra cả bài và phụ thuộc trong một transaction; sao chép câu giữ nguồn gốc, chỉnh sửa giữ phiên bản cũ. Có sẵn 6 bài, mỗi bài 15 câu (5 hướng dẫn, 5 tự làm, 5 vận dụng mới); tổng 90 câu, gồm 30 câu mỗi dạng. Bài đầu là **A day with Linh — Morning routine**.

Menu **Khám phá bài học** dùng danh mục công bố M02 và nối vào lượt học M03 cho STUDENT. Giới thiệu/snapshot trước nộp không trả khóa đáp án/ngân hàng hoặc transcript. Xem [tiến độ hoàn thành M02](docs/M02_PROGRESS.md), [nguồn và manifest](statics/dataset/README.md), [nghiệm thu seed](statics/results/module-2-seed-validation.md).

Để thử từng chức năng, dùng [checklist 98 ca kiểm thử M02](docs/M02_MANUAL_TEST_CASES.md), có tài khoản/dữ liệu mẫu, bước thực hiện và kết quả mong đợi.

## Công nghệ và cấu trúc

React 19 + TypeScript + Vite; Java 17 + Spring Boot 3.5.16; Spring Security; Spring Data JPA/Hibernate; MySQL 8.4; Flyway; Docker Compose. Backend dùng controller → service interface → service.impl → repository interface/implementation → Spring Data JPA.

- source/client: giao diện, gọi HTTP và kết nối WebSocket.
- source/server: backend và migration.
- source/common/API.md: hợp đồng API M01.
- docs: kiến trúc, kiểm tra và ghi chú triển khai.
- statics: sơ đồ, kết quả kiểm thử thực tế.
- report: nơi chuẩn bị báo cáo.
- INSTRUCTION.md, TOPICS.md, SUBMISSION.md: tài liệu môn học.
- PLAN_ENGLISH_LEARNING_SYSTEM.md: kế hoạch 7 module và solo chờ xác nhận.

## Chạy bằng Docker trên Windows

Cần Docker Desktop đang chạy. Tại thư mục dự án:

~~~powershell
.\start.cmd
~~~

Hoặc chạy scripts/init-env.ps1 rồi docker compose up -d --build --wait. Giao diện: http://localhost:5173; backend: http://localhost:8080/api/health. Dùng đúng cổng 5173/8080. Nếu dự án cũ đang dùng các cổng này, dừng server/client của dự án cũ trước khi chạy; không cần xóa volume.

Đăng nhập admin: admin@english.local. Mật khẩu được tạo ngẫu nhiên trong dòng BOOTSTRAP_ADMIN_PASSWORD của file .env. Không commit .env. Với DEMO_ENABLED=true, tài khoản demo người học: student@english.local / Student@12345; người biên soạn: teacher@english.local / Teacher@12345. Chỉ dùng những tài khoản demo này cho môi trường local.

Bootstrap chỉ tạo admin khi email chưa tồn tại; khởi động lại không tự đổi mật khẩu, cấp lại quyền hoặc mở khóa tài khoản đã có.

Linux/macOS: sh scripts/start.sh (cần openssl). Khi dùng HTTPS/cổng khác phải cập nhật SESSION_COOKIE_SECURE và WS_ALLOWED_ORIGINS.

## M01 và quyền

Đăng ký công khai luôn tạo STUDENT. TEACHER là người biên soạn; ADMIN quản lý người dùng. Admin có thể tìm theo tên/email, lọc vai trò/trạng thái, phân trang, đổi quyền, khóa/mở khóa và xóa logic.

Xóa logic giữ hàng dữ liệu cùng lịch sử; không được đăng nhập, không mở lại, email chưa được tái sử dụng. Admin không tự khóa, xóa hoặc đổi quyền mình; hệ thống giữ ít nhất một admin hoạt động. Khóa/xóa/đổi quyền thu hồi tất cả session và đóng WebSocket của tài khoản sau commit. Mở khóa không phục hồi session cũ.

Session cookie HttpOnly + SameSite=Lax, CSRF cho request thay đổi dữ liệu, BCrypt 12. Mỗi request có kiểm tra trạng thái/phiên bản quyền trong DB; các thao tác ghi của M01 khóa và kiểm tra lại người thực hiện trong transaction. Các module viết tiếp phải gọi AccountGuard.lockCurrent trước khi ghi. Đây không phải tuyên bố chống mọi hình thức gian lận.

API lớp học từ nền tảng cũ vẫn được giữ; lớp học không bắt buộc cho app mới. Màn hình chính M01 tập trung tài khoản và quản trị.

## Dữ liệu bền vững

Compose project english-learning-project dùng hai volume riêng: mysql_data (MySQL) và asset_data (file). Không dùng volume IELTS hoặc IoT. docker compose down rồi up giữ volume; down -v/xóa volume/gỡ Docker có thể làm mất dữ liệu. Sao lưu MySQL và file trước khi xóa.

Flyway V1 tạo nền tài khoản/lớp; V2 bổ sung ADMIN, trạng thái và phiên bản thu hồi quyền; V3 tạo 13 bảng M02 học liệu/receipt; V4 thêm 7 bảng M03 lượt làm/đáp án/trial/Submission/exposure/progress. Metadata/nội dung/phiên bản và dữ liệu học đã commit ở MySQL, ảnh/audio thật ở volume asset_data. Backend tự áp dụng migration còn thiếu, không sửa migration đã chạy. Hibernate chỉ validate schema; không dùng ddl-auto=create/update. Session trong RAM có thể mất sau restart; đăng nhập lại để tải nháp/kết quả từ DB.

## Phát triển và kiểm thử

~~~powershell
cd source\server
.\mvnw.cmd test
~~~

Backend tests dùng Testcontainers MySQL 8.4 với DB dùng riêng cho test; không xóa DB phát triển. Java 17 trở lên. Chạy backend/test trực tiếp cần `ffprobe` và `ffmpeg` trên PATH (có thể đặt `MEDIA_PROBE_EXECUTABLE` cho đường dẫn ffprobe); Dockerfile đã cài cả hai. Nếu wrapper cần tải Maven/dependency, cần kết nối Internet. Có thể dùng Docker test target theo [kết quả bước 2](statics/results/module-2-materials-validation.md).

~~~powershell
cd source\client
npm ci
npm run build
npx playwright install chromium
npm run test:e2e
~~~

`npm run test:e2e` tự mở môi trường Docker riêng **english-learning-e2e**, frontend mặc định cổng **5174**, backend **8081**, database/file volume riêng; đợi seed hoàn tất rồi chạy Playwright và dừng môi trường test khi xong. Cần Docker Desktop và Chromium/`PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`. Dữ liệu test không ghi vào DB demo ở cổng 5173. Test biên soạn ngừng cấp các bài/câu đã công bố khi kết thúc; lịch sử còn trong DB test để chẩn đoán. Có thể đổi cổng bằng `E2E_APP_PORT`/`E2E_API_PORT`.

Kiểm tra nháp/kết quả/tiến độ, seed và replay qua restart/recreate bằng `node scripts/verify-m03-persistence.mjs` từ `source/client`. Script tự build/chạy project **english-learning-m03-acceptance**, cổng **5175/8082**, volume riêng; so sánh dữ liệu trước/sau, kiểm tra audio rồi dừng container, giữ volume. Cần `.env` hợp lệ ở root. Xem [quy trình nghiệm thu M03](docs/M03_MANUAL_TEST_CASES.md); không cần dừng hoặc xóa dữ liệu demo.

Chỉ khi chủ động đặt `E2E_BASE_URL`, runner mới dùng app đã chạy ở URL đó và không khởi động Docker riêng; các test có ghi dữ liệu nên chỉ trỏ vào môi trường dành cho kiểm thử. E2E tạo tài khoản có tiền tố e2e; thông tin admin đọc từ .env hoặc biến môi trường E2E_ADMIN_EMAIL/E2E_ADMIN_PASSWORD theo từng fixture. Có thể chạy frontend phát triển bằng npm run dev, backend bằng .\mvnw.cmd spring-boot:run; mở MySQL cho IDE qua docker compose -f compose.yml -f compose.dev.yml up -d mysql rồi cấp DB_URL/DB_USERNAME/DB_PASSWORD.

Xem [kiểm tra M01](statics/results/module-1-validation.md), [danh mục M02](statics/results/module-2-catalog-validation.md), [tài liệu M02](statics/results/module-2-materials-validation.md), [biên soạn câu/bài M02](statics/results/module-2-authoring-validation.md) và [seed M02 hoàn chỉnh](statics/results/module-2-seed-validation.md) để biết những kiểm tra thực tế đã chạy. Chưa có đo tải hoặc kết luận hiệu quả học tập.

## Mạng, giới hạn và contribution

HTTP truyền auth/API; `/ws/events` có cookie/origin, HELLO/HEARTBEAT/ACK, subscription owner và invalidation sau commit. M06 có presence riêng tại `/api/me/connections`; heartbeat mặc định 25 giây, liveness server 75 giây, sweep 5 giây. Cấu hình `WS_PRESENCE_TIMEOUT_MS`/`WS_SWEEP_MS` trong `.env`, Compose truyền tới backend. Socket hỏng không mặc định chặn HTTP. Sự kiện giải đấu chờ M05, dashboard tổng hợp chờ M07. Xem [hợp đồng M06](source/common/M06_API.md).

Session/kết nối nằm trong RAM của một backend, restart cần đăng nhập lại; account/role/status bền vững trong DB. Chưa triển khai rate limiting đăng nhập, khôi phục mật khẩu qua email hoặc nhiều backend. Các hướng contribution của toàn dự án vẫn cần thực nghiệm theo plan.

Nguồn: [Spring Security session management](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html), [Spring WebSocket](https://docs.spring.io/spring-framework/reference/6.2/web/websocket/server.html), hướng dẫn môn học tại INSTRUCTION.md.
