-- Apply ULTIMATE_TRIAL_PACKAGE on stage + prod (Flyway disabled in runtime).
-- billing_period required.

BEGIN;

UPDATE tbl_plan_package
SET trial_eligible = FALSE,
    trial_days = NULL,
    updated_at = NOW()
WHERE code = 'ULTIMATE_PACKAGE'
  AND (trial_eligible = TRUE OR trial_days IS NOT NULL);

INSERT INTO tbl_plan_package (
    code, name, description, features,
    price, subtotal, vat_amount, currency,
    active, validity_days, trial_days, priority,
    purchasable, system_managed, trial_eligible, yearly_price,
    billing_period, created_at, updated_at
)
SELECT
    'ULTIMATE_TRIAL_PACKAGE',
    'Ultimate Deneme',
    '15 gunluk Ultimate deneme paketi',
    '["1 ucretsiz sube","Sube basi 1 ucretsiz menu","Garson siparis ve adisyon modulu","Ciro takibi ve gelismis raporlar","Haftalik akilli raporlama","Akilli asistan","Akilli ozet","Ozel tasarim menu","AI ile menu fotografından urun ekleme"]'::jsonb,
    0.00, 0.00, 0.00, 'TRY',
    TRUE, 30, 15, 190,
    FALSE, FALSE, TRUE, NULL,
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
    validity_days = 30,
    trial_days = 15,
    priority = 190,
    purchasable = FALSE,
    system_managed = FALSE,
    trial_eligible = TRUE,
    yearly_price = NULL,
    billing_period = 'MONTHLY',
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

COMMIT;

SELECT code, trial_eligible, trial_days, purchasable, active, billing_period
FROM tbl_plan_package
WHERE code IN ('ULTIMATE_PACKAGE', 'ULTIMATE_TRIAL_PACKAGE');

SELECT COUNT(*) AS trial_items
FROM tbl_plan_package_item i
JOIN tbl_plan_package p ON p.id = i.package_id
WHERE p.code = 'ULTIMATE_TRIAL_PACKAGE';
