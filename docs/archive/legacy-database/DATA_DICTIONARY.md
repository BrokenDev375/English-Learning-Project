# Từ điển dữ liệu — IELTS Learning

Ngày thiết kế: 12/10/2026. SQL tham chiếu: [schema.mysql.sql](schema.mysql.sql). Quyết định và cách triển khai: [DATABASE_DESIGN.md](../../DATABASE_DESIGN.md).

V1 là schema đã triển khai; V2–V6 là nhóm thiết kế cho các module tiếp theo. Các cột JSON có schema nghiệp vụ ghi trong tài liệu thiết kế; MySQL kiểm tra cú pháp JSON, ứng dụng kiểm tra ID/kiểu dữ liệu/các quy tắc bên trong.

## V1

### users

Tài khoản module 1 hiện có.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | PK |
| email | `VARCHAR(254) NOT NULL` | Unique, chuẩn hóa khi đăng ký |
| password_hash | `VARCHAR(100) NOT NULL` | BCrypt |
| full_name | `VARCHAR(100) NOT NULL` | Tên |
| role | `VARCHAR(16) NOT NULL` | TEACHER/STUDENT |
| created_at | `TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Thời điểm tạo |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (email)`.
- `CHECK (role IN ('TEACHER','STUDENT'))`.

### classrooms

Lớp do một giảng viên quản lý.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | PK |
| teacher_id | `BIGINT NOT NULL` | FK users.id |
| name | `VARCHAR(100) NOT NULL` | Tên |
| description | `VARCHAR(1000) NOT NULL DEFAULT ''` | Mô tả |
| join_code | `CHAR(8) NOT NULL` | Unique |
| create_request_id | `CHAR(36) NOT NULL` | UUID |
| created_at | `TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Thời điểm tạo |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (join_code)`.
- `UNIQUE (teacher_id, create_request_id)`.
- `FOREIGN KEY (teacher_id) REFERENCES users(id)`.

### class_members

Thành viên hiện tại; rời lớp chỉ xóa quan hệ này.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| classroom_id | `BIGINT NOT NULL` | FK classrooms.id |
| student_id | `BIGINT NOT NULL` | FK users.id |
| joined_at | `TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Lúc tham gia |

Khóa, index và ràng buộc:

- `PRIMARY KEY (classroom_id, student_id)`.
- `FOREIGN KEY (classroom_id) REFERENCES classrooms(id)`.
- `FOREIGN KEY (student_id) REFERENCES users(id)`.
- `INDEX (student_id)`.

## V2

### assets

Metadata tệp; file nằm trong kho tệp ngoài MySQL.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính do server cấp |
| owner_id | `BIGINT NOT NULL` | Người tải tệp |
| storage_key | `VARCHAR(255) NOT NULL` | Đường dẫn nội bộ bất biến, không trả trực tiếp cho client |
| original_name | `VARCHAR(255) NOT NULL` | Tên tệp hiển thị |
| mime_type | `VARCHAR(100) NOT NULL` | Loại tệp đã kiểm tra |
| size_bytes | `BIGINT NOT NULL` | Kích thước byte |
| sha256 | `CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Checksum nội dung |
| status | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'READY'` | READY/RETIRED |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Thời điểm tạo UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (storage_key)`.
- `FOREIGN KEY (owner_id) → users (id)`.
- `CHECK (size_bytes >= 0)`.
- `CHECK (status IN ('READY','RETIRED'))`.

### learning_labels

Danh mục mục tiêu/nhóm bổ trợ đã thống nhất; trở ngại tự nhận xét lưu riêng.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| code | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Mã nhãn theo plan |
| skill | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | READING/LISTENING/SPEAKING/WRITING |
| display_name | `VARCHAR(150) NOT NULL` | Tên tiếng Việt |
| label_kind | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | TARGET hoặc REMEDIAL |
| description | `TEXT NOT NULL` | Điều kiện áp dụng nhãn |
| is_active | `BOOLEAN NOT NULL DEFAULT TRUE` | Nhãn được dùng cho bài mới |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Thời điểm tạo UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (code)`.
- `UNIQUE (code, skill)`.
- `CHECK (skill IN ('READING','LISTENING','SPEAKING','WRITING'))`.
- `CHECK (label_kind IN ('TARGET','REMEDIAL'))`.
- `CHECK (is_active IN (0,1))`.

### resources

Danh tính bài trong ngân hàng; nội dung nằm trong từng phiên bản.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính do server cấp |
| owner_id | `BIGINT NOT NULL` | Giảng viên sở hữu |
| skill | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | READING/LISTENING/SPEAKING/WRITING |
| archived_at | `DATETIME(6) NULL` | Lưu trữ bài khỏi ngân hàng đang dùng |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Thời điểm tạo UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (id, skill)`.
- `INDEX (owner_id, skill, archived_at)`.
- `FOREIGN KEY (owner_id) → users (id)`.
- `CHECK (skill IN ('READING','LISTENING','SPEAKING','WRITING'))`.

### resource_versions

Snapshot nội dung đã xuất bản; sửa bài đã xuất bản bằng phiên bản mới.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính do server cấp |
| resource_id | `BIGINT NOT NULL` | Bài gốc |
| skill | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | READING/LISTENING/SPEAKING/WRITING |
| version_no | `INT NOT NULL` | Số phiên bản từ 1 |
| title | `VARCHAR(200) NOT NULL` | Tên bài theo phiên bản |
| resource_kind | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | MAIN/REMEDIAL |
| activity_type | `VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | MCQ/DICTATION/WRITING_ESSAY/SPEAKING_RECORDING/OUTLINE/PRONUNCIATION/GUIDED |
| topic | `VARCHAR(150) NULL` | Chủ đề |
| level_framework | `VARCHAR(24) NULL` | CEFR/IELTS_PRACTICE/RAW |
| level_label | `VARCHAR(32) NULL` | Mức luyện; không tự đổi sang band |
| support_level | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'INDEPENDENT'` | MODEL/GUIDED/INDEPENDENT/TRANSFER |
| status | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT'` | DRAFT/PUBLISHED/ARCHIVED |
| content_schema_version | `SMALLINT NOT NULL DEFAULT 1` | Phiên bản định dạng content_json |
| content_json | `JSON NOT NULL` | Nội dung khởi tạo được phép cấp: passage/paragraph, đề Writing/Speaking, bước tương tác |
| teaching_json | `JSON NULL` | Hướng dẫn, phản hồi và nội dung mở có điều kiện; DTO học viên không trả nguyên khối này |
| source_json | `JSON NULL` | URL/trang/tác giả, quyền sử dụng và trạng thái kiểm tra nguồn |
| rubric_version_id | `BIGINT NULL` | Rubric đúng nhiệm vụ; thêm FK ở V4 |
| reviewed_by | `BIGINT NULL` | Giảng viên duyệt |
| reviewed_at | `DATETIME(6) NULL` | Lúc duyệt |
| published_at | `DATETIME(6) NULL` | Lúc xuất bản |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Thời điểm tạo UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (resource_id, version_no)`.
- `UNIQUE (id, skill)`.
- `INDEX (skill, status, topic)`.
- `FOREIGN KEY (resource_id, skill) → resources (id, skill)`.
- `FOREIGN KEY (reviewed_by) → users (id)`.
- `CHECK (version_no > 0 AND content_schema_version > 0)`.
- `CHECK (resource_kind IN ('MAIN','REMEDIAL'))`.
- `CHECK (activity_type IN ('MCQ','DICTATION','WRITING_ESSAY','SPEAKING_RECORDING','OUTLINE','PRONUNCIATION','GUIDED'))`.
- `CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED'))`.
- `CHECK (support_level IN ('MODEL','GUIDED','INDEPENDENT','TRANSFER'))`.
- `CHECK (JSON_TYPE(content_json) = 'OBJECT')`.
- `FOREIGN KEY (rubric_version_id) → rubric_versions (id)` — bổ sung sau khi bảng đích đã được tạo ở V4.

