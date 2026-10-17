-- M02 schema v1. Catalog APIs ship first; content authoring follows in steps.

CREATE TABLE request_receipts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_id BIGINT NOT NULL,
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operation VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    response_status SMALLINT NOT NULL,
    response_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_request_receipts_actor_request (actor_id, request_id),
    CONSTRAINT fk_request_receipts_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT ck_request_receipts_status CHECK (response_status BETWEEN 200 AND 299)
) ENGINE=InnoDB;

CREATE TABLE topics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL DEFAULT '',
    revision BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_topics_code (code),
    CONSTRAINT fk_topics_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT ck_topics_revision CHECK (revision >= 0)
) ENGINE=InnoDB;

CREATE TABLE learning_objectives (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    code VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    revision BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_objectives_code (code),
    KEY ix_objectives_topic (topic_id),
    CONSTRAINT fk_objectives_topic FOREIGN KEY (topic_id) REFERENCES topics(id),
    CONSTRAINT fk_objectives_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT ck_objectives_revision CHECK (revision >= 0)
) ENGINE=InnoDB;

CREATE TABLE units (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    seed_key VARCHAR(96) NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    stage INT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_units_seed (seed_key),
    KEY ix_units_order (stage, sort_order, id),
    KEY ix_units_topic (topic_id),
    CONSTRAINT fk_units_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT fk_units_topic FOREIGN KEY (topic_id) REFERENCES topics(id),
    CONSTRAINT ck_units_order CHECK (stage >= 1 AND sort_order >= 0 AND revision >= 0)
) ENGINE=InnoDB;

CREATE TABLE assets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    storage_key VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    original_name VARCHAR(200) NOT NULL,
    mime_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'READY',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_assets_storage (storage_key),
    CONSTRAINT fk_assets_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT ck_assets_state CHECK (state IN ('READY','RETIRED')),
    CONSTRAINT ck_assets_size CHECK (size_bytes > 0 AND size_bytes <= 5242880)
) ENGINE=InnoDB;

CREATE TABLE materials (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    seed_key VARCHAR(96) NULL,
    draft_version_id BIGINT NULL,
    published_version_id BIGINT NULL,
    last_version_no INT NOT NULL DEFAULT 1,
    revision BIGINT NOT NULL DEFAULT 0,
    retired_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_materials_seed (seed_key),
    CONSTRAINT fk_materials_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT ck_materials_version_no CHECK (last_version_no > 0),
    CONSTRAINT ck_materials_revision CHECK (revision >= 0)
) ENGINE=InnoDB;

CREATE TABLE material_versions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    material_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    schema_version INT NOT NULL DEFAULT 1,
    kind VARCHAR(16) NOT NULL,
    title VARCHAR(160) NOT NULL,
    reading_text MEDIUMTEXT NULL,
    asset_id BIGINT NULL,
    transcript MEDIUMTEXT NULL,
    alt_text VARCHAR(500) NULL,
    caption VARCHAR(1000) NULL,
    start_seconds DECIMAL(10,3) NULL,
    end_seconds DECIMAL(10,3) NULL,
    source_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_material_versions_number (material_id, version_no),
    UNIQUE KEY uq_material_versions_parent_id (material_id, id),
    CONSTRAINT fk_material_versions_parent FOREIGN KEY (material_id) REFERENCES materials(id),
    CONSTRAINT ck_material_versions_state CHECK (state IN ('DRAFT','PUBLISHED')),
    CONSTRAINT ck_material_versions_stamp CHECK (version_no > 0 AND revision >= 0 AND schema_version = 1),
    CONSTRAINT ck_material_versions_published CHECK ((state = 'DRAFT' AND published_at IS NULL) OR (state = 'PUBLISHED' AND published_at IS NOT NULL)),
    KEY ix_material_versions_asset (asset_id),
    CONSTRAINT fk_material_versions_asset FOREIGN KEY (asset_id) REFERENCES assets(id),
    CONSTRAINT ck_material_versions_kind CHECK (kind IN ('READING','AUDIO','IMAGE')),
    CONSTRAINT ck_material_versions_source CHECK (JSON_TYPE(source_json) = 'OBJECT'),
    CONSTRAINT ck_material_versions_body CHECK (
        (kind = 'READING' AND reading_text IS NOT NULL AND transcript IS NULL AND start_seconds IS NULL AND end_seconds IS NULL)
        OR (kind = 'AUDIO' AND asset_id IS NOT NULL AND transcript IS NOT NULL AND reading_text IS NULL AND alt_text IS NULL AND start_seconds IS NOT NULL)
        OR (kind = 'IMAGE' AND asset_id IS NOT NULL AND alt_text IS NOT NULL AND reading_text IS NULL AND transcript IS NULL AND start_seconds IS NULL AND end_seconds IS NULL)
    ),
    CONSTRAINT ck_material_versions_segment CHECK (start_seconds IS NULL OR (start_seconds >= 0 AND (end_seconds IS NULL OR end_seconds > start_seconds)))
) ENGINE=InnoDB;

