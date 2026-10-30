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

Contest mới ngày 30/10/2026 trên `feature/online-contests`: V16 phòng thi/vi phạm/BXH; V17 kho bộ đề có thể dùng lại, quyền canAuthor/canProctor độc lập. ADMIN phân quyền/duyệt bộ đề/chọn một paperId READY cho cuộc thi; teacher tác giả chỉ bộ đề của mình, giám thị theo quyền + proctorIds không nhận questions/keys qua API contest. Điểm vào ContestPaperController → ContestPaperServiceImpl → ContestPaperRepositoryImpl, ContestServiceImpl pin snapshot nguồn; UI features/contests (PaperLibrary/PaperEditor/ContestPermissions, form contest chỉ thiết lập). Đọc [luồng](docs/ONLINE_CONTESTS.md), [API](source/common/ONLINE_CONTESTS_API.md), [kiểm tra V17](statics/results/contest-paper-library-validation.md). Không sinh XP/ôn/vòng học; không áp dụng số kiểm thử V16/M01–M07 cho source V17.

English Learning là bài tập lớn môn **Lập trình mạng của PTIT**, xây dựng ứng dụng học tiếng Anh theo mô hình client–server. Hệ thống hiện tập trung vào học liệu có phiên bản và quá trình làm bài có thể lưu, tiếp tục, nộp, chữa và theo dõi tiến độ.

Có ba vai trò:

| Vai trò | Chức năng chính |
|---|---|
| STUDENT | Khám phá bài công bố; học bài/luyện tập; lưu nháp; dùng hỗ trợ đúng luật; nộp, xem kết quả và chữa bài của mình |
| TEACHER | Quản lý nội dung do mình biên soạn; sử dụng nội dung công bố được phép; xem trước học liệu |
| ADMIN | Quản trị tài khoản/quyền/trạng thái và quản lý học liệu theo quyền quản trị |

Đăng ký công khai luôn tạo STUDENT. TEACHER/ADMIN không tạo lượt học thật qua giao diện học viên. API lớp học từ nền cũ vẫn còn nhưng việc tham gia lớp không bắt buộc cho luồng học hiện tại.

Đặc trưng của bài tập mạng nằm trong cách xử lý phiên đăng nhập, WebSocket, timeout, mất phản hồi sau commit, gửi lại request và ghi đồng thời. Chưa có bằng chứng đo tải hoặc hiệu quả học tập; không tự mô tả dự án như một hệ thống nhiều backend, offline đầy đủ hoặc ứng dụng đã nghiệm thu toàn bộ.

Vòng học và tương tác ngày 25/10: Unit được trình bày thành vòng cho STUDENT, trang chủ có gợi ý tiếp tục/ôn/bài tiếp theo, MATCHING dùng thẻ và có ORDERING xuyên suốt. Source thêm V9; DB demo chưa nâng trong lượt này. Xem [tiến độ](docs/LEARNING_JOURNEY_PROGRESS.md) và [kiểm tra](statics/results/learning-journey-validation.md); không dùng số test M07 lịch sử thay bằng chứng của thay đổi mới.

### Mốc tiến độ cập nhật ngày 25/10/2026

| Module | Trạng thái thực tế |
|---|---|
| M01 — Tài khoản và quản trị | Đã có đăng ký/đăng nhập, session/CSRF, quản trị, kiểm tra quyền hiện hành, nền WebSocket và thu hồi kết nối |
| M02 — Học liệu | Đã có danh mục, file/đọc/nghe/ảnh, biên soạn/xuất bản/phiên bản, cập nhật audio hàng loạt và learner catalog |
| M03 — Học/luyện tập | Đã triển khai bước 0–5: dữ liệu/bộ chấm, API lượt làm/nháp/feedback/nộp/chữa/tiến độ và giao diện |
| M03 bước 6 | Đã nghiệm thu ma trận/hồi quy, restart/recreate/seed/replay giữ dữ liệu và tài liệu bàn giao tại commit `9b0f1e0`; người dùng tạo PR trên web sau |
| M04 — Ôn tập cá nhân | Đã có V5, backfill/ledger, dashboard, provider REVIEW và giao diện; xem docs/M04_PROGRESS.md và biên bản kiểm tra M04 |
| M05 — Gamification/Giải đấu tuần | Hoàn tất chặng 0–6 P0: XP/cấp độ/streak/3 huy hiệu, worker/sync/receipt, UI phần thưởng/League, V8, ranking/tie/chốt/history. Hồi quy 235 backend + 4 ca bổ sung riêng, 68 browser; phục hồi/ledger/socket/backlog và workload local đạt. Demo 5176 bằng start-main.cmd; đọc docs/M05_PROGRESS.md, docs/M05_HANDOFF.md và statics/results/module-5-validation.md |
| M06 — Kết nối/phục hồi | Đã có heartbeat/presence/subscription, IndexedDB/autosave/journal và phục hồi M03/M04; bản sửa 80d8f46 đã commit, đã tích hợp main 6f9387d, xem [tiến độ](docs/M06_PROGRESS.md) và [nghiệm thu](statics/results/module-6-validation.md) |
| M07 — Hồ sơ và thống kê | API/UI STUDENT, TEACHER theo Unit sở hữu và ADMIN; đã tích hợp main 20652a7 với XP/League M05 và heartbeat M06. Xem docs/M07_PROGRESS.md và statics/results/module-7-validation.md |
| M08 | Thử thách cá nhân đang hoãn; không tự đưa vào phạm vi hiện tại |

