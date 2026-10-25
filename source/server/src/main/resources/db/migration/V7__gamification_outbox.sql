CREATE TABLE gamification_outbox (
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    delivered_at DATETIME(6) NULL,
    KEY ix_gamification_outbox_pending (delivered_at, created_at),
    CONSTRAINT fk_gamification_outbox_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_gamification_event_revision CHECK (revision >= 0),
    CONSTRAINT ck_gamification_event_type CHECK (event_type = 'GAMIFICATION_CHANGED')
) ENGINE=InnoDB;
