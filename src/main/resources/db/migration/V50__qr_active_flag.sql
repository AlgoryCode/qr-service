ALTER TABLE tbl_qr
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE tbl_qr q
SET active = m.active
FROM tbl_menu m
WHERE m.qr_id = q.qr_id
  AND m.is_deleted = FALSE;