### resource_assets

Các tệp đính kèm của đúng phiên bản bài.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| resource_version_id | `BIGINT NOT NULL` | Phiên bản |
| asset_id | `BIGINT NOT NULL` | Tệp |
| asset_role | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | VISUAL/AUDIO/PDF/MODEL_AUDIO/... |
| sort_order | `INT NOT NULL DEFAULT 0` | Thứ tự hiển thị |

Khóa, index và ràng buộc:

- `PRIMARY KEY (resource_version_id, asset_id, asset_role)`.
- `FOREIGN KEY (resource_version_id) → resource_versions (id)`.
- `FOREIGN KEY (asset_id) → assets (id)`.
- `CHECK (sort_order >= 0)`.

### resource_labels

Nhãn lọc bài/gợi ý bổ trợ; không suy ra điểm yếu chỉ từ nhãn.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| resource_version_id | `BIGINT NOT NULL` | Phiên bản |
| skill | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | READING/LISTENING/SPEAKING/WRITING |
| label_code | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Nhãn đã duyệt |

Khóa, index và ràng buộc:

- `PRIMARY KEY (resource_version_id, label_code)`.
- `INDEX (label_code, resource_version_id)`.
- `FOREIGN KEY (resource_version_id, skill) → resource_versions (id, skill)`.
- `FOREIGN KEY (label_code, skill) → learning_labels (code, skill)`.

### questions

Câu hỏi trắc nghiệm; q1 là khóa trong một phiên bản, không phải ID toàn hệ thống.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| resource_version_id | `BIGINT NOT NULL` | Phiên bản bài |
| question_key | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Ánh xạ questionId |
| skill | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | READING/LISTENING/SPEAKING/WRITING |
| sort_order | `INT NOT NULL` | Thứ tự câu |
| question_text | `TEXT NOT NULL` | Nội dung câu |
| question_type | `VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SINGLE_CHOICE'` | Phạm vi hiện tại: chọn một đáp án |
| correct_option_key | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL` | Ánh xạ correctOptionId; null khi đang soạn |
| explanation | `MEDIUMTEXT NULL` | Lời giải mở trong review |
| evidence_json | `JSON NULL` | paragraphId/câu hoặc segmentId/mốc nghe; service kiểm tra tham chiếu |
| primary_label_code | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL` | Nhãn chính đã duyệt |
| secondary_labels_json | `JSON NULL` | Nhãn phụ đã duyệt |
| label_status | `VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'UNDETERMINED'` | UNDETERMINED/TEACHER_REVIEWED |

Khóa, index và ràng buộc:

- `PRIMARY KEY (resource_version_id, question_key)`.
- `UNIQUE (resource_version_id, sort_order)`.
- `FOREIGN KEY (resource_version_id, skill) → resource_versions (id, skill)`.
- `FOREIGN KEY (primary_label_code, skill) → learning_labels (code, skill)`.
- `CHECK (skill IN ('READING','LISTENING'))`.
- `CHECK (question_type = 'SINGLE_CHOICE')`.
- `CHECK (sort_order > 0)`.
- `CHECK (label_status IN ('UNDETERMINED','TEACHER_REVIEWED'))`.
- `FOREIGN KEY (resource_version_id, question_key, correct_option_key) → question_options (resource_version_id, question_key, option_key)` — bổ sung sau khi bảng đích đã được tạo ở V2.

### question_options

Lựa chọn gắn đúng câu; không dùng vị trí A/B/C làm khóa thay đổi theo giao diện.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| resource_version_id | `BIGINT NOT NULL` | Phiên bản |
| question_key | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Câu |
| option_key | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Ánh xạ optionId |
| option_text | `TEXT NOT NULL` | Nội dung lựa chọn |
| sort_order | `INT NOT NULL` | Thứ tự |

Khóa, index và ràng buộc:

- `PRIMARY KEY (resource_version_id, question_key, option_key)`.
- `UNIQUE (resource_version_id, question_key, sort_order)`.
- `FOREIGN KEY (resource_version_id, question_key) → questions (resource_version_id, question_key)`.
- `CHECK (sort_order > 0)`.

### listening_media

Một nguồn nghe có thể dùng trong nhiều đoạn/bài; nguồn đã dùng được giữ bất biến.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính do server cấp |
| owner_id | `BIGINT NOT NULL` | Người quản lý nguồn |
| source_type | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | YOUTUBE/AUDIO_ASSET |
| video_id | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL` | ID YouTube; không phải file upload |
| asset_id | `BIGINT NULL` | File audio riêng |
| text_in_video | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'UNKNOWN'` | BURNED_IN/NONE/UNKNOWN |
| source_json | `JSON NULL` | URL/PDF đi kèm và trạng thái kiểm tra |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Thời điểm tạo UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `FOREIGN KEY (owner_id) → users (id)`.
- `FOREIGN KEY (asset_id) → assets (id)`.
- `CHECK ((source_type = 'YOUTUBE' AND video_id IS NOT NULL AND asset_id IS NULL) OR (source_type = 'AUDIO_ASSET' AND asset_id IS NOT NULL AND video_id IS NULL))`.
- `CHECK (text_in_video IN ('BURNED_IN','NONE','UNKNOWN'))`.

### listening_transcript_versions

