# Hướng dẫn làm việc với dự án English Learning

Đây là bản đồ dự án dành cho các phiên làm việc mới và khi tiếp tục trong cùng một phiên. Tra cứu ở đây trước, sau đó chỉ mở những file liên quan đến yêu cầu đang làm. Không đọc lại toàn bộ repository hoặc lặp lại những file chưa thay đổi. Trao đổi với người dùng bằng tiếng Việt.

## 1. Bắt đầu một phiên làm việc

Chạy từ thư mục gốc:

```powershell
git status --short
git branch --show-current
git log -3 --oneline
```

- Đọc yêu cầu mới nhất, xác định module/bước cần thực hiện và phần việc đã được người dùng đồng ý.
- Kiểm tra diff trước khi sửa file đang có thay đổi. Giữ nguyên công việc chưa commit của người dùng hoặc phiên trước; không ghi đè, reset hay dọn file không thuộc phạm vi.
- Không tự tạo nhánh, commit hoặc push khi chưa được yêu cầu. Khi được giao triển khai, thực hiện cả phần kiểm tra phù hợp và cập nhật tài liệu liên quan.
- Mốc tiến độ dưới đây là thông tin tham chiếu, không thay thế trạng thái Git và mã nguồn thực tế.
- Khi một thông tin đã rõ từ phiên hiện tại, tiếp tục làm; không hỏi lại những lựa chọn hoặc quyền đã được người dùng xác nhận.

## 2. Tổng quan hệ thống

English Learning là bài tập lớn môn **Lập trình mạng của PTIT**, xây dựng ứng dụng học tiếng Anh theo mô hình client–server. Hệ thống hiện tập trung vào học liệu có phiên bản và quá trình làm bài có thể lưu, tiếp tục, nộp, chữa và theo dõi tiến độ.

Có ba vai trò:

| Vai trò | Chức năng chính |
|---|---|
| STUDENT | Khám phá bài công bố; học bài/luyện tập; lưu nháp; dùng hỗ trợ đúng luật; nộp, xem kết quả và chữa bài của mình |
| TEACHER | Quản lý nội dung do mình biên soạn; sử dụng nội dung công bố được phép; xem trước học liệu |
| ADMIN | Quản trị tài khoản/quyền/trạng thái và quản lý học liệu theo quyền quản trị |

Đăng ký công khai luôn tạo STUDENT. TEACHER/ADMIN không tạo lượt học thật qua giao diện học viên. API lớp học từ nền cũ vẫn còn nhưng việc tham gia lớp không bắt buộc cho luồng học hiện tại.

Đặc trưng của bài tập mạng nằm trong cách xử lý phiên đăng nhập, WebSocket, timeout, mất phản hồi sau commit, gửi lại request và ghi đồng thời. Chưa có bằng chứng đo tải hoặc hiệu quả học tập; không tự mô tả dự án như một hệ thống nhiều backend, offline đầy đủ hoặc ứng dụng đã nghiệm thu toàn bộ.

### Mốc đã kiểm tra ngày 19/10/2026

| Module | Trạng thái thực tế |
|---|---|
| M01 — Tài khoản và quản trị | Đã có đăng ký/đăng nhập, session/CSRF, quản trị, kiểm tra quyền hiện hành, nền WebSocket và thu hồi kết nối |
| M02 — Học liệu | Đã có danh mục, file/đọc/nghe/ảnh, biên soạn/xuất bản/phiên bản, cập nhật audio hàng loạt và learner catalog |
| M03 — Học/luyện tập | Đã triển khai bước 0–5: dữ liệu/bộ chấm, API lượt làm/nháp/feedback/nộp/chữa/tiến độ và giao diện |
| M03 bước 6 | Đã nghiệm thu ma trận/hồi quy, restart/recreate/seed/replay giữ dữ liệu và tài liệu bàn giao; chưa commit bước 6, người dùng tạo PR trên web sau |
| M04/M05/M06/M07 | Lần lượt: lịch ôn, XP/giải đấu tuần, offline/phục hồi đầy đủ, thống kê nâng cao; chưa triển khai đầy đủ |
| M08 | Thử thách cá nhân đang hoãn; không tự đưa vào phạm vi hiện tại |

