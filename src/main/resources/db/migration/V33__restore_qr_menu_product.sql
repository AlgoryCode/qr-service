INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'QR_MENU', 'QR Menu', 'QR menu olusturma hakki', TRUE, 'QR_MENU_OWNER', TRUE, 29.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'QR_MENU');

UPDATE tbl_product SET
    name = 'QR Menu',
    description = 'QR menu olusturma hakki',
    scope_code = 'QR_MENU_OWNER',
    unit_price = 29.00,
    vat_rate = 20.00,
    active = TRUE,
    consumable = TRUE,
    updated_at = NOW()
WHERE code = 'QR_MENU';

UPDATE tbl_plan_package SET
    description = '30 QR olusturma, QR Menu ve Akilli Asistan',
    features = '["30 QR olusturma hakki","1 QR Menu","Akilli Asistan","30 gun gecerlilik"]'::jsonb,
    updated_at = NOW()
WHERE code = 'PRO_PACKAGE';

UPDATE tbl_plan_package SET
    description = '100 QR, QR Menu, Akilli Asistan, Akilli Ozet ve Akilli Raporlama',
    features = '["100 QR olusturma hakki","1 QR Menu","Akilli Asistan","Akilli Ozet","Akilli Raporlama","30 gun gecerlilik"]'::jsonb,
    updated_at = NOW()
WHERE code = 'ULTIMATE_PACKAGE';

DELETE FROM tbl_plan_package_item
WHERE package_id IN (SELECT id FROM tbl_plan_package WHERE code IN ('PRO_PACKAGE', 'ULTIMATE_PACKAGE'));

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 30, FALSE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'QR_CREATE'
WHERE p.code = 'PRO_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, FALSE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'QR_MENU'
WHERE p.code = 'PRO_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'SMART_ASSISTANT'
WHERE p.code = 'PRO_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 100, FALSE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'QR_CREATE'
WHERE p.code = 'ULTIMATE_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, FALSE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'QR_MENU'
WHERE p.code = 'ULTIMATE_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'SMART_ASSISTANT'
WHERE p.code = 'ULTIMATE_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'SMART_SUMMARY'
WHERE p.code = 'ULTIMATE_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'SMART_REPORTING'
WHERE p.code = 'ULTIMATE_PACKAGE';
