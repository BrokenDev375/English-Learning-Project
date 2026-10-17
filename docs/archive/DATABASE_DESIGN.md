# Thiết kế database — IELTS Learning

Ngày thiết kế: **12/10/2026**. Database: **MySQL 8.4 / InnoDB / UTF-8**. Thiết kế bám [plan](PLAN_IELTS_LEARNING_SYSTEM.md), giữ schema tài khoản/lớp đã có và dữ liệu/API Reading–Listening tại mục 6.4.

Thiết kế tổng thể có **44 bảng**, chia theo giai đoạn. Phiên bản ứng dụng hiện tại đã triển khai 3 bảng V1; các nhóm V2–V6 là thiết kế để triển khai lần lượt. SQL tham chiếu được đặt trong docs/database để phục vụ review/kiểm tra.

## Tài liệu sử dụng khi chia việc

| Tài liệu | Nội dung |
|---|---|
| [ERD](docs/database/ERD.md) | Quan hệ giữa các bảng, chia theo bốn nhóm |
| [Từ điển dữ liệu](docs/database/DATA_DICTIONARY.md) | Từng cột, kiểu, null/default, PK/FK/unique/index/check |
| [SQL MySQL](docs/database/schema.mysql.sql) | Schema hoàn chỉnh cho một database thử trống; bắt đầu bằng V1 hiện có |
| [Danh mục nhãn](docs/database/catalog-seed.sql) | 16 nhãn hiện hành, gồm 2 nhóm Speaking và 5 nhóm Writing |
| [Kết quả kiểm tra](docs/database/VALIDATION.md) | DDL và kiểm tra ràng buộc trên MySQL thật |
| [Bộ kiểm tra](docs/database/verify.ps1) | Tạo MySQL tạm, kiểm tra, ghi report rồi dọn container thử |

## 1. Thứ tự triển khai

V2–V6 là cách nhóm các phần thiết kế, không phải migration đã chạy. Khi triển khai có thể tách thành các file Flyway nhỏ hơn theo phụ thuộc.

| Giai đoạn | Số bảng | Phần việc |
|---|---:|---|
| V1 — đã có | 3 | users, classrooms, class_members |
| V2 — làm tiếp | 13 | assets, nhãn, resources/versions, câu hỏi/lựa chọn, nguồn nghe/đoạn/transcript, assignments/recipients |
| V3 | 9 | lần làm, nháp, lựa chọn, tiến trình/nộp đoạn, bài nộp, lựa chọn/tệp lúc nộp, review |
| V4 | 4 | rubric, outline Speaking, kết quả AI/công cụ, đánh giá giảng viên |
| V5 | 9 | đợt test, điểm kỹ năng, profile chung/lịch sử, nhu cầu và gợi ý bổ trợ |
| V6 | 6 | cấu hình XP, thưởng, ngày học, huy hiệu đã trao/định nghĩa, thực nghiệm |

Ưu tiên triển khai V2 và V3 để Reading/Listening có thể tải bài, lưu nháp và nộp qua mạng. Các cột/FK nối rubric, outline và profile được ghi rõ thời điểm bổ sung. V1 đã áp dụng giữ nguyên; bước triển khai tiếp tạo migration mới.

## 2. Những quyết định chính

- Mỗi tài khoản học viên có một learner_profiles; skill_profiles có tối đa bốn dòng theo kỹ năng. Cùng một học viên ở nhiều lớp vẫn dùng chung student_id.
- resources giữ danh tính/chủ sở hữu/kỹ năng. resource_versions giữ nội dung theo phiên bản. Khi giao, assignments tham chiếu một resource_version_id cố định.
- Reading, đề Writing/Speaking và các bước hướng dẫn có cấu trúc khác nhau nên nằm trong content_json của phiên bản. Câu hỏi và lựa chọn trắc nghiệm nằm trong bảng riêng để kiểm tra liên kết và thống kê.
- Listening lưu nguồn ở listening_media; listening_segments chứa các khoảng thời gian; listening_transcript_versions giữ bản transcript đúng nguồn/khoảng nghe. Nguồn/transcript đã dùng trong phiên bản xuất bản được giữ bất biến; sửa tạo bản mới.
- Câu hỏi dùng khóa ghép (resource_version_id, question_key); lựa chọn thêm option_key. q1/b là ID chuỗi trong một phiên bản, đúng hợp đồng API.
- Khi soạn trắc nghiệm, tạo câu với correct_option_key=null, thêm lựa chọn rồi đặt đáp án đúng. Trong DRAFT, xóa lựa chọn đang được tham chiếu cần gỡ con trỏ đáp án trước; publish kiểm tra đủ lựa chọn và đáp án.
- Một Attempt có một bản nộp đầu bất biến. Bản sửa/lần làm tiếp tạo Attempt và Submission mới theo quyền, nối previous_submission_id khi là bản sửa. SubmissionVersion trong mô hình logic được hiện thực bằng chính các snapshot ở submissions; submissionId luôn xác định một bản nội dung cố định.
- Lựa chọn nháp và lựa chọn lúc nộp nằm trong attempt_answers/submission_answers riêng. Review lưu thành sự kiện riêng, giữ câu trả lời lần đầu.
- AI giữ kết quả gốc và quyết định sửa/duyệt. Chấm giảng viên, điểm toàn kỹ năng và gamification có bảng riêng. Client tính kết quả theo phân công; server kiểm tra quyền/nguồn/trạng thái rồi lưu.
- Tệp audio/hình/PDF nằm ở kho tệp, assets chỉ giữ metadata và checksum. Phiên đăng nhập hiện dùng HTTP session trong RAM như module 1; khi backend khởi động lại người dùng đăng nhập lại, dữ liệu học tập ở MySQL.

