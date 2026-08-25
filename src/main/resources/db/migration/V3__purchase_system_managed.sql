-- V3: Denormalize system_managed flag onto tbl_purchase.
-- Eliminates runtime FREE_PACKAGE string comparisons against purchase.package_code.
-- DEFAULT false is safe; the backfill below sets existing free-package rows to true.

ALTER TABLE tbl_purchase
    ADD COLUMN IF NOT EXISTS system_managed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE tbl_purchase
SET system_managed = TRUE
WHERE package_code = 'FREE_PACKAGE';
