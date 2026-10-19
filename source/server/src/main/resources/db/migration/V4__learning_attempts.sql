-- M03 learning attempts, contract v1 (2026-10-16).
-- Requires V1, V2 and V3. Grades and immutable first trials belong to the server.

CREATE TABLE attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    mode VARCHAR(16) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS',
    lesson_version_id BIGINT NULL,
    selection_json JSON NOT NULL,
    player_state_json JSON NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    submitted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY ix_attempts_user_state (user_id, state, updated_at),
    CONSTRAINT fk_attempts_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_attempts_lesson_version FOREIGN KEY (lesson_version_id) REFERENCES lesson_versions(id),
    CONSTRAINT ck_attempts_mode CHECK (mode IN ('LEARN','PRACTICE','REVIEW')),
    CONSTRAINT ck_attempts_state CHECK (state IN ('IN_PROGRESS','SUBMITTED')),
    CONSTRAINT ck_attempts_lesson CHECK ((mode = 'LEARN' AND lesson_version_id IS NOT NULL) OR (mode <> 'LEARN' AND lesson_version_id IS NULL)),
    CONSTRAINT ck_attempts_revision CHECK (revision >= 0),
    CONSTRAINT ck_attempts_json CHECK (JSON_TYPE(selection_json) = 'OBJECT' AND JSON_TYPE(player_state_json) = 'OBJECT'),
    CONSTRAINT ck_attempts_submitted CHECK ((state = 'IN_PROGRESS' AND submitted_at IS NULL) OR (state = 'SUBMITTED' AND submitted_at IS NOT NULL))
) ENGINE=InnoDB;

CREATE TABLE attempt_questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_id BIGINT NOT NULL,
    question_version_id BIGINT NOT NULL,
    lesson_question_id BIGINT NULL,
    origin_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    practice_role VARCHAR(20) NOT NULL,
    position INT NOT NULL,
    display_json JSON NOT NULL,
    was_seen_before BOOLEAN NOT NULL,
    seen_count_before INT NOT NULL DEFAULT 0,
    hint_opened_at DATETIME(6) NULL,
    feedback_opened_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_attempt_questions_position (attempt_id, position),
    UNIQUE KEY uq_attempt_questions_origin (attempt_id, origin_key),
    CONSTRAINT fk_attempt_questions_attempt FOREIGN KEY (attempt_id) REFERENCES attempts(id),
    CONSTRAINT fk_attempt_questions_version FOREIGN KEY (question_version_id) REFERENCES question_versions(id),
    CONSTRAINT fk_attempt_questions_slot FOREIGN KEY (lesson_question_id) REFERENCES lesson_questions(id),
    CONSTRAINT ck_attempt_questions_role CHECK (practice_role IN ('GUIDED','INDEPENDENT','REVIEW_VARIANT')),
    CONSTRAINT ck_attempt_questions_position CHECK (position >= 0 AND seen_count_before >= 0),
    CONSTRAINT ck_attempt_questions_display CHECK (JSON_TYPE(display_json) = 'OBJECT')
) ENGINE=InnoDB;

CREATE TABLE attempt_answers (
    attempt_question_id BIGINT NOT NULL,
    response_json JSON NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (attempt_question_id),
    CONSTRAINT fk_attempt_answers_item FOREIGN KEY (attempt_question_id) REFERENCES attempt_questions(id),
    CONSTRAINT ck_attempt_answers_json CHECK (response_json IS NULL OR JSON_TYPE(response_json) = 'OBJECT')
) ENGINE=InnoDB;

