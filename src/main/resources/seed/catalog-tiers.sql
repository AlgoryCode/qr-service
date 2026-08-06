-- Optional manual import (idempotent-ish). Prefer POST /admin/catalog/import?useClasspathSeed=true

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'QR_CREATE', 'QR Olusturma', 'QR kod olusturma hakki', TRUE, 'QR_CREATE_OWNER', TRUE, 4.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'QR_CREATE');

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'QR_MENU', 'QR Menu', 'QR menu olusturma hakki', TRUE, 'QR_MENU_OWNER', TRUE, 29.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'QR_MENU');

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'SMART_ASSISTANT', 'Akilli Asistan', 'Akilli asistan erisimi', TRUE, 'SMART_ASSISTANT_OWNER', FALSE, 79.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'SMART_ASSISTANT');

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'SMART_SUMMARY', 'Akilli Ozet', 'Akilli ozet erisimi', TRUE, 'SMART_SUMMARY_OWNER', FALSE, 99.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'SMART_SUMMARY');

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'SMART_REPORTING', 'Akilli Raporlama', 'Akilli raporlama erisimi', TRUE, 'SMART_REPORTING_OWNER', FALSE, 129.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'SMART_REPORTING');

UPDATE tbl_product SET unit_price = 4.00, vat_rate = 20.00, active = TRUE, consumable = TRUE, updated_at = NOW() WHERE code = 'QR_CREATE';
UPDATE tbl_product SET name = 'QR Menu', description = 'QR menu olusturma hakki', scope_code = 'QR_MENU_OWNER',
    unit_price = 29.00, vat_rate = 20.00, active = TRUE, consumable = TRUE, updated_at = NOW() WHERE code = 'QR_MENU';
UPDATE tbl_product SET name = 'Akilli Asistan', description = 'Akilli asistan erisimi', scope_code = 'SMART_ASSISTANT_OWNER',
    unit_price = 79.00, vat_rate = 20.00, active = TRUE, consumable = FALSE, updated_at = NOW() WHERE code = 'SMART_ASSISTANT';
UPDATE tbl_product SET name = 'Akilli Ozet', description = 'Akilli ozet erisimi', scope_code = 'SMART_SUMMARY_OWNER',
    unit_price = 99.00, vat_rate = 20.00, active = TRUE, consumable = FALSE, updated_at = NOW() WHERE code = 'SMART_SUMMARY';
UPDATE tbl_product SET name = 'Akilli Raporlama', description = 'Akilli raporlama erisimi', scope_code = 'SMART_REPORTING_OWNER',
    unit_price = 129.00, vat_rate = 20.00, active = TRUE, consumable = FALSE, updated_at = NOW() WHERE code = 'SMART_REPORTING';

DELETE FROM tbl_plan_package_item
WHERE product_id IN (
    SELECT id FROM tbl_product
    WHERE code NOT IN ('QR_CREATE', 'QR_MENU', 'SMART_ASSISTANT', 'SMART_SUMMARY', 'SMART_REPORTING')
);

DELETE FROM tbl_user_entitlement
WHERE product_id IN (
    SELECT id FROM tbl_product
    WHERE code NOT IN ('QR_CREATE', 'QR_MENU', 'SMART_ASSISTANT', 'SMART_SUMMARY', 'SMART_REPORTING')
)
   OR product_code NOT IN ('QR_CREATE', 'QR_MENU', 'SMART_ASSISTANT', 'SMART_SUMMARY', 'SMART_REPORTING');

DELETE FROM tbl_product
WHERE code NOT IN ('QR_CREATE', 'QR_MENU', 'SMART_ASSISTANT', 'SMART_SUMMARY', 'SMART_REPORTING');

INSERT INTO tbl_plan_package (code, name, description, features, price, subtotal, vat_amount, currency, active, validity_days, trial_days, priority, purchasable, system_managed, trial_eligible, created_at, updated_at)
SELECT 'FREE_PACKAGE', 'Free', '5 adet QR olusturma hakki', '["5 QR olusturma hakki","Temel kullanim"]'::jsonb,
       0, 0, 0, 'TRY', TRUE, 36500, NULL, 1, FALSE, TRUE, FALSE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_plan_package WHERE code = 'FREE_PACKAGE');

