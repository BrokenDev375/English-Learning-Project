CREATE TABLE league_seasons (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    start_at DATETIME(6) NOT NULL UNIQUE,
    end_at DATETIME(6) NOT NULL,
    cutoff_at DATETIME(6) NOT NULL,
    status VARCHAR(16) NOT NULL,
    policy_version VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    rules_json JSON NOT NULL,
    CONSTRAINT fk_league_policy FOREIGN KEY (policy_version) REFERENCES gamification_policies(version),
    CONSTRAINT ck_league_times CHECK (start_at < end_at AND end_at < cutoff_at),
    CONSTRAINT ck_league_status CHECK (status IN ('OPEN','CLOSING','FINALIZED') AND revision >= 0)
) ENGINE=InnoDB;

CREATE TABLE league_group_allocators (
    season_id BIGINT NOT NULL,
    tier VARCHAR(16) NOT NULL,
    PRIMARY KEY (season_id,tier),
    CONSTRAINT fk_allocator_season FOREIGN KEY (season_id) REFERENCES league_seasons(id),
    CONSTRAINT ck_allocator_tier CHECK (tier IN ('BRONZE','SILVER','GOLD','PLATINUM','DIAMOND'))
) ENGINE=InnoDB;

CREATE TABLE league_groups (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    season_id BIGINT NOT NULL,
    tier VARCHAR(16) NOT NULL,
    group_no INT NOT NULL,
    member_count INT NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 0,
    finalized_at DATETIME(6) NULL,
    UNIQUE KEY uq_league_group (season_id,tier,group_no),
    UNIQUE KEY uq_league_group_season (id,season_id),
    CONSTRAINT fk_group_allocator FOREIGN KEY (season_id,tier) REFERENCES league_group_allocators(season_id,tier),
    CONSTRAINT ck_group_values CHECK (group_no > 0 AND member_count BETWEEN 0 AND 25 AND revision >= 0)
) ENGINE=InnoDB;

CREATE TABLE league_members (
    member_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    tier VARCHAR(16) NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    weekly_xp BIGINT NOT NULL DEFAULT 0,
    result_json JSON NULL,
    UNIQUE KEY uq_league_member (season_id,user_id),
    KEY ix_league_user_history (user_id,season_id),
    KEY ix_league_ranking (group_id,weekly_xp,member_key),
    CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_member_group FOREIGN KEY (group_id,season_id) REFERENCES league_groups(id,season_id),
    CONSTRAINT ck_member_values CHECK (weekly_xp BETWEEN 0 AND 9007199254740991 AND tier IN ('BRONZE','SILVER','GOLD','PLATINUM','DIAMOND'))
) ENGINE=InnoDB;

CREATE TABLE league_outbox (
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    group_id BIGINT NULL,
    user_id BIGINT NULL,
    revision BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    delivered_at DATETIME(6) NULL,
    KEY ix_league_outbox_pending (delivered_at,created_at),
    CONSTRAINT fk_league_outbox_group FOREIGN KEY (group_id) REFERENCES league_groups(id),
    CONSTRAINT ck_league_event CHECK (revision >= 0 AND event_type IN ('LEAGUE_CHANGED','LEADERBOARD_CHANGED')
        AND ((group_id IS NULL AND user_id IS NOT NULL) OR (group_id IS NOT NULL AND user_id IS NULL)))
) ENGINE=InnoDB;

ALTER TABLE xp_ledger ADD CONSTRAINT fk_xp_season FOREIGN KEY (season_id) REFERENCES league_seasons(id);
