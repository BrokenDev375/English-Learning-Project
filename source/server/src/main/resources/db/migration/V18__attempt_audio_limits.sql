CREATE TABLE attempt_audio_listens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_id BIGINT NOT NULL,
    material_version_id BIGINT NOT NULL,
    plays_used INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_attempt_audio_listens (attempt_id, material_version_id),
    CONSTRAINT fk_audio_listens_attempt FOREIGN KEY (attempt_id) REFERENCES attempts(id),
    CONSTRAINT fk_audio_listens_material FOREIGN KEY (material_version_id) REFERENCES material_versions(id),
    CONSTRAINT ck_audio_listens_count CHECK (plays_used BETWEEN 0 AND 2)
) ENGINE=InnoDB;
