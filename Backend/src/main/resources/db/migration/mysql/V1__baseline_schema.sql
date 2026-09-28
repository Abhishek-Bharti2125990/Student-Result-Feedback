-- ---------------------------------------------------------------------------
-- V1  Baseline schema for the Student Result Intelligence Platform (MySQL 8).
--
--     The H2 equivalent lives in db/migration/h2/V1__baseline_schema.sql and
--     must be kept in step with this file. Only the dialect differs: the tables,
--     columns and constraints are the same.
-- ---------------------------------------------------------------------------

-- Identity -------------------------------------------------------------------

-- The three roles, as a table rather than a bare enum, so the set of roles is
-- inspectable from SQL and users.role is a real foreign key.
CREATE TABLE roles (
    name        VARCHAR(16)  NOT NULL PRIMARY KEY,
    description VARCHAR(160) NOT NULL
);

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    email         VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP(6) NOT NULL,
    updated_at    TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email    UNIQUE (email),
    CONSTRAINT fk_users_role     FOREIGN KEY (role) REFERENCES roles (name)
);

-- Refresh tokens are stored as SHA-256 hashes, never in plain text, and are
-- rotated on every refresh so a leaked token is single-use at worst.
CREATE TABLE refresh_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    issued_at  TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

-- People ---------------------------------------------------------------------