CREATE TABLE answer_trials (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_question_id BIGINT NOT NULL,
    trial_no INT NOT NULL,
    kind VARCHAR(20) NOT NULL,
    source VARCHAR(12) NOT NULL,
    response_json JSON NULL,
    outcome VARCHAR(16) NOT NULL,
    response_complete BOOLEAN NOT NULL,
    support_used BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_answer_trials_sequence (attempt_question_id, trial_no),
    CONSTRAINT fk_answer_trials_item FOREIGN KEY (attempt_question_id) REFERENCES attempt_questions(id),
    CONSTRAINT ck_answer_trials_number CHECK (trial_no >= 1),
    CONSTRAINT ck_answer_trials_kind CHECK ((trial_no = 1 AND kind = 'FIRST') OR (trial_no > 1 AND kind IN ('GUIDED_RETRY','CORRECTION'))),
    CONSTRAINT ck_answer_trials_source CHECK (source IN ('CHECK','SUBMIT','RETRY')),
    CONSTRAINT ck_answer_trials_outcome CHECK (outcome IN ('CORRECT','INCORRECT','UNANSWERED')),
    CONSTRAINT ck_answer_trials_json CHECK (response_json IS NULL OR JSON_TYPE(response_json) = 'OBJECT')
) ENGINE=InnoDB;

CREATE TABLE submissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_id BIGINT NOT NULL,
    facts_schema_version INT NOT NULL DEFAULT 1,
    submitted_revision BIGINT NOT NULL,
    total_questions INT NOT NULL,
    answered_count INT NOT NULL,
    correct_count INT NOT NULL,
    unanswered_count INT NOT NULL,
    independent_fresh_answered_count INT NOT NULL,
    independent_fresh_correct_count INT NOT NULL,
    score_percent DECIMAL(5,2) NOT NULL,
    lesson_completed BOOLEAN NOT NULL,
    result_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_submissions_attempt (attempt_id),
    CONSTRAINT fk_submissions_attempt FOREIGN KEY (attempt_id) REFERENCES attempts(id),
    CONSTRAINT ck_submissions_schema CHECK (facts_schema_version = 1 AND submitted_revision > 0),
    CONSTRAINT ck_submissions_counts CHECK (total_questions > 0 AND answered_count >= 0 AND correct_count >= 0 AND unanswered_count >= 0 AND answered_count + unanswered_count = total_questions AND correct_count <= answered_count),
    CONSTRAINT ck_submissions_independent CHECK (independent_fresh_correct_count >= 0 AND independent_fresh_correct_count <= independent_fresh_answered_count AND independent_fresh_answered_count <= total_questions),
    CONSTRAINT ck_submissions_score CHECK (score_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_submissions_result CHECK (JSON_TYPE(result_json) = 'OBJECT')
) ENGINE=InnoDB;

CREATE TABLE question_exposures (
    user_id BIGINT NOT NULL,
    origin_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    first_seen_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    seen_count INT NOT NULL,
    first_attempt_id BIGINT NOT NULL,
    last_attempt_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, origin_key),
    CONSTRAINT fk_question_exposures_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_question_exposures_first FOREIGN KEY (first_attempt_id) REFERENCES attempts(id),
    CONSTRAINT fk_question_exposures_last FOREIGN KEY (last_attempt_id) REFERENCES attempts(id),
    CONSTRAINT ck_question_exposures_count CHECK (seen_count >= 1)
) ENGINE=InnoDB;

CREATE TABLE lesson_progress (
    user_id BIGINT NOT NULL,
    lesson_id BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'STARTED',
    last_attempt_id BIGINT NOT NULL,
    last_completed_version_id BIGINT NULL,
    completed_at DATETIME(6) NULL,
    last_score_percent DECIMAL(5,2) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, lesson_id),
    CONSTRAINT fk_lesson_progress_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_lesson_progress_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id),
    CONSTRAINT fk_lesson_progress_attempt FOREIGN KEY (last_attempt_id) REFERENCES attempts(id),
    CONSTRAINT fk_lesson_progress_version FOREIGN KEY (last_completed_version_id) REFERENCES lesson_versions(id),
    CONSTRAINT ck_lesson_progress_state CHECK ((state = 'STARTED' AND completed_at IS NULL AND last_completed_version_id IS NULL) OR (state = 'COMPLETED' AND completed_at IS NOT NULL AND last_completed_version_id IS NOT NULL)),
    CONSTRAINT ck_lesson_progress_score CHECK (last_score_percent IS NULL OR last_score_percent BETWEEN 0 AND 100)
) ENGINE=InnoDB;

