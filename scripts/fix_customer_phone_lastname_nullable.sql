-- Align stage/legacy DBs with Customer entity: phone and last_name are optional.
-- Register does not require phone; NOT NULL phone caused DataIntegrityViolation → HTTP 409.
-- Safe to re-run.

ALTER TABLE tbl_customer ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE tbl_customer ALTER COLUMN last_name DROP NOT NULL;