Transcript bất biến cho đúng nguồn và khoảng nghe; sửa tạo revision mới.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính do server cấp |
| media_id | `BIGINT NOT NULL` | Nguồn nghe |
| start_seconds | `DECIMAL(10,3) NOT NULL` | Mốc tuyệt đối đầu đoạn |
| end_seconds | `DECIMAL(10,3) NOT NULL` | Mốc tuyệt đối cuối đoạn |
| revision_no | `INT NOT NULL` | Lần sửa transcript của nguồn/khoảng nghe |
| transcript_text | `LONGTEXT NOT NULL` | Lời tiếng Anh đã đối chiếu |
| comparison_rules_json | `JSON NULL` | Quy ước số/tên/từ đệm/chữ hoa/dấu câu/biến thể |
| reviewed_by | `BIGINT NULL` | Người duyệt |
| reviewed_at | `DATETIME(6) NULL` | Lúc duyệt |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Thời điểm tạo UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (media_id, start_seconds, end_seconds, revision_no)`.
- `UNIQUE (id, media_id, start_seconds, end_seconds)`.
- `FOREIGN KEY (media_id) → listening_media (id)`.
- `FOREIGN KEY (reviewed_by) → users (id)`.
- `CHECK (start_seconds >= 0 AND end_seconds > start_seconds)`.
- `CHECK (revision_no > 0)`.

### listening_segments

Đoạn nghe thuộc phiên bản bài, giữ transcript phù hợp đúng nguồn/mốc.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| resource_version_id | `BIGINT NOT NULL` | Phiên bản bài |
| segment_key | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Ánh xạ segmentId |
| skill | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'LISTENING'` | Khóa ràng buộc kỹ năng |
| media_id | `BIGINT NOT NULL` | Nguồn |
| title | `VARCHAR(200) NOT NULL` | Tên đoạn |
| sort_order | `INT NOT NULL` | Thứ tự |
| start_seconds | `DECIMAL(10,3) NOT NULL` | Mốc đầu tuyệt đối |
| end_seconds | `DECIMAL(10,3) NOT NULL` | Mốc cuối tuyệt đối |
| transcript_version_id | `BIGINT NULL` | Transcript khớp nguồn/mốc; null khi soạn |

Khóa, index và ràng buộc:

- `PRIMARY KEY (resource_version_id, segment_key)`.
- `UNIQUE (resource_version_id, sort_order)`.
- `UNIQUE (resource_version_id, segment_key, transcript_version_id)`.
- `FOREIGN KEY (resource_version_id, skill) → resource_versions (id, skill)`.
- `FOREIGN KEY (media_id) → listening_media (id)`.
- `FOREIGN KEY (transcript_version_id, media_id, start_seconds, end_seconds) → listening_transcript_versions (id, media_id, start_seconds, end_seconds)`.
- `CHECK (skill = 'LISTENING')`.
- `CHECK (start_seconds >= 0 AND end_seconds > start_seconds)`.
- `CHECK (sort_order > 0)`.

### assignments

Một lần giao một phiên bản bài cho một lớp hoặc tập thành viên trong lớp.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính do server cấp |
| classroom_id | `BIGINT NOT NULL` | Lớp |
| created_by | `BIGINT NOT NULL` | Giảng viên tạo |
| resource_version_id | `BIGINT NOT NULL` | Phiên bản cố định |
| purpose | `VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | TEST_INITIAL/TEST_PERIODIC/PRACTICE/REMEDIAL/RECHECK |
| recipient_mode | `VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | CLASS_SNAPSHOT/SELECTED |
| status | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT'` | DRAFT/PUBLISHED/CLOSED/ARCHIVED |
| opens_at | `DATETIME(6) NULL` | Thời điểm mở |
| due_at | `DATETIME(6) NULL` | Hạn nộp |
| allow_late | `BOOLEAN NOT NULL DEFAULT FALSE` | Cho phép nộp muộn |
| max_attempts | `INT NOT NULL DEFAULT 1` | Số lần làm tối đa |
| feedback_policy | `VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'AFTER_SUBMISSION'` | AFTER_SUBMISSION/AFTER_DUE/TEACHER_RELEASE |
| feedback_released_at | `DATETIME(6) NULL` | Giảng viên mở phản hồi |
| settings_json | `JSON NULL` | Các segment được giao, hỗ trợ được phép, điều kiện hoàn thành và cấu hình thưởng snapshot |
| create_request_id | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | UUID giữ nguyên khi gửi lại cùng yêu cầu |
| request_fingerprint | `CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | SHA-256 của payload chuẩn hóa; server tính để nhận biết requestId bị dùng lại khác nội dung |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Thời điểm tạo UTC |
| updated_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)` | Thời điểm cập nhật UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (id, resource_version_id)`.
- `UNIQUE (created_by, create_request_id)`.
- `INDEX (classroom_id, status, due_at)`.
- `FOREIGN KEY (classroom_id) → classrooms (id)`.
- `FOREIGN KEY (created_by) → users (id)`.
- `FOREIGN KEY (resource_version_id) → resource_versions (id)`.
- `CHECK (purpose IN ('TEST_INITIAL','TEST_PERIODIC','PRACTICE','REMEDIAL','RECHECK'))`.
- `CHECK (recipient_mode IN ('CLASS_SNAPSHOT','SELECTED'))`.
- `CHECK (status IN ('DRAFT','PUBLISHED','CLOSED','ARCHIVED'))`.
- `CHECK (opens_at IS NULL OR due_at IS NULL OR due_at >= opens_at)`.
- `CHECK (max_attempts > 0)`.
- `CHECK (allow_late IN (0,1))`.
- `CHECK (feedback_policy IN ('AFTER_SUBMISSION','AFTER_DUE','TEACHER_RELEASE'))`.

### assignment_recipients

Snapshot người nhận; không FK vào class_members để rời lớp không làm mất lịch sử.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| assignment_id | `BIGINT NOT NULL` | Bài được giao |
| student_id | `BIGINT NOT NULL` | Người nhận |
| revoked_at | `DATETIME(6) NULL` | Thu hồi quyền làm mới |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Thời điểm tạo UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (assignment_id, student_id)`.
- `INDEX (student_id, revoked_at, assignment_id)`.
- `FOREIGN KEY (assignment_id) → assignments (id)`.
- `FOREIGN KEY (student_id) → users (id)`.

## V3

### learning_attempts

