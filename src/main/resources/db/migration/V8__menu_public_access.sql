ALTER TABLE tbl_menu
    ADD COLUMN IF NOT EXISTS public_access_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE tbl_menu
    ADD COLUMN IF NOT EXISTS public_access_disabled_reason VARCHAR(64);
