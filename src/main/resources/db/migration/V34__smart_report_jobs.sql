CREATE TABLE IF NOT EXISTS tbl_smart_report_job (
    job_id       UUID         PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    menu_id      BIGINT       NOT NULL,
    menu_name    VARCHAR(255) NOT NULL,
    from_date    DATE         NOT NULL,
    to_date      DATE         NOT NULL,
    locale       VARCHAR(16),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_smart_report_job_user_created
    ON tbl_smart_report_job (user_id, created_at DESC);