Seed M02 có **3 Unit, 6 Lesson, 90 Question, 13 Material**. Mỗi bài biên soạn có 5 GUIDED, 5 INDEPENDENT và 5 REVIEW_VARIANT; lượt LEARN hiện cấp 10 câu GUIDED + INDEPENDENT theo selector.

Mốc Git: `bd913be` — bước 0; `d37ba0b` — bước 1; `759ccb3` — bước 2–3; `2f09404` — bước 4; `97a881d` — bước 5. Luôn kiểm tra lại Git trước khi kết luận phần nào đã commit.

Bằng chứng bước 6 trên source `97a881d`: backend **136/136**, build frontend đạt, toàn bộ browser E2E **47/47** gồm **14 ca M03**, script bền vững đạt cả restart và recreate/seed/replay/media. Xem [biên bản toàn module](statics/results/module-3-validation.md). Đây là kết quả của mốc đã ghi nhận, không tự áp dụng cho thay đổi sau này.

## 3. Cấu trúc thư mục

```text
English-Learning-Project/
├── AGENTS.md                 Bản đồ dự án và quy tắc làm việc
├── README.md                 Chạy ứng dụng và giới thiệu chức năng hiện có
├── compose.yml               MySQL + server + client; volume dữ liệu/file
├── compose.dev.yml           Mở MySQL ra localhost để phát triển/IDE
├── start.cmd                 Điểm chạy thuận tiện trên Windows
├── scripts/                  Khởi tạo môi trường, script chạy Windows/Linux
├── source/
│   ├── client/
│   │   ├── src/              React/TypeScript, giao diện và adapter API
│   │   ├── e2e/              Playwright cho M01/M02/M03 và điều hướng
│   │   ├── scripts/          Runner E2E tạo môi trường Docker riêng
│   │   ├── nginx.conf        Phục vụ frontend, proxy HTTP/WebSocket
│   │   └── Dockerfile        Build Vite rồi đóng gói nginx
│   ├── server/
│   │   ├── src/main/java/vn/edu/ptit/english/   Mã backend
│   │   ├── src/main/resources/
│   │   │   ├── application.yml                Cấu hình qua biến môi trường
│   │   │   ├── db/migration/                  Flyway V1–V4 đang chạy
│   │   │   └── demo/                          Dữ liệu seed học liệu
│   │   ├── src/test/                          Unit/integration/contract tests
│   │   └── Dockerfile                         Runtime và target test
│   └── common/               Hợp đồng API, OpenAPI, ví dụ payload
├── docs/                     Tiến độ, kế hoạch, bàn giao, kiến trúc, checklist
├── statics/                  Nguồn học liệu, sơ đồ và báo cáo kiểm thử
├── report/                   Nội dung phục vụ báo cáo môn học
└── .test-state/              Log/ảnh/tệp tạm để kiểm tra; đã bị Git ignore
```

`.env` chứa cấu hình và thông tin xác thực, không được commit hoặc in nội dung ra log. Không đưa credentials thật, dữ liệu người dùng hoặc artifact build vào Git. Các thư mục `node_modules/`, `target/`, `dist/`, `test-results/`, `playwright-report/` cũng được ignore.

## 4. Kiến trúc chạy và giao tiếp mạng

Công nghệ: **React 19 + TypeScript + Vite**, **Java 17 + Spring Boot 3.5**, **Spring Security**, **Spring Data JPA/Hibernate**, **MySQL 8.4**, **Flyway**, **Docker Compose**. Xem package.json/pom.xml khi cần phiên bản chính xác.

```text
Trình duyệt
  ├── React: giao diện, điều hướng hash, trạng thái nhập
  ├── /api/*: HTTP JSON, cookie phiên, CSRF, upload và media Range
  └── /ws/events: WebSocket xác thực bằng phiên HTTP
             │
       nginx hoặc Vite proxy
             │
       Spring Boot :8080
          ├── Security/filter + controller/service/repository
          ├── MySQL: tài khoản, phiên bản nội dung, lượt làm, kết quả, receipt
          └── AssetStorage: file ảnh/audio trong asset_data
```

