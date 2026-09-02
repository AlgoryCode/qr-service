ALTER TABLE tbl_user
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS email_verification_code_hash VARCHAR(255),
    ADD COLUMN IF NOT EXISTS email_verification_expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS email_verification_sent_at TIMESTAMP;

UPDATE tbl_user
SET email_verified = TRUE
WHERE provider <> 'BASIC';
