-- Merchant feedback on a finished smart report; drives an optional revised report.
CREATE TABLE IF NOT EXISTS tbl_smart_report_feedback (
    id                   BIGSERIAL PRIMARY KEY,
    process_id           UUID          NOT NULL,
    merchant_id          BIGINT        NOT NULL,
    helpful              VARCHAR(16),
    recommendations_fit  VARCHAR(16),
    detail_level         VARCHAR(16),
    unclear_sections     TEXT,
    focus_areas          TEXT,
    note                 VARCHAR(1000),
    revision_process_id  UUID,
    created_at           TIMESTAMP     NOT NULL,
    updated_at           TIMESTAMP     NOT NULL,
    CONSTRAINT uk_smart_report_feedback_process UNIQUE (process_id)
);

CREATE INDEX IF NOT EXISTS idx_smart_report_feedback_merchant ON tbl_smart_report_feedback (merchant_id, created_at);
