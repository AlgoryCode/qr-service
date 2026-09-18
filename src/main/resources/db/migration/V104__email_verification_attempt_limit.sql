-- Brute-force guard for the e-mail verification code.
-- Safe to run manually on stage/prod while Flyway remains disabled.

ALTER TABLE tbl_user
    ADD COLUMN IF NOT EXISTS email_verification_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS email_verification_locked_until TIMESTAMP;