Tích hợp M07 ngày 25/10: Backend: 265/266 ở lượt đầy đủ, lỗi duy nhất do tên bảng trong fixture mới; sửa fixture rồi cả 3/3 ca tích hợp thưởng qua. Tổng cộng **266 ca backend khác nhau đã qua qua các lượt**, không phải một lượt 266/266 duy nhất. Browser **91/91 PASS** trong một lượt, **4.7 phút**. Build TypeScript/Vite và manager **9/9 PASS**. M05LearnerRewardsReader tái sử dụng GET service M05 với principal gốc, không thưởng/sync/join trên GET; snapshot quản trị lọc heartbeat hết hạn.

Tích hợp M05–M06 ngày 25/10 (bằng chứng trước M07): Backend **246/246 PASS** (0 failure/error/skip; BUILD SUCCESS, 13:21 min), browser **82/82 PASS** trong một lượt (4.1 phút), manager **9/9**, IndexedDB/journal **6/6** và build TypeScript/Vite PASS. Xem biên bản M06; không áp dụng các số lịch sử bên dưới cho bản mới.

Bằng chứng M06 ban đầu trên nền `57f2951` (lịch sử, trước tích hợp M05): backend **162/162**, E2E **66/66** (11 M06 + 55 hồi quy), manager harness **5/5**; script browser restart/recreate/replay/ledger/media đạt. Xem [biên bản M06](statics/results/module-6-validation.md), gồm các giới hạn còn lại. Không tự áp dụng bằng chứng này cho source thay đổi sau đó.

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
│   │   │   ├── db/migration/                  Flyway V1–V17
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
- Dùng HTTP cho nghiệp vụ học, quản trị và học liệu. WebSocket M06 giữ heartbeat/deadline, presence và subscription Attempt theo owner; cùng kết nối hỗ trợ invalidation GAMIFICATION/LEAGUE của M05. Reconnect đăng ký lại và GET đối soát, không cộng XP từ event.
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
| Ôn tập cá nhân | PersonalReviewService, ReviewRepository, PersonalReviewSelectionProvider, ReviewQuestionPool |
| Bằng chứng thưởng ôn M05 | ReviewRewardContextRecorder, GamificationReviewContextRecorder, GamificationContextRepository; chỉ capture khi tạo REVIEW mới |
| XP/streak/badge/worker M05 | GamificationServiceImpl, GamificationRules, GamificationRepositoryImpl, GamificationWorker, GamificationFailureTracker, GamificationEventDispatcher |
| Giải đấu tuần M05 | LeagueServiceImpl/LeagueRepositoryImpl, LeagueRanking, LeagueLifecycle, LeagueWorker, LeagueEventDispatcher và LeagueController |

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
| `features/gamification/` | RewardStrip/AttemptRewards/RewardsDashboard, LeagueDashboard; API đối soát XP/streak/badge/League và invalidation theo user/nhóm |
| `features/statistics/` | Hồ sơ STUDENT, thống kê ADMIN, TeacherStatistics theo Unit và các trạng thái dữ liệu/phân trang |
| `components/`, `styles.css` | Thành phần và phong cách dùng chung |

Trong `features/learning/`:

- `types.ts`: DTO học viên và hàm xác định đã trả lời/đầy đủ; không dùng DTO tác giả chứa keys để dựng câu trước nộp.
- `api.ts`: adapter create/read/list/mutation/result; tái sử dụng `api.ts` chung để giữ cookie/CSRF.
- `LearningWorkspace.tsx`: nối bản đồ vào LEARN, Ôn tập cá nhân và lịch sử; không có form PRACTICE, vẫn phục hồi lượt/lệnh cũ.
- `PersonalReview.tsx`: sync kết quả theo batch, dashboard cá nhân và tạo lượt REVIEW qua cùng start/pending command của M03.
- `LessonIntroduction.tsx`: trang giới thiệu gọn, tóm tắt số câu, CTA bắt đầu/tiếp tục/học lại theo lượt thực tế và kết quả gần nhất. Bài COMPLETED vẫn có thể có lượt học lại dở; khi tìm kết quả gần nhất phải so submittedAt, không lấy updatedAt của lượt vừa chữa làm thứ tự nộp.
- `AttemptRunner.tsx`: runner chung, điều hướng câu, hint/check, lưu và xác nhận submit.
- `AnswerFields.tsx`, `LearningMaterial.tsx`: renderer đáp án và đọc/ảnh/audio công khai.
- `AttemptResult.tsx`: điểm, lần đầu/khi nộp, giải thích/transcript và form chữa.
- `session.ts`: bộ nhớ theo user/Attempt, pending command, tuần tự hóa mutation và đối soát.