Một lần làm của đúng người nhận và đúng phiên bản bài.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính do server cấp |
| assignment_id | `BIGINT NOT NULL` | Bài được giao |
| student_id | `BIGINT NOT NULL` | Học viên |
| resource_version_id | `BIGINT NOT NULL` | Phiên bản được giao |
| attempt_no | `INT NOT NULL` | Số lần làm từ 1 |
| status | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT'` | DRAFT/SUBMITTED/ABANDONED |
| create_request_id | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | UUID giữ nguyên khi gửi lại cùng yêu cầu |
| request_fingerprint | `CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | SHA-256 của payload chuẩn hóa; server tính để nhận biết requestId bị dùng lại khác nội dung |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Thời điểm tạo UTC |
| updated_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)` | Thời điểm cập nhật UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (assignment_id, student_id, attempt_no)`.
- `UNIQUE (student_id, create_request_id)`.
- `UNIQUE (id, resource_version_id)`.
- `UNIQUE (id, assignment_id, student_id, resource_version_id)`.
- `INDEX (student_id, status, updated_at)`.
- `FOREIGN KEY (assignment_id, student_id) → assignment_recipients (assignment_id, student_id)`.
- `FOREIGN KEY (assignment_id, resource_version_id) → assignments (id, resource_version_id)`.
- `CHECK (attempt_no > 0)`.
- `CHECK (status IN ('DRAFT','SUBMITTED','ABANDONED'))`.

### attempt_drafts

Snapshot nháp văn bản/outline/vị trí; lựa chọn trắc nghiệm ở attempt_answers.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| attempt_id | `BIGINT NOT NULL` | Lần làm |
| body_text | `LONGTEXT NULL` | Bài viết hoặc ý tiếng Việt đang soạn |
| preparation_json | `JSON NULL` | Outline/hoạt động bổ trợ đang làm |
| cursor_json | `JSON NULL` | Vị trí paragraph/câu |
| support_used_json | `JSON NULL` | Hỗ trợ đã dùng; dữ liệu quan sát, không tự xác nhận năng lực |
| updated_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)` | Thời điểm cập nhật UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (attempt_id)`.
- `FOREIGN KEY (attempt_id) → learning_attempts (id)`.

### attempt_answers

Lựa chọn nháp được kiểm tra bằng FK đúng câu, phiên bản và option.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| attempt_id | `BIGINT NOT NULL` | Lần làm |
| resource_version_id | `BIGINT NOT NULL` | Phiên bản |
| question_key | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Câu |
| selected_option_key | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL` | Lựa chọn; null chưa trả lời |
| updated_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)` | Thời điểm cập nhật UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (attempt_id, question_key)`.
- `FOREIGN KEY (attempt_id, resource_version_id) → learning_attempts (id, resource_version_id)`.
- `FOREIGN KEY (resource_version_id, question_key) → questions (resource_version_id, question_key)`.
- `FOREIGN KEY (resource_version_id, question_key, selected_option_key) → question_options (resource_version_id, question_key, option_key)`.

### attempt_listening_progress

Nháp chép lời và vị trí theo từng đoạn; trạng thái nộp suy từ bảng nộp đoạn.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| attempt_id | `BIGINT NOT NULL` | Lần làm |
| resource_version_id | `BIGINT NOT NULL` | Phiên bản |
| segment_key | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Đoạn |
| draft_text | `LONGTEXT NOT NULL` | Bản chép đang làm |
| last_playback_seconds | `DECIMAL(10,3) NOT NULL` | Vị trí tuyệt đối; service kiểm tra trong khoảng |
| support_used_json | `JSON NULL` | Tốc độ/nghe lại/chữ trong hình |
| reference_opened_at | `DATETIME(6) NULL` | Server ghi lần đầu cấp transcript |
| updated_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)` | Thời điểm cập nhật UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (attempt_id, segment_key)`.
- `FOREIGN KEY (attempt_id, resource_version_id) → learning_attempts (id, resource_version_id)`.
- `FOREIGN KEY (resource_version_id, segment_key) → listening_segments (resource_version_id, segment_key)`.
- `CHECK (last_playback_seconds >= 0)`.

### listening_segment_submissions

Bản chép đầu bất biến trước khi mở transcript; cho nộp từng đoạn của cùng Attempt.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính do server cấp |
| attempt_id | `BIGINT NOT NULL` | Lần làm |
| resource_version_id | `BIGINT NOT NULL` | Phiên bản |
| segment_key | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Đoạn |
| transcript_version_id | `BIGINT NOT NULL` | Bản tham chiếu cố định |
| submitted_text | `LONGTEXT NOT NULL` | Bản chép đầu |
| support_used_json | `JSON NULL` | Điều kiện nghe lúc nộp |
| request_id | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | UUID giữ nguyên khi gửi lại cùng yêu cầu |
| request_fingerprint | `CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | SHA-256 của payload chuẩn hóa; server tính để nhận biết requestId bị dùng lại khác nội dung |
| submitted_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Lúc server xác nhận |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (attempt_id, segment_key)`.
- `UNIQUE (attempt_id, request_id)`.
- `FOREIGN KEY (attempt_id, resource_version_id) → learning_attempts (id, resource_version_id)`.
- `FOREIGN KEY (resource_version_id, segment_key, transcript_version_id) → listening_segments (resource_version_id, segment_key, transcript_version_id)`.

### submissions

Một bản nộp bất biến cho một Attempt; bản sửa là row mới liên kết previous_submission_id.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính do server cấp |
| attempt_id | `BIGINT NOT NULL` | Lần làm |
| assignment_id | `BIGINT NOT NULL` | Bài được giao; lặp để FK bảo vệ người và phiên bản |
| student_id | `BIGINT NOT NULL` | Học viên |
| resource_version_id | `BIGINT NOT NULL` | Phiên bản |
| previous_submission_id | `BIGINT NULL` | Bản nộp trước của cùng người/bài |
| status | `VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SUBMITTED'` | SUBMITTED/TEACHER_REVIEWED/RETURNED |
| outline_version_id | `BIGINT NULL` | Outline Speaking đã dùng; FK được bổ sung ở V4 |
| body_text | `LONGTEXT NULL` | Bài Writing hoàn chỉnh; audio ở submission_assets |
| preparation_snapshot_json | `JSON NULL` | Outline/hoạt động bổ trợ đúng bản đã sử dụng |
| support_used_json | `JSON NULL` | Điều kiện hỗ trợ đã sử dụng |
| client_result_json | `JSON NULL` | Kết quả CLIENT_OBJECTIVE; không là điểm xác nhận |
| request_id | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | UUID giữ nguyên khi gửi lại cùng yêu cầu |
| request_fingerprint | `CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | SHA-256 của payload chuẩn hóa; server tính để nhận biết requestId bị dùng lại khác nội dung |
| submitted_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Server xác nhận |
| returned_at | `DATETIME(6) NULL` | Phản hồi được trả |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (attempt_id)`.
- `UNIQUE (student_id, request_id)`.
- `UNIQUE (id, resource_version_id)`.
- `UNIQUE (id, assignment_id, student_id)`.
- `UNIQUE (id, student_id)`.
- `INDEX (assignment_id, status, submitted_at)`.
- `FOREIGN KEY (attempt_id, assignment_id, student_id, resource_version_id) → learning_attempts (id, assignment_id, student_id, resource_version_id)`.
- `FOREIGN KEY (previous_submission_id, assignment_id, student_id) → submissions (id, assignment_id, student_id)`.
- `CHECK (status IN ('SUBMITTED','TEACHER_REVIEWED','RETURNED'))`.
- `FOREIGN KEY (outline_version_id, attempt_id) → speaking_outline_versions (id, attempt_id)` — bổ sung sau khi bảng đích đã được tạo ở V4.

### submission_answers

