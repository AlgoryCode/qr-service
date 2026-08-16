CREATE TABLE IF NOT EXISTS tbl_platform_feedback (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    title           VARCHAR(120) NOT NULL,
    subject         VARCHAR(60)  NOT NULL,
    description     TEXT         NOT NULL,
    screenshot_url  TEXT,
    screenshot_key  TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    admin_note      TEXT,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT chk_platform_feedback_status
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
    CONSTRAINT fk_platform_feedback_user
        FOREIGN KEY (user_id) REFERENCES tbl_user (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_platform_feedback_user_created
    ON tbl_platform_feedback (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_platform_feedback_status_created
    ON tbl_platform_feedback (status, created_at DESC);