M06 dùng **autosave tại máy 250 ms và server debounce 1500 ms**, vẫn có nút lưu ngay. IndexedDB tách user/Attempt/writer, giữ baseline/draft/pending/pendingAck trong một hàng nguyên tử; journal riêng cho create/review-sync, lease cho sender. Chỉ báo lưu tại máy sau transaction complete, lưu server sau ACK/GET. Snapshot/feedback phải là DTO học viên đã được cấp đúng thời điểm.

Pending command giữ đúng key/payload tới khi replay và GET đối soát xong; chặn mutation tiếp theo khi kết quả chưa rõ. Generation bảo vệ bản nhập mới hơn khỏi ACK cũ. 409 cần lựa chọn rõ ràng sau so sánh, không tự tăng revision rồi ghi đè.

Cập nhật 20/10: `AttemptSession.playerRestoreVersion` đánh dấu lần nhận playerState từ server khi tải bản nháp hoặc chọn **Dùng bản máy chủ**. Runner truyền mốc này cho LearningMaterial để seek audio sau khi metadata sẵn sàng. Giữ bản local hoặc nhận ACK lưu thông thường phải giữ vị trí đang nghe; không seek lại theo mỗi tick cập nhật local. Hồi quy và phạm vi kiểm tra tại [bản sửa khôi phục audio](statics/results/module-3-audio-restore-validation.md).

URL/history chỉ chứa màn hình, ID và bộ lọc, không chứa đáp án/học liệu. Reload xác minh auth trước hydrate, không dùng cached userId làm bằng chứng phiên hợp lệ. Web Locks hỗ trợ thì giữ writer qua reload và lane theo document; không có thì writer mới, nhận lại bản cũ có lựa chọn và lease có expiry. localStorage chỉ có marker logout, sessionStorage chỉ có writer ID; không chứa đáp án/token. Logout fence callback cũ và xóa dữ liệu riêng, record sai được cách ly, không replay URL tùy ý. Chưa có PWA/login offline/cache toàn bộ media; bản chưa commit IndexedDB vẫn có thể mất.

Điểm vào M06: `features/connection/manager.ts` (clock/ACK/backoff/subscription), `storage/database.ts` (transaction/writer/lease/retention), `storage/journal.ts` (create/review sync), `session.ts` (working copy/pending). Backend: ConnectionService, LiveConnections, AccountSocketHandler và AttemptChanged AFTER_COMMIT. Không sửa luật chấm/ReviewPolicy hoặc migration V1–V5 để làm recovery.

Sửa M06 ngày 23/10: retention đọc/check/delete trong một readwrite transaction; create mới lưu riêng requestId/queuedAt trước khi đối soát command cũ, chỉ điều hướng bằng kết quả intent đã chọn; auth callback được fence theo generation kết nối. Hồi quy tập trung: `npm run test:connection`, `npm run test:recovery-storage` (Chrome qua PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH khi cần) và hai ca nhiều tab trong `module-six-recovery.spec.ts`. Xem tiến độ/biên bản M06 cho kết quả thực tế.

## 7. Mô hình dữ liệu và vòng đời nghiệp vụ

### Các nhóm bảng

| Migration | Dữ liệu chính |
|---|---|
| V1 | users, classrooms, class_members |
| V2 | Quản trị tài khoản, trạng thái/security_version và account_control |
| V3 | Topic/Objective/Unit, Asset, Material/Question/Lesson và các Version/join, request_receipts |
| V4 | attempts, attempt_questions, attempt_answers, answer_trials, submissions, question_exposures, lesson_progress |
| V5 | review_objective_progress, review_difficulty_evidence, review_processed_submissions |
| V6 | Nền M05: gamification policy/claim/ledger/profile/day/badge/processed/failure và review_reward_context; đã nâng local english-learning-main tại 5176/8083 theo yêu cầu chạy thử |
| V7 | gamification_outbox; đã áp dụng trên demo |
| V8 | league_seasons/group_allocators/groups/members/outbox; FK xp_ledger.season_id, kết quả frozen trên membership; đã áp dụng demo, schema tiếp theo cần migration mới |
| V9 | Mở rộng CHECK question_versions.type cho ORDERING; không thêm bảng hoặc viết lại dữ liệu; chưa nâng DB demo trong lượt triển khai này |
| V14 | `lesson_versions.activities_json` nullable: cấu trúc sáu hoạt động của bài nâng cấp 18 câu và ánh xạ đọc điền từ; không sửa lượt/bài cũ. Source đã có V10/V11 cho chính sách M05, xem ghi chú cuối file |
| V15 | Bài viết cuối Unit: task/version/work/submission/grade riêng; tiêu chí 10 điểm, chấm tay và giữ lịch sử; không đổi Submission/FIRST/XP M03–M05 |

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

- Bốn loại câu hiện có: SINGLE_CHOICE, FILL_BLANK, MATCHING, ORDERING (mở rộng 25/10). Dùng ID ổn định của option/blank/pair, không dùng chỉ số hiển thị.
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

