INSERT INTO tbl_product (
    code, name, description, active, scope_code, type_id, feature_code,
    consumable, unit_price, vat_rate, created_at, updated_at
)
SELECT
    'ONLINE_ORDER',
    'Online Siparis',
    'Gizli adresli paket servis magazasi, siparis paneli ve kurye yonetimi',
    TRUE,
    'ONLINE_ORDER_OWNER',
    'PACKAGE_PRODUCT',
    'ONLINE_ORDER',
    FALSE,
    179.00,
    20.00,
    NOW(),
    NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'ONLINE_ORDER');

UPDATE tbl_product SET
    name = 'Online Siparis',
    description = 'Gizli adresli paket servis magazasi, siparis paneli ve kurye yonetimi',
    scope_code = 'ONLINE_ORDER_OWNER',
    type_id = 'PACKAGE_PRODUCT',
    feature_code = 'ONLINE_ORDER',
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

-- Entitlement backfill: assign/reconcile packages via fulfillment API (user_entitlement / active_package).