- Compose local: trình duyệt vào **5173**, nginx trong container nghe **8080**, proxy tới service `server:8080`. Backend cũng được mở trên localhost **8080**.
- Khi phát triển bằng Vite, `vite.config.ts` proxy `/api` và `/ws`; mặc định tới `127.0.0.1:8080`, có thể đổi bằng `API_PROXY_TARGET`.
- Dùng HTTP cho nghiệp vụ học, quản trị và học liệu. WebSocket hiện là nền kiểm tra phiên/heartbeat và thu hồi kết nối; chưa phải kênh đồng bộ toàn bộ tiến độ hoặc giải đấu.
- Session HTTP và danh sách kết nối nằm trong RAM của một backend. Restart có thể cần đăng nhập lại; tài khoản, nội dung, nháp và kết quả đã commit nằm trong DB.
- File thật nằm trong volume, metadata nằm trong MySQL. Không chuyển file sang lưu trực tiếp trong cột JSON hoặc trả đường dẫn lưu trữ nội bộ cho client.

## 5. Kiến trúc backend và trách nhiệm từng lớp

Luồng chuẩn:

```text
Controller → Service interface → service.impl
           → Repository interface → repository.impl → Spring Data JPA → MySQL
```

| Package | Trách nhiệm |
|---|---|
| `controller/` | Route HTTP, nhận principal/body, gọi parser/service, dựng status/header/response |
| `dto/` | Request/response, parser nghiêm, cấu trúc JSON; tách dữ liệu công khai và nội bộ |
| `service/` | Hợp đồng nghiệp vụ và các điểm mở rộng giữa module |
| `service/impl/` | Nghiệp vụ, transaction, khóa, kiểm tra trạng thái, chọn phiên bản, chấm và ánh xạ DTO |
| `repository/` | Hợp đồng truy cập dữ liệu và Spring Data JPA repository |
| `repository/impl/` | Adapter triển khai hợp đồng repository, truy vấn/khóa/flush cần thiết |
| `entity/`, `enums/` | ORM, khóa ghép, kiểu trạng thái và dữ liệu JSON đã định nghĩa |
| `security/` | Principal, kiểm tra tài khoản hiện hành, khóa actor, quản lý phiên/kết nối |
| `socket/`, `config/` | WebSocket, origin, đăng ký security và cấu hình framework |
| `bootstrap/` | Bootstrap admin, tài khoản demo và seed học liệu |
| `exception/` | Lỗi nghiệp vụ và chuyển lỗi thành hợp đồng HTTP |

Không serialize entity trực tiếp cho API; không để controller tự thực hiện transaction/nghiệp vụ. Giữ kiến trúc JPA hiện có, không thay bằng JDBC thủ công chỉ để thêm một chức năng. `open-in-view=false`: đọc quan hệ/ánh xạ DTO trong phạm vi transaction thích hợp.

### Điểm vào theo nghiệp vụ

| Nghiệp vụ | Thành phần nên tìm trước |
|---|---|
| Đăng nhập/quản trị | AuthService, AdminUserService, AccountGuard, AccountGuardFilter |
| Topic/Objective/Unit | CatalogService, CatalogRepository |
| File và Material | AssetService, MaterialService, AssetStorage, MediaInspector, AssetReadGrant |
| Question/Lesson và xuất bản | AuthoringService, AuthoringPublication, ContentRepository |
| Cập nhật audio hàng loạt | AudioUpdateService và controller/implementation tương ứng |
| Danh mục cho người học | LearnerCatalogService |
| Đọc học liệu nội bộ cho M03 | PublishedContentReader |
| Tạo/tiếp tục/danh sách lượt | AttemptService, AttemptSelector, AttemptRepository |
| Snapshot và nháp | AttemptSnapshotReader, AttemptDraftService, AttemptDraftValidator |
| Hint/check/nộp/chữa/kết quả | AttemptCompletionService, AnswerGrader, AnswerValidator |
| Tiến độ | LearningProgressService |
| Gửi lại request | RequestReceiptService và request_receipts |
| Quyền file theo lượt | AttemptAssetReadGrant |
| Chọn câu ôn tương lai | ReviewSelectionProvider |

Dùng `rg` tìm tên lớp; chỉ mở implementation, DTO và test của luồng đang sửa.

## 6. Kiến trúc frontend

Gốc mã: `source/client/src/`.

| File/thư mục | Vai trò |
|---|---|
| `App.tsx` | Khôi phục user, chọn màn hình theo vai trò, đăng xuất/hết phiên, kết nối WebSocket |
| `navigation.ts` | Điều hướng hash/history, Back/Forward và tham số màn hình |
| `api.ts` | Cookie cùng origin, CSRF/refresh token CSRF, timeout 15 giây và ApiError |
| `features/auth/` | Đăng nhập/đăng ký |
| `features/admin/` | Quản trị tài khoản |
| `features/content/` | Danh mục, tác giả, preview, học liệu và LearningCatalog |
| `features/learning/` | Giao diện làm bài, trạng thái đồng bộ, kết quả và chữa |
| `components/`, `styles.css` | Thành phần và phong cách dùng chung |

