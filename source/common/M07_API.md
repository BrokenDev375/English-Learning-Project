# Hợp đồng M07 — Hồ sơ và thống kê

Bổ sung 29/10/2026: giao diện quản trị dùng [API giám sát phiên](ADMIN_SESSION_MONITORING_API.md) cho bảng hoạt động/phiên/tab. GET /api/admin/statistics/presence và PRESENCE_CHANGED giữ tương thích; event chỉ invalidation cho ADMIN subscribe, gom tối đa một lần/giây, ACK/reconnect GET và poll visible 15s. Hai bộ lọc giám sát/CRUD độc lập; không dùng số tab làm số tài khoản. Context hoạt động không phải dữ liệu thống kê kết quả hoặc bằng chứng chú ý học. Xem [nghiệm thu tích hợp](../../statics/results/admin-session-step5-validation.md).

Cập nhật 24/10/2026. Cookie session của M01, xác thực lại quyền tài khoản hiện hành. Tất cả endpoint GET, không thay đổi DB, trả Cache-Control: no-store, private. Không nhận userId để chọn học viên; /me chỉ dùng principal. TEACHER chỉ đọc các endpoint /api/teacher/statistics trong phạm vi Unit mình làm chủ; không được đọc /me của học viên hoặc /admin.

## Endpoint

| GET | Quyền | Query |
|---|---|---|
| /api/me/statistics | STUDENT | period=ALL/7D/30D/90D |
| /api/me/statistics/breakdown | STUDENT | period, groupBy=TOPIC/OBJECTIVE, page=0, size=20 |
| /api/me/statistics/lessons | STUDENT | page=0, size=10 |
| /api/admin/statistics | ADMIN | period |
| /api/admin/statistics/presence | ADMIN | Không có |
| /api/admin/statistics/questions | ADMIN | period, difficulty=EASY/MEDIUM/HARD (tùy chọn), page=0, size=20 |
| /api/teacher/statistics | TEACHER | period, unitId (tùy chọn) |
| /api/teacher/statistics/units | TEACHER | page=0, size=20 |
| /api/teacher/statistics/lessons | TEACHER | period, unitId, page=0, size=20 |
| /api/teacher/statistics/questions | TEACHER | period, unitId, difficulty (tùy chọn), page=0, size=20 |
| /api/teacher/statistics/breakdown | TEACHER | period, unitId, groupBy=TOPIC/OBJECTIVE, page=0, size=20 |

period mặc định ALL. 7D/30D/90D là khoảng lùi theo 24 giờ tính từ thời điểm đọc, không phải tuần/tháng lịch. Lọc a.submittedAt >= from và <= generatedAt. Kết quả câu trả lời chỉ tính Attempt đã có Submission; số đang làm/đã hoàn thành và danh mục có phạm vi riêng được ghi bên dưới. page >= 0, size 1–50, offset không vượt Integer.MAX_VALUE. Phân trang CatalogPage như M02/M03: items,totalElements,page,size,totalPages.

## Metric và Performance

Metric: total, correct, incorrect, unanswered là số nguyên; accuracyPercent là số 0–100 làm tròn hai số lẻ hoặc null nếu correct+incorrect=0. total=correct+incorrect+unanswered. Tỷ lệ không lấy bỏ trống làm mẫu số; UI luôn hiện số bỏ trống.

Performance chứa Metric cho first, guided, assisted, independentNew, independentSeen, guidedRetries, corrections.

- guided: FIRST của slot GUIDED, bất kể cờ hỗ trợ.
- assisted: FIRST của slot khác GUIDED, có supportUsed.
- independentNew: FIRST tự làm, không hỗ trợ và wasSeenBefore=false.
- independentSeen: FIRST tự làm, không hỗ trợ và wasSeenBefore=true.
- first bằng tổng bốn nhóm trên. Các lần quan sát có thể thuộc cùng một câu ở nhiều lượt khác nhau.
- guidedRetries và corrections là các phase khác nhau, không nằm trong first. Lọc theo bài đã nộp trong khoảng; các trial của bài đó tính tới generatedAt.

## LearnerOverview

- generatedAt, period, from: thời điểm và phạm vi quan sát.
- activity: submittedAttempts (trong khoảng), learnersWithSubmissions (0 hoặc 1), lastSubmittedAt, modes [{mode: LEARN/PRACTICE/REVIEW, submissions}]; activeAttempts và completedLessons là trạng thái hiện tại/toàn bộ lịch sử.
- performance: Performance từ FIRST/trial đã lưu, không chấm lại.
- review: trackedObjectives, needsReview, mistakeObjectives, pendingSubmissions, nextScheduledAt. Projection M04 hiện có, độc lập bộ lọc period. needsReview gồm needsPractice hoặc đã tới mốc. nextScheduledAt là mốc sớm nhất còn ở tương lai và không cần luyện ngay. Có pendingSubmissions thì vào M04 để sync; endpoint này không ghi projection.
- rewards: state=UNAVAILABLE khi chưa có LearnerRewardsReader, totalXp/level/streakDays/badges/currentWeek/history đều null.