LEARN hoàn thành khi nộp đầy đủ mọi đáp án; CHECK GUIDED trước nộp là tùy chọn, không yêu cầu đúng hết (đổi theo yêu cầu 25/10). Kết quả hiển thị toàn bộ đáp án/giải thích và một nút xác nhận về vòng. POST /api/attempts/{id}/complete xác nhận idempotent, cho bài cũ đủ đáp án nhưng chưa hoàn thành ghi nhận tiến độ mà không đổi Submission/FIRST/điểm/thưởng cũ. DB chỉ lưu STARTED/COMPLETED, API tổng hợp NOT_STARTED. Học lại/nộp dở không hạ COMPLETED. Tiến độ gồm bài đang công bố và lịch sử riêng; bài retire có currentPublishedVersionId=null. updateAvailable so bản công bố với bản hoàn thành gần nhất, hoặc bản lượt gần nhất nếu chưa hoàn thành.

REVIEW có provider M04; khi kết quả chưa đồng bộ trả REVIEW_SYNC_REQUIRED. Bản test có thể thay provider bằng mock để kiểm tra lỗi REVIEW_NOT_READY. M05 xử lý thưởng sau commit Submission bằng worker/sync riêng.

### M04 — ôn tập cá nhân

PersonalReviewService đồng bộ tối đa 100 Submission schema 1/batch dưới actor lock và receipt; progress/evidence/ledger cùng transaction. GET chỉ đọc. Khi tạo REVIEW, provider dùng progress đã đồng bộ; M03 tiếp tục kiểm tra và pin mọi Version ID. Không đưa callback RAM làm nguồn duy nhất hoặc sửa FIRST/Submission khi chữa.

Tổng hợp mỗi objective một lần mỗi Submission; stage tăng tối đa một lần mỗi ngày Việt Nam, khoảng 1–3–7 ngày có cấu hình. Câu hỗ trợ/GUIDED/bỏ trống/cũ không chứng minh tăng mức. Câu cũ tự làm đúng có thể đặt mốc ôn. Gợi ý mức cần ít nhất hai bộ mới đủ câu và nội dung mức kế tiếp; giữ lý do thiếu dữ liệu/học liệu. Seed chia nhiều độ khó nên số câu phù hợp có thể thấp hơn yêu cầu.

Xem docs/M04_IMPLEMENTATION_PLAN.md, source/common/M04_API.md và statics/results/module-4-validation.md trước khi đổi luật hoặc diễn giải kiểm thử.

### M07 — hồ sơ và thống kê

StatisticsController → StatisticsService/impl → StatisticsRepository/impl dùng JPQL aggregate, chỉ đọc trong transaction REPEATABLE_READ. Chỉ lấy FIRST/trial của Attempt đã có Submission; phân nhóm GUIDED, hỗ trợ, câu mới tự làm và câu cũ tự làm. Tỷ lệ lấy correct/(correct+incorrect), bỏ trống riêng, không có mẫu trả null. CORRECTION/GUIDED_RETRY không thay FIRST. Bộ lọc khoảng áp dụng submittedAt; tiến độ bài/lịch ôn là toàn lịch sử. Metadata theo phiên bản đã chốt, không phụ thuộc bản đang công bố.

Không thêm schema/migration hoặc mutation. GET không sync M04; báo pendingSubmissions. Không cho TEACHER/ADMIN đọc /me, không cho STUDENT/TEACHER đọc thống kê admin, không trả đáp án hoặc PII học viên trong bảng admin. M05LearnerRewardsReader tái sử dụng GET service M05 với principal gốc để đọc thưởng đã xử lý, không sync/process/join. LiveConnections.snapshot chỉ đếm socket mở trong cửa sổ heartbeat M06, distinct user cho nhiều tab, tại một backend. Xem docs/M07_PROGRESS.md, source/common/M07_API.md và statics/results/module-7-validation.md.

TEACHER có /api/teacher/statistics (overview/units/lessons/questions/breakdown), quyền theo Unit.ownerId của principal; foreign/missing Unit cùng 404. Chỉ LEARN pin LessonVersion → Lesson → Unit; không lọc bằng chủ câu/membership hiện tại và không gán PRACTICE/REVIEW vào Unit. Chỉ metadata/tổng hợp, không learnerId/email/Attempt ID/đáp án. Metric kết quả theo period, bắt đầu/đang làm/hoàn thành toàn lịch sử. Hồi quy trong StatisticsApiTest và module-seven-teacher.spec.ts.

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
| Vòng học/trang chủ/ORDERING | [Tiến độ](docs/LEARNING_JOURNEY_PROGRESS.md), [API](source/common/M02_M03_API.md), [biên bản](statics/results/learning-journey-validation.md); RoundMeter/StudentHome, LearningProgressService.rounds, OrderingEditor/InteractiveAnswers |
| Tiếp tục M07 | [Tiến độ](docs/M07_PROGRESS.md), [kế hoạch](docs/M07_IMPLEMENTATION_PLAN.md), [API](source/common/M07_API.md), [kiểm tra](statics/results/module-7-validation.md) |
| Tiếp tục M03 | [Tiến độ](docs/M03_PROGRESS.md), rồi đúng bước trong [kế hoạch](docs/M03_IMPLEMENTATION_PLAN.md) |
| Tiếp tục M04 | [Tiến độ](docs/M04_PROGRESS.md), [kế hoạch](docs/M04_IMPLEMENTATION_PLAN.md), [API](source/common/M04_API.md), [kiểm tra](statics/results/module-4-validation.md) |
| Tiếp tục M05 | [Tiến độ](docs/M05_PROGRESS.md), [bàn giao](docs/M05_HANDOFF.md), [quyết định P0](docs/M05_DECISIONS.md), [API](source/common/M05_API.md), [nghiệm thu](statics/results/module-5-validation.md); không sửa V1–V8 đã chạy |
| Tiếp tục M06 | [Tiến độ](docs/M06_PROGRESS.md), [kế hoạch](docs/M06_IMPLEMENTATION_PLAN.md), [API](source/common/M06_API.md), [ca thao tác](docs/M06_MANUAL_TEST_CASES.md), [nghiệm thu](statics/results/module-6-validation.md) |
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