CREATE TABLE questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    seed_key VARCHAR(96) NULL,
    origin_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    draft_version_id BIGINT NULL,
    published_version_id BIGINT NULL,
    last_version_no INT NOT NULL DEFAULT 1,
    revision BIGINT NOT NULL DEFAULT 0,
    retired_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_questions_seed (seed_key),
    CONSTRAINT fk_questions_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    KEY ix_questions_origin (origin_key),
    CONSTRAINT ck_questions_version_no CHECK (last_version_no > 0),
    CONSTRAINT ck_questions_revision CHECK (revision >= 0)
) ENGINE=InnoDB;

CREATE TABLE question_versions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    schema_version INT NOT NULL DEFAULT 1,
    type VARCHAR(24) NOT NULL,
    topic_id BIGINT NOT NULL,
    objective_id BIGINT NOT NULL,
    difficulty VARCHAR(8) NOT NULL,
    prompt VARCHAR(2000) NOT NULL,
    content_json JSON NOT NULL,
    answer_json JSON NOT NULL,
    explanation TEXT NOT NULL,
    hint TEXT NULL,
    material_version_id BIGINT NULL,
    source_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_question_versions_number (question_id, version_no),
    UNIQUE KEY uq_question_versions_parent_id (question_id, id),
    CONSTRAINT fk_question_versions_parent FOREIGN KEY (question_id) REFERENCES questions(id),
    CONSTRAINT ck_question_versions_state CHECK (state IN ('DRAFT','PUBLISHED')),
    CONSTRAINT ck_question_versions_stamp CHECK (version_no > 0 AND revision >= 0 AND schema_version = 1),
    CONSTRAINT ck_question_versions_published CHECK ((state = 'DRAFT' AND published_at IS NULL) OR (state = 'PUBLISHED' AND published_at IS NOT NULL)),
    KEY ix_question_versions_filter (state, topic_id, difficulty, objective_id, id),
    CONSTRAINT fk_question_versions_topic FOREIGN KEY (topic_id) REFERENCES topics(id),
    CONSTRAINT fk_question_versions_objective FOREIGN KEY (objective_id) REFERENCES learning_objectives(id),
    CONSTRAINT fk_question_versions_material FOREIGN KEY (material_version_id) REFERENCES material_versions(id),
    CONSTRAINT ck_question_versions_type CHECK (type IN ('SINGLE_CHOICE','FILL_BLANK','MATCHING')),
    CONSTRAINT ck_question_versions_difficulty CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
    CONSTRAINT ck_question_versions_json CHECK (JSON_TYPE(content_json) = 'OBJECT' AND JSON_TYPE(answer_json) = 'OBJECT' AND JSON_TYPE(source_json) = 'OBJECT')
) ENGINE=InnoDB;

