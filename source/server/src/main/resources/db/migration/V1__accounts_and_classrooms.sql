CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('TEACHER', 'STUDENT'))
) ENGINE=InnoDB;

CREATE TABLE classrooms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    teacher_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NOT NULL DEFAULT '',
    join_code CHAR(8) NOT NULL,
    create_request_id CHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_classrooms_join_code UNIQUE (join_code),
    CONSTRAINT uq_classrooms_create_request UNIQUE (teacher_id, create_request_id),
    CONSTRAINT fk_classrooms_teacher FOREIGN KEY (teacher_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE class_members (
    classroom_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    joined_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (classroom_id, student_id),
    CONSTRAINT fk_members_classroom FOREIGN KEY (classroom_id) REFERENCES classrooms (id),
    CONSTRAINT fk_members_student FOREIGN KEY (student_id) REFERENCES users (id),
    INDEX ix_members_student (student_id)
) ENGINE=InnoDB;