## 3. Dữ liệu API lấy từ đâu?

| Trường API | Nơi lưu |
|---|---|
| resourceId / resourceVersionId / title / skill | resources + resource_versions |
| content.paragraphs | resource_versions.content_json |
| content.media / content.segments | listening_media + listening_segments |
| transcriptVersionId | listening_segments.transcript_version_id |
| questionId / questionText | questions.question_key / question_text |
| options[].optionId / text | question_options.option_key / option_text |
| correctOptionId / explanation | questions.correct_option_key / explanation; cấp ở bước được phép |
| answers: questionId → selectedOptionId | attempt_answers hoặc submission_answers tùy nháp/bản nộp |
| draftText / lastPlaybackSeconds | attempt_listening_progress |
| bản chép đầu của đoạn | listening_segment_submissions |
| submissionId / attemptNo | submissions.id + learning_attempts.attempt_no |
| outline đã dùng khi ghi âm | submissions.outline_version_id |
| nhãn/trở ngại/âm trên profile | learner_needs + learner_need_events |
| XP / ngày học / huy hiệu | reward_events / daily_learning_activities / earned_badges |

Các PK số nội bộ và tên snake_case được backend ánh xạ sang camelCase. ResourceVersion, Assignment, Attempt trong request/response luôn giữ đúng quan hệ; API không gửi studentId để tự nhận quyền sở hữu.

## 4. Schema JSON nghiệp vụ

MySQL kiểm tra JSON hợp lệ; backend cần kiểm tra thêm schema và tham chiếu bên trong trước khi lưu/xuất bản.

| Cột | Nội dung bắt buộc kiểm tra |
|---|---|
| resource_versions.content_json | Theo content_schema_version của phiên bản; Reading paragraphs với paragraphId ổn định; Writing taskType/prompt/visual refs; Speaking task/checklist; các hoạt động bổ trợ có activityId/type/stepOrder |
| resource_versions.teaching_json | model/guided feedback, đáp án bước bổ trợ, lời giải có điều kiện; xây DTO theo chế độ học/review |
| questions.evidence_json | paragraphId/range đúng văn bản hoặc segmentId/mốc đúng khoảng nghe; suy luận liên đoạn có ít nhất hai đoạn đủ ngữ cảnh |
| questions.secondary_labels_json | danh sách code tồn tại, đúng kỹ năng, đã duyệt |
| assignments.settings_json | segmentKeys nếu chỉ giao một phần, hỗ trợ cho phép, điều kiện hoàn thành; phiên bản cấu hình XP khi áp dụng |
| attempt_review_events.payload_json | SELF_REPORT: reason/skipped/note; PROBE: hoạt động và phản hồi; CORRECTION: lựa chọn/bản sửa; FEEDBACK_OPENED: nội dung đã mở |
| teacher_assessments.annotations_json | annotationId, câu/trích dẫn và phạm vi gốc hoặc mốc audio/âm, nhãn và nhận xét giảng viên; Writing grammar repair tham chiếu annotation đã xác nhận |
| skill_assessments.evidence_json | bài/đánh giá/đợt test nguồn, phạm vi toàn kỹ năng, điều kiện và cơ sở quy đổi nếu có |
| profile_history.components_json | bốn kỹ năng, skillAssessmentId, framework/score/performedAt; thiếu giữ null |
| learner_needs.details_json | âm cần luyện trong nhóm Speaking, trở ngại Listening đã đối chiếu và ghi chú; giữ mục tiêu câu hỏi riêng với trở ngại |