CREATE TABLE lessons (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    seed_key VARCHAR(96) NULL,
    unit_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    draft_version_id BIGINT NULL,
    published_version_id BIGINT NULL,
    last_version_no INT NOT NULL DEFAULT 1,
    revision BIGINT NOT NULL DEFAULT 0,
    retired_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_lessons_seed (seed_key),
    CONSTRAINT fk_lessons_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    KEY ix_lessons_unit_order (unit_id, sort_order, id),
    CONSTRAINT fk_lessons_unit FOREIGN KEY (unit_id) REFERENCES units(id),
    CONSTRAINT ck_lessons_order CHECK (sort_order >= 0),
    CONSTRAINT ck_lessons_version_no CHECK (last_version_no > 0),
    CONSTRAINT ck_lessons_revision CHECK (revision >= 0)
) ENGINE=InnoDB;

CREATE TABLE lesson_versions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lesson_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    schema_version INT NOT NULL DEFAULT 1,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    instructions_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_lesson_versions_number (lesson_id, version_no),
    UNIQUE KEY uq_lesson_versions_parent_id (lesson_id, id),
    CONSTRAINT fk_lesson_versions_parent FOREIGN KEY (lesson_id) REFERENCES lessons(id),
    CONSTRAINT ck_lesson_versions_state CHECK (state IN ('DRAFT','PUBLISHED')),
    CONSTRAINT ck_lesson_versions_stamp CHECK (version_no > 0 AND revision >= 0 AND schema_version = 1),
    CONSTRAINT ck_lesson_versions_published CHECK ((state = 'DRAFT' AND published_at IS NULL) OR (state = 'PUBLISHED' AND published_at IS NOT NULL)),
    CONSTRAINT ck_lesson_versions_instructions CHECK (JSON_TYPE(instructions_json) = 'ARRAY')
) ENGINE=InnoDB;

CREATE TABLE lesson_objectives (
    lesson_version_id BIGINT NOT NULL,
    objective_id BIGINT NOT NULL,
    PRIMARY KEY (lesson_version_id, objective_id),
    CONSTRAINT fk_lesson_objectives_version FOREIGN KEY (lesson_version_id) REFERENCES lesson_versions(id),
    CONSTRAINT fk_lesson_objectives_objective FOREIGN KEY (objective_id) REFERENCES learning_objectives(id)
) ENGINE=InnoDB;

CREATE TABLE lesson_questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lesson_version_id BIGINT NOT NULL,
    question_version_id BIGINT NOT NULL,
    practice_role VARCHAR(20) NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_lesson_questions_question (lesson_version_id, question_version_id),
    UNIQUE KEY uq_lesson_questions_position (lesson_version_id, practice_role, sort_order),
    CONSTRAINT fk_lesson_questions_version FOREIGN KEY (lesson_version_id) REFERENCES lesson_versions(id),
    CONSTRAINT fk_lesson_questions_question FOREIGN KEY (question_version_id) REFERENCES question_versions(id),
    CONSTRAINT ck_lesson_questions_role CHECK (practice_role IN ('GUIDED','INDEPENDENT','REVIEW_VARIANT')),
    CONSTRAINT ck_lesson_questions_order CHECK (sort_order >= 0)
) ENGINE=InnoDB;

ALTER TABLE materials
    ADD CONSTRAINT fk_materials_draft_pointer FOREIGN KEY (id, draft_version_id) REFERENCES material_versions(material_id, id),
    ADD CONSTRAINT fk_materials_published_pointer FOREIGN KEY (id, published_version_id) REFERENCES material_versions(material_id, id);

ALTER TABLE questions
    ADD CONSTRAINT fk_questions_draft_pointer FOREIGN KEY (id, draft_version_id) REFERENCES question_versions(question_id, id),
    ADD CONSTRAINT fk_questions_published_pointer FOREIGN KEY (id, published_version_id) REFERENCES question_versions(question_id, id);

ALTER TABLE lessons
    ADD CONSTRAINT fk_lessons_draft_pointer FOREIGN KEY (id, draft_version_id) REFERENCES lesson_versions(lesson_id, id),
    ADD CONSTRAINT fk_lessons_published_pointer FOREIGN KEY (id, published_version_id) REFERENCES lesson_versions(lesson_id, id);