Trong `features/learning/`:

- `types.ts`: DTO học viên và hàm xác định đã trả lời/đầy đủ; không dùng DTO tác giả chứa keys để dựng câu trước nộp.
- `api.ts`: adapter create/read/list/mutation/result; tái sử dụng `api.ts` chung để giữ cookie/CSRF.
- `LearningWorkspace.tsx`: nối catalog vào LEARN; form PRACTICE và lịch sử.
- `LessonIntroduction.tsx`: trang giới thiệu gọn, tóm tắt số câu, CTA bắt đầu/tiếp tục/học lại theo lượt thực tế và kết quả gần nhất. Bài COMPLETED vẫn có thể có lượt học lại dở; khi tìm kết quả gần nhất phải so submittedAt, không lấy updatedAt của lượt vừa chữa làm thứ tự nộp.
- `AttemptRunner.tsx`: runner chung, điều hướng câu, hint/check, lưu và xác nhận submit.
- `AnswerFields.tsx`, `LearningMaterial.tsx`: renderer đáp án và đọc/ảnh/audio công khai.
- `AttemptResult.tsx`: điểm, lần đầu/khi nộp, giải thích/transcript và form chữa.
- `session.ts`: bộ nhớ theo user/Attempt, pending command, tuần tự hóa mutation và đối soát.

Hiện tại dùng **lưu nháp chủ động bằng nút**, chưa có autosave. Không báo đã lưu trước ACK. Bộ nhớ phiên trang giữ bản nhập khi chuyển màn SPA và khi cùng user đăng nhập lại; đăng xuất chủ động/đổi user xóa bộ nhớ. App giữ route học khi hết phiên và cảnh báo reload/đóng tab nếu còn thay đổi.

Pending command giữ đúng key/payload tới khi replay và GET đối soát xong; chặn mutation tiếp theo khi kết quả chưa rõ. Generation bảo vệ bản nhập mới hơn khỏi ACK cũ. 409 cần lựa chọn rõ ràng sau so sánh, không tự tăng revision rồi ghi đè.

URL/history chỉ chứa màn hình, ID và bộ lọc; không chứa đáp án hoặc nội dung học liệu. F5/deep link đọc Attempt đã có, không gọi create để chọn lại câu. Chưa có IndexedDB/localStorage/outbox: không khẳng định phần chưa commit được lưu bền vững tại máy.

## 7. Mô hình dữ liệu và vòng đời nghiệp vụ

### Các nhóm bảng

| Migration | Dữ liệu chính |
|---|---|
| V1 | users, classrooms, class_members |
| V2 | Quản trị tài khoản, trạng thái/security_version và account_control |
| V3 | Topic/Objective/Unit, Asset, Material/Question/Lesson và các Version/join, request_receipts |
| V4 | attempts, attempt_questions, attempt_answers, answer_trials, submissions, question_exposures, lesson_progress |

Flyway quản lý schema; Hibernate giữ `ddl-auto=validate`. Không sửa migration đã áp dụng, không dùng create/update để chữa lệch schema. Thay đổi schema tiếp theo cần migration mới. SQL dưới `docs/database/planned/` là tài liệu hợp đồng, không tự chạy thay migration runtime.

### M02 — học liệu có phiên bản

`Topic → Objective/Unit → Lesson`; LessonVersion tham chiếu các QuestionVersion theo nhóm/vị trí, QuestionVersion có thể tham chiếu MaterialVersion. Root giữ định danh/con trỏ phiên bản; bản công bố được giữ làm lịch sử khi tạo bản chỉnh sửa hoặc ngừng cấp.

Luồng tác giả: tạo/lưu draft → preview/kiểm tra phụ thuộc → publish → chỉnh sửa thành phiên bản mới hoặc retire. Xuất bản phải kiểm tra lại preview token và toàn bộ phụ thuộc dưới khóa. Giữ thứ tự khóa actor, rồi tài nguyên theo quy trình hiện có; publication khóa Material → Question → Lesson, tăng dần ID trong mỗi nhóm, refresh sau khi chờ khóa.