M05LearnerRewardsReader đã tích hợp ngày 25/10, trả state=AVAILABLE và dữ liệu đã commit: totalXp (long), level (long như M05), streakDays (int), badges [{code,name,earnedAt}], currentWeek {week,tier,position,xp}, history cùng kiểu LeagueWeek. Chỉ huy hiệu đã nhận; currentWeek=null nếu chưa tham gia tuần hiện tại, history gồm tối đa 5 tuần đã chốt gần nhất. week là ngày bắt đầu tuần yyyy-MM-dd theo Asia/Ho_Chi_Minh; tier của lịch sử là bậc trong tuần đó. Dữ liệu là kết quả đã xử lý, không cộng phần đang pending; GET không gọi sync/process/join. M07 không diễn giải luật thưởng/chốt tuần hay tự sửa giá trị.

## Breakdown và bài hoàn thành

Breakdown item: id, name, topicId, performance. Metadata theo TOPIC hoặc OBJECTIVE; chỉ nhóm có FIRST trong bài đã nộp của chính học viên. Sắp id tăng dần, phân trang trước tổng hợp chi tiết. Không suy ra thành thạo từ tỷ lệ trên ít câu.

Lesson item: lessonId, lessonVersionId (phiên bản hoàn thành gần nhất), title, state=COMPLETED, lastAttemptId, completedAt, lastScorePercent. Danh sách toàn bộ lịch sử COMPLETED, sắp completedAt giảm dần rồi lessonId giảm dần. lastAttemptId/lastScorePercent là lần làm/nộp gần nhất theo M03, có thể khác lần hoàn thành. Giữ được bài retire và không đếm học lại thành bài mới.

Lịch sử lượt làm dùng GET /api/attempts của M03; UI phân trang/lọc mode, liên kết bằng Attempt ID vào runner hoặc kết quả, không tạo lượt mới.

## AdminOverview và câu thường sai

AdminOverview: generatedAt, period, from, activity, performance, roles/statuses [{label,count}], difficulties [{difficulty,performance}], presence.

roles/statuses là số tài khoản hiện tại (bao gồm DELETED); activity giữ lịch sử kể cả tài khoản bị khóa/xóa. completedLessons là tổng các cặp học viên/bài COMPLETED; learnersWithSubmissions là distinct user có bài nộp trong khoảng. difficulties là phân bố câu đã gặp trong bài đã nộp, không phải toàn ngân hàng M02.

presence={connectedAccounts,openConnections,basis:OPEN_AUTHENTICATED_SOCKETS}. Chỉ dựa trên WebSocket còn mở tại một backend; nhiều tab cùng user đếm một account. Snapshot lấy từ RAM nên không cùng transaction snapshot với MySQL. Đã tích hợp cửa sổ heartbeat M06: socket hết liveness không được đếm, kể cả transport còn mở. Không có presence nhiều server và không suy ra số người đang học.

Question item: questionId,questionVersionId,versionNo,prompt,type,difficulty,learners,first (Metric). Chỉ câu có ít nhất một FIRST sai; sắp số sai giảm dần, questionVersionId tăng dần để phân trang ổn định trên một snapshot. learners đếm distinct user có FIRST trên phiên bản đó trong bài đã nộp trong khoảng, kể cả người trả lời đúng/bỏ trống. Không chứa người nào đã trả lời gì, answerKey, explanation hoặc transcript. Câu ít mẫu không mặc định là câu kém chất lượng.

## Người đang online

GET /api/admin/statistics/presence trả {generatedAt,connectedAccounts,openConnections,basis:OPEN_AUTHENTICATED_SOCKETS,users:[{id,fullName,role}]}. Chỉ ADMIN hiện hành; no-store/private. users chỉ có tài khoản ACTIVE với socket còn mở và còn heartbeat, mỗi ID một lần, sắp theo tên không phân biệt hoa/thường rồi ID. Không trả email, password, session, đáp án hoặc dữ liệu học riêng. STUDENT/TEACHER không đọc được endpoint hoặc nhận subscription PRESENCE.

LiveConnections.onlineSnapshot chụp tập ID và số kết nối từ cùng danh sách socket. Repository đọc một batch tài khoản theo các ID này, lọc ACTIVE; connectedAccounts bằng users.length. Không truy vấn tổng hợp bài nộp hoặc ghi DB, không migration. Snapshot RAM và dữ liệu tài khoản không phải cùng một transaction snapshot. Thông báo WebSocket vẫn chỉ chứa version/type và không có danh tính.

Giao diện chỉ đặt một danh sách online trong Quản lý tài khoản, ngay dưới tiêu đề; mỗi dòng gồm tên và vai trò. Không còn ô online ở Trang chủ/Thống kê, không nút cập nhật, thời gian đo hoặc đoạn giải thích. WebSocket PRESENCE_CHANGED và ACK đăng ký kích hoạt đọc mới, thay danh sách hiện tại thay vì nối thêm; lỗi giữ bản cũ và báo ngắn. Giữ tự đối soát sau reconnect/focus và fallback HTTP khi subscription chưa sẵn sàng.

