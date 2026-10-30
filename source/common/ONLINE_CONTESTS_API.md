# API cuộc thi online

Hợp đồng máy đọc: [openapi-contests.json](openapi-contests.json). Schema content/answerKey lồng bên trong tái sử dụng bốn định dạng biên soạn hiện có; parser backend vẫn kiểm tra chặt theo từng type.

Session cookie cùng origin, CSRF cho mọi POST/PUT. HTTP GET trả `Cache-Control: no-store, private`. Thời gian ISO 8601 UTC; `serverTime` để hiển thị đồng hồ. Server quyết định mọi hạn/quyền, không nhận giờ hoặc điểm do client tính.

## Luồng và quyền

ADMIN phân quyền → giáo viên soạn bộ đề riêng → ADMIN duyệt bộ đề → tạo DRAFT/chọn bộ đề/phân công giám thị → công bố PUBLISHED → học viên đăng ký REQUESTED → ADMIN APPROVED/REJECTED → học viên check-in **trước** `startsAt` → RUNNING → chốt FINALIZED → ARCHIVED. Có thể CANCELLED trước giờ thi. CLOSED là trạng thái xử lý nội bộ, API hiển thị CLOSING. Đề và thiết lập bất biến sau công bố; không nhận người bắt đầu muộn. Người đã check-in được claim/reconnect đến hạn cá nhân.

ADMIN quản lý mọi contest và quyền giáo viên. TEACHER có canAuthor được soạn bộ đề của mình; canProctor và được phân công trong proctorIds mới được giám sát/acknowledge. Hai quyền độc lập. Giám thị không được soạn câu ngay trong contest hoặc nhận đáp án từ Author; không công bố, duyệt đăng ký, đóng hoặc thu hồi. STUDENT chỉ đọc đề đang thi/bài làm/kết quả của chính mình. Thu hồi khỏi contest không khóa tài khoản website.

| Method / path | Body / kết quả |
|---|---|
| GET `/api/contests?page=0&size=12` | Page metadata, lọc quyền trước phân trang; học viên không thấy DRAFT |
| POST `/api/author/contests` | requestId + Settings → 201 Author |
| GET `/api/author/contests/{id}` | Author: ADMIN nhận bản đề riêng; giám thị chỉ nhận metadata, questions là mảng rỗng |
| PUT `/api/author/contests/{id}/draft` | requestId, baseRevision + Settings; ADMIN |
| POST `/api/author/contests/{id}/publish` | requestId, baseRevision; kiểm tra đầy đủ, khóa đề |
| POST `/api/author/contests/{id}/close` | requestId, baseRevision, reason; đang thi, chốt đáp án đã lưu và BXH |
| POST `/api/author/contests/{id}/cancel` | requestId, baseRevision, reason; trước giờ thi |
| POST `/api/author/contests/{id}/archive` | requestId, baseRevision; sau FINALIZED/CANCELLED |
| GET `/api/author/contests/{id}/monitor` | filter, page, size → metadata, statistics, participants Page; không trả đáp án thí sinh |
| GET `/api/author/contests/{id}/events` | participantId tùy chọn, page, size → Page sự kiện, người xử lý/lý do/mốc giờ |
| POST `/api/author/contests/{id}/participants/{participantId}/{action}` | requestId, baseRevision = **controlRevision**; approve/reject/revoke/acknowledge, các action trừ approve cần reason |
| GET `/api/me/contests/{id}` | Learner: contest, participation, canRegister/canCheckIn/canResume, paper, answers, result |
| POST `/api/me/contests/{id}/register` | requestId; 201 lần đầu, 200 nếu đã có đăng ký |
| POST `/api/me/contests/{id}/check-in` | requestId, clientId UUID; vào phòng trước giờ thi |
| POST `/api/me/contests/{id}/claim` | requestId, clientId; kết nối lại người đã check-in |
| POST `/api/me/contests/{id}/save` | requestId, clientId, baseRevision = **revision**, answers |
| POST `/api/me/contests/{id}/submit` | như save; lưu đáp án và chốt trong cùng transaction |
| POST `/api/me/contests/{id}/visibility` | requestId, clientId, hidden boolean; chỉ chuyển visible → hidden mới tăng lượt |
| POST `/api/me/contests/{id}/heartbeat` | clientId; không có receipt/revision, gia hạn lease 45 giây |
| GET `/api/contests/{id}/leaderboard` | tác giả/giám thị hoặc học viên đã đăng ký; finalized, items; không trả ID tài khoản thí sinh |

`page` từ 0, `size` 1–50. Monitor filter: ALL / REQUESTED / ALERTS / APPROVED / REVOKED / SUBMITTED. `controlRevision` chỉ đổi với quyết định quản lý/vi phạm/check-in/chốt; autosave dùng `revision`, không làm quyết định thu hồi lỗi chỉ vì thí sinh vừa lưu đáp án.

