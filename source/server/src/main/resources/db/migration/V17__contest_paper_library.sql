CREATE TABLE contest_teacher_permissions (
    user_id BIGINT NOT NULL PRIMARY KEY,
    can_author BOOLEAN NOT NULL DEFAULT FALSE,
    can_proctor BOOLEAN NOT NULL DEFAULT FALSE,
    revision BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_contest_permission_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_contest_permission_admin FOREIGN KEY (updated_by) REFERENCES users(id)
);
CREATE TABLE contest_papers (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    title VARCHAR(160) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    questions_json JSON NOT NULL,
    review_note VARCHAR(2000) NOT NULL DEFAULT '',
    reviewed_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    submitted_at DATETIME(6) NULL,
    reviewed_at DATETIME(6) NULL,
    legacy_contest_id BIGINT NULL,
    UNIQUE KEY uq_contest_paper_legacy (legacy_contest_id),
    KEY ix_contest_paper_owner_state (owner_id,state,id),
    CONSTRAINT fk_contest_paper_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT fk_contest_paper_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id),
    CONSTRAINT ck_contest_paper_state CHECK (state IN ('DRAFT','PENDING','READY','ARCHIVED'))
);
ALTER TABLE contests
    ADD COLUMN source_paper_id BIGINT NULL,
    ADD COLUMN source_paper_revision BIGINT NULL,
    ADD COLUMN source_paper_title VARCHAR(160) NULL,
    ADD CONSTRAINT fk_contest_source_paper FOREIGN KEY (source_paper_id) REFERENCES contest_papers(id);

-- Preserve prior assignments and all existing contest snapshots. The first
-- assigned teacher owns a migrated draft; otherwise its administrator owns it.
INSERT INTO contest_teacher_permissions (user_id,can_author,can_proctor,updated_by,updated_at)
SELECT t.teacher_id,TRUE,TRUE,MIN(c.owner_id),MIN(c.created_at)
FROM contests c
JOIN JSON_TABLE(c.proctor_ids_json,'$[*]' COLUMNS(teacher_id BIGINT PATH '$')) t
JOIN users u ON u.id=t.teacher_id AND u.role='TEACHER'
GROUP BY t.teacher_id;

INSERT INTO contest_papers (owner_id,state,title,description,questions_json,reviewed_by,created_at,updated_at,reviewed_at,legacy_contest_id)
SELECT COALESCE((SELECT u.id FROM users u WHERE u.id=CAST(JSON_UNQUOTE(JSON_EXTRACT(c.proctor_ids_json,'$[0]')) AS UNSIGNED) AND u.role='TEACHER'),c.owner_id),
       IF(c.paper_json IS NULL,'DRAFT','READY'),c.title,c.description,COALESCE(c.paper_json,c.draft_json),
       IF(c.paper_json IS NULL,NULL,c.owner_id),c.created_at,c.created_at,IF(c.paper_json IS NULL,NULL,c.created_at),c.id
FROM contests c;
UPDATE contests c JOIN contest_papers p ON p.legacy_contest_id=c.id
SET c.source_paper_id=p.id,c.source_paper_revision=p.revision,c.source_paper_title=p.title;