`originKey` biểu diễn nguồn câu hỏi: sao chép hoặc tạo phiên bản mới không làm câu trở thành nguồn mới. Seed có checkpoint/seedKey, giữ phần tác giả đã tùy chỉnh; không viết seed đè nháp/lịch sử hoặc reset dữ liệu học viên.

### M03 — lượt làm và kết quả

```text
Chọn LEARN/PRACTICE → tạo hoặc tiếp tục Attempt IN_PROGRESS
  → snapshot theo phiên bản đã chốt → nhập/lưu nháp
  → hint/check cho GUIDED → submit full snapshot
  → Attempt SUBMITTED + Submission bất biến
  → đọc kết quả, chữa bằng CORRECTION, xem tiến độ
```

- Ba loại câu hiện có: SINGLE_CHOICE, FILL_BLANK, MATCHING. Dùng ID ổn định của option/blank/pair, không dùng chỉ số hiển thị.
- Chốt Version ID, origin, slot, permutation và dấu seen khi tạo lượt. Lượt cũ tiếp tục dùng bản đã chốt dù nội dung được xuất bản lại, đổi audio hoặc retire.
- Exposure tăng khi tạo lượt mới; resume/GET/replay không tăng lại.
- Attempt chỉ có IN_PROGRESS/SUBMITTED. `revision` là revision nghiệp vụ, không dùng JPA `@Version`; mỗi mutation thật chỉ tăng một lần dưới khóa.
- Lưu nháp nhận full bộ câu/playerState, không tạo trial và không chấm.
- Hint/check chỉ cho GUIDED đang làm; mở lại hint không tăng revision thêm. Check/retry cần response đầy đủ.
- Check tạo FIRST hoặc GUIDED_RETRY; ghi FIRST.supportUsed trước khi mở feedback. Có hint trước đó thì support=true; phản hồi check không trả transcript chung audio.
- Submit luôn nhận full đáp án/playerState mới nhất, không lệ thuộc autosave. Cho phép null/partial; tạo FIRST nguồn SUBMIT cho item chưa có trial, kể cả UNANSWERED.
- FIRST bất biến; đáp án cuối và trial chữa là dữ liệu khác nhau. Sau nộp, retry chỉ thêm CORRECTION và tăng revision, không thay attempt_answers, FIRST, Submission, điểm hay tiến độ.
- Chấm theo QuestionVersion đã chốt. Fill tuân thủ NFC, các cờ chuẩn hóa khoảng trắng/case và Locale.ROOT; không tự thêm fuzzy matching.
- Số đếm independentFresh chỉ lấy FIRST của INDEPENDENT/REVIEW_VARIANT chưa gặp trước đó, chưa dùng hỗ trợ và có nhập đáp án; partial vẫn là answered. Không lấy GUIDED hoặc CORRECTION để tính thành tích lần đầu độc lập.

Mỗi Attempt có đúng một Submission. Trong transaction, tạo Result nội bộ có submissionId=0 và factsSchemaVersion=1; lưu qua `AttemptRepository.save(SubmissionEntity)` để INSERT, gắn ID thật vào result_json rồi flush. Không gọi JPA save trực tiếp hoặc trả Result tạm.

Submit lưu final/trials/Submission/state/revision/progress/receipt **cùng một transaction**. GET result đọc kết quả đã lưu, không chấm lại theo nội dung hoặc trial mới. submittedAt của Result dùng JSON STRING để tránh mất độ chính xác khi MySQL lưu epoch dạng số.

LEARN hoàn thành khi mọi đáp án đầy đủ và mọi GUIDED đã CHECK trước submit; không yêu cầu đúng hết. DB chỉ lưu STARTED/COMPLETED, API tổng hợp NOT_STARTED. Học lại/nộp dở không hạ COMPLETED. Tiến độ gồm bài đang công bố và lịch sử riêng; bài retire có currentPublishedVersionId=null. updateAvailable so bản công bố với bản hoàn thành gần nhất, hoặc bản lượt gần nhất nếu chưa hoàn thành.

REVIEW chưa có provider M04 thì trả REVIEW_NOT_READY; không chọn câu ngẫu nhiên để giả lập tính năng đã hoàn thành. Chưa cộng XP hoặc phát sự kiện nghiệp vụ chỉ có trong RAM thay cho dữ liệu Submission bền vững.

