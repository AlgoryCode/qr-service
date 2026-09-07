-- Upsert ULTIMATE_TRIAL_PACKAGE (validity_days = trial duration), then drop trial_* columns.

INSERT INTO tbl_plan_package (
    code, name, description, features,
    price, subtotal, vat_amount, currency,
    active, validity_days, priority,
    purchasable, system_managed, yearly_price,
    billing_period, created_at, updated_at
)
SELECT
    'ULTIMATE_TRIAL_PACKAGE',
    'Ultimate Deneme',
    '15 gunluk Ultimate deneme paketi',
    '["1 ucretsiz sube","Sube basi 1 ucretsiz menu","Garson siparis ve adisyon modulu","Ciro takibi ve gelismis raporlar","Haftalik akilli raporlama","Akilli asistan","Akilli ozet","Ozel tasarim menu","AI ile menu fotografından urun ekleme"]'::jsonb,
    0.00, 0.00, 0.00, 'TRY',
    TRUE, 15, 190,
    FALSE, FALSE, NULL,
    'MONTHLY',
    NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM tbl_plan_package WHERE code = 'ULTIMATE_TRIAL_PACKAGE'
);

UPDATE tbl_plan_package
SET name = 'Ultimate Deneme',
    description = '15 gunluk Ultimate deneme paketi',
    features = '["1 ucretsiz sube","Sube basi 1 ucretsiz menu","Garson siparis ve adisyon modulu","Ciro takibi ve gelismis raporlar","Haftalik akilli raporlama","Akilli asistan","Akilli ozet","Ozel tasarim menu","AI ile menu fotografından urun ekleme"]'::jsonb,
    price = 0.00,
    subtotal = 0.00,
    vat_amount = 0.00,
    currency = 'TRY',
    active = TRUE,
    validity_days = 15,
    priority = 190,
    purchasable = FALSE,
    system_managed = FALSE,
    yearly_price = NULL,
    updated_at = NOW()
WHERE code = 'ULTIMATE_TRIAL_PACKAGE';

DELETE FROM tbl_plan_package_item
WHERE package_id = (SELECT id FROM tbl_plan_package WHERE code = 'ULTIMATE_TRIAL_PACKAGE');

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'QR_CREATE'
WHERE p.code = 'ULTIMATE_TRIAL_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, FALSE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'QR_BRANCH'
WHERE p.code = 'ULTIMATE_TRIAL_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, FALSE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'QR_MENU'
WHERE p.code = 'ULTIMATE_TRIAL_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'MENU_PRODUCT'
WHERE p.code = 'ULTIMATE_TRIAL_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'SMART_REPORTING'
WHERE p.code = 'ULTIMATE_TRIAL_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'SMART_ASSISTANT'
WHERE p.code = 'ULTIMATE_TRIAL_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'SMART_SUMMARY'
WHERE p.code = 'ULTIMATE_TRIAL_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'CUSTOM_DESIGN'
WHERE p.code = 'ULTIMATE_TRIAL_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'WAITER_PANEL'
WHERE p.code = 'ULTIMATE_TRIAL_PACKAGE';

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, 1, TRUE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'AI_MENU_IMPORT'
WHERE p.code = 'ULTIMATE_TRIAL_PACKAGE';

ALTER TABLE tbl_plan_package DROP COLUMN IF EXISTS trial_days;
ALTER TABLE tbl_plan_package DROP COLUMN IF EXISTS trial_eligible;
