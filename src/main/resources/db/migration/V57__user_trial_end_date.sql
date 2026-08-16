ALTER TABLE tbl_user
    ADD COLUMN IF NOT EXISTS trial_end_date TIMESTAMPTZ;

UPDATE tbl_user u
SET trial_end_date = sub.expires_at,
    trial_used = TRUE
FROM (
    SELECT DISTINCT ON (p.user_id) p.user_id, p.expires_at
    FROM tbl_purchase p
    WHERE p.purchase_type = 'TRIAL'
      AND p.status = 'EXPIRED'
      AND p.expires_at IS NOT NULL
    ORDER BY p.user_id, p.purchased_at DESC
) sub
WHERE u.id = sub.user_id
  AND u.trial_end_date IS NULL;