Participant trả thêm `tabHidden` boolean: tín hiệu ẩn/hiện tab cuối cùng server đã nhận từ writer đang thi. Monitor chỉ diễn giải Trong/Ngoài tab thi khi APPROVED, đã check-in, RUNNING, chưa nộp/chưa hết hạn và kết nối còn mới (45 giây); hết kết nối hiển thị Chưa rõ trạng thái tab. Không suy ra tab đích hoặc sự chú ý. Visibility cập nhật qua invalidation WebSocket rồi GET đối soát; khi claim/khôi phục, client gửi trạng thái tab hiện tại nếu khác tín hiệu cuối/đang chờ, không tăng lại lượt khi gửi trùng. Người REVOKED vẫn nằm trong ALL/REVOKED, không xóa khỏi danh sách.

## Kho bộ đề và phân quyền — V17

| Method / path | Quyền và body |
|---|---|
| GET `/api/author/contest-permissions/mine` | TEACHER/ADMIN; canAuthor, canProctor, canManage hiện hành |
| GET `/api/author/contest-permissions?q=&proctorOnly=false&page=0&size=20` | ADMIN; giáo viên ACTIVE, hai quyền, revision. q tìm tên/email, proctorOnly=true chỉ giáo viên hiện có quyền giám sát, lọc trước phân trang |
| PUT `/api/author/contest-permissions/{user}` | ADMIN; requestId, baseRevision, canAuthor, canProctor |
| GET `/api/author/contest-papers?state=ALL&q=&page=0&size=20` | canAuthor: teacher chỉ bộ đề của mình; ADMIN tất cả. q tìm tên bộ đề/người soạn, giới hạn owner/state trước phân trang; metadata không có questions |
| POST `/api/author/contest-papers` | canAuthor/ADMIN; requestId, title, description, questions → 201 bộ đề DRAFT |
| GET `/api/author/contest-papers/{id}` | Chủ bộ đề có canAuthor hoặc ADMIN; chi tiết có câu/keys |
| PUT `/api/author/contest-papers/{id}/draft` | DRAFT; requestId, baseRevision, title, description, questions |
| POST `/api/author/contest-papers/{id}/submit` | Chủ bộ đề/ADMIN, DRAFT đầy đủ → PENDING; requestId, baseRevision |
| POST `/api/author/contest-papers/{id}/approve` | ADMIN, PENDING → READY; requestId, baseRevision |
| POST `/api/author/contest-papers/{id}/return` | ADMIN, PENDING → DRAFT; requestId, baseRevision, note không trống |
| POST `/api/author/contest-papers/{id}/archive` | Chủ bộ đề/ADMIN, DRAFT/READY → ARCHIVED; requestId, baseRevision |
| POST `/api/author/contest-papers/{id}/clone` | Chủ bộ đề/ADMIN; requestId, baseRevision → 201 bản DRAFT mới, ID câu mới |

`q` tối đa 160 ký tự, bỏ khoảng trắng hai đầu. Tìm chuỗi con; %, _ và ! được coi là ký tự thường, không là wildcard do client cung cấp. Count và items dùng cùng bộ lọc. Author của ADMIN thêm proctors[{userId,fullName,email,eligible}] cho những ID đã chọn, để giữ tên khi đổi trang/từ khóa và báo người không còn đủ quyền; giám thị nhận mảng rỗng.

Metadata bộ đề: id, ownerId/ownerName, revision, title/description, state, questionCount, usageCount, reviewNote, reviewedBy/reviewedAt, updatedAt, canEdit/canReview. Detail thêm questions. READY không sửa trực tiếp; nhân bản rồi duyệt lại. Một bộ đề được chọn cho nhiều contest. Mỗi contest giữ snapshot và nguồn riêng; archive hoặc sửa bản sao không thay contest đã công bố. Nguồn đã archive không cho công bố contest nháp.

Quyền hiện hành được kiểm tra trước replay lệnh tác giả bộ đề để không trả private content từ receipt sau khi thu hồi quyền. Sai quyền trả 403 CONTEST_AUTHOR_DENIED; bộ đề ngoài owner-scope trả 404 CONTEST_PAPER_NOT_FOUND; sai revision/state trả 409 CONTEST_PAPER_CONFLICT. Thu hồi canProctor gỡ subscription contest của teacher; không logout website. Route chỉnh câu trực tiếp `/author/contests/{id}/paper` cũ chỉ trả 409 hướng dẫn dùng kho (TEACHER 403), không còn là đường soạn đề.

## Settings và câu hỏi

Settings: `title` (1–160), `description` (0–4000), `registrationClosesAt <= startsAt < endsAt`, thời lượng 1 phút–24 giờ, `penaltyMinutes` (1–120, UI mặc định 10), `shuffleQuestions`, `proctorIds` (tối đa 20 giáo viên ACTIVE có quyền canProctor), `paperId` (ID bộ đề READY hoặc null khi lưu nháp). Không nhận questions trong Settings. Công bố cần bộ đề READY chưa lưu trữ, có câu hợp lệ và các mốc đăng ký/thi còn ở tương lai. Author thêm paperId/paperTitle/paperRevision; snapshot questions chỉ cấp ADMIN.