Snapshot lựa chọn lúc nộp; dùng phân tích câu sai và giữ điểm lần đầu.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| submission_id | `BIGINT NOT NULL` | Bản nộp |
| resource_version_id | `BIGINT NOT NULL` | Phiên bản |
| question_key | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Câu |
| selected_option_key | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL` | Lựa chọn lúc nộp |

Khóa, index và ràng buộc:

- `PRIMARY KEY (submission_id, question_key)`.
- `INDEX (resource_version_id, question_key)`.
- `FOREIGN KEY (submission_id, resource_version_id) → submissions (id, resource_version_id)`.
- `FOREIGN KEY (resource_version_id, question_key) → questions (resource_version_id, question_key)`.
- `FOREIGN KEY (resource_version_id, question_key, selected_option_key) → question_options (resource_version_id, question_key, option_key)`.

### submission_assets

Audio/tệp của bản nộp; service kiểm tra người sở hữu tệp.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| submission_id | `BIGINT NOT NULL` | Bản nộp |
| asset_id | `BIGINT NOT NULL` | Tệp đã lưu thành công |
| asset_role | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | RECORDING/ATTACHMENT/... |
| sort_order | `INT NOT NULL DEFAULT 0` | Thứ tự |

Khóa, index và ràng buộc:

- `PRIMARY KEY (submission_id, asset_id, asset_role)`.
- `FOREIGN KEY (submission_id) → submissions (id)`.
- `FOREIGN KEY (asset_id) → assets (id)`.
- `CHECK (sort_order >= 0)`.

### attempt_review_events

Lịch sử tự nhận xét, kiểm tra thêm, mở phản hồi và sửa; append-only.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính do server cấp |
| attempt_id | `BIGINT NOT NULL` | Lần làm |
| resource_version_id | `BIGINT NOT NULL` | Phiên bản |
| question_key | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL` | Câu được review |
| segment_key | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL` | Đoạn được review |
| event_type | `VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | SELF_REPORT/PROBE/FEEDBACK_OPENED/CORRECTION |
| payload_json | `JSON NOT NULL` | Lý do hoặc skipped, bằng chứng chọn, câu sửa; schema theo event_type |
| event_request_id | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | UUID giữ nguyên khi gửi lại cùng yêu cầu |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Thời điểm tạo UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (attempt_id, event_request_id)`.
- `INDEX (attempt_id, question_key, event_type, id)`.
- `INDEX (attempt_id, segment_key, event_type, id)`.
- `FOREIGN KEY (attempt_id, resource_version_id) → learning_attempts (id, resource_version_id)`.
- `FOREIGN KEY (resource_version_id, question_key) → questions (resource_version_id, question_key)`.
- `FOREIGN KEY (resource_version_id, segment_key) → listening_segments (resource_version_id, segment_key)`.
- `CHECK ((question_key IS NOT NULL AND segment_key IS NULL) OR (question_key IS NULL AND segment_key IS NOT NULL))`.
- `CHECK (event_type IN ('SELF_REPORT','PROBE','FEEDBACK_OPENED','CORRECTION'))`.

## V4

### rubric_versions

Rubric/checklist bất biến; nguồn và tiêu chí đúng nhiệm vụ.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính |
| rubric_code | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Mã rubric |
| version_no | `INT NOT NULL` | Phiên bản |
| skill | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Kỹ năng |
| task_type | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | TASK_1/TASK_2/SPEAKING/OUTLINE/... |
| rubric_type | `VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | IELTS_BAND/CLASS_POINTS/OUTLINE_CHECKLIST |
| criteria_json | `JSON NOT NULL` | Tiêu chí/descriptors; không tự tạo điểm tham chiếu chưa được công bố |
| source_json | `JSON NULL` | Nguồn rubric, phiên bản tài liệu |
| approved_by | `BIGINT NULL` | Giảng viên duyệt |
| approved_at | `DATETIME(6) NULL` | Duyệt |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (rubric_code, version_no)`.
- `FOREIGN KEY (approved_by) → users (id)`.
- `CHECK (version_no > 0)`.
- `CHECK (skill IN ('READING','LISTENING','SPEAKING','WRITING'))`.
- `CHECK (rubric_type IN ('IELTS_BAND','CLASS_POINTS','OUTLINE_CHECKLIST'))`.

### speaking_outline_versions

Giữ outline trước/sau AI; bản ghi âm tham chiếu đúng outline đã dùng.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính |
| attempt_id | `BIGINT NOT NULL` | Lần làm Speaking |
| version_no | `INT NOT NULL` | Phiên bản outline |
| vietnamese_ideas | `LONGTEXT NULL` | Ý tiếng Việt tùy chọn |
| english_outline | `LONGTEXT NOT NULL` | Dàn ý/từ khóa tiếng Anh |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (attempt_id, version_no)`.
- `UNIQUE (id, attempt_id)`.
- `FOREIGN KEY (attempt_id) → learning_attempts (id)`.
- `CHECK (version_no > 0)`.

### ai_analyses

Kết quả công cụ/AI nguyên bản và quyết định giảng viên; không tự cập nhật band.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính |
| requested_by | `BIGINT NOT NULL` | Tài khoản được phép yêu cầu |
| resource_version_id | `BIGINT NOT NULL` | Ngữ cảnh bài cố định |
| submission_id | `BIGINT NULL` | Bài viết/bài làm được phân tích |
| outline_version_id | `BIGINT NULL` | Outline Speaking |
| outline_attempt_id | `BIGINT NULL` | Ràng buộc outline đúng lần làm |
| audio_asset_id | `BIGINT NULL` | Bản thu nếu công cụ phát âm |
| rubric_version_id | `BIGINT NULL` | Rubric/checklist |
| analysis_type | `VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | QUESTION_LABELS/WRITING/OUTLINE/NEEDS_SUMMARY/MATERIAL_DRAFT/PRONUNCIATION |
| status | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | PENDING/SUCCEEDED/FAILED |
| provider | `VARCHAR(80) NULL` | Nhà cung cấp |
| model_name | `VARCHAR(120) NULL` | Model/công cụ |
| prompt_version | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL` | Phiên bản prompt |
| input_snapshot_json | `JSON NOT NULL` | Ngữ cảnh/ID nguồn/phiên bản/rubric/mẫu/config ngưỡng |
| result_json | `JSON NULL` | Kết quả gốc có bằng chứng |
| error_code | `VARCHAR(64) NULL` | Mã lỗi an toàn |
| reviewed_by | `BIGINT NULL` | Giảng viên kiểm tra |
| review_decision | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL` | ACCEPTED/EDITED/REJECTED |
| reviewed_result_json | `JSON NULL` | Bản đã sửa/duyệt, giữ kết quả gốc |
| reviewed_at | `DATETIME(6) NULL` | Thời gian duyệt |
| request_id | `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Chống gửi trùng |
| request_fingerprint | `CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Hash payload |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | UTC |
| completed_at | `DATETIME(6) NULL` | Kết thúc xử lý |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (requested_by, request_id)`.
- `INDEX (submission_id, analysis_type, created_at)`.
- `FOREIGN KEY (requested_by) → users (id)`.
- `FOREIGN KEY (resource_version_id) → resource_versions (id)`.
- `FOREIGN KEY (submission_id, resource_version_id) → submissions (id, resource_version_id)`.
- `FOREIGN KEY (outline_version_id, outline_attempt_id) → speaking_outline_versions (id, attempt_id)`.
- `FOREIGN KEY (audio_asset_id) → assets (id)`.
- `FOREIGN KEY (rubric_version_id) → rubric_versions (id)`.
- `FOREIGN KEY (reviewed_by) → users (id)`.
- `CHECK (analysis_type IN ('QUESTION_LABELS','WRITING','OUTLINE','NEEDS_SUMMARY','MATERIAL_DRAFT','PRONUNCIATION'))`.
- `CHECK (status IN ('PENDING','SUCCEEDED','FAILED'))`.
- `CHECK ((outline_version_id IS NULL AND outline_attempt_id IS NULL) OR (outline_version_id IS NOT NULL AND outline_attempt_id IS NOT NULL))`.
- `CHECK (review_decision IS NULL OR review_decision IN ('ACCEPTED','EDITED','REJECTED'))`.
- `FOREIGN KEY (outline_attempt_id, resource_version_id) → learning_attempts (id, resource_version_id)`.

