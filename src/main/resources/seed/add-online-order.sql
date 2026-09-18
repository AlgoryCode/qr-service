INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'ONLINE_ORDER', 'Online Siparis', 'Gizli adresli paket servis magazasi, siparis paneli ve kurye yonetimi', TRUE, 'ONLINE_ORDER_OWNER', FALSE, 179.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'ONLINE_ORDER');

UPDATE tbl_product SET
    name = 'Online Siparis',
    description = 'Gizli adresli paket servis magazasi, siparis paneli ve kurye yonetimi',
    scope_code = 'ONLINE_ORDER_OWNER',
    unit_price = 179.00,
    vat_rate = 20.00,
    active = TRUE,
    consumable = FALSE,
    updated_at = NOW()
WHERE code = 'ONLINE_ORDER';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'ONLINE_ORDER'
WHERE p.code IN ('ULTIMATE_PACKAGE', 'ULTIMATE_TRIAL_PACKAGE')
  AND NOT EXISTS (
      SELECT 1
      FROM tbl_plan_package_item i
      WHERE i.package_id = p.id
        AND i.product_id = pr.id
  );

INSERT INTO tbl_user_entitlement (
    user_id, product_id, product_code, purchase_id,
    total_quantity, remaining_quantity, used_quantity, unlimited,
    starts_at, expires_at, created_at, updated_at
)
SELECT
    pu.user_id,
    pr.id,
    'ONLINE_ORDER',
    pu.id,
    1,
    1,
    0,
    TRUE,
    COALESCE(pu.starts_at, NOW()),
    COALESCE(pu.expires_at, NOW() + INTERVAL '30 days'),
    NOW(),
    NOW()
FROM tbl_purchase pu
JOIN tbl_plan_package pkg ON pkg.id = pu.package_id AND pkg.code IN ('ULTIMATE_PACKAGE', 'ULTIMATE_TRIAL_PACKAGE')
JOIN tbl_product pr ON pr.code = 'ONLINE_ORDER'
WHERE pu.status = 'ACTIVE'
  AND (pu.expires_at IS NULL OR pu.expires_at > NOW())
  AND NOT EXISTS (
      SELECT 1
      FROM tbl_user_entitlement ue
      WHERE ue.purchase_id = pu.id
        AND ue.product_id = pr.id
  );
