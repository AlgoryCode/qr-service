-- Soft-delete flag for restaurant tables (separate from active/passive).
-- Safe to run manually on stage/prod while Flyway remains disabled.

ALTER TABLE tbl_restaurant_table
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
