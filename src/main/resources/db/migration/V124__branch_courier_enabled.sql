-- Optional courier department per branch. New branches start without courier ops.

ALTER TABLE tbl_branch
    ADD COLUMN IF NOT EXISTS courier_enabled BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE tbl_branch b
SET courier_enabled = TRUE
WHERE EXISTS (
    SELECT 1
    FROM tbl_merchant_staff s
    WHERE s.branch_id = b.id
      AND s.staff_role = 'COURIER'
);