Nội dung khởi tạo học viên được dựng từ các trường được phép. Đáp án/lời giải, teaching_json và transcript mở theo điều kiện học/review; metadata JSON không được trả nguyên khối theo một serializer chung.

## 5. Giao bài và quyền theo lớp

Mặc định thiết kế dùng snapshot người nhận: lúc xuất bản bài giao chung, server tạo assignment_recipients cho các thành viên hiện tại. Người vào lớp sau nhận bài cũ khi giảng viên bổ sung người nhận. Bài riêng dùng SELECTED và chỉ thêm những thành viên được chọn.

Bản ghi người nhận tham chiếu users thay vì class_members. Khi rời/bị xóa khỏi lớp, dữ liệu bài nộp/profile được giữ; tạo lần làm hoặc nộp mới phải kiểm tra thành viên hiện tại và quyền chưa bị thu hồi. Lịch sử cá nhân có thể đọc qua trang lịch sử của chính học viên; tài nguyên/bài mới cần quyền lớp. Giảng viên giữ quyền xem/chấm bài cũ thuộc lớp mình, còn xem profile tổng hợp của học viên cần quan hệ quản lý phù hợp như plan.

Backend kiểm tra người tạo Assignment là chủ lớp, quyền với Resource và học viên thực sự có vai trò STUDENT. FK chỉ kiểm tra tồn tại/quan hệ dữ liệu; phiên đăng nhập và vai trò được kiểm tra ở service.

## 6. Transaction và trạng thái cần thực hiện

1. **Xuất bản tài liệu:** kiểm tra nội dung, câu hỏi có ít nhất hai lựa chọn/đúng một đáp án, nhãn/bằng chứng và nguồn. Dictation có transcript đã đối chiếu. Ghi người duyệt, xuất bản rồi giữ nội dung/ID bất biến. Chỉnh nội dung tạo ResourceVersion mới.
2. **Giao bài:** kiểm tra phiên bản đã xuất bản và chủ lớp; insert Assignment cùng người nhận trong một transaction. Unique requestId và fingerprint trả lại cùng bài khi retry đúng payload.
3. **Tạo Attempt:** khóa hàng người nhận bằng SELECT FOR UPDATE, kiểm tra thời hạn/số lần làm, cấp attemptNo rồi insert. Unique(assignment_id, student_id, attempt_no) chống hai client tạo cùng số lần.
4. **Lưu nháp:** khóa Attempt, kiểm tra còn DRAFT và đúng quyền; thay snapshot lựa chọn/văn bản/tiến trình trong cùng transaction. API chỉ xác nhận sau commit. Client giữ nháp tại máy nếu lưu thất bại.
5. **Nộp đoạn nghe:** ghi listening_segment_submissions với transcriptVersion cố định trước khi mở bản tham chiếu. Unique(attempt_id, segment_key) giữ một bản chép đầu; bản sửa vào review. Cần bổ sung endpoint POST /api/attempts/{id}/segments/{segmentId}/submission khi triển khai M06; POST /api/submissions xác nhận toàn Assignment khi các nhiệm vụ bắt buộc hoàn thành.
6. **Nộp bài:** khóa Attempt, kiểm tra requestId/trạng thái/điều kiện; tạo Submission, copy lựa chọn/tệp/outline/hỗ trợ, chuyển Attempt sang SUBMITTED trong một transaction. Khóa unique giữ một bản nộp cho một Attempt. Khi trả lại request cũ, truy vấn theo requestId trước khi báo lỗi trạng thái.
7. **Review:** lưu tự nhận xét hoặc skipped, thao tác kiểm tra rồi mở nội dung được phép. Các sự kiện có event_request_id; điểm/lựa chọn nộp đầu được giữ riêng.
8. **Chấm và profile:** đánh giá đã trả được giữ bất biến; sửa tạo revision. Client giảng viên tính/chốt điểm phù hợp; transaction ghi nguồn, con trỏ kỹ năng và profile_history. Thiếu một kỹ năng giữ overall null. Profile chung và báo cáo đợt có scope riêng.
9. **Bổ trợ:** giảng viên duyệt lý do/bài; dùng ResourceVersion và Assignment hiện có. remedial_recommendations liên kết nhu cầu, profile nguồn, bài được giao và bài mới đánh giá lại; không cần một ngân hàng hoặc cơ chế nộp riêng cho mỗi kỹ năng.
10. **Gamification:** kiểm tra hoạt động hoàn thành và cấu hình đã chốt; ghi thưởng/ngày/huy hiệu theo các khóa unique. Client tính tổng XP/cấp độ/streak từ dữ liệu hợp lệ. Bài dùng hỗ trợ và XP không tự đổi điểm kỹ năng.