### teacher_assessments

Nháp/chốt chấm bài; sửa đánh giá đã trả bằng revision mới, giữ lịch sử.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính |
| submission_id | `BIGINT NOT NULL` | Bản nộp cố định |
| student_id | `BIGINT NOT NULL` | Học viên của bản nộp |
| teacher_id | `BIGINT NOT NULL` | Người chấm |
| rubric_version_id | `BIGINT NOT NULL` | Rubric |
| revision_no | `INT NOT NULL` | Lần đánh giá |
| status | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT'` | DRAFT/RETURNED |
| score_scale | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | BAND/POINTS/RAW/NONE |
| score_value | `DECIMAL(7,2) NULL` | Điểm nhiệm vụ, không tự là điểm toàn kỹ năng |
| score_max | `DECIMAL(7,2) NULL` | Mẫu số nếu chấm điểm thô |
| criteria_json | `JSON NOT NULL` | Điểm từng tiêu chí và căn cứ |
| annotations_json | `JSON NULL` | Câu lỗi Writing/âm Speaking/mốc audio, quote và phạm vi đã xác nhận |
| feedback_text | `MEDIUMTEXT NULL` | Nhận xét tổng thể |
| ai_analysis_id | `BIGINT NULL` | Gợi ý đã tham khảo |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | UTC |
| returned_at | `DATETIME(6) NULL` | Khi trả học viên |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (submission_id, revision_no)`.
- `UNIQUE (id, student_id)`.
- `INDEX (student_id, status, returned_at)`.
- `FOREIGN KEY (submission_id, student_id) → submissions (id, student_id)`.
- `FOREIGN KEY (teacher_id) → users (id)`.
- `FOREIGN KEY (rubric_version_id) → rubric_versions (id)`.
- `FOREIGN KEY (ai_analysis_id) → ai_analyses (id)`.
- `CHECK (revision_no > 0)`.
- `CHECK (status IN ('DRAFT','RETURNED'))`.
- `CHECK (score_scale IN ('BAND','POINTS','RAW','NONE'))`.
- `CHECK (score_value IS NULL OR score_value >= 0)`.
- `CHECK (score_scale <> 'BAND' OR score_value IS NULL OR (score_value <= 9 AND MOD(score_value * 2, 1) = 0))`.
- `CHECK (score_scale <> 'NONE' OR score_value IS NULL)`.
- `CHECK (score_max IS NULL OR (score_max > 0 AND (score_value IS NULL OR score_value <= score_max)))`.

## V5

### assessment_cycles

Đợt test gồm các bài bốn kỹ năng; báo cáo đợt không đổi theo profile mới.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính |
| classroom_id | `BIGINT NOT NULL` | Lớp |
| created_by | `BIGINT NOT NULL` | Giảng viên |
| title | `VARCHAR(200) NOT NULL` | Tên đợt |
| purpose | `VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | TEST_INITIAL/TEST_PERIODIC/RECHECK |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `FOREIGN KEY (classroom_id) → classrooms (id)`.
- `FOREIGN KEY (created_by) → users (id)`.
- `CHECK (purpose IN ('TEST_INITIAL','TEST_PERIODIC','RECHECK'))`.

### cycle_assignments

Một kỹ năng có thể gồm nhiều nhiệm vụ, ví dụ Writing Task 1 và Task 2.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| cycle_id | `BIGINT NOT NULL` | Đợt test |
| assignment_id | `BIGINT NOT NULL` | Bài giao |
| resource_version_id | `BIGINT NOT NULL` | Phiên bản bài |
| skill | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Kỹ năng |

Khóa, index và ràng buộc:

- `PRIMARY KEY (cycle_id, assignment_id)`.
- `FOREIGN KEY (cycle_id) → assessment_cycles (id)`.
- `FOREIGN KEY (assignment_id, resource_version_id) → assignments (id, resource_version_id)`.
- `FOREIGN KEY (resource_version_id, skill) → resource_versions (id, skill)`.

### skill_assessments

Ước lượng toàn kỹ năng do giảng viên xác nhận; lịch sử append-only.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính |
| student_id | `BIGINT NOT NULL` | Học viên |
| skill | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Kỹ năng |
| confirmed_by | `BIGINT NOT NULL` | Giảng viên xác nhận |
| cycle_id | `BIGINT NULL` | Đợt test nguồn |
| level_framework | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | IELTS_BAND/CEFR/RAW |
| score_value | `DECIMAL(7,2) NULL` | Điểm phù hợp khung |
| level_label | `VARCHAR(32) NULL` | Ví dụ B1; không quy đổi tự động |
| method_version | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Quy tắc/phiên bản cách tổng hợp |
| evidence_json | `JSON NOT NULL` | Bài nộp, teacherAssessmentId, phạm vi và điều kiện đánh giá |
| performed_at | `DATETIME(6) NOT NULL` | Thời điểm thực hiện đánh giá |
| confirmed_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Lúc chốt |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (id, student_id)`.
- `UNIQUE (id, student_id, skill)`.
- `INDEX (student_id, skill, performed_at, id)`.
- `FOREIGN KEY (student_id) → users (id)`.
- `FOREIGN KEY (confirmed_by) → users (id)`.
- `FOREIGN KEY (cycle_id) → assessment_cycles (id)`.
- `CHECK (skill IN ('READING','LISTENING','SPEAKING','WRITING'))`.
- `CHECK (level_framework IN ('IELTS_BAND','CEFR','RAW'))`.
- `CHECK (score_value IS NULL OR score_value >= 0)`.
- `CHECK (level_framework <> 'IELTS_BAND' OR (score_value IS NOT NULL AND score_value <= 9 AND MOD(score_value * 2, 1) = 0))`.
- `CHECK (level_framework <> 'CEFR' OR level_label IS NOT NULL)`.

### learner_profiles

Một hồ sơ cho một tài khoản học viên, không nhân theo lớp.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| student_id | `BIGINT NOT NULL` | Học viên |
| target_overall | `DECIMAL(3,1) NULL` | Mục tiêu học viên chọn; không là điểm hiện tại |
| learning_goal | `TEXT NULL` | Mục tiêu |
| current_history_id | `BIGINT NULL` | Snapshot profile hiện tại; thêm FK sau profile_history |
| revision_no | `INT NOT NULL DEFAULT 1` | Tuần tự cập nhật trong transaction |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (student_id)`.
- `FOREIGN KEY (student_id) → users (id)`.
- `CHECK (target_overall IS NULL OR (target_overall BETWEEN 0 AND 9 AND MOD(target_overall * 2, 1) = 0))`.
- `CHECK (revision_no > 0)`.
- `FOREIGN KEY (current_history_id, student_id) → profile_history (id, student_id)` — bổ sung sau khi bảng đích đã được tạo ở V5.

