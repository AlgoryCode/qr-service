-- Panel/fulfillment rows keyed by legacy tbl_user.id must use auth tbl_merchant.id (JWT merchantId).

CREATE TEMP TABLE auth_merchant_user_map ON COMMIT DROP AS
SELECT m.id AS merchant_id,
       u.id AS legacy_user_id
FROM tbl_merchant m
         INNER JOIN tbl_user u ON lower(trim(u.email)) = lower(trim(m.email))
WHERE u.deleted_at IS NULL
  AND m.deleted_at IS NULL
  AND m.id <> u.id;

UPDATE active_package ap
SET user_id = map.merchant_id,
    updated_at = NOW()
FROM auth_merchant_user_map map
WHERE ap.user_id = map.legacy_user_id
  AND NOT EXISTS (
    SELECT 1 FROM active_package existing WHERE existing.user_id = map.merchant_id
);

UPDATE package_fulfillment pf
SET user_id = map.merchant_id,
    updated_at = NOW()
FROM auth_merchant_user_map map
WHERE pf.user_id = map.legacy_user_id;

UPDATE user_entitlement ue
SET user_id = map.merchant_id,
    updated_at = NOW()
FROM auth_merchant_user_map map
WHERE ue.user_id = map.legacy_user_id;

UPDATE tbl_purchase p
SET user_id = map.merchant_id
FROM auth_merchant_user_map map
WHERE p.user_id = map.legacy_user_id;

UPDATE tbl_menu menu
SET user_id = map.merchant_id
FROM auth_merchant_user_map map
WHERE menu.user_id = map.legacy_user_id;

UPDATE tbl_merchant_staff staff
SET merchant_id = map.merchant_id
FROM auth_merchant_user_map map
WHERE staff.merchant_id = map.legacy_user_id;

UPDATE merchants store
SET user_id = map.merchant_id,
    updated_at = NOW()
FROM auth_merchant_user_map map
WHERE store.user_id = map.legacy_user_id
  AND store.is_deleted = FALSE;
