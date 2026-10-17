-- Remaining purchases keyed by legacy tbl_user.id while JWT uses tbl_merchant.id.

UPDATE tbl_purchase p
SET user_id = map.merchant_id
FROM (
    SELECT m.id AS merchant_id,
           u.id AS legacy_user_id
    FROM tbl_merchant m
             INNER JOIN tbl_user u ON lower(trim(u.email)) = lower(trim(m.email))
    WHERE u.deleted_at IS NULL
      AND m.deleted_at IS NULL
      AND m.id <> u.id
) map
WHERE p.user_id = map.legacy_user_id;
