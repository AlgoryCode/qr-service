UPDATE tbl_user SET role = 'USER' WHERE role IS NULL;
ALTER TABLE tbl_user ALTER COLUMN role SET DEFAULT 'USER';
ALTER TABLE tbl_user ALTER COLUMN role SET NOT NULL;

ALTER TABLE tbl_user_entitlement ADD COLUMN IF NOT EXISTS user_id bigint;
UPDATE tbl_user_entitlement ue
SET user_id = p.user_id
FROM tbl_purchase p
WHERE ue.purchase_id = p.id AND ue.user_id IS NULL;
DELETE FROM tbl_user_entitlement WHERE user_id IS NULL;

ALTER TABLE tbl_user_session ADD COLUMN IF NOT EXISTS user_id bigint;
DELETE FROM tbl_user_session WHERE user_id IS NULL;

ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS user_id bigint;
UPDATE tbl_purchase p
SET user_id = ue.user_id
FROM tbl_user_entitlement ue
WHERE p.id = ue.purchase_id AND p.user_id IS NULL;