Bản demo M05 hiện dùng **`start-main.cmd`**, project `english-learning-main`, UI **5176**, API **8083**, giữ các volume riêng của project này. Không dùng lẫn với môi trường mặc định trên. Hướng dẫn thử phần thưởng trong README.

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

Nghiệm thu M05: từ root chạy `node scripts/verify-m05-acceptance.mjs`, project/volume riêng **english-learning-m05-acceptance**, cổng **15177/18084**, không nhận URL/container demo. Kiểm tra backlog/restart/recreate/seed/replay/context/badge/frozen history, 6 nhóm SQL audit và workload local 20 học viên/40 socket/120 nguồn; dừng container khi xong, giữ volume. Dependency frontend phải có để dùng WebSocket test. Xem biên bản cuối, không coi workload nhỏ là SLA production. Spring test context đóng AFTER_CLASS; fixture XP không kế thừa test để nhân bản số ca.

Nghiệm thu bền vững: từ `source/client`, chạy `node scripts/verify-m03-persistence.mjs`. Script tự build project **english-learning-m03-acceptance** ở **5175/8082**, tạo user test mới, kiểm tra nháp/kết quả/tiến độ/receipt/media qua restart server và force recreate cả ba container, seed chạy lại; dừng container khi xong và giữ volume riêng. Không chấp nhận URL demo. JSON/log trong `.test-state/m03-step6-*` bị ignore; xem [biên bản](statics/results/module-3-validation.md) trước khi diễn giải số hàng hoặc kết quả.

M06: `node scripts/test-connection.mjs` kiểm tra manager bằng clock/RNG/transport kiểm soát; `npm run test:e2e -- module-six-recovery.spec.ts` dùng IndexedDB/browser thật; `node scripts/verify-m06-recovery.mjs` dùng project **english-learning-m06-acceptance**, **5176/8083**, kiểm tra browser journal qua restart và dữ liệu/M04 ledger/media qua recreate. Cần Chrome/Chromium theo cấu hình Playwright; log/JSON trong `.test-state/m06-*`, không dùng DB demo. Giới hạn các nhánh chưa thử ở [biên bản M06](statics/results/module-6-validation.md).

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

Luồng học hiện tại 26/10: STUDENT chỉ có **Vào chơi** và **Ôn tập cá nhân** làm lối vào chính. Chơi lại bài/vòng ngay trong bản đồ; không có mục Luyện tự chọn hoặc Luyện thêm riêng. Giữ ba vai trò câu hỏi GUIDED/INDEPENDENT/REVIEW_VARIANT, API và lịch sử PRACTICE cũ, pending journal/receipt. URL tab=practice không có attempt được thay bằng bản đồ. Không migration. Xem [tiến độ](docs/LEARNING_JOURNEY_PROGRESS.md) và [kiểm tra hai luồng](statics/results/two-learning-modes-validation.md).

M05 v2 ngày 26/10: **50 XP/cấp**, League xét từ **2 thành viên và 2 người có XP**, tối thiểu một suất mỗi phía. V10 thêm policy mới, giữ XP/claim và lịch sử tuần đã chốt; chỉ tuần hiện tại còn OPEN được chuyển luật ngay. Xem [quyết định](docs/M05_ENGAGEMENT_V2.md), [kiểm tra](statics/results/engagement-v2-validation.md) và [gameplay](docs/GAMEPLAY_INTEGRATION_PROPOSAL.md).

Game ngày 26/10: Săn kho báu tích hợp trong AttemptRunner/AttemptResult; GameArtwork và GameWorld là điểm vào cảnh SVG/hoạt cảnh/kết quả. InteractiveAnswers giữ bấm/chạm/kéo/bàn phím, dùng draft M06. Mở rương từ feedback đúng khớp đáp án hiện tại hoặc finalOutcome khi nộp; không tự chấm hay tăng XP. Demo chưa có ORDERING, không tự thêm câu. Không migration/backend mới. Xem [gameplay](docs/GAMEPLAY_INTEGRATION_PROPOSAL.md) và [kiểm tra](statics/results/gameplay-validation.md).

