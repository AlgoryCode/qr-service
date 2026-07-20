-- Optional manual import (idempotent-ish). Prefer POST /admin/catalog/import?useClasspathSeed=true

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'QR_CREATE', 'QR Olusturma', 'QR kod olusturma hakki', TRUE, 'QR_CREATE_OWNER', TRUE, 4.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'QR_CREATE');

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'QR_MENU', 'QR Menu', 'Dijital menu QR hakki', TRUE, 'QR_MENU_OWNER', TRUE, 30.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'QR_MENU');

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'QR_AGENT', 'QR Agent', 'QR Agent hakki', TRUE, 'QR_AGENT_OWNER', TRUE, 15.83, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'QR_AGENT');

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'QR_ANALYTICS', 'Detayli Raporlama', 'Detayli raporlama erisimi', TRUE, 'QR_ANALYTICS_OWNER', FALSE, 40.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'QR_ANALYTICS');

UPDATE tbl_product SET unit_price = 4.00, vat_rate = 20.00, active = TRUE, updated_at = NOW() WHERE code = 'QR_CREATE';
UPDATE tbl_product SET unit_price = 30.00, vat_rate = 20.00, active = TRUE, updated_at = NOW() WHERE code = 'QR_MENU';
UPDATE tbl_product SET unit_price = 15.83, vat_rate = 20.00, active = TRUE, updated_at = NOW() WHERE code = 'QR_AGENT';
UPDATE tbl_product SET unit_price = 40.00, vat_rate = 20.00, active = TRUE, consumable = FALSE, updated_at = NOW() WHERE code = 'QR_ANALYTICS';

INSERT INTO tbl_plan_package (code, name, description, features, price, subtotal, vat_amount, currency, active, validity_days, priority, purchasable, system_managed, trial_eligible, created_at, updated_at)
SELECT 'FREE_PACKAGE', 'Free', '5 adet QR olusturma hakki', '["5 QR olusturma hakki","Temel kullanim"]'::jsonb,
       0, 0, 0, 'TRY', TRUE, 36500, 1, FALSE, TRUE, FALSE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_plan_package WHERE code = 'FREE_PACKAGE');

INSERT INTO tbl_plan_package (code, name, description, features, price, subtotal, vat_amount, currency, active, validity_days, priority, purchasable, system_managed, trial_eligible, created_at, updated_at)
SELECT 'PRO_PACKAGE', 'Pro', '30 QR olusturma, 1 menu ve 1 agent hakki',
       '["30 QR olusturma hakki","1 Dijital menu QR","1 QR Agent hakki","30 gun gecerlilik"]'::jsonb,
       199.00, 165.83, 33.17, 'TRY', TRUE, 30, 100, TRUE, FALSE, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_plan_package WHERE code = 'PRO_PACKAGE');

INSERT INTO tbl_plan_package (code, name, description, features, price, subtotal, vat_amount, currency, active, validity_days, priority, purchasable, system_managed, trial_eligible, created_at, updated_at)
SELECT 'ULTIMATE_PACKAGE', 'Ultimate', '100 QR, 5 menu, 3 agent ve detayli raporlama',
       '["100 QR olusturma hakki","5 Dijital menu QR","3 QR Agent hakki","Detayli raporlama","30 gun gecerlilik"]'::jsonb,
       499.00, 415.83, 83.17, 'TRY', TRUE, 30, 200, TRUE, FALSE, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_plan_package WHERE code = 'ULTIMATE_PACKAGE');

UPDATE tbl_plan_package SET
    name = 'Free', description = '5 adet QR olusturma hakki',
    features = '["5 QR olusturma hakki","Temel kullanim"]'::jsonb,
    price = 0, subtotal = 0, vat_amount = 0, currency = 'TRY', active = TRUE,
    validity_days = 36500, priority = 1, purchasable = FALSE, system_managed = TRUE, trial_eligible = FALSE,
    updated_at = NOW()
WHERE code = 'FREE_PACKAGE';

UPDATE tbl_plan_package SET
    name = 'Pro', description = '30 QR olusturma, 1 menu ve 1 agent hakki',
    features = '["30 QR olusturma hakki","1 Dijital menu QR","1 QR Agent hakki","30 gun gecerlilik"]'::jsonb,
    price = 199.00, currency = 'TRY', active = TRUE, validity_days = 30, priority = 100,
    purchasable = TRUE, system_managed = FALSE, trial_eligible = TRUE, updated_at = NOW()
WHERE code = 'PRO_PACKAGE';

UPDATE tbl_plan_package SET
    name = 'Ultimate', description = '100 QR, 5 menu, 3 agent ve detayli raporlama',
    features = '["100 QR olusturma hakki","5 Dijital menu QR","3 QR Agent hakki","Detayli raporlama","30 gun gecerlilik"]'::jsonb,
    price = 499.00, currency = 'TRY', active = TRUE, validity_days = 30, priority = 200,
    purchasable = TRUE, system_managed = FALSE, trial_eligible = TRUE, updated_at = NOW()
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
SELECT p.id, pr.id, 1, FALSE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'QR_AGENT' WHERE p.code = 'PRO_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 100, FALSE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'QR_CREATE' WHERE p.code = 'ULTIMATE_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 5, FALSE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'QR_MENU' WHERE p.code = 'ULTIMATE_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 3, FALSE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'QR_AGENT' WHERE p.code = 'ULTIMATE_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 0, TRUE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'QR_ANALYTICS' WHERE p.code = 'ULTIMATE_PACKAGE';
