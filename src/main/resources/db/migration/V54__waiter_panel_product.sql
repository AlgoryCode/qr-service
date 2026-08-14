INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'WAITER_PANEL', 'Garson Paneli', 'Garson siparis ve adisyon modulu erisimi', TRUE, 'WAITER_PANEL_OWNER', FALSE, 149.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'WAITER_PANEL');

UPDATE tbl_product
SET name = 'Garson Paneli',
    description = 'Garson siparis ve adisyon modulu erisimi',
    scope_code = 'WAITER_PANEL_OWNER',
    unit_price = 149.00,
    vat_rate = 20.00,
    active = TRUE,
    consumable = FALSE,
    updated_at = NOW()
WHERE code = 'WAITER_PANEL';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE
FROM tbl_plan_package p
         JOIN tbl_product pr ON pr.code = 'WAITER_PANEL'
WHERE p.code = 'ULTIMATE_PACKAGE'
  AND NOT EXISTS (
    SELECT 1
    FROM tbl_plan_package_item existing
    WHERE existing.package_id = p.id
      AND existing.product_id = pr.id
);

UPDATE tbl_plan_package
SET features = '["Sinirsiz urun, QR ve dijital menu","Garson siparis ve adisyon modulu","Ciro takibi ve gelismis raporlar","Haftalik akilli raporlama","Akilli asistan","Akilli ozet","Ozel tasarim menu"]'::jsonb,
    updated_at = NOW()
WHERE code = 'ULTIMATE_PACKAGE';

UPDATE tbl_plan_package
SET features = '["50 urun hakki","1 aktif dijital menu","Standart sablonlar"]'::jsonb,
    updated_at = NOW()
WHERE code = 'STARTER_PACKAGE';

UPDATE tbl_plan_package
SET features = '["Sinirsiz urun hakki","Sinirsiz QR ve dijital menu","Ciro takibi ve gelir raporlamasi"]'::jsonb,
    updated_at = NOW()
WHERE code = 'PRO_PACKAGE';

INSERT INTO tbl_user_entitlement (
    user_id,
    product_id,
    product_code,
    purchase_id,
    total_quantity,
    remaining_quantity,
    used_quantity,
    unlimited,
    starts_at,
    expires_at,
    created_at,
    updated_at
)
SELECT pu.user_id,
       pr.id,
       'WAITER_PANEL',
       pu.id,
       1,
       1,
       0,
       TRUE,
       COALESCE(pu.starts_at, pu.purchased_at, NOW()),
       COALESCE(pu.expires_at, NOW() + INTERVAL '30 days'),
       NOW(),
       NOW()
FROM tbl_purchase pu
         JOIN tbl_plan_package pkg ON pkg.id = pu.package_id
         JOIN tbl_product pr ON pr.code = 'WAITER_PANEL'
WHERE pkg.code = 'ULTIMATE_PACKAGE'
  AND pu.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1
    FROM tbl_user_entitlement ue
    WHERE ue.purchase_id = pu.id
      AND ue.product_id = pr.id
);
