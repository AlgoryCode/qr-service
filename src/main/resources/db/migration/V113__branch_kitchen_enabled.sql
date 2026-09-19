-- Optional kitchen department per branch. New branches start waiter-only.
-- Safe to run manually on stage/prod while Flyway remains disabled.

ALTER TABLE tbl_branch
    ADD COLUMN IF NOT EXISTS kitchen_enabled BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE tbl_branch b
SET kitchen_enabled = TRUE
WHERE EXISTS (
    SELECT 1
    FROM tbl_menu_waiter w
    WHERE w.branch_id = b.id
      AND w.staff_role = 'KITCHEN'
);