### skill_profiles

Bốn dòng trên mỗi hồ sơ; điểm chưa có giữ con trỏ null.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| student_id | `BIGINT NOT NULL` | Học viên |
| skill | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Kỹ năng |
| current_assessment_id | `BIGINT NULL` | Đánh giá đã xác nhận đúng người/kỹ năng |
| target_score | `DECIMAL(3,1) NULL` | Mục tiêu IELTS nếu có |
| updated_at | `DATETIME(6) NULL` | Lần cập nhật |

Khóa, index và ràng buộc:

- `PRIMARY KEY (student_id, skill)`.
- `FOREIGN KEY (student_id) → learner_profiles (student_id)`.
- `FOREIGN KEY (current_assessment_id, student_id, skill) → skill_assessments (id, student_id, skill)`.
- `CHECK (skill IN ('READING','LISTENING','SPEAKING','WRITING'))`.
- `CHECK (target_score IS NULL OR (target_score BETWEEN 0 AND 9 AND MOD(target_score * 2, 1) = 0))`.

### profile_history

Snapshot bốn nguồn/điểm/overall do client giảng viên tính; giữ cả báo cáo đợt.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính |
| student_id | `BIGINT NOT NULL` | Học viên |
| confirmed_by | `BIGINT NOT NULL` | Giảng viên |
| scope | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | PROFILE/CYCLE |
| cycle_id | `BIGINT NULL` | Đợt test nếu scope=CYCLE |
| changed_assessment_id | `BIGINT NULL` | Đánh giá vừa cập nhật |
| components_json | `JSON NOT NULL` | Bốn kỹ năng, score/skillAssessmentId/performedAt; thiếu giữ null |
| overall_estimate | `DECIMAL(3,1) NULL` | Null khi chưa đủ bốn nguồn IELTS phù hợp |
| method_version | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Công thức/thang điểm được dùng |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (id, student_id)`.
- `INDEX (student_id, scope, created_at)`.
- `FOREIGN KEY (student_id) → users (id)`.
- `FOREIGN KEY (confirmed_by) → users (id)`.
- `FOREIGN KEY (cycle_id) → assessment_cycles (id)`.
- `FOREIGN KEY (changed_assessment_id, student_id) → skill_assessments (id, student_id)`.
- `CHECK ((scope = 'PROFILE' AND cycle_id IS NULL) OR (scope = 'CYCLE' AND cycle_id IS NOT NULL))`.
- `CHECK (overall_estimate IS NULL OR (overall_estimate BETWEEN 0 AND 9 AND MOD(overall_estimate * 2, 1) = 0))`.

### learner_needs

Nhu cầu hiện tại theo nhãn; âm/trở ngại là chi tiết bên trong, không tạo nhãn mới.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính |
| student_id | `BIGINT NOT NULL` | Học viên |
| skill | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Kỹ năng |
| label_code | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Mục tiêu/nhóm bổ trợ |
| source | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | AI/TEACHER |
| status | `VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | SUGGESTED/CONFIRMED/PRACTICING/RECHECKING/IMPROVING/CLOSED |
| confirmed_by | `BIGINT NULL` | Giảng viên xác nhận |
| details_json | `JSON NULL` | Âm cần luyện, các trở ngại đã đối chiếu và ghi chú |
| revision_no | `INT NOT NULL DEFAULT 1` | Phiên bản trạng thái |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (student_id, skill, label_code)`.
- `UNIQUE (id, student_id, skill)`.
- `INDEX (student_id, status)`.
- `FOREIGN KEY (student_id) → users (id)`.
- `FOREIGN KEY (label_code, skill) → learning_labels (code, skill)`.
- `FOREIGN KEY (confirmed_by) → users (id)`.
- `CHECK (source IN ('AI','TEACHER'))`.
- `CHECK (status IN ('SUGGESTED','CONFIRMED','PRACTICING','RECHECKING','IMPROVING','CLOSED'))`.
- `CHECK (skill <> 'SPEAKING' OR (source = 'TEACHER' AND confirmed_by IS NOT NULL AND status <> 'SUGGESTED'))`.
- `CHECK (status = 'SUGGESTED' OR confirmed_by IS NOT NULL)`.
- `CHECK (revision_no > 0)`.

### learner_need_events

Lịch sử thay đổi nhu cầu và bằng chứng đúng học viên.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính |
| need_id | `BIGINT NOT NULL` | Nhu cầu |
| student_id | `BIGINT NOT NULL` | Học viên |
| skill | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Kỹ năng |
| actor_id | `BIGINT NOT NULL` | Người ghi/xác nhận |
| event_type | `VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | AI_SUGGESTION/TEACHER_NOTE/STATUS_CHANGE |
| submission_id | `BIGINT NULL` | Bài nguồn |
| teacher_assessment_id | `BIGINT NULL` | Đánh giá nguồn |
| before_json | `JSON NULL` | Trạng thái trước |
| after_json | `JSON NOT NULL` | Trạng thái/âm/ghi chú sau |
| evidence_json | `JSON NULL` | Mốc audio, quote/câu/hoạt động và thông tin nguồn |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `INDEX (need_id, created_at, id)`.
- `FOREIGN KEY (need_id, student_id, skill) → learner_needs (id, student_id, skill)`.
- `FOREIGN KEY (actor_id) → users (id)`.
- `FOREIGN KEY (submission_id, student_id) → submissions (id, student_id)`.
- `FOREIGN KEY (teacher_assessment_id, student_id) → teacher_assessments (id, student_id)`.
- `CHECK (event_type IN ('AI_SUGGESTION','TEACHER_NOTE','STATUS_CHANGE'))`.
- `CHECK (skill <> 'SPEAKING' OR event_type <> 'AI_SUGGESTION')`.

### remedial_recommendations