## 7. Ràng buộc database và kiểm tra ở ứng dụng

| Yêu cầu | Cách bảo đảm |
|---|---|
| Đáp án thuộc đúng câu/phiên bản | FK ghép trên questions/options/attempt_answers/submission_answers |
| Attempt thuộc đúng người nhận và phiên bản được giao | FK ghép vào assignment_recipients và assignments |
| Transcript đúng nguồn và khoảng nghe | FK ghép gồm transcript ID, media ID, start/end |
| Điểm profile thuộc đúng người/kỹ năng | FK ghép skill_profiles → skill_assessments |
| Bài nộp dùng đúng outline | FK ghép outline_version_id + attempt_id |
| Speaking không có nhu cầu do AI xác nhận | CHECK source/status/confirmed_by và cấm AI_SUGGESTION trong sự kiện Speaking |
| Nộp/XP/ngày/huy hiệu không bị nhân đôi | Các unique/PK tương ứng |
| Quyền giảng viên/học viên, còn thuộc lớp, trạng thái chuyển hợp lệ | Service dựa vào session, khóa hàng và các quan hệ nguồn |
| Nội dung xuất bản/bài nộp đã chốt bất biến, phiên bản JSON/nguồn đủ ngữ cảnh | Service kiểm tra trước update/publish; SQL tham chiếu chưa thêm trigger cho các quy tắc này |
| Điểm toàn kỹ năng/overall có cơ sở | Client giảng viên tính và chốt; service kiểm tra quyền/liên kết; lịch sử ghi nguồn |

Các FK mặc định RESTRICT; dùng archive/retire cho học liệu đã được sử dụng. Dữ liệu chưa dùng có thể xóa theo thứ tự phụ thuộc. Khi rời lớp chỉ thay đổi membership/quyền hiện tại.

## 8. Kiểm tra thiết kế

Đã tạo toàn bộ schema trên **MySQL 8.4.9**, cùng collation dự án và restrict_fk_on_non_standard_key=ON. **39 kiểm tra qua**, gồm 12 kiểm tra hợp lệ và 27 trường hợp dữ liệu sai phải bị từ chối. Chi tiết, checksum và danh sách ở [VALIDATION.md](docs/database/VALIDATION.md).

Kiểm tra này xác nhận DDL/PK/FK/unique/check và cách lưu dữ liệu mẫu. Các API/quyền/trạng thái service của module tiếp theo sẽ được kiểm tra khi triển khai. Fixture là dữ liệu tổng hợp của bộ kiểm tra; [catalog-seed.sql](docs/database/catalog-seed.sql) chỉ chứa danh mục nhãn, học liệu Reading/Listening vẫn chuẩn bị theo plan.

Để kiểm tra lại, mở Docker Desktop và chạy từ thư mục project:

    powershell -ExecutionPolicy Bypass -File .\docs\database\verify.ps1

Script tạo container MySQL tạm không publish cổng, kiểm tra rồi dọn container mình tạo. schema.mysql.sql có cả V1 nên dùng cho database thử trống. Khi nâng database ứng dụng, tách phần mới thành Flyway migration sau V1.

## 9. Những chính sách còn chờ chốt

- Model/nơi gọi AI, ngân sách và ngưỡng phát âm vẫn theo các quyết định mở trong plan. Schema lưu provider/config/result nên không buộc một nhà cung cấp.
- XP 10/15/30, 100 XP/cấp và điều kiện huy hiệu vẫn là đề xuất. Schema giữ cấu hình theo phiên bản; catalog chưa bật chính sách thưởng.
- Academic/General và học liệu cụ thể chưa được quyết định bởi việc tạo schema. Rubric, taskType và source phải ghi rõ khi nhập dữ liệu.

## 10. Nguồn kỹ thuật

[MySQL 8.4 — Foreign keys](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html) là cơ sở cho FK ghép và khóa được tham chiếu. Thiết kế dùng PK/unique đầy đủ ở bảng cha; đã chạy với chế độ hạn chế nonstandard key bật.

[MySQL 8.4 — CHECK constraints](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html) là cơ sở cho giới hạn trạng thái, điểm và khoảng nghe. Các điều kiện liên bảng/quyền nghiệp vụ được kiểm tra tại service.

[MySQL 8.4 — JSON](https://dev.mysql.com/doc/refman/8.4/en/json.html) mô tả kiểu JSON và việc kiểm tra cú pháp. Các schema nghiệp vụ ở mục 4 là thiết kế ứng dụng, cần validator khi triển khai.
