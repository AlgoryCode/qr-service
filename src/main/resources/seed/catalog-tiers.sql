-- Optional manual import (idempotent-ish). Prefer POST /admin/catalog/import?useClasspathSeed=true

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'QR_CREATE', 'QR Olusturma', 'QR kod olusturma hakki', TRUE, 'QR_CREATE_OWNER', TRUE, 4.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'QR_CREATE');

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'QR_MENU', 'QR Menu', 'QR menu olusturma hakki', TRUE, 'QR_MENU_OWNER', TRUE, 29.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'QR_MENU');

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'MENU_PRODUCT', 'Menu Urun Hakki', 'Dijital menude tanimlanabilecek urun sayisi', TRUE, 'MENU_PRODUCT_OWNER', TRUE, 2.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'MENU_PRODUCT');

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'SMART_ASSISTANT', 'Akilli Asistan', 'Akilli asistan erisimi', TRUE, 'SMART_ASSISTANT_OWNER', FALSE, 79.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'SMART_ASSISTANT');

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'SMART_SUMMARY', 'Akilli Ozet', 'Akilli ozet erisimi', TRUE, 'SMART_SUMMARY_OWNER', FALSE, 99.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'SMART_SUMMARY');

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'SMART_REPORTING', 'Akilli Raporlama', 'Akilli raporlama erisimi', TRUE, 'SMART_REPORTING_OWNER', FALSE, 129.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'SMART_REPORTING');

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'CUSTOM_DESIGN', 'Ozel Tasarim Menu', 'Butik marka tasarimi hazirlama hakki', TRUE, 'CUSTOM_DESIGN_OWNER', FALSE, 199.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'CUSTOM_DESIGN');

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'WAITER_PANEL', 'Garson Paneli', 'Garson siparis ve adisyon modulu erisimi', TRUE, 'WAITER_PANEL_OWNER', FALSE, 149.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'WAITER_PANEL');

UPDATE tbl_product SET unit_price = 4.00, vat_rate = 20.00, active = TRUE, consumable = TRUE, updated_at = NOW() WHERE code = 'QR_CREATE';
UPDATE tbl_product SET name = 'QR Menu', description = 'QR menu olusturma hakki', scope_code = 'QR_MENU_OWNER',
    unit_price = 29.00, vat_rate = 20.00, active = TRUE, consumable = TRUE, updated_at = NOW() WHERE code = 'QR_MENU';
UPDATE tbl_product SET name = 'Menu Urun Hakki', description = 'Dijital menude tanimlanabilecek urun sayisi', scope_code = 'MENU_PRODUCT_OWNER',
    unit_price = 2.00, vat_rate = 20.00, active = TRUE, consumable = TRUE, updated_at = NOW() WHERE code = 'MENU_PRODUCT';
UPDATE tbl_product SET name = 'Akilli Asistan', description = 'Akilli asistan erisimi', scope_code = 'SMART_ASSISTANT_OWNER',
    unit_price = 79.00, vat_rate = 20.00, active = TRUE, consumable = FALSE, updated_at = NOW() WHERE code = 'SMART_ASSISTANT';
UPDATE tbl_product SET name = 'Akilli Ozet', description = 'Akilli ozet erisimi', scope_code = 'SMART_SUMMARY_OWNER',
    unit_price = 99.00, vat_rate = 20.00, active = TRUE, consumable = FALSE, updated_at = NOW() WHERE code = 'SMART_SUMMARY';
UPDATE tbl_product SET name = 'Akilli Raporlama', description = 'Akilli raporlama erisimi', scope_code = 'SMART_REPORTING_OWNER',
    unit_price = 129.00, vat_rate = 20.00, active = TRUE, consumable = FALSE, updated_at = NOW() WHERE code = 'SMART_REPORTING';
UPDATE tbl_product SET name = 'Ozel Tasarim Menu', description = 'Butik marka tasarimi hazirlama hakki', scope_code = 'CUSTOM_DESIGN_OWNER',
    unit_price = 199.00, vat_rate = 20.00, active = TRUE, consumable = FALSE, updated_at = NOW() WHERE code = 'CUSTOM_DESIGN';
UPDATE tbl_product SET name = 'Garson Paneli', description = 'Garson siparis ve adisyon modulu erisimi', scope_code = 'WAITER_PANEL_OWNER',
    unit_price = 149.00, vat_rate = 20.00, active = TRUE, consumable = FALSE, updated_at = NOW() WHERE code = 'WAITER_PANEL';