## TeacherOverview và phạm vi Unit

- Mọi truy vấn teacher lọc bằng Unit.ownerId = principal.id. unitId bỏ trống nghĩa là tất cả Unit của chính giảng viên; unitId <= 0/không phải số trả 400, Unit người khác hoặc không tồn tại cùng trả 404. Không nhận teacherId/ownerId để chọn người khác.
- Quan sát học tập chỉ lấy mode LEARN có LessonVersion đã chốt thuộc Lesson trong Unit được phép. Câu dùng chung ở Unit khác không kéo số liệu vào đây, kể cả câu do chính giảng viên tạo. Ngược lại, câu của tác giả khác được dùng trong Unit mình vẫn tính lượt học của Unit mình. PRACTICE/REVIEW chưa có Unit nguồn nên không được suy ngược từ câu hỏi để gán Unit.
- TeacherOverview: generatedAt,period,from,unitId,units,learnersStarted,activity,performance,hintOpenedQuestions,difficulties. units là số Unit trong phạm vi; learnersStarted là distinct user có LEARN trong phạm vi, toàn thời gian. activity/performance/difficulties dùng kiểu chung; activity.modes chỉ có LEARN. activity.submittedAttempts và learnersWithSubmissions theo khoảng, activeAttempts hiện tại, completedLessons là tổng cặp user/lesson COMPLETED toàn thời gian.
- hintOpenedQuestions đếm AttemptQuestion có hintOpenedAt trong các bài đã nộp của khoảng chọn, mỗi item tối đa một. performance giữ nguyên FIRST/hỗ trợ/câu mới/cũ/làm lại/chữa; retry/correction tính đến generatedAt của những bài đó.
- /units trả CatalogPage<TeacherUnit>: unitId,title,stage,sortOrder,roundNumber; tất cả Unit mình sở hữu, kể cả chưa có bài. Sắp roundNumber,unitId tăng dần.
- /lessons trả CatalogPage<TeacherLesson>: lessonId,unitId,unitTitle,title,learnersStarted,activeAttempts,submittedAttempts,completedLearners,hintOpenedQuestions,performance. Tên theo LessonVersion có versionNo mới nhất (có thể là draft); số liệu gồm mọi phiên bản của root. Giữ bài retire. completedLearners là số cặp user/lesson COMPLETED của bài đó, không tăng khi học lại. Sắp Unit roundNumber/id rồi Lesson order/id.
- /questions dùng QuestionPage chung, chỉ FIRST sai trong bài đã nộp thuộc phạm vi; tách từng QuestionVersion, hỗ trợ difficulty và phân trang, không lộ kết quả từng người.
- /breakdown dùng BreakdownPage chung, nhóm TOPIC/OBJECTIVE theo metadata QuestionVersion đã chốt trong phạm vi Unit. Chỉ nhóm có FIRST trong bài đã nộp.
- Không trả danh tính học viên, userId, Attempt ID, đáp án, explanation, transcript hoặc reward cá nhân. Xem số tổng hợp của Unit không cấp quyền đọc snapshot/lịch sử của từng học viên.

## Lỗi và giới hạn

- 400 VALIDATION_FAILED: query/period/phân trang sai.
- 401 UNAUTHENTICATED/SESSION_REVOKED: đăng nhập lại.
- 403 FORBIDDEN: sai vai trò.
- 404 NOT_FOUND: Unit không tồn tại hoặc không thuộc giảng viên.
- 503 DATABASE_UNAVAILABLE: giữ màn hình lỗi, thử tải lại; không giả thành số 0.
- Không có mutation để gửi lại, không có migration mới; đọc schema V1–V8 hiện có sau tích hợp M05.
- P1 xuất báo cáo và thống kê thử nghiệm mạng chưa triển khai.

Schema máy đọc: [openapi-m07.json](openapi-m07.json). Nguồn: [M02/M03](M02_M03_API.md), [M04](M04_API.md).

Tích hợp main 20652a7 vào M07 ngày 25/10/2026. LearnerRewardsReader giữ principal để dịch vụ M05 kiểm tra quyền phiên hiện hành; không nhận userId khác từ query. Giao diện hồ sơ có liên kết sang phần thưởng và bảng tuần.

V13: phạm vi TEACHER lấy theo Unit.assignedTeacherId hiện tại, không lấy ownerId người tạo. Khi chuyển phân công, giáo viên mới xem số liệu lịch sử trong vòng được giao; người cũ không đọc số liệu vòng đó nữa. Không đổi hàng progress/Attempt/XP hoặc dữ liệu tổng hợp gốc. OWN của các bank tác giả thay đổi tương ứng cho giáo viên; xem [quy tắc V13](../../docs/CONTENT_GOVERNANCE.md).
