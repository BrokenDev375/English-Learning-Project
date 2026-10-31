CREATE TABLE unit_writing_tasks (
    unit_id BIGINT NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 0,
    draft_json JSON NULL,
    published_version_id BIGINT NULL,
    CONSTRAINT fk_writing_task_unit FOREIGN KEY (unit_id) REFERENCES units(id)
);
CREATE TABLE unit_writing_versions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    definition_json JSON NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_writing_version_task FOREIGN KEY (unit_id) REFERENCES unit_writing_tasks(unit_id),
    CONSTRAINT fk_writing_version_author FOREIGN KEY (created_by) REFERENCES users(id)
);
ALTER TABLE unit_writing_tasks ADD CONSTRAINT fk_writing_published FOREIGN KEY (published_version_id) REFERENCES unit_writing_versions(id);
CREATE TABLE unit_writing_works (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    draft_text MEDIUMTEXT NOT NULL,
    latest_submission_id BIGINT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uq_writing_student_unit (student_id,unit_id),
    CONSTRAINT fk_writing_work_task FOREIGN KEY (unit_id) REFERENCES unit_writing_tasks(unit_id),
    CONSTRAINT fk_writing_work_student FOREIGN KEY (student_id) REFERENCES users(id),
    CONSTRAINT fk_writing_work_version FOREIGN KEY (version_id) REFERENCES unit_writing_versions(id),
    CONSTRAINT ck_writing_work_state CHECK (state IN ('DRAFT','SUBMITTED','RETURNED','GRADED')),
    CONSTRAINT ck_writing_work_revision CHECK (revision>=0)
);
CREATE TABLE unit_writing_submissions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    work_id BIGINT NOT NULL,
    unit_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    body_text MEDIUMTEXT NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    INDEX ix_writing_unit_queue (unit_id,id),
    INDEX ix_writing_work_history (work_id,id),
    CONSTRAINT fk_writing_submission_work FOREIGN KEY (work_id) REFERENCES unit_writing_works(id),
    CONSTRAINT fk_writing_submission_unit FOREIGN KEY (unit_id) REFERENCES units(id),
    CONSTRAINT fk_writing_submission_student FOREIGN KEY (student_id) REFERENCES users(id),
    CONSTRAINT fk_writing_submission_version FOREIGN KEY (version_id) REFERENCES unit_writing_versions(id)
);
ALTER TABLE unit_writing_works ADD CONSTRAINT fk_writing_latest_submission FOREIGN KEY (latest_submission_id) REFERENCES unit_writing_submissions(id);
CREATE TABLE unit_writing_grades (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    graded_by BIGINT NOT NULL,
    graded_at DATETIME(6) NOT NULL,
    earned_points INT NOT NULL,
    total_points INT NOT NULL,
    criteria_json JSON NOT NULL,
    comment_text VARCHAR(4000) NOT NULL,
    allow_revision BOOLEAN NOT NULL,
    UNIQUE KEY uq_writing_submission_grade (submission_id),
    CONSTRAINT fk_writing_grade_submission FOREIGN KEY (submission_id) REFERENCES unit_writing_submissions(id),
    CONSTRAINT fk_writing_grade_teacher FOREIGN KEY (graded_by) REFERENCES users(id),
    CONSTRAINT ck_writing_grade_points CHECK (earned_points>=0 AND earned_points<=total_points AND total_points=10)
);