Level v3 ngày 26/10: V11 INSERT policy m05-p0-v3, mỗi bước L→L+1 cần 10×L XP, ngưỡng L là 5×L×(L−1). ActivityLevels là bộ tính cấp dùng số nguyên; GamificationServiceImpl giữ hỗ trợ v1/v2 và policy nguồn theo submittedAt. Không đổi XP/cap/League/nguồn cũ. UI lấy floor/next từ Summary. Xem [luật và giới hạn](docs/M05_PROGRESSIVE_LEVELS.md), [kiểm tra](statics/results/progressive-levels-validation.md).

Bảng xếp hạng chung ngày 27/10: GET /api/me/leaderboard, WeeklyLeaderboardService/Repository/Views là điểm vào; đọc daily stats đã commit theo tuần VN, ACTIVE STUDENT có XP dương, đồng hạng, top/page/self một snapshot. Không migration/ghi XP/join League; không cấp account ID/email. Leaderboards.tsx gom tab Toàn hệ thống/Nhóm thi đấu, sidebar Bảng xếp hạng; #/league tương thích mở nhóm. Xem [nghiệp vụ](docs/M05_GLOBAL_LEADERBOARD.md), [kiểm tra](statics/results/global-leaderboard-validation.md).

Bài 18 câu ngày 28/10: LessonActivities/activities_json của LessonVersion là cấu trúc tùy chọn theo phiên bản. Sáu phần CHOICE → MATCHING → ORDERING → FILL → READING_CLOZE → READING_CHOICE, số câu 3/2/2/3/4/4, mỗi phần một GUIDED rồi INDEPENDENT. AttemptSelector lưu thứ tự mới chỉ khi có metadata; bài/lượt cũ giữ nguyên. Hai dạng đọc có ví dụ riêng; InlineReading dùng snapshot đã chiếu theo attemptQuestionId, chỉ có ô thuộc lượt, không chấm ở client. Điểm vào: LessonActivityFields, AuthorPreview, AttemptSnapshotReader và V14; API tác giả activities tùy chọn, GET giới thiệu chỉ có activitySummary an toàn. Bài mẫu tạo riêng bằng `scripts/seed-activity-sample.mjs` qua API với checkpoint, không tự nâng DB demo hoặc đè ngân hàng nền. Xem [tiến độ](docs/QUESTION_VARIETY_PROGRESS.md), [kế hoạch](docs/QUESTION_VARIETY_IMPLEMENTATION_PLAN.md), [kiểm tra](statics/results/question-variety-validation.md). Bài viết tiếp tục để sau.

Chạy demo 28/10 theo yêu cầu: `english-learning-main` đã build/chạy source mới trên **5176/8083**, DB từ V11 lên **V12**, giữ nguyên volume. Đã sao lưu trước nâng trong `.test-state/main-activities-run/`; sau chạy vẫn 9 tài khoản, 58 Attempt, 47 Submission. Bài mẫu 18 câu đã nạp: **Unit 6 / Vòng 2 — Everyday adventures / Lesson 11 / LessonVersion 11**, checkpoint `.test-state/activity-sample/0ae6f571866778a9.json`. Giữ demo đang chạy; không chạy bộ E2E ghi dữ liệu vào môi trường này.

Điều chỉnh bài 18 câu: cấu trúc mới bảy phần CHOICE → MATCHING → ORDERING → FILL → READING_CLOZE → READING_CHOICE → LISTENING_CHOICE, số câu 3/2/2/3/3/3/2, 7 GUIDED + 11 INDEPENDENT. Nghe dùng SINGLE_CHOICE + AUDIO, bốn option/câu, tái sử dụng audio player/grant/transcript hiện hành. Giữ validator tương thích sáu phần cũ, không đổi lượt đã pin. Bộ mẫu activities-1.1.0 có upload audio qua receipt/checkpoint; chạy seed mới tạo bài riêng, chưa thay bài demo Lesson 11. Kết quả kiểm tra riêng ghi tại statics/results/question-variety-validation.md.

Trạng thái chạy ngày 29/10 theo yêu cầu test lại dữ liệu nền main: project `english-learning-baseline` đang phục vụ **5176/8083**, mã nguồn working tree hiện tại, volume DB/assets mới riêng. Đã xác nhận 3 user, 3 Unit, 6 Lesson, 90 Question, 13 Material, 0 Attempt/Submission/XP ledger; UI và đăng nhập student/danh mục đạt. Project `english-learning-main` chứa dữ liệu demo bổ sung đã dừng, volume/checkpoint còn nguyên. Chạy tiếp bằng `start-baseline.cmd`; không chạy E2E/seed bổ sung vào baseline khi chưa được yêu cầu. Chạy lại script giữ tiến độ test mới, không reset mỗi lần. Xem [cách chuyển môi trường](docs/BASELINE_TESTING.md).

