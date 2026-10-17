ALTER TABLE users DROP CHECK ck_users_role;
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('TEACHER', 'STUDENT', 'ADMIN'));
ALTER TABLE users
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DELETED')),
    ADD INDEX ix_users_role_status (role, status);
CREATE TABLE account_control (
    id INT NOT NULL PRIMARY KEY,
    CONSTRAINT ck_account_control_singleton CHECK (id = 1)
) ENGINE=InnoDB;
INSERT INTO account_control (id) VALUES (1);
