ALTER TABLE tbl_user_session
    ADD COLUMN IF NOT EXISTS impersonator_dashboard_user_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_user_session_impersonator
    ON tbl_user_session (impersonator_dashboard_user_id)
    WHERE impersonator_dashboard_user_id IS NOT NULL;