Demo linh hoạt 29/10: bài đầu Vòng 1 của baseline **Lesson 1 / LessonVersion 7** đã nâng thành 18 câu, gồm 6 trắc nghiệm, 4 ghép cặp, 6 điền từ, 2 nghe; dùng lại 10 câu LEARN và audio cũ, bổ sung 8 câu qua API, giữ 5 câu ôn. Các bài khác không tự đổi, lượt cũ pin phiên bản cũ. Metadata activities nay cho chọn 1–7 dạng, 15–20 câu, mỗi dạng tối thiểu một GUIDED và một INDEPENDENT; LISTENING_CHOICE dùng AUDIO 3–6 lựa chọn. Chú ý các số baseline 90 câu/0 lượt phía trên là trước khi nâng và trước khi người dùng test. Checkpoint/backup và kiểm tra tại [hướng dẫn](docs/BASELINE_TESTING.md), [biên bản](statics/results/question-variety-validation.md).

Tên giao diện giáo viên ngày 27/10: Unit được gọi Vòng đấu trong danh mục/biên soạn/duyệt/thống kê; bỏ badge Chặng. Stage/sortOrder vẫn là thông tin sắp xếp, đặt trong mục Sắp xếp vòng đấu (Nhóm sắp xếp/Vị trí trong nhóm). Không gán số vòng từ danh sách riêng/lọc, không đổi API/DB hoặc tên nội dung tác giả đã nhập. Xem [quy ước](docs/TEACHER_ROUND_TERMINOLOGY.md), [kiểm tra](statics/results/teacher-round-terminology-validation.md).

Số vòng giáo viên: roundNumbering.tsx đọc đủ public /api/units (cùng visibility và stage/sortOrder/id với RoundJourneyReader), lập map ID→số trước mọi lọc của tác giả. Dùng ở thẻ, picker, preview, sắp xếp bài và thống kê. Vòng không có trong public journey hiện Chưa có số; lỗi có retry và không gán số theo trang/của tôi. Không đổi API/backend. Xem [quy ước](docs/TEACHER_ROUND_TERMINOLOGY.md), [kiểm tra](statics/results/teacher-round-numbering-validation.md).

V12 ngày 27/10 thay số vòng tính động bằng units.round_number (INT dương/duy nhất). Form Số thứ tự vòng sửa trực tiếp; payload thiếu số tạo max+1 hoặc giữ số khi update, null bị loại khi băm receipt cũ. Unit/TeacherUnit có roundNumber, RoundProgress.number lấy đúng số DB; repository/khóa vòng/statistics sắp theo roundNumber/id. roundNumbering.tsx chỉ định dạng, không fetch map. Backfill giữ số vòng công bố; seed không ghi đè số đã sửa. Stage/sortOrder chỉ còn tương thích, không quyết định lộ trình. Xem [quy ước](docs/TEACHER_ROUND_TERMINOLOGY.md), [kiểm tra](statics/results/persistent-round-number-validation.md).

V13 — phân công/duyệt: ADMIN quản lý topics/objectives/units/số vòng và assignee; TEACHER quản lý Lesson theo units.assigned_teacher_id hiện tại, Question/Material giữ tác giả. Unit/Lesson ownerId là người tạo. ContentGovernance + content_control khóa actor→content→dependency; ContentReviewService/Controller và content_review_requests lưu snapshot, pending uniqueness, duyệt/trả lại/rút với receipt/revision. Public publish/retire Q/L và apply audio ADMIN-only; quyền kiểm tra trong receipt action để replay trước V13 không đổi. canEdit/review từ API; ContentReviews.tsx là hàng đợi. Direct author version routes và MaterialAssetReadGrant cấp đọc đúng dependency của vòng được giao, không quyền sửa root khác. Bootstrap CurriculumSeedService dùng publisher nội bộ cho dataset tin cậy, không bỏ luật HTTP giáo viên. Stats teacher scope chuyển sang assignee. Xem [nghiệp vụ](docs/CONTENT_GOVERNANCE.md), [kiểm tra](statics/results/content-governance-validation.md).

Online quản trị: AdminPresence chỉ mount một lần bên trong AdminAccounts, hiển thị users tên/vai trò, không nút cập nhật hoặc đoạn giải thích. GET /api/admin/statistics/presence chỉ ADMIN, thêm users{id,fullName,role}, đọc batch ACTIVE theo LiveConnections.onlineSnapshot và dedup ID; snapshot dùng ID set để phát hiện cả đổi thành viên khi số đếm không đổi. Giữ WebSocket PRESENCE_CHANGED, ACK/reconnect GET đối soát và fallback khi cần. Không migration. Xem [API](source/common/M07_API.md), [kiểm tra](statics/results/admin-online-list-validation.md).

Ôn tập ngày 28/10: UI ba lựa chọn ALL (câu sai chưa ôn đúng + toàn bộ REVIEW_VARIANT), PRIORITY (câu sai + biến thể cùng mục tiêu), WRONG (chỉ câu gốc sai LEARN đã nộp, gồm GUIDED/INDEPENDENT). ReviewChoiceSelector + ReviewRepository.wrongLearnQuestions/correctReviewQuestions đọc lịch sử owner; REVIEW nộp đúng cùng origin sau lần LEARN sai gần nhất bỏ nguồn sai, LEARN sai mới thêm lại. Đúng biến thể/lưu nháp/chữa sau nộp không xóa nguồn gốc. Không lọc difficulty/lịch cho ba lựa chọn; chỉ phiên bản trong bài công bố, dedup origin, 1–10 câu. Dashboard thêm choices; DUE/MISTAKES cũ giữ tương thích request/snapshot. Không migration/đổi FIRST/Submission/luật thưởng. Xem [tiến độ](docs/M04_PROGRESS.md), [API](source/common/M04_API.md), [kiểm tra](statics/results/review-choices-validation.md).

