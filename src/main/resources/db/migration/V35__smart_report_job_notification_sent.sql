ALTER TABLE tbl_smart_report_job
    ADD COLUMN IF NOT EXISTS notification_sent_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_smart_report_job_notify_pending
    ON tbl_smart_report_job (created_at)
    WHERE notification_sent_at IS NULL;