CREATE TABLE students (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT,
    admission_no  VARCHAR(32)  NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    class_name    VARCHAR(16)  NOT NULL,
    section       VARCHAR(8),
    academic_year VARCHAR(16)  NOT NULL,
    created_at    TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_students_admission_no UNIQUE (admission_no),
    CONSTRAINT uk_students_user         UNIQUE (user_id),
    CONSTRAINT fk_students_user         FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_students_class ON students (class_name, section);

CREATE TABLE teachers (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    staff_no   VARCHAR(32)  NOT NULL,
    full_name  VARCHAR(120) NOT NULL,
    department VARCHAR(64),
    CONSTRAINT uk_teachers_staff_no UNIQUE (staff_no),
    CONSTRAINT uk_teachers_user     UNIQUE (user_id),
    CONSTRAINT fk_teachers_user     FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Curriculum -----------------------------------------------------------------

CREATE TABLE subjects (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    -- Derived from the subject name in the CSV, e.g. Social Science ->
    -- SOCIAL-SCIENCE, so the same name always resolves to the same row.
    code       VARCHAR(32) NOT NULL,
    name       VARCHAR(96) NOT NULL,
    class_name VARCHAR(16) NOT NULL,
    CONSTRAINT uk_subjects_code UNIQUE (code)
);

-- chapter_name comes straight from the CSV. A topic is identified by its name
-- within a subject; the chapter is the textbook unit it sits in, and is carried
-- so a recommendation can say "revise chapter 4" rather than just "revise
-- quadratic equations".
CREATE TABLE topics (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subject_id   BIGINT      NOT NULL,
    chapter_name VARCHAR(96),
    name         VARCHAR(96) NOT NULL,
    CONSTRAINT uk_topics_subject_name UNIQUE (subject_id, name),
    CONSTRAINT fk_topics_subject      FOREIGN KEY (subject_id) REFERENCES subjects (id)
);

CREATE TABLE teacher_subjects (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    teacher_id BIGINT      NOT NULL,
    subject_id BIGINT      NOT NULL,
    class_name VARCHAR(16) NOT NULL,
    CONSTRAINT uk_teacher_subject UNIQUE (teacher_id, subject_id, class_name),
    CONSTRAINT fk_teacher_subject_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id),
    CONSTRAINT fk_teacher_subject_subject FOREIGN KEY (subject_id) REFERENCES subjects (id)
);

CREATE TABLE exams (
    id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    -- Derived from exam_name in the CSV, e.g. Unit Test 1 -> UNIT-TEST-1.
    code          VARCHAR(64) NOT NULL,
    name          VARCHAR(96) NOT NULL,
    term          VARCHAR(24) NOT NULL,
    exam_date     DATE        NOT NULL,
    class_name    VARCHAR(16) NOT NULL,
    academic_year VARCHAR(16) NOT NULL,
    CONSTRAINT uk_exams_code UNIQUE (code)
);

CREATE INDEX ix_exams_date ON exams (exam_date);

-- Results --------------------------------------------------------------------

-- One row per student, exam and subject. Marks are the sum of the subject's
-- topic rows in the uploaded file, so this table is derived from topic_scores
-- rather than loaded directly.
CREATE TABLE exam_results (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id     BIGINT       NOT NULL,
    exam_id        BIGINT       NOT NULL,
    subject_id     BIGINT       NOT NULL,
    marks_obtained DECIMAL(8,2) NOT NULL,
    max_marks      DECIMAL(8,2) NOT NULL,
    percentage     DECIMAL(5,2) NOT NULL,
    grade          VARCHAR(4)   NOT NULL,
    attempted      BOOLEAN      NOT NULL DEFAULT TRUE,
    remarks        VARCHAR(255),
    upload_job_id  BIGINT,
    created_at     TIMESTAMP(6) NOT NULL,
    updated_at     TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_exam_result UNIQUE (student_id, exam_id, subject_id),
    CONSTRAINT ck_exam_result_max_marks CHECK (max_marks > 0),
    CONSTRAINT ck_exam_result_marks     CHECK (marks_obtained >= 0),
    CONSTRAINT fk_exam_result_student   FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_exam_result_exam      FOREIGN KEY (exam_id)    REFERENCES exams (id),
    CONSTRAINT fk_exam_result_subject   FOREIGN KEY (subject_id) REFERENCES subjects (id)
);

CREATE INDEX ix_exam_results_student ON exam_results (student_id);
CREATE INDEX ix_exam_results_exam    ON exam_results (exam_id);
CREATE INDEX ix_exam_results_subject ON exam_results (subject_id);

-- One CSV row lands here. This is what makes weak-topic detection possible
-- instead of inferring it from the subject total.
CREATE TABLE topic_scores (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    exam_result_id BIGINT       NOT NULL,
    topic_id       BIGINT       NOT NULL,
    marks_obtained DECIMAL(8,2) NOT NULL,
    max_marks      DECIMAL(8,2) NOT NULL,
    percentage     DECIMAL(5,2) NOT NULL,
    CONSTRAINT uk_topic_score UNIQUE (exam_result_id, topic_id),
    CONSTRAINT ck_topic_score_max_marks CHECK (max_marks > 0),
    CONSTRAINT fk_topic_score_result    FOREIGN KEY (exam_result_id) REFERENCES exam_results (id),
    CONSTRAINT fk_topic_score_topic     FOREIGN KEY (topic_id)       REFERENCES topics (id)
);

CREATE INDEX ix_topic_scores_topic ON topic_scores (topic_id);

-- Analytics ------------------------------------------------------------------

-- The computed view of one student in one exam, written by the analytics step
-- of the import job. Percentage, grade, rank and category are stored rather
-- than recomputed per request: the teacher dashboard buckets a whole class by
-- category, and deriving that on every read would mean re-ranking the class
-- each time.
CREATE TABLE analytics (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id      BIGINT       NOT NULL,
    exam_id         BIGINT       NOT NULL,
    class_name      VARCHAR(16)  NOT NULL,
    section         VARCHAR(8),
    total_marks     DECIMAL(8,2) NOT NULL,
    max_marks       DECIMAL(8,2) NOT NULL,
    percentage      DECIMAL(5,2) NOT NULL,
    grade           VARCHAR(4)   NOT NULL,
    category        VARCHAR(16)  NOT NULL,
    rank_in_class   INT          NOT NULL,
    class_size      INT          NOT NULL,
    strong_subjects TEXT,
    weak_subjects   TEXT,
    strong_topics   TEXT,
    weak_topics     TEXT,
    upload_job_id   BIGINT,
    computed_at     TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_analytics_student_exam UNIQUE (student_id, exam_id),
    CONSTRAINT ck_analytics_category     CHECK (category IN ('CRITICAL', 'AVERAGE', 'GOOD', 'EXCELLENT')),
    CONSTRAINT fk_analytics_student      FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_analytics_exam         FOREIGN KEY (exam_id)    REFERENCES exams (id)
);

CREATE INDEX ix_analytics_exam_class ON analytics (exam_id, class_name);
CREATE INDEX ix_analytics_category   ON analytics (exam_id, class_name, category);

-- Learning resources --------------------------------------------------------

-- Subject and topic are plain text, not foreign keys: this table is curated
-- independently of whatever a CSV happens to contain, and a resource for a
-- topic nobody has been examined on yet is still a valid row.
CREATE TABLE study_resources (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subject       VARCHAR(96)  NOT NULL,
    topic         VARCHAR(96)  NOT NULL,
    resource_type VARCHAR(16)  NOT NULL,
    title         VARCHAR(200) NOT NULL,
    url           VARCHAR(512),
    CONSTRAINT ck_study_resource_type CHECK (resource_type IN ('BOOK', 'VIDEO'))
);

CREATE INDEX ix_study_resources_topic ON study_resources (topic);

-- CSV ingestion --------------------------------------------------------------

CREATE TABLE upload_jobs (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    stored_path       VARCHAR(512) NOT NULL,
    uploaded_by       BIGINT       NOT NULL,
    status            VARCHAR(24)  NOT NULL,
    total_records     INT          NOT NULL DEFAULT 0,
    valid_records     INT          NOT NULL DEFAULT 0,
    invalid_records   INT          NOT NULL DEFAULT 0,
    job_execution_id  BIGINT,
    failure_message   VARCHAR(1000),
    created_at        TIMESTAMP(6) NOT NULL,
    completed_at      TIMESTAMP(6),
    CONSTRAINT ck_upload_job_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED')),
    CONSTRAINT fk_upload_job_user   FOREIGN KEY (uploaded_by) REFERENCES users (id)
);

-- Every rejected row is kept with its line number so the uploader can fix the
-- source file instead of guessing which record failed.
CREATE TABLE upload_errors (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    upload_job_id BIGINT        NOT NULL,
    line_number   INT           NOT NULL,
    raw_line      VARCHAR(1000),
    message       VARCHAR(1000) NOT NULL,
    CONSTRAINT fk_upload_error_job FOREIGN KEY (upload_job_id) REFERENCES upload_jobs (id)
);

CREATE INDEX ix_upload_errors_job ON upload_errors (upload_job_id);

-- AI feedback ----------------------------------------------------------------

CREATE TABLE feedback (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    -- Null for TEACHER feedback, which is scoped to a class rather than to one
    -- student; class_name carries the scope in that case.
    student_id    BIGINT,
    class_name    VARCHAR(16),
    exam_id       BIGINT,
    audience      VARCHAR(16)  NOT NULL,
    payload_json  LONGTEXT     NOT NULL,
    model         VARCHAR(64)  NOT NULL,
    source        VARCHAR(16)  NOT NULL,
    input_tokens  INT,
    output_tokens INT,
    generated_at  TIMESTAMP(6) NOT NULL,
    CONSTRAINT ck_feedback_audience CHECK (audience IN ('STUDENT', 'TEACHER')),
    CONSTRAINT ck_feedback_source   CHECK (source IN ('CLAUDE', 'FALLBACK')),
    CONSTRAINT fk_feedback_student  FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_feedback_exam     FOREIGN KEY (exam_id)    REFERENCES exams (id)
);

CREATE INDEX ix_feedback_lookup ON feedback (student_id, exam_id, audience);
CREATE INDEX ix_feedback_class  ON feedback (class_name, exam_id, audience);
