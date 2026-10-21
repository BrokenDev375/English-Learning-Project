-- M04: rebuildable projections from immutable M03 submissions; no changes to V1-V4.
CREATE TABLE review_objective_progress (
    user_id BIGINT NOT NULL,
    objective_id BIGINT NOT NULL,
    submission_count INT NOT NULL DEFAULT 0,
    first_answered_count INT NOT NULL DEFAULT 0,
    first_correct_count INT NOT NULL DEFAULT 0,
    assisted_count INT NOT NULL DEFAULT 0,
    unanswered_count INT NOT NULL DEFAULT 0,
    last_wrong_count INT NOT NULL DEFAULT 0,
    last_independent_answered INT NOT NULL DEFAULT 0,
    last_independent_correct INT NOT NULL DEFAULT 0,
    needs_practice BOOLEAN NOT NULL DEFAULT TRUE,
    has_mistakes BOOLEAN NOT NULL DEFAULT FALSE,
    stage INT NOT NULL DEFAULT 0,
    current_difficulty VARCHAR(8) NOT NULL DEFAULT 'EASY',
    last_practiced_at DATETIME(6) NOT NULL,
    next_review_at DATETIME(6) NOT NULL,
    last_success_on DATE NULL,
    last_submission_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, objective_id),
    KEY ix_review_due (user_id, needs_practice, next_review_at),
    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_review_objective FOREIGN KEY (objective_id) REFERENCES learning_objectives(id),
    CONSTRAINT fk_review_last_submission FOREIGN KEY (last_submission_id) REFERENCES submissions(id),
    CONSTRAINT ck_review_stage CHECK (stage BETWEEN 0 AND 3),
    CONSTRAINT ck_review_difficulty CHECK (current_difficulty IN ('EASY','MEDIUM','HARD')),
    CONSTRAINT ck_review_counts CHECK (submission_count >= 0 AND first_answered_count >= first_correct_count
        AND first_correct_count >= 0 AND assisted_count >= 0 AND unanswered_count >= 0
        AND last_wrong_count >= 0 AND last_independent_answered >= last_independent_correct AND last_independent_correct >= 0)
) ENGINE=InnoDB;

CREATE TABLE review_difficulty_evidence (
    user_id BIGINT NOT NULL,
    objective_id BIGINT NOT NULL,
    difficulty VARCHAR(8) NOT NULL,
    fresh_set_count INT NOT NULL DEFAULT 0,
    fresh_answered_count INT NOT NULL DEFAULT 0,
    fresh_correct_count INT NOT NULL DEFAULT 0,
    good_set_streak INT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, objective_id, difficulty),
    CONSTRAINT fk_review_evidence_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_review_evidence_objective FOREIGN KEY (objective_id) REFERENCES learning_objectives(id),
    CONSTRAINT ck_review_evidence_difficulty CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
    CONSTRAINT ck_review_evidence_counts CHECK (fresh_set_count >= 0 AND fresh_answered_count >= fresh_correct_count
        AND fresh_correct_count >= 0 AND good_set_streak >= 0)
) ENGINE=InnoDB;

CREATE TABLE review_processed_submissions (
    submission_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (submission_id),
    KEY ix_review_processed_user (user_id, submission_id),
    CONSTRAINT fk_review_processed_submission FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_review_processed_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;
