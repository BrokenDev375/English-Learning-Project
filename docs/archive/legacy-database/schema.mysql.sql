-- IELTS Learning: reference schema design, 2026-10-12.
-- Run ONLY in an empty scratch database. This is NOT a Flyway migration.
-- Baseline is the current V1; following groups are proposed V2..V6.
-- No DROP, TRUNCATE or FOREIGN_KEY_CHECKS=0. No educational seed data here.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+00:00';

-- V1 baseline (unchanged content)
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('TEACHER', 'STUDENT'))
) ENGINE=InnoDB;

CREATE TABLE classrooms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    teacher_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NOT NULL DEFAULT '',
    join_code CHAR(8) NOT NULL,
    create_request_id CHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_classrooms_join_code UNIQUE (join_code),
    CONSTRAINT uq_classrooms_create_request UNIQUE (teacher_id, create_request_id),
    CONSTRAINT fk_classrooms_teacher FOREIGN KEY (teacher_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE class_members (
    classroom_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    joined_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (classroom_id, student_id),
    CONSTRAINT fk_members_classroom FOREIGN KEY (classroom_id) REFERENCES classrooms (id),
    CONSTRAINT fk_members_student FOREIGN KEY (student_id) REFERENCES users (id),
    INDEX ix_members_student (student_id)
) ENGINE=InnoDB;

-- V2 DESIGN GROUP

CREATE TABLE assets (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính do server cấp',
    owner_id BIGINT NOT NULL COMMENT 'Người tải tệp',
    storage_key VARCHAR(255) NOT NULL COMMENT 'Đường dẫn nội bộ bất biến, không trả trực tiếp cho client',
    original_name VARCHAR(255) NOT NULL COMMENT 'Tên tệp hiển thị',
    mime_type VARCHAR(100) NOT NULL COMMENT 'Loại tệp đã kiểm tra',
    size_bytes BIGINT NOT NULL COMMENT 'Kích thước byte',
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Checksum nội dung',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'READY' COMMENT 'READY/RETIRED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Thời điểm tạo UTC',
    PRIMARY KEY (id),
    CONSTRAINT uq_assets_storage UNIQUE (storage_key),
    CONSTRAINT fk_assets_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT ck_assets_size CHECK (size_bytes >= 0),
    CONSTRAINT ck_assets_status CHECK (status IN ('READY','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Metadata tệp; file nằm trong kho tệp ngoài MySQL.';

CREATE TABLE learning_labels (
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Mã nhãn theo plan',
    skill VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'READING/LISTENING/SPEAKING/WRITING',
    display_name VARCHAR(150) NOT NULL COMMENT 'Tên tiếng Việt',
    label_kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'TARGET hoặc REMEDIAL',
    description TEXT NOT NULL COMMENT 'Điều kiện áp dụng nhãn',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Nhãn được dùng cho bài mới',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Thời điểm tạo UTC',
    PRIMARY KEY (code),
    CONSTRAINT uq_learning_labels_skill UNIQUE (code, skill),
    CONSTRAINT ck_learning_labels_skill CHECK (skill IN ('READING','LISTENING','SPEAKING','WRITING')),
    CONSTRAINT ck_learning_labels_kind CHECK (label_kind IN ('TARGET','REMEDIAL')),
    CONSTRAINT ck_learning_labels_active CHECK (is_active IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Danh mục mục tiêu/nhóm bổ trợ đã thống nhất; trở ngại tự nhận xét lưu riêng.';

CREATE TABLE resources (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính do server cấp',
    owner_id BIGINT NOT NULL COMMENT 'Giảng viên sở hữu',
    skill VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'READING/LISTENING/SPEAKING/WRITING',
    archived_at DATETIME(6) NULL COMMENT 'Lưu trữ bài khỏi ngân hàng đang dùng',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Thời điểm tạo UTC',
    PRIMARY KEY (id),
    CONSTRAINT uq_resources_skill UNIQUE (id, skill),
    INDEX ix_resources_owner (owner_id, skill, archived_at),
    CONSTRAINT fk_resources_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT ck_resources_skill CHECK (skill IN ('READING','LISTENING','SPEAKING','WRITING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Danh tính bài trong ngân hàng; nội dung nằm trong từng phiên bản.';

CREATE TABLE resource_versions (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính do server cấp',
    resource_id BIGINT NOT NULL COMMENT 'Bài gốc',
    skill VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'READING/LISTENING/SPEAKING/WRITING',
    version_no INT NOT NULL COMMENT 'Số phiên bản từ 1',
    title VARCHAR(200) NOT NULL COMMENT 'Tên bài theo phiên bản',
    resource_kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'MAIN/REMEDIAL',
    activity_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'MCQ/DICTATION/WRITING_ESSAY/SPEAKING_RECORDING/OUTLINE/PRONUNCIATION/GUIDED',
    topic VARCHAR(150) NULL COMMENT 'Chủ đề',
    level_framework VARCHAR(24) NULL COMMENT 'CEFR/IELTS_PRACTICE/RAW',
    level_label VARCHAR(32) NULL COMMENT 'Mức luyện; không tự đổi sang band',
    support_level VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'INDEPENDENT' COMMENT 'MODEL/GUIDED/INDEPENDENT/TRANSFER',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
    content_schema_version SMALLINT NOT NULL DEFAULT 1 COMMENT 'Phiên bản định dạng content_json',
    content_json JSON NOT NULL COMMENT 'Nội dung khởi tạo được phép cấp: passage/paragraph, đề Writing/Speaking, bước tương tác',
    teaching_json JSON NULL COMMENT 'Hướng dẫn, phản hồi và nội dung mở có điều kiện; DTO học viên không trả nguyên khối này',
    source_json JSON NULL COMMENT 'URL/trang/tác giả, quyền sử dụng và trạng thái kiểm tra nguồn',
    rubric_version_id BIGINT NULL COMMENT 'Rubric đúng nhiệm vụ; thêm FK ở V4',
    reviewed_by BIGINT NULL COMMENT 'Giảng viên duyệt',
    reviewed_at DATETIME(6) NULL COMMENT 'Lúc duyệt',
    published_at DATETIME(6) NULL COMMENT 'Lúc xuất bản',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Thời điểm tạo UTC',
    PRIMARY KEY (id),
    CONSTRAINT uq_resource_versions_number UNIQUE (resource_id, version_no),
    CONSTRAINT uq_resource_versions_skill UNIQUE (id, skill),
    INDEX ix_resource_versions_find (skill, status, topic),
    CONSTRAINT fk_resource_versions_resource_skill FOREIGN KEY (resource_id, skill) REFERENCES resources (id, skill),
    CONSTRAINT fk_resource_versions_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id),
    CONSTRAINT ck_resource_versions_number CHECK (version_no > 0 AND content_schema_version > 0),
    CONSTRAINT ck_resource_versions_kind CHECK (resource_kind IN ('MAIN','REMEDIAL')),
    CONSTRAINT ck_resource_versions_activity CHECK (activity_type IN ('MCQ','DICTATION','WRITING_ESSAY','SPEAKING_RECORDING','OUTLINE','PRONUNCIATION','GUIDED')),
    CONSTRAINT ck_resource_versions_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    CONSTRAINT ck_resource_versions_support CHECK (support_level IN ('MODEL','GUIDED','INDEPENDENT','TRANSFER')),
    CONSTRAINT ck_resource_versions_content CHECK (JSON_TYPE(content_json) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Snapshot nội dung đã xuất bản; sửa bài đã xuất bản bằng phiên bản mới.';

CREATE TABLE resource_assets (
    resource_version_id BIGINT NOT NULL COMMENT 'Phiên bản',
    asset_id BIGINT NOT NULL COMMENT 'Tệp',
    asset_role VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'VISUAL/AUDIO/PDF/MODEL_AUDIO/...',
    sort_order INT NOT NULL DEFAULT 0 COMMENT 'Thứ tự hiển thị',
    PRIMARY KEY (resource_version_id, asset_id, asset_role),
    CONSTRAINT fk_resource_assets_version FOREIGN KEY (resource_version_id) REFERENCES resource_versions (id),
    CONSTRAINT fk_resource_assets_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT ck_resource_assets_sort CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Các tệp đính kèm của đúng phiên bản bài.';

CREATE TABLE resource_labels (
    resource_version_id BIGINT NOT NULL COMMENT 'Phiên bản',
    skill VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'READING/LISTENING/SPEAKING/WRITING',
    label_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Nhãn đã duyệt',
    PRIMARY KEY (resource_version_id, label_code),
    INDEX ix_resource_labels_lookup (label_code, resource_version_id),
    CONSTRAINT fk_resource_labels_version_skill FOREIGN KEY (resource_version_id, skill) REFERENCES resource_versions (id, skill),
    CONSTRAINT fk_resource_labels_label_skill FOREIGN KEY (label_code, skill) REFERENCES learning_labels (code, skill)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Nhãn lọc bài/gợi ý bổ trợ; không suy ra điểm yếu chỉ từ nhãn.';

CREATE TABLE questions (
    resource_version_id BIGINT NOT NULL COMMENT 'Phiên bản bài',
    question_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Ánh xạ questionId',
    skill VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'READING/LISTENING/SPEAKING/WRITING',
    sort_order INT NOT NULL COMMENT 'Thứ tự câu',
    question_text TEXT NOT NULL COMMENT 'Nội dung câu',
    question_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SINGLE_CHOICE' COMMENT 'Phạm vi hiện tại: chọn một đáp án',
    correct_option_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Ánh xạ correctOptionId; null khi đang soạn',
    explanation MEDIUMTEXT NULL COMMENT 'Lời giải mở trong review',
    evidence_json JSON NULL COMMENT 'paragraphId/câu hoặc segmentId/mốc nghe; service kiểm tra tham chiếu',
    primary_label_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Nhãn chính đã duyệt',
    secondary_labels_json JSON NULL COMMENT 'Nhãn phụ đã duyệt',
    label_status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'UNDETERMINED' COMMENT 'UNDETERMINED/TEACHER_REVIEWED',
    PRIMARY KEY (resource_version_id, question_key),
    CONSTRAINT uq_questions_order UNIQUE (resource_version_id, sort_order),
    CONSTRAINT fk_questions_version_skill FOREIGN KEY (resource_version_id, skill) REFERENCES resource_versions (id, skill),
    CONSTRAINT fk_questions_label_skill FOREIGN KEY (primary_label_code, skill) REFERENCES learning_labels (code, skill),
    CONSTRAINT ck_questions_skill CHECK (skill IN ('READING','LISTENING')),
    CONSTRAINT ck_questions_type CHECK (question_type = 'SINGLE_CHOICE'),
    CONSTRAINT ck_questions_order CHECK (sort_order > 0),
    CONSTRAINT ck_questions_label_status CHECK (label_status IN ('UNDETERMINED','TEACHER_REVIEWED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Câu hỏi trắc nghiệm; q1 là khóa trong một phiên bản, không phải ID toàn hệ thống.';

CREATE TABLE question_options (
    resource_version_id BIGINT NOT NULL COMMENT 'Phiên bản',
    question_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Câu',
    option_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Ánh xạ optionId',
    option_text TEXT NOT NULL COMMENT 'Nội dung lựa chọn',
    sort_order INT NOT NULL COMMENT 'Thứ tự',
    PRIMARY KEY (resource_version_id, question_key, option_key),
    CONSTRAINT uq_question_options_order UNIQUE (resource_version_id, question_key, sort_order),
    CONSTRAINT fk_question_options_question FOREIGN KEY (resource_version_id, question_key) REFERENCES questions (resource_version_id, question_key),
    CONSTRAINT ck_question_options_order CHECK (sort_order > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lựa chọn gắn đúng câu; không dùng vị trí A/B/C làm khóa thay đổi theo giao diện.';

CREATE TABLE listening_media (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính do server cấp',
    owner_id BIGINT NOT NULL COMMENT 'Người quản lý nguồn',
    source_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'YOUTUBE/AUDIO_ASSET',
    video_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'ID YouTube; không phải file upload',
    asset_id BIGINT NULL COMMENT 'File audio riêng',
    text_in_video VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'UNKNOWN' COMMENT 'BURNED_IN/NONE/UNKNOWN',
    source_json JSON NULL COMMENT 'URL/PDF đi kèm và trạng thái kiểm tra',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Thời điểm tạo UTC',
    PRIMARY KEY (id),
    CONSTRAINT fk_listening_media_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_listening_media_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT ck_listening_media_source CHECK ((source_type = 'YOUTUBE' AND video_id IS NOT NULL AND asset_id IS NULL) OR (source_type = 'AUDIO_ASSET' AND asset_id IS NOT NULL AND video_id IS NULL)),
    CONSTRAINT ck_listening_media_text CHECK (text_in_video IN ('BURNED_IN','NONE','UNKNOWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Một nguồn nghe có thể dùng trong nhiều đoạn/bài; nguồn đã dùng được giữ bất biến.';

CREATE TABLE listening_transcript_versions (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính do server cấp',
    media_id BIGINT NOT NULL COMMENT 'Nguồn nghe',
    start_seconds DECIMAL(10,3) NOT NULL COMMENT 'Mốc tuyệt đối đầu đoạn',
    end_seconds DECIMAL(10,3) NOT NULL COMMENT 'Mốc tuyệt đối cuối đoạn',
    revision_no INT NOT NULL COMMENT 'Lần sửa transcript của nguồn/khoảng nghe',
    transcript_text LONGTEXT NOT NULL COMMENT 'Lời tiếng Anh đã đối chiếu',
    comparison_rules_json JSON NULL COMMENT 'Quy ước số/tên/từ đệm/chữ hoa/dấu câu/biến thể',
    reviewed_by BIGINT NULL COMMENT 'Người duyệt',
    reviewed_at DATETIME(6) NULL COMMENT 'Lúc duyệt',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Thời điểm tạo UTC',
    PRIMARY KEY (id),
    CONSTRAINT uq_listening_transcript_versions_revision UNIQUE (media_id, start_seconds, end_seconds, revision_no),
    CONSTRAINT uq_listening_transcript_versions_binding UNIQUE (id, media_id, start_seconds, end_seconds),
    CONSTRAINT fk_listening_transcript_versions_media FOREIGN KEY (media_id) REFERENCES listening_media (id),
    CONSTRAINT fk_listening_transcript_versions_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id),
    CONSTRAINT ck_listening_transcript_versions_interval CHECK (start_seconds >= 0 AND end_seconds > start_seconds),
    CONSTRAINT ck_listening_transcript_versions_revision CHECK (revision_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Transcript bất biến cho đúng nguồn và khoảng nghe; sửa tạo revision mới.';

CREATE TABLE listening_segments (
    resource_version_id BIGINT NOT NULL COMMENT 'Phiên bản bài',
    segment_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Ánh xạ segmentId',
    skill VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'LISTENING' COMMENT 'Khóa ràng buộc kỹ năng',
    media_id BIGINT NOT NULL COMMENT 'Nguồn',
    title VARCHAR(200) NOT NULL COMMENT 'Tên đoạn',
    sort_order INT NOT NULL COMMENT 'Thứ tự',
    start_seconds DECIMAL(10,3) NOT NULL COMMENT 'Mốc đầu tuyệt đối',
    end_seconds DECIMAL(10,3) NOT NULL COMMENT 'Mốc cuối tuyệt đối',
    transcript_version_id BIGINT NULL COMMENT 'Transcript khớp nguồn/mốc; null khi soạn',
    PRIMARY KEY (resource_version_id, segment_key),
    CONSTRAINT uq_listening_segments_order UNIQUE (resource_version_id, sort_order),
    CONSTRAINT uq_listening_segments_transcript UNIQUE (resource_version_id, segment_key, transcript_version_id),
    CONSTRAINT fk_listening_segments_version_skill FOREIGN KEY (resource_version_id, skill) REFERENCES resource_versions (id, skill),
    CONSTRAINT fk_listening_segments_media FOREIGN KEY (media_id) REFERENCES listening_media (id),
    CONSTRAINT fk_listening_segments_transcript_binding FOREIGN KEY (transcript_version_id, media_id, start_seconds, end_seconds) REFERENCES listening_transcript_versions (id, media_id, start_seconds, end_seconds),
    CONSTRAINT ck_listening_segments_skill CHECK (skill = 'LISTENING'),
    CONSTRAINT ck_listening_segments_interval CHECK (start_seconds >= 0 AND end_seconds > start_seconds),
    CONSTRAINT ck_listening_segments_order CHECK (sort_order > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Đoạn nghe thuộc phiên bản bài, giữ transcript phù hợp đúng nguồn/mốc.';

CREATE TABLE assignments (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính do server cấp',
    classroom_id BIGINT NOT NULL COMMENT 'Lớp',
    created_by BIGINT NOT NULL COMMENT 'Giảng viên tạo',
    resource_version_id BIGINT NOT NULL COMMENT 'Phiên bản cố định',
    purpose VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'TEST_INITIAL/TEST_PERIODIC/PRACTICE/REMEDIAL/RECHECK',
    recipient_mode VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CLASS_SNAPSHOT/SELECTED',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/CLOSED/ARCHIVED',
    opens_at DATETIME(6) NULL COMMENT 'Thời điểm mở',
    due_at DATETIME(6) NULL COMMENT 'Hạn nộp',
    allow_late BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Cho phép nộp muộn',
    max_attempts INT NOT NULL DEFAULT 1 COMMENT 'Số lần làm tối đa',
    feedback_policy VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'AFTER_SUBMISSION' COMMENT 'AFTER_SUBMISSION/AFTER_DUE/TEACHER_RELEASE',
    feedback_released_at DATETIME(6) NULL COMMENT 'Giảng viên mở phản hồi',
    settings_json JSON NULL COMMENT 'Các segment được giao, hỗ trợ được phép, điều kiện hoàn thành và cấu hình thưởng snapshot',
    create_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'UUID giữ nguyên khi gửi lại cùng yêu cầu',
    request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 của payload chuẩn hóa; server tính để nhận biết requestId bị dùng lại khác nội dung',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Thời điểm tạo UTC',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Thời điểm cập nhật UTC',
    PRIMARY KEY (id),
    CONSTRAINT uq_assignments_version UNIQUE (id, resource_version_id),
    CONSTRAINT uq_assignments_request UNIQUE (created_by, create_request_id),
    INDEX ix_assignments_class (classroom_id, status, due_at),
    CONSTRAINT fk_assignments_class FOREIGN KEY (classroom_id) REFERENCES classrooms (id),
    CONSTRAINT fk_assignments_creator FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_assignments_version FOREIGN KEY (resource_version_id) REFERENCES resource_versions (id),
    CONSTRAINT ck_assignments_purpose CHECK (purpose IN ('TEST_INITIAL','TEST_PERIODIC','PRACTICE','REMEDIAL','RECHECK')),
    CONSTRAINT ck_assignments_mode CHECK (recipient_mode IN ('CLASS_SNAPSHOT','SELECTED')),
    CONSTRAINT ck_assignments_status CHECK (status IN ('DRAFT','PUBLISHED','CLOSED','ARCHIVED')),
    CONSTRAINT ck_assignments_dates CHECK (opens_at IS NULL OR due_at IS NULL OR due_at >= opens_at),
    CONSTRAINT ck_assignments_attempts CHECK (max_attempts > 0),
    CONSTRAINT ck_assignments_late CHECK (allow_late IN (0,1)),
    CONSTRAINT ck_assignments_feedback CHECK (feedback_policy IN ('AFTER_SUBMISSION','AFTER_DUE','TEACHER_RELEASE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Một lần giao một phiên bản bài cho một lớp hoặc tập thành viên trong lớp.';

CREATE TABLE assignment_recipients (
    assignment_id BIGINT NOT NULL COMMENT 'Bài được giao',
    student_id BIGINT NOT NULL COMMENT 'Người nhận',
    revoked_at DATETIME(6) NULL COMMENT 'Thu hồi quyền làm mới',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Thời điểm tạo UTC',
    PRIMARY KEY (assignment_id, student_id),
    INDEX ix_assignment_recipients_student (student_id, revoked_at, assignment_id),
    CONSTRAINT fk_assignment_recipients_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id),
    CONSTRAINT fk_assignment_recipients_student FOREIGN KEY (student_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Snapshot người nhận; không FK vào class_members để rời lớp không làm mất lịch sử.';

ALTER TABLE questions ADD CONSTRAINT fk_questions_correct_option FOREIGN KEY (resource_version_id, question_key, correct_option_key) REFERENCES question_options (resource_version_id, question_key, option_key);

-- V3 DESIGN GROUP

CREATE TABLE learning_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính do server cấp',
    assignment_id BIGINT NOT NULL COMMENT 'Bài được giao',
    student_id BIGINT NOT NULL COMMENT 'Học viên',
    resource_version_id BIGINT NOT NULL COMMENT 'Phiên bản được giao',
    attempt_no INT NOT NULL COMMENT 'Số lần làm từ 1',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SUBMITTED/ABANDONED',
    create_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'UUID giữ nguyên khi gửi lại cùng yêu cầu',
    request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 của payload chuẩn hóa; server tính để nhận biết requestId bị dùng lại khác nội dung',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Thời điểm tạo UTC',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Thời điểm cập nhật UTC',
    PRIMARY KEY (id),
    CONSTRAINT uq_learning_attempts_number UNIQUE (assignment_id, student_id, attempt_no),
    CONSTRAINT uq_learning_attempts_request UNIQUE (student_id, create_request_id),
    CONSTRAINT uq_learning_attempts_version UNIQUE (id, resource_version_id),
    CONSTRAINT uq_learning_attempts_owner UNIQUE (id, assignment_id, student_id, resource_version_id),
    INDEX ix_learning_attempts_student (student_id, status, updated_at),
    CONSTRAINT fk_learning_attempts_recipient FOREIGN KEY (assignment_id, student_id) REFERENCES assignment_recipients (assignment_id, student_id),
    CONSTRAINT fk_learning_attempts_assignment_version FOREIGN KEY (assignment_id, resource_version_id) REFERENCES assignments (id, resource_version_id),
    CONSTRAINT ck_learning_attempts_number CHECK (attempt_no > 0),
    CONSTRAINT ck_learning_attempts_status CHECK (status IN ('DRAFT','SUBMITTED','ABANDONED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Một lần làm của đúng người nhận và đúng phiên bản bài.';

CREATE TABLE attempt_drafts (
    attempt_id BIGINT NOT NULL COMMENT 'Lần làm',
    body_text LONGTEXT NULL COMMENT 'Bài viết hoặc ý tiếng Việt đang soạn',
    preparation_json JSON NULL COMMENT 'Outline/hoạt động bổ trợ đang làm',
    cursor_json JSON NULL COMMENT 'Vị trí paragraph/câu',
    support_used_json JSON NULL COMMENT 'Hỗ trợ đã dùng; dữ liệu quan sát, không tự xác nhận năng lực',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Thời điểm cập nhật UTC',
    PRIMARY KEY (attempt_id),
    CONSTRAINT fk_attempt_drafts_attempt FOREIGN KEY (attempt_id) REFERENCES learning_attempts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Snapshot nháp văn bản/outline/vị trí; lựa chọn trắc nghiệm ở attempt_answers.';

CREATE TABLE attempt_answers (
    attempt_id BIGINT NOT NULL COMMENT 'Lần làm',
    resource_version_id BIGINT NOT NULL COMMENT 'Phiên bản',
    question_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Câu',
    selected_option_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Lựa chọn; null chưa trả lời',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Thời điểm cập nhật UTC',
    PRIMARY KEY (attempt_id, question_key),
    CONSTRAINT fk_attempt_answers_attempt_version FOREIGN KEY (attempt_id, resource_version_id) REFERENCES learning_attempts (id, resource_version_id),
    CONSTRAINT fk_attempt_answers_question FOREIGN KEY (resource_version_id, question_key) REFERENCES questions (resource_version_id, question_key),
    CONSTRAINT fk_attempt_answers_option FOREIGN KEY (resource_version_id, question_key, selected_option_key) REFERENCES question_options (resource_version_id, question_key, option_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lựa chọn nháp được kiểm tra bằng FK đúng câu, phiên bản và option.';

CREATE TABLE attempt_listening_progress (
    attempt_id BIGINT NOT NULL COMMENT 'Lần làm',
    resource_version_id BIGINT NOT NULL COMMENT 'Phiên bản',
    segment_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Đoạn',
    draft_text LONGTEXT NOT NULL COMMENT 'Bản chép đang làm',
    last_playback_seconds DECIMAL(10,3) NOT NULL COMMENT 'Vị trí tuyệt đối; service kiểm tra trong khoảng',
    support_used_json JSON NULL COMMENT 'Tốc độ/nghe lại/chữ trong hình',
    reference_opened_at DATETIME(6) NULL COMMENT 'Server ghi lần đầu cấp transcript',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Thời điểm cập nhật UTC',
    PRIMARY KEY (attempt_id, segment_key),
    CONSTRAINT fk_attempt_listening_progress_attempt_version FOREIGN KEY (attempt_id, resource_version_id) REFERENCES learning_attempts (id, resource_version_id),
    CONSTRAINT fk_attempt_listening_progress_segment FOREIGN KEY (resource_version_id, segment_key) REFERENCES listening_segments (resource_version_id, segment_key),
    CONSTRAINT ck_attempt_listening_progress_position CHECK (last_playback_seconds >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Nháp chép lời và vị trí theo từng đoạn; trạng thái nộp suy từ bảng nộp đoạn.';

CREATE TABLE listening_segment_submissions (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính do server cấp',
    attempt_id BIGINT NOT NULL COMMENT 'Lần làm',
    resource_version_id BIGINT NOT NULL COMMENT 'Phiên bản',
    segment_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Đoạn',
    transcript_version_id BIGINT NOT NULL COMMENT 'Bản tham chiếu cố định',
    submitted_text LONGTEXT NOT NULL COMMENT 'Bản chép đầu',
    support_used_json JSON NULL COMMENT 'Điều kiện nghe lúc nộp',
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'UUID giữ nguyên khi gửi lại cùng yêu cầu',
    request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 của payload chuẩn hóa; server tính để nhận biết requestId bị dùng lại khác nội dung',
    submitted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Lúc server xác nhận',
    PRIMARY KEY (id),
    CONSTRAINT uq_listening_segment_submissions_segment UNIQUE (attempt_id, segment_key),
    CONSTRAINT uq_listening_segment_submissions_request UNIQUE (attempt_id, request_id),
    CONSTRAINT fk_listening_segment_submissions_attempt_version FOREIGN KEY (attempt_id, resource_version_id) REFERENCES learning_attempts (id, resource_version_id),
    CONSTRAINT fk_listening_segment_submissions_segment_transcript FOREIGN KEY (resource_version_id, segment_key, transcript_version_id) REFERENCES listening_segments (resource_version_id, segment_key, transcript_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Bản chép đầu bất biến trước khi mở transcript; cho nộp từng đoạn của cùng Attempt.';

CREATE TABLE submissions (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính do server cấp',
    attempt_id BIGINT NOT NULL COMMENT 'Lần làm',
    assignment_id BIGINT NOT NULL COMMENT 'Bài được giao; lặp để FK bảo vệ người và phiên bản',
    student_id BIGINT NOT NULL COMMENT 'Học viên',
    resource_version_id BIGINT NOT NULL COMMENT 'Phiên bản',
    previous_submission_id BIGINT NULL COMMENT 'Bản nộp trước của cùng người/bài',
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED/TEACHER_REVIEWED/RETURNED',
    outline_version_id BIGINT NULL COMMENT 'Outline Speaking đã dùng; FK được bổ sung ở V4',
    body_text LONGTEXT NULL COMMENT 'Bài Writing hoàn chỉnh; audio ở submission_assets',
    preparation_snapshot_json JSON NULL COMMENT 'Outline/hoạt động bổ trợ đúng bản đã sử dụng',
    support_used_json JSON NULL COMMENT 'Điều kiện hỗ trợ đã sử dụng',
    client_result_json JSON NULL COMMENT 'Kết quả CLIENT_OBJECTIVE; không là điểm xác nhận',
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'UUID giữ nguyên khi gửi lại cùng yêu cầu',
    request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 của payload chuẩn hóa; server tính để nhận biết requestId bị dùng lại khác nội dung',
    submitted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Server xác nhận',
    returned_at DATETIME(6) NULL COMMENT 'Phản hồi được trả',
    PRIMARY KEY (id),
    CONSTRAINT uq_submissions_attempt UNIQUE (attempt_id),
    CONSTRAINT uq_submissions_request UNIQUE (student_id, request_id),
    CONSTRAINT uq_submissions_version UNIQUE (id, resource_version_id),
    CONSTRAINT uq_submissions_owner UNIQUE (id, assignment_id, student_id),
    CONSTRAINT uq_submissions_student UNIQUE (id, student_id),
    INDEX ix_submissions_assignment (assignment_id, status, submitted_at),
    CONSTRAINT fk_submissions_attempt_owner FOREIGN KEY (attempt_id, assignment_id, student_id, resource_version_id) REFERENCES learning_attempts (id, assignment_id, student_id, resource_version_id),
    CONSTRAINT fk_submissions_previous_owner FOREIGN KEY (previous_submission_id, assignment_id, student_id) REFERENCES submissions (id, assignment_id, student_id),
    CONSTRAINT ck_submissions_status CHECK (status IN ('SUBMITTED','TEACHER_REVIEWED','RETURNED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Một bản nộp bất biến cho một Attempt; bản sửa là row mới liên kết previous_submission_id.';

CREATE TABLE submission_answers (
    submission_id BIGINT NOT NULL COMMENT 'Bản nộp',
    resource_version_id BIGINT NOT NULL COMMENT 'Phiên bản',
    question_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Câu',
    selected_option_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Lựa chọn lúc nộp',
    PRIMARY KEY (submission_id, question_key),
    INDEX ix_submission_answers_question (resource_version_id, question_key),
    CONSTRAINT fk_submission_answers_submission_version FOREIGN KEY (submission_id, resource_version_id) REFERENCES submissions (id, resource_version_id),
    CONSTRAINT fk_submission_answers_question FOREIGN KEY (resource_version_id, question_key) REFERENCES questions (resource_version_id, question_key),
    CONSTRAINT fk_submission_answers_option FOREIGN KEY (resource_version_id, question_key, selected_option_key) REFERENCES question_options (resource_version_id, question_key, option_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Snapshot lựa chọn lúc nộp; dùng phân tích câu sai và giữ điểm lần đầu.';

CREATE TABLE submission_assets (
    submission_id BIGINT NOT NULL COMMENT 'Bản nộp',
    asset_id BIGINT NOT NULL COMMENT 'Tệp đã lưu thành công',
    asset_role VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'RECORDING/ATTACHMENT/...',
    sort_order INT NOT NULL DEFAULT 0 COMMENT 'Thứ tự',
    PRIMARY KEY (submission_id, asset_id, asset_role),
    CONSTRAINT fk_submission_assets_submission FOREIGN KEY (submission_id) REFERENCES submissions (id),
    CONSTRAINT fk_submission_assets_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT ck_submission_assets_sort CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Audio/tệp của bản nộp; service kiểm tra người sở hữu tệp.';

CREATE TABLE attempt_review_events (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính do server cấp',
    attempt_id BIGINT NOT NULL COMMENT 'Lần làm',
    resource_version_id BIGINT NOT NULL COMMENT 'Phiên bản',
    question_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Câu được review',
    segment_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Đoạn được review',
    event_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SELF_REPORT/PROBE/FEEDBACK_OPENED/CORRECTION',
    payload_json JSON NOT NULL COMMENT 'Lý do hoặc skipped, bằng chứng chọn, câu sửa; schema theo event_type',
    event_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'UUID giữ nguyên khi gửi lại cùng yêu cầu',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Thời điểm tạo UTC',
    PRIMARY KEY (id),
    CONSTRAINT uq_attempt_review_events_request UNIQUE (attempt_id, event_request_id),
    INDEX ix_attempt_review_events_question (attempt_id, question_key, event_type, id),
    INDEX ix_attempt_review_events_segment (attempt_id, segment_key, event_type, id),
    CONSTRAINT fk_attempt_review_events_attempt_version FOREIGN KEY (attempt_id, resource_version_id) REFERENCES learning_attempts (id, resource_version_id),
    CONSTRAINT fk_attempt_review_events_question FOREIGN KEY (resource_version_id, question_key) REFERENCES questions (resource_version_id, question_key),
    CONSTRAINT fk_attempt_review_events_segment FOREIGN KEY (resource_version_id, segment_key) REFERENCES listening_segments (resource_version_id, segment_key),
    CONSTRAINT ck_attempt_review_events_target CHECK ((question_key IS NOT NULL AND segment_key IS NULL) OR (question_key IS NULL AND segment_key IS NOT NULL)),
    CONSTRAINT ck_attempt_review_events_event CHECK (event_type IN ('SELF_REPORT','PROBE','FEEDBACK_OPENED','CORRECTION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lịch sử tự nhận xét, kiểm tra thêm, mở phản hồi và sửa; append-only.';

-- V4 DESIGN GROUP

CREATE TABLE rubric_versions (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính',
    rubric_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Mã rubric',
    version_no INT NOT NULL COMMENT 'Phiên bản',
    skill VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Kỹ năng',
    task_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'TASK_1/TASK_2/SPEAKING/OUTLINE/...',
    rubric_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'IELTS_BAND/CLASS_POINTS/OUTLINE_CHECKLIST',
    criteria_json JSON NOT NULL COMMENT 'Tiêu chí/descriptors; không tự tạo điểm tham chiếu chưa được công bố',
    source_json JSON NULL COMMENT 'Nguồn rubric, phiên bản tài liệu',
    approved_by BIGINT NULL COMMENT 'Giảng viên duyệt',
    approved_at DATETIME(6) NULL COMMENT 'Duyệt',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC',
    PRIMARY KEY (id),
    CONSTRAINT uq_rubric_versions_version UNIQUE (rubric_code, version_no),
    CONSTRAINT fk_rubric_versions_approver FOREIGN KEY (approved_by) REFERENCES users (id),
    CONSTRAINT ck_rubric_versions_number CHECK (version_no > 0),
    CONSTRAINT ck_rubric_versions_skill CHECK (skill IN ('READING','LISTENING','SPEAKING','WRITING')),
    CONSTRAINT ck_rubric_versions_type CHECK (rubric_type IN ('IELTS_BAND','CLASS_POINTS','OUTLINE_CHECKLIST'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Rubric/checklist bất biến; nguồn và tiêu chí đúng nhiệm vụ.';

CREATE TABLE speaking_outline_versions (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính',
    attempt_id BIGINT NOT NULL COMMENT 'Lần làm Speaking',
    version_no INT NOT NULL COMMENT 'Phiên bản outline',
    vietnamese_ideas LONGTEXT NULL COMMENT 'Ý tiếng Việt tùy chọn',
    english_outline LONGTEXT NOT NULL COMMENT 'Dàn ý/từ khóa tiếng Anh',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC',
    PRIMARY KEY (id),
    CONSTRAINT uq_speaking_outline_versions_version UNIQUE (attempt_id, version_no),
    CONSTRAINT uq_speaking_outline_versions_attempt UNIQUE (id, attempt_id),
    CONSTRAINT fk_speaking_outline_versions_attempt FOREIGN KEY (attempt_id) REFERENCES learning_attempts (id),
    CONSTRAINT ck_speaking_outline_versions_version CHECK (version_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Giữ outline trước/sau AI; bản ghi âm tham chiếu đúng outline đã dùng.';

CREATE TABLE ai_analyses (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính',
    requested_by BIGINT NOT NULL COMMENT 'Tài khoản được phép yêu cầu',
    resource_version_id BIGINT NOT NULL COMMENT 'Ngữ cảnh bài cố định',
    submission_id BIGINT NULL COMMENT 'Bài viết/bài làm được phân tích',
    outline_version_id BIGINT NULL COMMENT 'Outline Speaking',
    outline_attempt_id BIGINT NULL COMMENT 'Ràng buộc outline đúng lần làm',
    audio_asset_id BIGINT NULL COMMENT 'Bản thu nếu công cụ phát âm',
    rubric_version_id BIGINT NULL COMMENT 'Rubric/checklist',
    analysis_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'QUESTION_LABELS/WRITING/OUTLINE/NEEDS_SUMMARY/MATERIAL_DRAFT/PRONUNCIATION',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'PENDING/SUCCEEDED/FAILED',
    provider VARCHAR(80) NULL COMMENT 'Nhà cung cấp',
    model_name VARCHAR(120) NULL COMMENT 'Model/công cụ',
    prompt_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Phiên bản prompt',
    input_snapshot_json JSON NOT NULL COMMENT 'Ngữ cảnh/ID nguồn/phiên bản/rubric/mẫu/config ngưỡng',
    result_json JSON NULL COMMENT 'Kết quả gốc có bằng chứng',
    error_code VARCHAR(64) NULL COMMENT 'Mã lỗi an toàn',
    reviewed_by BIGINT NULL COMMENT 'Giảng viên kiểm tra',
    review_decision VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'ACCEPTED/EDITED/REJECTED',
    reviewed_result_json JSON NULL COMMENT 'Bản đã sửa/duyệt, giữ kết quả gốc',
    reviewed_at DATETIME(6) NULL COMMENT 'Thời gian duyệt',
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Chống gửi trùng',
    request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Hash payload',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC',
    completed_at DATETIME(6) NULL COMMENT 'Kết thúc xử lý',
    PRIMARY KEY (id),
    CONSTRAINT uq_ai_analyses_request UNIQUE (requested_by, request_id),
    INDEX ix_ai_analyses_submission (submission_id, analysis_type, created_at),
    CONSTRAINT fk_ai_analyses_requester FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT fk_ai_analyses_version FOREIGN KEY (resource_version_id) REFERENCES resource_versions (id),
    CONSTRAINT fk_ai_analyses_submission_version FOREIGN KEY (submission_id, resource_version_id) REFERENCES submissions (id, resource_version_id),
    CONSTRAINT fk_ai_analyses_outline FOREIGN KEY (outline_version_id, outline_attempt_id) REFERENCES speaking_outline_versions (id, attempt_id),
    CONSTRAINT fk_ai_analyses_audio FOREIGN KEY (audio_asset_id) REFERENCES assets (id),
    CONSTRAINT fk_ai_analyses_rubric FOREIGN KEY (rubric_version_id) REFERENCES rubric_versions (id),
    CONSTRAINT fk_ai_analyses_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id),
    CONSTRAINT ck_ai_analyses_type CHECK (analysis_type IN ('QUESTION_LABELS','WRITING','OUTLINE','NEEDS_SUMMARY','MATERIAL_DRAFT','PRONUNCIATION')),
    CONSTRAINT ck_ai_analyses_status CHECK (status IN ('PENDING','SUCCEEDED','FAILED')),
    CONSTRAINT ck_ai_analyses_outline_pair CHECK ((outline_version_id IS NULL AND outline_attempt_id IS NULL) OR (outline_version_id IS NOT NULL AND outline_attempt_id IS NOT NULL)),
    CONSTRAINT ck_ai_analyses_review CHECK (review_decision IS NULL OR review_decision IN ('ACCEPTED','EDITED','REJECTED')),
    CONSTRAINT fk_ai_analyses_outline_context FOREIGN KEY (outline_attempt_id, resource_version_id) REFERENCES learning_attempts (id, resource_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Kết quả công cụ/AI nguyên bản và quyết định giảng viên; không tự cập nhật band.';

CREATE TABLE teacher_assessments (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính',
    submission_id BIGINT NOT NULL COMMENT 'Bản nộp cố định',
    student_id BIGINT NOT NULL COMMENT 'Học viên của bản nộp',
    teacher_id BIGINT NOT NULL COMMENT 'Người chấm',
    rubric_version_id BIGINT NOT NULL COMMENT 'Rubric',
    revision_no INT NOT NULL COMMENT 'Lần đánh giá',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/RETURNED',
    score_scale VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'BAND/POINTS/RAW/NONE',
    score_value DECIMAL(7,2) NULL COMMENT 'Điểm nhiệm vụ, không tự là điểm toàn kỹ năng',
    score_max DECIMAL(7,2) NULL COMMENT 'Mẫu số nếu chấm điểm thô',
    criteria_json JSON NOT NULL COMMENT 'Điểm từng tiêu chí và căn cứ',
    annotations_json JSON NULL COMMENT 'Câu lỗi Writing/âm Speaking/mốc audio, quote và phạm vi đã xác nhận',
    feedback_text MEDIUMTEXT NULL COMMENT 'Nhận xét tổng thể',
    ai_analysis_id BIGINT NULL COMMENT 'Gợi ý đã tham khảo',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC',
    returned_at DATETIME(6) NULL COMMENT 'Khi trả học viên',
    PRIMARY KEY (id),
    CONSTRAINT uq_teacher_assessments_revision UNIQUE (submission_id, revision_no),
    CONSTRAINT uq_teacher_assessments_student UNIQUE (id, student_id),
    INDEX ix_teacher_assessments_student (student_id, status, returned_at),
    CONSTRAINT fk_teacher_assessments_submission_student FOREIGN KEY (submission_id, student_id) REFERENCES submissions (id, student_id),
    CONSTRAINT fk_teacher_assessments_teacher FOREIGN KEY (teacher_id) REFERENCES users (id),
    CONSTRAINT fk_teacher_assessments_rubric FOREIGN KEY (rubric_version_id) REFERENCES rubric_versions (id),
    CONSTRAINT fk_teacher_assessments_ai FOREIGN KEY (ai_analysis_id) REFERENCES ai_analyses (id),
    CONSTRAINT ck_teacher_assessments_revision CHECK (revision_no > 0),
    CONSTRAINT ck_teacher_assessments_status CHECK (status IN ('DRAFT','RETURNED')),
    CONSTRAINT ck_teacher_assessments_scale CHECK (score_scale IN ('BAND','POINTS','RAW','NONE')),
    CONSTRAINT ck_teacher_assessments_score CHECK (score_value IS NULL OR score_value >= 0),
    CONSTRAINT ck_teacher_assessments_band CHECK (score_scale <> 'BAND' OR score_value IS NULL OR (score_value <= 9 AND MOD(score_value * 2, 1) = 0)),
    CONSTRAINT ck_teacher_assessments_none CHECK (score_scale <> 'NONE' OR score_value IS NULL),
    CONSTRAINT ck_teacher_assessments_max CHECK (score_max IS NULL OR (score_max > 0 AND (score_value IS NULL OR score_value <= score_max)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Nháp/chốt chấm bài; sửa đánh giá đã trả bằng revision mới, giữ lịch sử.';

ALTER TABLE resource_versions ADD CONSTRAINT fk_resource_versions_rubric FOREIGN KEY (rubric_version_id) REFERENCES rubric_versions (id);

ALTER TABLE submissions ADD CONSTRAINT fk_submissions_outline_attempt FOREIGN KEY (outline_version_id, attempt_id) REFERENCES speaking_outline_versions (id, attempt_id);

-- V5 DESIGN GROUP

CREATE TABLE assessment_cycles (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính',
    classroom_id BIGINT NOT NULL COMMENT 'Lớp',
    created_by BIGINT NOT NULL COMMENT 'Giảng viên',
    title VARCHAR(200) NOT NULL COMMENT 'Tên đợt',
    purpose VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'TEST_INITIAL/TEST_PERIODIC/RECHECK',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC',
    PRIMARY KEY (id),
    CONSTRAINT fk_assessment_cycles_class FOREIGN KEY (classroom_id) REFERENCES classrooms (id),
    CONSTRAINT fk_assessment_cycles_creator FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_assessment_cycles_purpose CHECK (purpose IN ('TEST_INITIAL','TEST_PERIODIC','RECHECK'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Đợt test gồm các bài bốn kỹ năng; báo cáo đợt không đổi theo profile mới.';

CREATE TABLE cycle_assignments (
    cycle_id BIGINT NOT NULL COMMENT 'Đợt test',
    assignment_id BIGINT NOT NULL COMMENT 'Bài giao',
    resource_version_id BIGINT NOT NULL COMMENT 'Phiên bản bài',
    skill VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Kỹ năng',
    PRIMARY KEY (cycle_id, assignment_id),
    CONSTRAINT fk_cycle_assignments_cycle FOREIGN KEY (cycle_id) REFERENCES assessment_cycles (id),
    CONSTRAINT fk_cycle_assignments_assignment_version FOREIGN KEY (assignment_id, resource_version_id) REFERENCES assignments (id, resource_version_id),
    CONSTRAINT fk_cycle_assignments_version_skill FOREIGN KEY (resource_version_id, skill) REFERENCES resource_versions (id, skill)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Một kỹ năng có thể gồm nhiều nhiệm vụ, ví dụ Writing Task 1 và Task 2.';

CREATE TABLE skill_assessments (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính',
    student_id BIGINT NOT NULL COMMENT 'Học viên',
    skill VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Kỹ năng',
    confirmed_by BIGINT NOT NULL COMMENT 'Giảng viên xác nhận',
    cycle_id BIGINT NULL COMMENT 'Đợt test nguồn',
    level_framework VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'IELTS_BAND/CEFR/RAW',
    score_value DECIMAL(7,2) NULL COMMENT 'Điểm phù hợp khung',
    level_label VARCHAR(32) NULL COMMENT 'Ví dụ B1; không quy đổi tự động',
    method_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Quy tắc/phiên bản cách tổng hợp',
    evidence_json JSON NOT NULL COMMENT 'Bài nộp, teacherAssessmentId, phạm vi và điều kiện đánh giá',
    performed_at DATETIME(6) NOT NULL COMMENT 'Thời điểm thực hiện đánh giá',
    confirmed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Lúc chốt',
    PRIMARY KEY (id),
    CONSTRAINT uq_skill_assessments_student UNIQUE (id, student_id),
    CONSTRAINT uq_skill_assessments_skill UNIQUE (id, student_id, skill),
    INDEX ix_skill_assessments_history (student_id, skill, performed_at, id),
    CONSTRAINT fk_skill_assessments_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT fk_skill_assessments_teacher FOREIGN KEY (confirmed_by) REFERENCES users (id),
    CONSTRAINT fk_skill_assessments_cycle FOREIGN KEY (cycle_id) REFERENCES assessment_cycles (id),
    CONSTRAINT ck_skill_assessments_skill CHECK (skill IN ('READING','LISTENING','SPEAKING','WRITING')),
    CONSTRAINT ck_skill_assessments_framework CHECK (level_framework IN ('IELTS_BAND','CEFR','RAW')),
    CONSTRAINT ck_skill_assessments_score CHECK (score_value IS NULL OR score_value >= 0),
    CONSTRAINT ck_skill_assessments_band CHECK (level_framework <> 'IELTS_BAND' OR (score_value IS NOT NULL AND score_value <= 9 AND MOD(score_value * 2, 1) = 0)),
    CONSTRAINT ck_skill_assessments_cefr CHECK (level_framework <> 'CEFR' OR level_label IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Ước lượng toàn kỹ năng do giảng viên xác nhận; lịch sử append-only.';

CREATE TABLE learner_profiles (
    student_id BIGINT NOT NULL COMMENT 'Học viên',
    target_overall DECIMAL(3,1) NULL COMMENT 'Mục tiêu học viên chọn; không là điểm hiện tại',
    learning_goal TEXT NULL COMMENT 'Mục tiêu',
    current_history_id BIGINT NULL COMMENT 'Snapshot profile hiện tại; thêm FK sau profile_history',
    revision_no INT NOT NULL DEFAULT 1 COMMENT 'Tuần tự cập nhật trong transaction',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC',
    PRIMARY KEY (student_id),
    CONSTRAINT fk_learner_profiles_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT ck_learner_profiles_target CHECK (target_overall IS NULL OR (target_overall BETWEEN 0 AND 9 AND MOD(target_overall * 2, 1) = 0)),
    CONSTRAINT ck_learner_profiles_revision CHECK (revision_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Một hồ sơ cho một tài khoản học viên, không nhân theo lớp.';

CREATE TABLE skill_profiles (
    student_id BIGINT NOT NULL COMMENT 'Học viên',
    skill VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Kỹ năng',
    current_assessment_id BIGINT NULL COMMENT 'Đánh giá đã xác nhận đúng người/kỹ năng',
    target_score DECIMAL(3,1) NULL COMMENT 'Mục tiêu IELTS nếu có',
    updated_at DATETIME(6) NULL COMMENT 'Lần cập nhật',
    PRIMARY KEY (student_id, skill),
    CONSTRAINT fk_skill_profiles_profile FOREIGN KEY (student_id) REFERENCES learner_profiles (student_id),
    CONSTRAINT fk_skill_profiles_assessment FOREIGN KEY (current_assessment_id, student_id, skill) REFERENCES skill_assessments (id, student_id, skill),
    CONSTRAINT ck_skill_profiles_skill CHECK (skill IN ('READING','LISTENING','SPEAKING','WRITING')),
    CONSTRAINT ck_skill_profiles_target CHECK (target_score IS NULL OR (target_score BETWEEN 0 AND 9 AND MOD(target_score * 2, 1) = 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Bốn dòng trên mỗi hồ sơ; điểm chưa có giữ con trỏ null.';

CREATE TABLE profile_history (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính',
    student_id BIGINT NOT NULL COMMENT 'Học viên',
    confirmed_by BIGINT NOT NULL COMMENT 'Giảng viên',
    scope VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'PROFILE/CYCLE',
    cycle_id BIGINT NULL COMMENT 'Đợt test nếu scope=CYCLE',
    changed_assessment_id BIGINT NULL COMMENT 'Đánh giá vừa cập nhật',
    components_json JSON NOT NULL COMMENT 'Bốn kỹ năng, score/skillAssessmentId/performedAt; thiếu giữ null',
    overall_estimate DECIMAL(3,1) NULL COMMENT 'Null khi chưa đủ bốn nguồn IELTS phù hợp',
    method_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Công thức/thang điểm được dùng',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC',
    PRIMARY KEY (id),
    CONSTRAINT uq_profile_history_student UNIQUE (id, student_id),
    INDEX ix_profile_history_history (student_id, scope, created_at),
    CONSTRAINT fk_profile_history_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT fk_profile_history_teacher FOREIGN KEY (confirmed_by) REFERENCES users (id),
    CONSTRAINT fk_profile_history_cycle FOREIGN KEY (cycle_id) REFERENCES assessment_cycles (id),
    CONSTRAINT fk_profile_history_changed_student FOREIGN KEY (changed_assessment_id, student_id) REFERENCES skill_assessments (id, student_id),
    CONSTRAINT ck_profile_history_scope CHECK ((scope = 'PROFILE' AND cycle_id IS NULL) OR (scope = 'CYCLE' AND cycle_id IS NOT NULL)),
    CONSTRAINT ck_profile_history_overall CHECK (overall_estimate IS NULL OR (overall_estimate BETWEEN 0 AND 9 AND MOD(overall_estimate * 2, 1) = 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Snapshot bốn nguồn/điểm/overall do client giảng viên tính; giữ cả báo cáo đợt.';

CREATE TABLE learner_needs (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính',
    student_id BIGINT NOT NULL COMMENT 'Học viên',
    skill VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Kỹ năng',
    label_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Mục tiêu/nhóm bổ trợ',
    source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'AI/TEACHER',
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SUGGESTED/CONFIRMED/PRACTICING/RECHECKING/IMPROVING/CLOSED',
    confirmed_by BIGINT NULL COMMENT 'Giảng viên xác nhận',
    details_json JSON NULL COMMENT 'Âm cần luyện, các trở ngại đã đối chiếu và ghi chú',
    revision_no INT NOT NULL DEFAULT 1 COMMENT 'Phiên bản trạng thái',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC',
    PRIMARY KEY (id),
    CONSTRAINT uq_learner_needs_label UNIQUE (student_id, skill, label_code),
    CONSTRAINT uq_learner_needs_owner_skill UNIQUE (id, student_id, skill),
    INDEX ix_learner_needs_student (student_id, status),
    CONSTRAINT fk_learner_needs_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT fk_learner_needs_label_skill FOREIGN KEY (label_code, skill) REFERENCES learning_labels (code, skill),
    CONSTRAINT fk_learner_needs_teacher FOREIGN KEY (confirmed_by) REFERENCES users (id),
    CONSTRAINT ck_learner_needs_source CHECK (source IN ('AI','TEACHER')),
    CONSTRAINT ck_learner_needs_status CHECK (status IN ('SUGGESTED','CONFIRMED','PRACTICING','RECHECKING','IMPROVING','CLOSED')),
    CONSTRAINT ck_learner_needs_speaking CHECK (skill <> 'SPEAKING' OR (source = 'TEACHER' AND confirmed_by IS NOT NULL AND status <> 'SUGGESTED')),
    CONSTRAINT ck_learner_needs_confirmation CHECK (status = 'SUGGESTED' OR confirmed_by IS NOT NULL),
    CONSTRAINT ck_learner_needs_revision CHECK (revision_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Nhu cầu hiện tại theo nhãn; âm/trở ngại là chi tiết bên trong, không tạo nhãn mới.';

CREATE TABLE learner_need_events (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính',
    need_id BIGINT NOT NULL COMMENT 'Nhu cầu',
    student_id BIGINT NOT NULL COMMENT 'Học viên',
    skill VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Kỹ năng',
    actor_id BIGINT NOT NULL COMMENT 'Người ghi/xác nhận',
    event_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'AI_SUGGESTION/TEACHER_NOTE/STATUS_CHANGE',
    submission_id BIGINT NULL COMMENT 'Bài nguồn',
    teacher_assessment_id BIGINT NULL COMMENT 'Đánh giá nguồn',
    before_json JSON NULL COMMENT 'Trạng thái trước',
    after_json JSON NOT NULL COMMENT 'Trạng thái/âm/ghi chú sau',
    evidence_json JSON NULL COMMENT 'Mốc audio, quote/câu/hoạt động và thông tin nguồn',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC',
    PRIMARY KEY (id),
    INDEX ix_learner_need_events_history (need_id, created_at, id),
    CONSTRAINT fk_learner_need_events_need_owner FOREIGN KEY (need_id, student_id, skill) REFERENCES learner_needs (id, student_id, skill),
    CONSTRAINT fk_learner_need_events_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    CONSTRAINT fk_learner_need_events_submission_student FOREIGN KEY (submission_id, student_id) REFERENCES submissions (id, student_id),
    CONSTRAINT fk_learner_need_events_assessment_student FOREIGN KEY (teacher_assessment_id, student_id) REFERENCES teacher_assessments (id, student_id),
    CONSTRAINT ck_learner_need_events_type CHECK (event_type IN ('AI_SUGGESTION','TEACHER_NOTE','STATUS_CHANGE')),
    CONSTRAINT ck_learner_need_events_speaking CHECK (skill <> 'SPEAKING' OR event_type <> 'AI_SUGGESTION')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lịch sử thay đổi nhu cầu và bằng chứng đúng học viên.';

CREATE TABLE remedial_recommendations (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính',
    student_id BIGINT NOT NULL COMMENT 'Học viên',
    skill VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Kỹ năng',
    need_id BIGINT NOT NULL COMMENT 'Nhu cầu',
    resource_version_id BIGINT NOT NULL COMMENT 'Bài bổ trợ đề xuất',
    profile_history_id BIGINT NULL COMMENT 'Profile lúc đề xuất',
    reason_text TEXT NOT NULL COMMENT 'Lý do chọn bài',
    evidence_json JSON NULL COMMENT 'Nguồn và điều kiện gợi ý',
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PROPOSED' COMMENT 'PROPOSED/ASSIGNED/COMPLETED/RECHECK_REQUIRED/CLOSED',
    confirmed_by BIGINT NULL COMMENT 'Giảng viên duyệt',
    assignment_id BIGINT NULL COMMENT 'Lần giao đã tạo',
    recheck_assignment_id BIGINT NULL COMMENT 'Bài mới kiểm tra vận dụng',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC',
    PRIMARY KEY (id),
    INDEX ix_remedial_recommendations_student (student_id, status),
    CONSTRAINT fk_remedial_recommendations_need_owner FOREIGN KEY (need_id, student_id, skill) REFERENCES learner_needs (id, student_id, skill),
    CONSTRAINT fk_remedial_recommendations_version_skill FOREIGN KEY (resource_version_id, skill) REFERENCES resource_versions (id, skill),
    CONSTRAINT fk_remedial_recommendations_profile_student FOREIGN KEY (profile_history_id, student_id) REFERENCES profile_history (id, student_id),
    CONSTRAINT fk_remedial_recommendations_teacher FOREIGN KEY (confirmed_by) REFERENCES users (id),
    CONSTRAINT fk_remedial_recommendations_recipient FOREIGN KEY (assignment_id, student_id) REFERENCES assignment_recipients (assignment_id, student_id),
    CONSTRAINT fk_remedial_recommendations_recheck_recipient FOREIGN KEY (recheck_assignment_id, student_id) REFERENCES assignment_recipients (assignment_id, student_id),
    CONSTRAINT ck_remedial_recommendations_status CHECK (status IN ('PROPOSED','ASSIGNED','COMPLETED','RECHECK_REQUIRED','CLOSED')),
    CONSTRAINT fk_remedial_recommendations_assigned_version FOREIGN KEY (assignment_id, resource_version_id) REFERENCES assignments (id, resource_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Gợi ý có lý do và liên kết bài được giao; tái sử dụng Resource/Assignment.';

ALTER TABLE learner_profiles ADD CONSTRAINT fk_learner_profiles_current_history FOREIGN KEY (current_history_id, student_id) REFERENCES profile_history (id, student_id);

-- V6 DESIGN GROUP

CREATE TABLE gamification_rule_versions (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính',
    rule_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Mã cấu hình',
    version_no INT NOT NULL COMMENT 'Phiên bản',
    xp_amount INT NOT NULL COMMENT 'XP cho loại hoạt động',
    conditions_json JSON NOT NULL COMMENT 'Điều kiện hoàn thành, timezone, ngưỡng cấp nếu áp dụng',
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Bật sau khi chốt chính sách',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC',
    PRIMARY KEY (id),
    CONSTRAINT uq_gamification_rule_versions_version UNIQUE (rule_code, version_no),
    CONSTRAINT ck_gamification_rule_versions_version CHECK (version_no > 0),
    CONSTRAINT ck_gamification_rule_versions_xp CHECK (xp_amount >= 0),
    CONSTRAINT ck_gamification_rule_versions_enabled CHECK (is_enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Cấu hình thưởng/cấp độ/ngày học theo phiên bản; số 10/15/30 còn là đề xuất.';

CREATE TABLE reward_events (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính',
    student_id BIGINT NOT NULL COMMENT 'Học viên',
    assignment_id BIGINT NOT NULL COMMENT 'Bài hoàn thành',
    submission_id BIGINT NOT NULL COMMENT 'Bài nộp làm căn cứ',
    rule_version_id BIGINT NOT NULL COMMENT 'Cấu hình áp dụng',
    xp_amount INT NOT NULL COMMENT 'XP đã xác nhận theo cấu hình',
    confirmed_by BIGINT NULL COMMENT 'Giảng viên nếu hoạt động cần chốt',
    basis_json JSON NOT NULL COMMENT 'Điều kiện hoàn thành và cấu hình snapshot',
    awarded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Ngày giờ server xác nhận',
    PRIMARY KEY (id),
    CONSTRAINT uq_reward_events_award UNIQUE (student_id, assignment_id),
    INDEX ix_reward_events_student (student_id, awarded_at),
    CONSTRAINT fk_reward_events_recipient FOREIGN KEY (assignment_id, student_id) REFERENCES assignment_recipients (assignment_id, student_id),
    CONSTRAINT fk_reward_events_submission_owner FOREIGN KEY (submission_id, assignment_id, student_id) REFERENCES submissions (id, assignment_id, student_id),
    CONSTRAINT fk_reward_events_rule FOREIGN KEY (rule_version_id) REFERENCES gamification_rule_versions (id),
    CONSTRAINT fk_reward_events_confirmer FOREIGN KEY (confirmed_by) REFERENCES users (id),
    CONSTRAINT ck_reward_events_xp CHECK (xp_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Sổ XP; một Assignment chỉ thưởng một lần cho mỗi học viên.';

CREATE TABLE daily_learning_activities (
    student_id BIGINT NOT NULL COMMENT 'Học viên',
    learning_date DATE NOT NULL COMMENT 'Ngày theo timezone chính sách, server xác nhận',
    timezone VARCHAR(64) NOT NULL COMMENT 'Ví dụ Asia/Ho_Chi_Minh',
    first_submission_id BIGINT NOT NULL COMMENT 'Hoạt động hợp lệ đầu ngày',
    rule_version_id BIGINT NOT NULL COMMENT 'Chính sách ngày học',
    confirmed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Thời điểm xác nhận',
    PRIMARY KEY (student_id, learning_date),
    CONSTRAINT fk_daily_learning_activities_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT fk_daily_learning_activities_submission_student FOREIGN KEY (first_submission_id, student_id) REFERENCES submissions (id, student_id),
    CONSTRAINT fk_daily_learning_activities_rule FOREIGN KEY (rule_version_id) REFERENCES gamification_rule_versions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Một dòng cho một ngày học hợp lệ; tách khỏi XP để không phụ thuộc số điểm thưởng.';

CREATE TABLE badge_definitions (
    badge_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Mã huy hiệu',
    version_no INT NOT NULL COMMENT 'Phiên bản',
    display_name VARCHAR(150) NOT NULL COMMENT 'Tên',
    conditions_json JSON NOT NULL COMMENT 'Điều kiện xét và thông tin hiển thị',
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Bật khi chốt chính sách',
    PRIMARY KEY (badge_code, version_no),
    CONSTRAINT ck_badge_definitions_version CHECK (version_no > 0),
    CONSTRAINT ck_badge_definitions_enabled CHECK (is_enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Định nghĩa huy hiệu có phiên bản, mã huy hiệu giữ ổn định qua các lần sửa điều kiện.';

CREATE TABLE earned_badges (
    student_id BIGINT NOT NULL COMMENT 'Học viên',
    badge_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Huy hiệu',
    definition_version INT NOT NULL COMMENT 'Phiên bản đã dùng',
    evidence_json JSON NOT NULL COMMENT 'Nguồn bài nộp/ngày học và điều kiện đã đạt',
    earned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Lúc trao',
    PRIMARY KEY (student_id, badge_code),
    CONSTRAINT fk_earned_badges_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT fk_earned_badges_definition FOREIGN KEY (badge_code, definition_version) REFERENCES badge_definitions (badge_code, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Mỗi học viên chỉ nhận một lần theo mã huy hiệu, kể cả khi định nghĩa đổi phiên bản.';

CREATE TABLE experiment_runs (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Khóa chính',
    created_by BIGINT NOT NULL COMMENT 'Người thực nghiệm',
    run_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Mã run để đối chiếu CSV/log',
    experiment_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'LABEL_REVIEW/GRADING/NETWORK/...',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PLANNED' COMMENT 'PLANNED/RUNNING/COMPLETED/FAILED',
    config_json JSON NOT NULL COMMENT 'Baseline/proposed, dữ liệu, model/prompt/rubric/phiên bản phương pháp',
    results_json JSON NULL COMMENT 'Chỉ số đo thực tế',
    result_asset_id BIGINT NULL COMMENT 'CSV/log đã lưu',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC',
    completed_at DATETIME(6) NULL COMMENT 'Kết thúc',
    PRIMARY KEY (id),
    CONSTRAINT uq_experiment_runs_code UNIQUE (run_code),
    CONSTRAINT fk_experiment_runs_creator FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_experiment_runs_asset FOREIGN KEY (result_asset_id) REFERENCES assets (id),
    CONSTRAINT ck_experiment_runs_status CHECK (status IN ('PLANNED','RUNNING','COMPLETED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lưu cấu hình/bằng chứng phép đo; kết quả chỉ điền khi đã chạy thực tế.';