UPDATE tbl_plan_package SET active = FALSE, purchasable = FALSE, trial_eligible = FALSE, updated_at = NOW()
WHERE code IN ('FREE_PACKAGE', 'CORPORATE_PACKAGE');

INSERT INTO tbl_plan_package (code, name, description, features, price, subtotal, vat_amount, currency, active, validity_days, trial_days, priority, purchasable, system_managed, trial_eligible, yearly_price, created_at, updated_at)
SELECT 'STARTER_PACKAGE', 'Baslangic', 'Kucuk kafeler icin operasyonel giris paketi',
       '["50 urun hakki","1 aktif dijital menu","Standart sablonlar"]'::jsonb,
       299.00, 249.17, 49.83, 'TRY', TRUE, 30, 7, 50, TRUE, FALSE, TRUE, 2988.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_plan_package WHERE code = 'STARTER_PACKAGE');

UPDATE tbl_plan_package SET
    name = 'Baslangic', description = 'Kucuk kafeler icin operasyonel giris paketi',
    features = '["50 urun hakki","1 aktif dijital menu","Standart sablonlar"]'::jsonb,
    price = 299.00, subtotal = 249.17, vat_amount = 49.83, currency = 'TRY', active = TRUE, validity_days = 30, trial_days = 7, priority = 50,
    purchasable = TRUE, system_managed = FALSE, trial_eligible = TRUE, yearly_price = 2988.00, updated_at = NOW()
WHERE code = 'STARTER_PACKAGE';

UPDATE tbl_plan_package SET
    name = 'Pro', description = 'Sinirsiz urun, QR ve menu ile ciro takibi',
    features = '["Sinirsiz urun hakki","Sinirsiz QR ve dijital menu","Ciro takibi ve gelir raporlamasi"]'::jsonb,
    price = 599.00, subtotal = 499.17, vat_amount = 99.83, currency = 'TRY', active = TRUE, validity_days = 30, trial_days = 7, priority = 100,
    purchasable = TRUE, system_managed = FALSE, trial_eligible = TRUE, yearly_price = 5643.00, updated_at = NOW()
WHERE code = 'PRO_PACKAGE';

UPDATE tbl_plan_package SET
    name = 'Ultimate', description = 'Pro ozellikleri, ozel tasarim ve yapay zeka araclari',
    features = '["Sinirsiz urun, QR ve dijital menu","Garson siparis ve adisyon modulu","Ciro takibi ve gelismis raporlar","Haftalik akilli raporlama","Akilli asistan","Akilli ozet","Ozel tasarim menu"]'::jsonb,
    price = 999.00, subtotal = 832.50, vat_amount = 166.50, currency = 'TRY', active = TRUE, validity_days = 30, trial_days = NULL, priority = 200,
    purchasable = TRUE, system_managed = FALSE, trial_eligible = FALSE, yearly_price = 9215.00, updated_at = NOW()
WHERE code = 'ULTIMATE_PACKAGE';

DELETE FROM tbl_plan_package_item
WHERE package_id IN (SELECT id FROM tbl_plan_package WHERE code IN ('STARTER_PACKAGE', 'PRO_PACKAGE', 'ULTIMATE_PACKAGE'));

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 5, FALSE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'QR_CREATE' WHERE p.code = 'STARTER_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, FALSE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'QR_MENU' WHERE p.code = 'STARTER_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 50, FALSE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'MENU_PRODUCT' WHERE p.code = 'STARTER_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'QR_CREATE' WHERE p.code = 'PRO_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'QR_MENU' WHERE p.code = 'PRO_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'MENU_PRODUCT' WHERE p.code = 'PRO_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'SMART_REPORTING' WHERE p.code = 'PRO_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'QR_CREATE' WHERE p.code = 'ULTIMATE_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'QR_MENU' WHERE p.code = 'ULTIMATE_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'MENU_PRODUCT' WHERE p.code = 'ULTIMATE_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'SMART_REPORTING' WHERE p.code = 'ULTIMATE_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'SMART_ASSISTANT' WHERE p.code = 'ULTIMATE_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'SMART_SUMMARY' WHERE p.code = 'ULTIMATE_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'CUSTOM_DESIGN' WHERE p.code = 'ULTIMATE_PACKAGE';
INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE FROM tbl_plan_package p JOIN tbl_product pr ON pr.code = 'WAITER_PANEL' WHERE p.code = 'ULTIMATE_PACKAGE';