## 8. Quyền, transaction và độ tin cậy

### Phiên đăng nhập và quyền dữ liệu

- Dùng session cookie HttpOnly/SameSite và CSRF hiện có; không tự thêm JWT. Danh tính lấy từ UserPrincipal, không tin userId/owner do client gửi.
- AccountGuardFilter kiểm tra trạng thái/security_version hiện hành. Mutation phải khóa/kiểm tra lại actor bằng AccountGuard trong transaction trước khóa tài nguyên nghiệp vụ.
- Quản trị M01 còn có account_control; giữ nguyên quy trình khóa của service hiện tại. Đổi quyền/khóa/xóa thu hồi phiên và socket sau commit.
- Không trả passwordHash/securityVersion hoặc storageKey ra DTO. Không cho đọc/sửa lượt của người khác; tài nguyên riêng không được phép xem trả 404 theo hợp đồng.
- PublishedContentReader là nội bộ, có keys/transcript; phải ánh xạ sang DTO học viên an toàn. Snapshot không chứa keys/giải thích/transcript kể cả sau nộp; chi tiết lấy qua feedback/result đúng thời điểm.
- AssetReadGrant là điểm mở rộng quyền file; STUDENT chỉ được đọc file đúng phiên bản thuộc lượt của mình, kể cả sau retire/nộp. Giữ hỗ trợ HTTP Range.

### Gửi lại và ghi đồng thời

Thứ tự ghi chuẩn: khóa actor → tìm receipt/replay → khóa tài nguyên và đọc lại → kiểm tra state/revision → validate → ghi nghiệp vụ + receipt → commit → trả thành công.

RequestReceiptService lưu theo actor + requestId, kiểm tra operation/payload hash; replay phải xảy ra trước kiểm tra revision/state. Cùng key khác operation/payload trả REQUEST_ID_REUSED. Receipt và nghiệp vụ phải cùng transaction, kể cả status đã chọn; overload Supplier<Reply> phục vụ create mới 201/resume 200.

Không phát ACK thành công trước commit. Một lỗi ở bước lưu receipt/progress phải rollback cả dữ liệu đã ghi trước đó. Sau khi chờ khóa phải đọc/refresh lại trạng thái; không quyết định bằng entity đã đọc trước khi chờ.

| Tình huống | Cách xử lý cần giữ |
|---|---|
| Timeout/mất phản hồi/503 | Chưa thể kết luận thao tác có commit hay chưa; giữ bản nhập và gửi lại đúng key/payload rồi GET đối soát |
| 409 REVISION_CONFLICT | Giữ dữ liệu nhập, lấy bản server và yêu cầu đối chiếu; không tự ghi đè |
| 401 | Dừng ghi, yêu cầu đăng nhập lại; tránh làm lẫn bộ nhớ giữa hai user |
| Submit đồng thời | Chỉ một Submission; cùng key replay, khác key bị từ chối theo state/revision |
| ACK/replay cũ | Có thể có revision cũ; không dùng nó thay bản nhập hoặc trạng thái mới hơn |

## 9. Tài liệu cần đọc theo công việc

| Việc đang làm | Đọc trước |
|---|---|
| Tiếp tục M03 | [Tiến độ](docs/M03_PROGRESS.md), rồi đúng bước trong [kế hoạch](docs/M03_IMPLEMENTATION_PLAN.md) |
| Luật nghiệp vụ M03 | [Bàn giao M02–M03](docs/M02_M03_HANDOFF.md), mục 2, 4, 6–7; [quy tắc API](source/common/M02_M03_API.md), mục liên quan |
| Trường/kiểu payload chính xác | Đúng schema/path trong [OpenAPI](source/common/openapi-m02-m03.json), [ví dụ](source/common/examples-m02-m03.json), [fixture](source/server/src/test/resources/contracts/m03-fixtures.json) |
| Giao diện học/phục hồi | [Hướng dẫn UI](docs/M03_UI_SMOKE.md), session.ts và test M03 |
| Thử API M03 | [Hướng dẫn API M03](docs/M03_API_SMOKE.md) |
| Nghiệm thu/bàn giao M03 | [Ma trận và ca thao tác](docs/M03_MANUAL_TEST_CASES.md), [kết quả toàn module](statics/results/module-3-validation.md) |
| M02/tác giả/seed | [Tiến độ M02](docs/M02_PROGRESS.md), service/test tương ứng; [nguồn học liệu](statics/dataset/README.md) |
| ORM/schema | Migration runtime liên quan, [thiết kế DB](DATABASE_DESIGN.md), [kiến trúc ORM](docs/ORM_ARCHITECTURE.md) |
| Kiến trúc chi tiết | [Cấu trúc backend](docs/BACKEND_STRUCTURE.md), sau đó đối chiếu lớp hiện tại |
| Chạy/cấu hình | Đúng phần trong [README](README.md), Compose/Dockerfile/script liên quan |
| Báo cáo/phạm vi môn học | [Hướng dẫn](INSTRUCTION.md), [nộp bài](SUBMISSION.md), [kế hoạch tổng](PLAN_ENGLISH_LEARNING_SYSTEM.md) |

