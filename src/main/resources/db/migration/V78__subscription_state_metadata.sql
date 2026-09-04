ALTER TABLE tbl_purchase
    ADD COLUMN IF NOT EXISTS subscription_status_reason VARCHAR(128),
    ADD COLUMN IF NOT EXISTS subscription_status_changed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS subscription_status_changed_by VARCHAR(64);