Gợi ý có lý do và liên kết bài được giao; tái sử dụng Resource/Assignment.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính |
| student_id | `BIGINT NOT NULL` | Học viên |
| skill | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Kỹ năng |
| need_id | `BIGINT NOT NULL` | Nhu cầu |
| resource_version_id | `BIGINT NOT NULL` | Bài bổ trợ đề xuất |
| profile_history_id | `BIGINT NULL` | Profile lúc đề xuất |
| reason_text | `TEXT NOT NULL` | Lý do chọn bài |
| evidence_json | `JSON NULL` | Nguồn và điều kiện gợi ý |
| status | `VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PROPOSED'` | PROPOSED/ASSIGNED/COMPLETED/RECHECK_REQUIRED/CLOSED |
| confirmed_by | `BIGINT NULL` | Giảng viên duyệt |
| assignment_id | `BIGINT NULL` | Lần giao đã tạo |
| recheck_assignment_id | `BIGINT NULL` | Bài mới kiểm tra vận dụng |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `INDEX (student_id, status)`.
- `FOREIGN KEY (need_id, student_id, skill) → learner_needs (id, student_id, skill)`.
- `FOREIGN KEY (resource_version_id, skill) → resource_versions (id, skill)`.
- `FOREIGN KEY (profile_history_id, student_id) → profile_history (id, student_id)`.
- `FOREIGN KEY (confirmed_by) → users (id)`.
- `FOREIGN KEY (assignment_id, student_id) → assignment_recipients (assignment_id, student_id)`.
- `FOREIGN KEY (recheck_assignment_id, student_id) → assignment_recipients (assignment_id, student_id)`.
- `CHECK (status IN ('PROPOSED','ASSIGNED','COMPLETED','RECHECK_REQUIRED','CLOSED'))`.
- `FOREIGN KEY (assignment_id, resource_version_id) → assignments (id, resource_version_id)`.

## V6

### gamification_rule_versions

Cấu hình thưởng/cấp độ/ngày học theo phiên bản; số 10/15/30 còn là đề xuất.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính |
| rule_code | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Mã cấu hình |
| version_no | `INT NOT NULL` | Phiên bản |
| xp_amount | `INT NOT NULL` | XP cho loại hoạt động |
| conditions_json | `JSON NOT NULL` | Điều kiện hoàn thành, timezone, ngưỡng cấp nếu áp dụng |
| is_enabled | `BOOLEAN NOT NULL DEFAULT FALSE` | Bật sau khi chốt chính sách |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | UTC |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (rule_code, version_no)`.
- `CHECK (version_no > 0)`.
- `CHECK (xp_amount >= 0)`.
- `CHECK (is_enabled IN (0,1))`.

### reward_events

Sổ XP; một Assignment chỉ thưởng một lần cho mỗi học viên.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính |
| student_id | `BIGINT NOT NULL` | Học viên |
| assignment_id | `BIGINT NOT NULL` | Bài hoàn thành |
| submission_id | `BIGINT NOT NULL` | Bài nộp làm căn cứ |
| rule_version_id | `BIGINT NOT NULL` | Cấu hình áp dụng |
| xp_amount | `INT NOT NULL` | XP đã xác nhận theo cấu hình |
| confirmed_by | `BIGINT NULL` | Giảng viên nếu hoạt động cần chốt |
| basis_json | `JSON NOT NULL` | Điều kiện hoàn thành và cấu hình snapshot |
| awarded_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Ngày giờ server xác nhận |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (student_id, assignment_id)`.
- `INDEX (student_id, awarded_at)`.
- `FOREIGN KEY (assignment_id, student_id) → assignment_recipients (assignment_id, student_id)`.
- `FOREIGN KEY (submission_id, assignment_id, student_id) → submissions (id, assignment_id, student_id)`.
- `FOREIGN KEY (rule_version_id) → gamification_rule_versions (id)`.
- `FOREIGN KEY (confirmed_by) → users (id)`.
- `CHECK (xp_amount >= 0)`.

### daily_learning_activities

Một dòng cho một ngày học hợp lệ; tách khỏi XP để không phụ thuộc số điểm thưởng.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| student_id | `BIGINT NOT NULL` | Học viên |
| learning_date | `DATE NOT NULL` | Ngày theo timezone chính sách, server xác nhận |
| timezone | `VARCHAR(64) NOT NULL` | Ví dụ Asia/Ho_Chi_Minh |
| first_submission_id | `BIGINT NOT NULL` | Hoạt động hợp lệ đầu ngày |
| rule_version_id | `BIGINT NOT NULL` | Chính sách ngày học |
| confirmed_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Thời điểm xác nhận |

Khóa, index và ràng buộc:

- `PRIMARY KEY (student_id, learning_date)`.
- `FOREIGN KEY (student_id) → users (id)`.
- `FOREIGN KEY (first_submission_id, student_id) → submissions (id, student_id)`.
- `FOREIGN KEY (rule_version_id) → gamification_rule_versions (id)`.

### badge_definitions

Định nghĩa huy hiệu có phiên bản, mã huy hiệu giữ ổn định qua các lần sửa điều kiện.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| badge_code | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Mã huy hiệu |
| version_no | `INT NOT NULL` | Phiên bản |
| display_name | `VARCHAR(150) NOT NULL` | Tên |
| conditions_json | `JSON NOT NULL` | Điều kiện xét và thông tin hiển thị |
| is_enabled | `BOOLEAN NOT NULL DEFAULT FALSE` | Bật khi chốt chính sách |

Khóa, index và ràng buộc:

- `PRIMARY KEY (badge_code, version_no)`.
- `CHECK (version_no > 0)`.
- `CHECK (is_enabled IN (0,1))`.

### earned_badges

Mỗi học viên chỉ nhận một lần theo mã huy hiệu, kể cả khi định nghĩa đổi phiên bản.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| student_id | `BIGINT NOT NULL` | Học viên |
| badge_code | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Huy hiệu |
| definition_version | `INT NOT NULL` | Phiên bản đã dùng |
| evidence_json | `JSON NOT NULL` | Nguồn bài nộp/ngày học và điều kiện đã đạt |
| earned_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Lúc trao |

Khóa, index và ràng buộc:

- `PRIMARY KEY (student_id, badge_code)`.
- `FOREIGN KEY (student_id) → users (id)`.
- `FOREIGN KEY (badge_code, definition_version) → badge_definitions (badge_code, version_no)`.

### experiment_runs

Lưu cấu hình/bằng chứng phép đo; kết quả chỉ điền khi đã chạy thực tế.

| Cột | Kiểu và null/default | Ý nghĩa |
|---|---|---|
| id | `BIGINT NOT NULL AUTO_INCREMENT` | Khóa chính |
| created_by | `BIGINT NOT NULL` | Người thực nghiệm |
| run_code | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Mã run để đối chiếu CSV/log |
| experiment_type | `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | LABEL_REVIEW/GRADING/NETWORK/... |
| status | `VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PLANNED'` | PLANNED/RUNNING/COMPLETED/FAILED |
| config_json | `JSON NOT NULL` | Baseline/proposed, dữ liệu, model/prompt/rubric/phiên bản phương pháp |
| results_json | `JSON NULL` | Chỉ số đo thực tế |
| result_asset_id | `BIGINT NULL` | CSV/log đã lưu |
| created_at | `DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | UTC |
| completed_at | `DATETIME(6) NULL` | Kết thúc |

Khóa, index và ràng buộc:

- `PRIMARY KEY (id)`.
- `UNIQUE (run_code)`.
- `FOREIGN KEY (created_by) → users (id)`.
- `FOREIGN KEY (result_asset_id) → assets (id)`.
- `CHECK (status IN ('PLANNED','RUNNING','COMPLETED','FAILED'))`.