Lối vào phân công: ADMIN sidebar Phân công biên soạn mở #/content?area=assignments (ContentCatalog assignmentMode chỉ units, editor chỉ assignee và giữ metadata). Teacher mặc định Vòng được giao, Soạn bài mở AuthorLibrary với URL unit, thêm bài chọn sẵn vòng hợp lệ. Xem bài chờ duyệt nối sang reviews. Không backend/migration mới. Xem [luồng](docs/CONTENT_GOVERNANCE.md), [kiểm tra](statics/results/content-assignment-ui-validation.md).


Tích hợp main ngày 29/10: V12 số vòng cố định và V13 phân công/duyệt giữ nguyên từ main; migration activities của nhánh này chuyển sang V14. Các ghi nhận V12 activities phía trên là lịch sử local trước tích hợp. DB local từng áp dụng V12 activities chưa tương thích lịch sử Flyway mới; không tự repair/xóa volume hoặc chạy startup vào DB đó. Xem docs/QUESTION_VARIETY_PROGRESS.md.

Bài viết cuối vòng ngày 29/10: WritingController → WritingService/impl → WritingRepository/impl → JPA, migration V15. TEACHER theo assignedTeacherId hiện hành hoặc ADMIN đặt/công bố đề viết và chấm; đây là luồng riêng được người dùng đồng ý, không đổi luật duyệt Question/Lesson V13. Chốt WritingVersion khi bắt đầu; Work revision dưới khóa Unit, Submission/Grade bất biến, điểm cộng từ đủ tiêu chí đạt theo bản đề đã chốt. Nộp một lần mở vòng sau, RETURNED không khóa lại; không ghi progress M03/XP/M04/League. RoundJourneyReader đọc bulk task/work và thêm writing tùy chọn; vòng không có đề giữ luật cũ. UI features/writing (TeacherWriting, LearnerWriting, session), tab area=writing mở trang chọn Tạo bài viết/Chấm bài (writingMode=create|grade), WritingTaskEditor và WritingQueue tách riêng; writingSubmission giữ chi tiết chấm qua reload. Thẻ học sinh nằm cuối Unit. Nháp IndexedDB dùng workingDrafts M06 với key writing/user/unit/writer, local 250 ms/server 1500 ms; pending giữ payload/requestId tới replay+GET, 409 so sánh rõ, logout fence/xóa dữ liệu riêng. Xem [thiết kế](docs/UNIT_WRITING.md), [API](source/common/UNIT_WRITING_API.md), [kiểm tra](statics/results/unit-writing-validation.md). Không tự tạo đề mẫu hoặc commit/push.

Tích hợp main 30/10: ghép main 4cbd4bc (ôn ALL/PRIORITY/WRONG của M07) vào addquestionsystem, giữ activities và bài viết V15; không migration mới. Ca activity-variety kiểm tra nguồn sai GUIDED trong đoạn cloze được chọn vào WRONG, chỉ chiếu một ô, lưu/tải lại và giải quyết đúng origin khi nộp ôn. Xem [kết quả tích hợp](statics/results/addquestion-main-integration-validation.md); không dùng các số kiểm thử lịch sử làm kết quả của lần merge này.

Tìm kiếm contest: GET /author/contest-papers nhận q (tên/người soạn), /author/contest-permissions nhận q (tên/email) + proctorOnly; lọc scope/state/quyền trước pagination/count, escape LIKE ký tự %,_,!. PaperPicker dùng dialog tìm kiếm/phân trang; ProctorPicker giữ selected IDs/metadata qua từ khóa/trang, Author ADMIN trả proctors[{userId,fullName,email,eligible}]. usePagedSearch loại phản hồi cũ và tải lại khi trở về danh sách kho; không allPages cho chọn đề/giám thị. Không migration. Xem statics/results/contest-search-validation.md.

Monitor contest: ParticipantStatus + participant-state chiếu trạng thái từ metadata/serverTime. API Participant trả thêm tabHidden từ entity đã có (không migration); tách đăng ký, tiến trình, kết nối và Trong/Ngoài tab thi. Online dùng lease 45s, hết kết nối hiện Chưa rõ trạng thái tab. Session đối soát visibility sau load/claim/heartbeat, so với cuối hàng đợi hoặc server để reload không kẹt hidden và không cộng trùng lượt. REVOKED vẫn trong ALL/REVOKED, không hiện tab thi đang hoạt động. Mobile contest-monitor-table chuyển sang thẻ có nhãn; bộ lọc server giữ nguyên. Xem statics/results/contest-tab-status-validation.md (13 backend + 3 browser trên thay đổi này); bản UI trước tại statics/results/contest-monitor-status-validation.md.