OpenAPI quy định cấu trúc request/response; API rules/handoff quy định hành vi/transaction; progress và validation ghi phạm vi thực sự đã làm. Ví dụ JSON là các mẫu độc lập, không mặc định là một dataset liên kết hoàn chỉnh.

Một số đoạn trong API.md còn mô tả mốc cũ. Không dùng câu “chưa triển khai” cũ để phủ nhận code hiện tại; đối chiếu tiến độ, Git và implementation. Nếu hợp đồng và code mâu thuẫn, ghi rõ điểm lệch, xử lý theo yêu cầu thay vì âm thầm đổi nghiệp vụ cho thuận tiện.

`docs/archive/` chứa thiết kế IELTS lịch sử; không mặc định là yêu cầu hiện hành. Dùng `rg`/`rg --files` tìm đúng symbol/section; không in toàn bộ OpenAPI, toàn bộ tài liệu hoặc mọi test chỉ để tìm một endpoint.

## 10. Chạy ứng dụng và kiểm thử

### Chạy local

```powershell
# Từ root, Docker Desktop đang chạy
.\start.cmd
```

UI: http://localhost:5173; health backend: http://localhost:8080/api/health. Cấu hình qua .env/application.yml: nhóm DB_*, ASSET_DIRECTORY, BOOTSTRAP_ADMIN_*, DEMO_*, WS_ALLOWED_ORIGINS và SESSION_COOKIE_SECURE. Bootstrap không tự reset tài khoản đã tồn tại. Tra cứu tài khoản demo/cách tạo môi trường trong README, không sao chép mật khẩu thật vào hướng dẫn làm việc.

Compose có mysql_data và asset_data. Build/chạy backend mới có thể áp dụng migration còn thiếu lên DB cấu hình. Không restart demo chỉ để kiểm tra tài liệu; không down -v, truncate hoặc xóa volume để chạy test/nâng schema.

### Backend

Từ `source/server`, cần Java 17, Docker cho Testcontainers và ffprobe/ffmpeg khi chạy trực tiếp:

```powershell
.\mvnw.cmd '-Dtest=AnswerGraderTest,AttemptRequestsTest,AttemptPersistenceTest' test
.\mvnw.cmd test
```

Hoặc từ root:

```powershell
docker build --target test -t english-learning-test ./source/server
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-test mvn -B -ntp test
```

Mỗi lần đổi code cần rebuild test image, hoặc chủ động bind mount source hiện tại và để Maven compile lại. Không báo kết quả từ image cũ như bằng chứng của source mới. Test Spring có cấu hình WebSocket cần RANDOM_PORT để có servlet container thật. Integration dùng MySQL Testcontainers riêng, không DB demo.

### Frontend và E2E

Từ `source/client`:

```powershell
npm ci
npm run build
npx playwright install chromium
npm run test:e2e -- module-three-learning.spec.ts
# Khi cần hồi quy toàn giao diện:
npm run test:e2e
```

Có thể dùng Chrome cài sẵn qua PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH, xem [nghiệm thu bước 5](statics/results/module-3-step5-validation.md). Trên Windows, nếu sandbox chặn tiến trình con Vite/browser, dùng quyền thực thi phù hợp; không sửa code ứng dụng để né lỗi môi trường. Nếu npm bị ép offline, xử lý tùy chọn/cache môi trường, giữ nguyên lockfile khi không đổi dependency.

