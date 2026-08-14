ALTER TABLE tbl_qr
    ADD COLUMN IF NOT EXISTS purchase_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_qr_user_purchase_active
    ON tbl_qr (user_id, purchase_id)
    WHERE is_deleted = false;

-- Best-effort backfill: assign purchase active at QR creation time.
UPDATE tbl_qr q
SET purchase_id = matched.purchase_id
FROM (
    SELECT DISTINCT ON (q2.qr_id)
        q2.qr_id,
        p.id AS purchase_id
    FROM tbl_qr q2
    JOIN tbl_purchase p ON p.user_id = q2.user_id
    WHERE q2.purchase_id IS NULL
      AND q2.is_deleted = false
      AND p.status = 'ACTIVE'
      AND q2.created_at >= p.starts_at
      AND q2.created_at < p.expires_at
    ORDER BY q2.qr_id, p.purchased_at DESC
) matched
WHERE q.qr_id = matched.qr_id;
