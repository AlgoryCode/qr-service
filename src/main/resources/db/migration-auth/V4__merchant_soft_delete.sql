ALTER TABLE tbl_merchant
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

ALTER TABLE tbl_merchant DROP CONSTRAINT IF EXISTS uk_merchant_email;

CREATE UNIQUE INDEX IF NOT EXISTS uk_merchant_email_live
    ON tbl_merchant (email)
    WHERE deleted_at IS NULL;