Mỗi câu trong bộ đề gồm đúng `id` UUID, `type`, `prompt`, `context` (string tối đa 12000 hoặc null), `difficulty`, `content`, `answerKey`, `explanation`. Dùng bốn định dạng SINGLE_CHOICE, FILL_BLANK, MATCHING, ORDERING hiện có; không nhận topicId, objectiveId, materialVersionId, hint hoặc nguồn ngân hàng học. Đoạn đọc dùng context; bản đầu chưa có file nghe/ảnh hay tự luận trong contest.

`answers` là object theo UUID câu trong đề: `{ "<question-id>": { "type": "SINGLE_CHOICE", "optionId": "opt-a" } }`; giá trị null bỏ đáp án, câu không có key xem như chưa trả lời. save/submit thay toàn bộ tập đáp án. Đáp án khác loại, không thuộc đề hoặc cấu trúc thừa bị từ chối.

Trước giờ thi `paper/answers/result` null. Trong giờ, chỉ người APPROVED đã check-in, chưa chốt và còn hạn nhận paper/answers; paper có nội dung công khai và thứ tự được ghim từ check-in, không có answerKey/explanation/hint. Nộp sớm chưa nhận điểm/đáp án. Sau chốt toàn contest, APPROVED có bài nộp nhận result gồm correctCount/totalCount/scorePercent/elapsedMillis, submittedAt/processedAt, source/penaltySeconds và từng câu/response/outcome/answerKey/explanation.

## Nhất quán, hết giờ và phục hồi

Mọi lệnh nghiệp vụ cần UUID requestId. Cùng actor/requestId/operation/payload trả lại status/body cũ và header `Idempotent-Replayed: true`; requestId dùng với dữ liệu khác trả 409 REQUEST_ID_REUSED. Xung đột revision trả 409 CONTEST_REVISION_CONFLICT; lấy GET mới để đối chiếu, không tự ghi đè. Trước retry luôn giữ nguyên body cũ.

Một clientId giữ quyền ghi qua lease 45 giây; heartbeat UI 15 giây. Tab khác trả 409 CONTEST_OTHER_TAB. Reload giữ clientId qua cơ chế Web Lock M06; tab nhân bản nhận ID riêng. IndexedDB lưu bản nhập và lệnh chưa xác nhận, cùng fence theo user/logout. WebSocket reconnect đăng ký lại và GET đối soát. Autosave không gia hạn thời gian thi.

Khi hết hạn, worker hoặc API tự chốt từ đáp án đã commit. Gửi bài/nháp muộn trả **409 CONTEST_EXPIRED** đồng thời commit kết quả tự chốt; đây là 409 có receipt để retry vẫn nhận cùng kết quả. Các lỗi thông thường ném exception và rollback. `submittedAt` là hạn hiệu lực; `processedAt` là lúc thực sự xử lý, kể cả server khởi động lại muộn. Bài nộp unique theo participant, kết quả không sửa lại.

Lần rời màn thứ hai giảm deadline một lần; deadline mới không trước lúc áp dụng phạt. Từ lần ba bật một cảnh báo ở dòng participant; acknowledge theo lượt đếm đã xem, vi phạm mới có thể bật lại. Admin revoke chốt bài và loại khỏi BXH, giữ lịch sử. Mất mạng riêng với rời màn; trình duyệt bị đóng đột ngột/tắt JS hoặc offline không bảo đảm gửi được tín hiệu rời màn, không suy đoán tab đích.

BXH chỉ mở khi chốt toàn contest. Sort đúng giảm dần → elapsedMillis tăng dần; bằng cả hai đồng hạng. elapsed tính từ startsAt chung đến submittedAt, không cộng phạt thêm lần nữa. Người REVOKED hoặc không còn ACTIVE/STUDENT lúc chốt không được xếp hạng. Snapshot BXH và bài nộp giữ nguyên sau lưu trữ.

## WebSocket

Kết nối `/ws/events` dùng chung M06. Gửi `{ "version":1, "type":"SUBSCRIBE", "requestId":"<uuid>", "resource":"CONTEST", "contestId":123 }`, UNSUBSCRIBE cùng cấu trúc. Sau ACK mới nhận `{ "version":1, "type":"CONTEST_CHANGED", "contestId":123 }`. Tối đa bốn contest/socket. Event chỉ báo thay đổi sau commit: thay đổi riêng thí sinh gửi tới chính họ và giám thị; thay đổi trạng thái chung gửi tới mọi subscriber được phép. Event không mang câu hỏi, đáp án, điểm hay danh sách người dùng.

Khi quyền giáo viên đổi, socket của chính giáo viên nhận `{ "version":1, "type":"CONTEST_PERMISSIONS_CHANGED" }`, không có dữ liệu riêng. Client GET lại quyền; khi reconnect cũng GET đối soát. Thu hồi giám sát xóa các subscription contest tại server.

Worker `app.contest.worker-enabled` mặc định true, quét mỗi giây. HTTP deadline vẫn có hiệu lực khi worker/WebSocket chậm. Ứng dụng hiện có một backend và session/socket trong RAM; chưa có đo tải contest hoặc broadcast nhiều node.
