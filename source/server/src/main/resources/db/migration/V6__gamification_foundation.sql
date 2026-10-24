-- M05 foundation. Existing learning content/results and V1-V5 remain unchanged.
CREATE TABLE gamification_policies (
    version VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    activated_at DATETIME(6) NOT NULL,
    effective_at DATETIME(6) NOT NULL,
    policy_json JSON NOT NULL,
    CONSTRAINT ck_gamification_policy_json CHECK (JSON_TYPE(policy_json) = 'OBJECT')
) ENGINE=InnoDB;

INSERT INTO gamification_policies(version, activated_at, effective_at, policy_json)
VALUES ('m05-p0-v1', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6),
    JSON_OBJECT('zone','Asia/Ho_Chi_Minh','freshQuestionXp',5,'dueObjectiveXp',5,'dailyXpCap',200,
                'xpPerActivityLevel',100,'minimumIndependent',3,'groupCapacity',25,'minimumMembers',10,
                'minimumActive',5,'promotionPercent',20,'demotionPercent',20,'graceSeconds',120));

CREATE TABLE review_reward_context (
    attempt_id BIGINT NOT NULL,
    objective_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    policy_version VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    cycle_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    selected_at DATETIME(6) NOT NULL,
    due_at DATETIME(6) NOT NULL,
    stage INT NOT NULL,
    needs_practice BOOLEAN NOT NULL,
    has_mistakes BOOLEAN NOT NULL,
    eligible BOOLEAN NOT NULL,
    last_submission_id BIGINT NOT NULL,
    PRIMARY KEY (attempt_id, objective_id),
    KEY ix_reward_context_user (user_id, attempt_id),
    CONSTRAINT fk_reward_context_attempt FOREIGN KEY (attempt_id) REFERENCES attempts(id),
    CONSTRAINT fk_reward_context_objective FOREIGN KEY (objective_id) REFERENCES learning_objectives(id),
    CONSTRAINT fk_reward_context_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_reward_context_policy FOREIGN KEY (policy_version) REFERENCES gamification_policies(version),
    CONSTRAINT fk_reward_context_submission FOREIGN KEY (last_submission_id) REFERENCES submissions(id),
    CONSTRAINT ck_reward_context_stage CHECK (stage BETWEEN 0 AND 3),
    CONSTRAINT ck_reward_context_eligibility CHECK (eligible = FALSE OR
        (stage > 0 AND needs_practice = FALSE AND has_mistakes = FALSE AND due_at <= selected_at))
) ENGINE=InnoDB;

CREATE TABLE gamification_processed_submissions (
    submission_id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    policy_version VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    disposition VARCHAR(24) NOT NULL,
    result_json JSON NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    KEY ix_gamification_processed_user (user_id, submission_id),
    CONSTRAINT fk_gamification_processed_submission FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_gamification_processed_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_gamification_processed_policy FOREIGN KEY (policy_version) REFERENCES gamification_policies(version),
    CONSTRAINT ck_gamification_disposition CHECK (disposition IN ('PROCESSED','BEFORE_ACTIVATION')),
    CONSTRAINT ck_gamification_result_json CHECK (JSON_TYPE(result_json) = 'OBJECT')
) ENGINE=InnoDB;

CREATE TABLE gamification_reward_claims (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    submission_id BIGINT NOT NULL,
    reward_type VARCHAR(20) NOT NULL,
    source_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    objective_id BIGINT NULL,
    activity_date DATE NOT NULL,
    UNIQUE KEY uq_reward_source (user_id, reward_type, source_key),
    UNIQUE KEY uq_reward_objective_day (user_id, reward_type, objective_id, activity_date),
    CONSTRAINT fk_reward_claim_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_reward_claim_submission FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_reward_claim_objective FOREIGN KEY (objective_id) REFERENCES learning_objectives(id),
    CONSTRAINT ck_reward_claim_type CHECK (reward_type IN ('NEW_QUESTION','DUE_REVIEW')),
    CONSTRAINT ck_reward_claim_objective CHECK ((reward_type='NEW_QUESTION' AND objective_id IS NULL)
        OR (reward_type='DUE_REVIEW' AND objective_id IS NOT NULL))
) ENGINE=InnoDB;

CREATE TABLE xp_ledger (
    claim_id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    submission_id BIGINT NOT NULL,
    policy_version VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    requested_xp INT NOT NULL,
    awarded_xp INT NOT NULL,
    activity_date DATE NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    season_id BIGINT NULL,
    league_counted_xp INT NOT NULL DEFAULT 0,
    league_reason VARCHAR(32) NOT NULL,
    KEY ix_xp_user_day (user_id, activity_date),
    KEY ix_xp_user_season (user_id, season_id),
    KEY ix_xp_submission (submission_id),
    CONSTRAINT fk_xp_claim FOREIGN KEY (claim_id) REFERENCES gamification_reward_claims(id),
    CONSTRAINT fk_xp_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_xp_submission FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_xp_policy FOREIGN KEY (policy_version) REFERENCES gamification_policies(version),
    CONSTRAINT ck_xp_amounts CHECK (requested_xp >= 0 AND awarded_xp BETWEEN 0 AND requested_xp
        AND league_counted_xp BETWEEN 0 AND awarded_xp),
    CONSTRAINT ck_xp_league_reason CHECK (league_reason IN
        ('COUNTED','NOT_JOINED','BEFORE_JOIN','LATE_AFTER_CUTOFF','NO_AWARDED_XP','BEFORE_ACTIVATION'))
) ENGINE=InnoDB;

CREATE TABLE gamification_profiles (
    user_id BIGINT NOT NULL PRIMARY KEY,
    total_xp BIGINT NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_gamification_profile_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_gamification_profile CHECK (total_xp >= 0 AND total_xp <= 9007199254740991 AND revision >= 0)
) ENGINE=InnoDB;

CREATE TABLE gamification_daily_stats (
    user_id BIGINT NOT NULL,
    activity_date DATE NOT NULL,
    awarded_xp INT NOT NULL DEFAULT 0,
    cap_xp INT NOT NULL,
    qualified_submissions INT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, activity_date),
    CONSTRAINT fk_gamification_day_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_gamification_day CHECK (cap_xp > 0 AND awarded_xp BETWEEN 0 AND cap_xp AND qualified_submissions >= 0)
) ENGINE=InnoDB;

CREATE TABLE user_badges (
    user_id BIGINT NOT NULL,
    badge_code VARCHAR(32) NOT NULL,
    earned_at DATETIME(6) NOT NULL,
    evidence_source_at DATETIME(6) NOT NULL,
    source_submission_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, badge_code),
    CONSTRAINT fk_badge_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_badge_submission FOREIGN KEY (source_submission_id) REFERENCES submissions(id),
    CONSTRAINT ck_badge_code CHECK (badge_code IN ('FIRST_LESSON','DUE_REVIEW','SEVEN_DAY_STREAK'))
) ENGINE=InnoDB;

CREATE TABLE gamification_processing_failures (
    submission_id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    error_code VARCHAR(64) NOT NULL,
    attempts INT NOT NULL,
    retry_at DATETIME(6) NOT NULL,
    KEY ix_gamification_retry (retry_at, user_id),
    CONSTRAINT fk_gamification_failure_submission FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_gamification_failure_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_gamification_failure_attempts CHECK (attempts > 0)
) ENGINE=InnoDB;
