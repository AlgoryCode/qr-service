-- A cancelled session is closed without charge and left out of revenue, shift and analytics totals.
ALTER TABLE tbl_console_session
    ADD COLUMN IF NOT EXISTS cancelled_at        TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancel_reason       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS cancelled_by_staff_id BIGINT;
