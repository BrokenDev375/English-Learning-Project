ALTER TABLE units ADD COLUMN assigned_teacher_id BIGINT NULL,
    ADD CONSTRAINT fk_units_assigned_teacher FOREIGN KEY (assigned_teacher_id) REFERENCES users(id),
    ADD INDEX ix_units_assigned_order (assigned_teacher_id,round_number,id);
-- Ownership remains the creator; legacy teacher owners become the initial assignees.
UPDATE units u JOIN users a ON a.id=u.owner_id
SET u.assigned_teacher_id=a.id WHERE a.role='TEACHER';

CREATE TABLE content_control (id INT NOT NULL PRIMARY KEY, CONSTRAINT ck_content_control CHECK (id=1));
INSERT INTO content_control(id) VALUES(1);
CREATE TABLE content_review_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    kind VARCHAR(16) NOT NULL,
    question_id BIGINT NULL,
    lesson_id BIGINT NULL,
    version_id BIGINT NOT NULL,
    version_revision BIGINT NOT NULL,
    preview_token CHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    snapshot_json JSON NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    revision BIGINT NOT NULL DEFAULT 0,
    submitted_by BIGINT NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    note VARCHAR(2000) NOT NULL DEFAULT '',
    pending_key VARCHAR(96) GENERATED ALWAYS AS
        (CASE WHEN state='PENDING' THEN CONCAT(kind,':',COALESCE(question_id,lesson_id)) ELSE NULL END) STORED,
    UNIQUE KEY uq_content_review_pending (pending_key),
    INDEX ix_content_review_queue (state,id),
    INDEX ix_content_review_question (question_id,id),
    INDEX ix_content_review_lesson (lesson_id,id),
    CONSTRAINT fk_content_review_question FOREIGN KEY (question_id) REFERENCES questions(id),
    CONSTRAINT fk_content_review_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id),
    CONSTRAINT fk_content_review_submitter FOREIGN KEY (submitted_by) REFERENCES users(id),
    CONSTRAINT fk_content_review_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id),
    CONSTRAINT ck_content_review_kind CHECK
        ((kind='QUESTION' AND question_id IS NOT NULL AND lesson_id IS NULL)
        OR (kind='LESSON' AND lesson_id IS NOT NULL AND question_id IS NULL)),
    CONSTRAINT ck_content_review_state CHECK (state IN ('PENDING','APPROVED','RETURNED','WITHDRAWN')),
    CONSTRAINT ck_content_review_revision CHECK (version_id>0 AND version_revision>=0 AND revision>=0),
    CONSTRAINT ck_content_review_decision CHECK
        ((state='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR (state<>'PENDING' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    CONSTRAINT ck_content_review_return_note CHECK (state<>'RETURNED' OR CHAR_LENGTH(TRIM(note))>0)
) ENGINE=InnoDB;