INSERT INTO tbl_plan_package (code, name, description, features, price, subtotal, vat_amount, currency, active, validity_days, trial_days, priority, purchasable, system_managed, trial_eligible, created_at, updated_at)
SELECT 'PRO_PACKAGE', 'Pro', '30 QR olusturma, QR Menu ve Akilli Asistan',
       '["30 QR olusturma hakki","1 QR Menu","Akilli Asistan","30 gun gecerlilik"]'::jsonb,
       249.00, 207.50, 41.50, 'TRY', TRUE, 30, 7, 100, TRUE, FALSE, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_plan_package WHERE code = 'PRO_PACKAGE');

INSERT INTO tbl_plan_package (code, name, description, features, price, subtotal, vat_amount, currency, active, validity_days, trial_days, priority, purchasable, system_managed, trial_eligible, created_at, updated_at)
SELECT 'ULTIMATE_PACKAGE', 'Ultimate', '100 QR, QR Menu, Akilli Asistan, Akilli Ozet ve Akilli Raporlama',
       '["100 QR olusturma hakki","1 QR Menu","Akilli Asistan","Akilli Ozet","Akilli Raporlama","30 gun gecerlilik"]'::jsonb,
       649.00, 540.83, 108.17, 'TRY', TRUE, 30, NULL, 200, TRUE, FALSE, FALSE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_plan_package WHERE code = 'ULTIMATE_PACKAGE');

UPDATE tbl_plan_package SET
    name = 'Free', description = '5 adet QR olusturma hakki',
    features = '["5 QR olusturma hakki","Temel kullanim"]'::jsonb,
    price = 0, subtotal = 0, vat_amount = 0, currency = 'TRY', active = TRUE,
    validity_days = 36500, trial_days = NULL, priority = 1, purchasable = FALSE, system_managed = TRUE, trial_eligible = FALSE,
    updated_at = NOW()
WHERE code = 'FREE_PACKAGE';

UPDATE tbl_plan_package SET
    name = 'Pro', description = '30 QR olusturma, QR Menu ve Akilli Asistan',
    features = '["30 QR olusturma hakki","1 QR Menu","Akilli Asistan","30 gun gecerlilik"]'::jsonb,
    price = 249.00, subtotal = 207.50, vat_amount = 41.50, currency = 'TRY', active = TRUE, validity_days = 30, trial_days = 7, priority = 100,
    purchasable = TRUE, system_managed = FALSE, trial_eligible = TRUE, updated_at = NOW()
WHERE code = 'PRO_PACKAGE';

UPDATE tbl_plan_package SET
    name = 'Ultimate', description = '100 QR, QR Menu, Akilli Asistan, Akilli Ozet ve Akilli Raporlama',
    features = '["100 QR olusturma hakki","1 QR Menu","Akilli Asistan","Akilli Ozet","Akilli Raporlama","30 gun gecerlilik"]'::jsonb,
    price = 649.00, subtotal = 540.83, vat_amount = 108.17, currency = 'TRY', active = TRUE, validity_days = 30, trial_days = NULL, priority = 200,
    purchasable = TRUE, system_managed = FALSE, trial_eligible = FALSE, updated_at = NOW()
WHERE code = 'ULTIMATE_PACKAGE';

DELETE FROM tbl_plan_package_item
WHERE package_id IN (SELECT id FROM tbl_plan_package WHERE code IN ('FREE_PACKAGE', 'PRO_PACKAGE', 'ULTIMATE_PACKAGE'));

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 5, FALSE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'QR_CREATE' WHERE p.code = 'FREE_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 30, FALSE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'QR_CREATE' WHERE p.code = 'PRO_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, FALSE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'QR_MENU' WHERE p.code = 'PRO_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'SMART_ASSISTANT' WHERE p.code = 'PRO_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 100, FALSE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'QR_CREATE' WHERE p.code = 'ULTIMATE_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, FALSE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'QR_MENU' WHERE p.code = 'ULTIMATE_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'SMART_ASSISTANT' WHERE p.code = 'ULTIMATE_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'SMART_SUMMARY' WHERE p.code = 'ULTIMATE_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'SMART_REPORTING' WHERE p.code = 'ULTIMATE_PACKAGE';