Runner mặc định build/chạy project **english-learning-e2e** ở **5174/8081**, volume riêng, đợi seed rồi test và dừng container. E2E có ghi dữ liệu; không trỏ E2E_BASE_URL vào demo 5173. Chỉ dùng biến này khi đã chủ động chọn một môi trường test riêng.

Nghiệm thu bền vững: từ `source/client`, chạy `node scripts/verify-m03-persistence.mjs`. Script tự build project **english-learning-m03-acceptance** ở **5175/8082**, tạo user test mới, kiểm tra nháp/kết quả/tiến độ/receipt/media qua restart server và force recreate cả ba container, seed chạy lại; dừng container khi xong và giữ volume riêng. Không chấp nhận URL demo. JSON/log trong `.test-state/m03-step6-*` bị ignore; xem [biên bản](statics/results/module-3-validation.md) trước khi diễn giải số hàng hoặc kết quả.

### Chọn phạm vi kiểm tra

- Sửa tài liệu: kiểm tra diff, liên kết và tính đúng của mô tả; không cần chạy lại cả ứng dụng.
- Sửa riêng frontend: production build và các E2E liên quan; mở rộng hồi quy khi đụng App/navigation/auth hoặc kết thúc tích hợp.
- Sửa service/DTO/bộ chấm: test tập trung tương ứng; mở rộng backend khi đổi thành phần dùng chung, migration hoặc hoàn tất tích hợp.
- Thử lỗi mạng/concurrency/rollback cần bằng chứng thật phù hợp. Ghi rõ fixture HTTP, mock metadata, bỏ response sau commit và timeout thật khác nhau thế nào; không gọi đó là benchmark mạng.
- Test đã qua thì không chạy lại không cần thiết nếu không có thay đổi hoặc vấn đề mới.

Báo cáo hiện tại: [nghiệm thu M03](statics/results/module-3-validation.md). Lịch sử: [bước 0](statics/results/module-3-step0-validation.md), [bước 1](statics/results/module-3-step1-validation.md), [bước 2–3](statics/results/module-3-step23-validation.md), [bước 4](statics/results/module-3-step4-validation.md), [bước 5](statics/results/module-3-step5-validation.md). Không ghi PASS cho kiểm tra chưa chạy.

## 11. Chẩn đoán nhanh

| Hiện tượng | Kiểm tra trước |
|---|---|
| Docker không resolve registry-1.docker.io | DNS/mạng/proxy Docker Desktop; đây có thể là lỗi kéo base image trước khi build source |
| UI chưa có chức năng vừa thêm | Image có được build từ source mới không; có đang mở đúng cổng/môi trường không |
| Health đã lên nhưng thiếu seed | Log hoàn tất curriculum seed; health không đồng nghĩa mọi ApplicationRunner đã xong |
| 403 khi ghi | CSRF và vai trò; dùng adapter API chung, không tự bỏ kiểm tra security |
| 401 hoặc socket 1008 | Trạng thái/security_version, cookie/phiên, origin và luồng thu hồi quyền |
| 409 khi lưu/nộp | Pending receipt, baseRevision, state và thao tác tab khác; đối soát trước khi gửi tiếp |
| Audio/ảnh lỗi | Grant đúng owner/Version, file trong volume, MIME/Range và ffprobe khi upload |
| Schema validate thất bại | Migration runtime/DB đang dùng; không bật ddl-auto=update hoặc sửa migration cũ để chữa tạm |

## 12. Duy trì tài liệu và bàn giao phiên sau

Sau thay đổi đáng kể, cập nhật progress/checkbox và validation của module với phạm vi đã làm, lệnh/kết quả thật và giới hạn còn lại. Cập nhật AGENTS.md khi có kiến trúc, điểm vào hoặc mốc tiến độ mới; không biến file này thành nhật ký từng tool call.

Trước khi kết thúc, kiểm tra `git diff --check` và `git status --short`; file mới cần kiểm tra whitespace/liên kết vì diff mặc định có thể chưa chứa nội dung untracked. Báo rõ phần đã commit/chưa commit, test thực sự chạy và việc còn chờ. Chỉ commit/push khi được yêu cầu.

Ưu tiên để phiên sau biết **dự án đang ở đâu, sửa ở đâu, phải giữ điều gì và đã kiểm tra đến đâu**. Khi cần chi tiết, dẫn tới file chuyên trách thay vì sao chép toàn bộ đặc tả hoặc báo cáo vào đây.
