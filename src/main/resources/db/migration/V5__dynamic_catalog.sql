ALTER TABLE tbl_product DROP CONSTRAINT IF EXISTS tbl_product_code_check;
ALTER TABLE tbl_plan_package DROP CONSTRAINT IF EXISTS tbl_plan_package_code_check;
ALTER TABLE tbl_purchase DROP CONSTRAINT IF EXISTS tbl_purchase_package_code_check;
ALTER TABLE tbl_user_entitlement DROP CONSTRAINT IF EXISTS tbl_user_entitlement_product_code_check;

ALTER TABLE tbl_product
    ALTER COLUMN code TYPE VARCHAR(64);

ALTER TABLE tbl_plan_package
    ALTER COLUMN code TYPE VARCHAR(64);

ALTER TABLE tbl_purchase
    ALTER COLUMN package_code TYPE VARCHAR(64);

ALTER TABLE tbl_user_entitlement
    ALTER COLUMN product_code TYPE VARCHAR(64);

ALTER TABLE tbl_product
    ADD COLUMN IF NOT EXISTS scope_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS consumable BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE tbl_plan_package
    ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS purchasable BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS system_managed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS trial_eligible BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE tbl_product
SET scope_code = code || '_OWNER',
    consumable = CASE
        WHEN code IN ('QR_CREATE', 'QR_MENU', 'QR_AGENT') THEN TRUE
        ELSE FALSE
    END
WHERE scope_code IS NULL OR scope_code = '';

UPDATE tbl_plan_package
SET system_managed = TRUE,
    purchasable = FALSE,
    trial_eligible = FALSE,
    priority = 1
WHERE code = 'FREE_PACKAGE';

UPDATE tbl_plan_package
SET system_managed = FALSE,
    purchasable = TRUE,
    trial_eligible = TRUE,
    priority = 100
WHERE code = 'PRO_PACKAGE';

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, created_at, updated_at)
SELECT 'QR_CREATE', 'QR Oluşturma', 'QR Oluşturma ürünü', TRUE, 'QR_CREATE_OWNER', TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'QR_CREATE');

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, created_at, updated_at)
SELECT 'QR_MENU', 'QR Menü', 'QR Menü ürünü', TRUE, 'QR_MENU_OWNER', TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'QR_MENU');

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, created_at, updated_at)
SELECT 'QR_AGENT', 'QR Agent', 'QR Agent ürünü', TRUE, 'QR_AGENT_OWNER', TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'QR_AGENT');

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, created_at, updated_at)
SELECT 'QR_ANALYTICS', 'Detaylı Raporlama', 'Detaylı Raporlama ürünü', TRUE, 'QR_ANALYTICS_OWNER', FALSE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'QR_ANALYTICS');

INSERT INTO tbl_plan_package (code, name, description, price, currency, active, validity_days, priority, purchasable, system_managed, trial_eligible, created_at, updated_at)
SELECT 'PRO_PACKAGE', 'Pro', '30 QR oluşturma, 1 menü ve 1 agent hakkı', 199.00, 'TRY', TRUE, 30, 100, TRUE, FALSE, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_plan_package WHERE code = 'PRO_PACKAGE');

UPDATE tbl_plan_package
SET name = 'Pro',
    description = '30 QR oluşturma, 1 menü ve 1 agent hakkı',
    price = 199.00,
    currency = 'TRY',
    active = TRUE,
    validity_days = 30,
    priority = 100,
    purchasable = TRUE,
    system_managed = FALSE,
    trial_eligible = TRUE,
    updated_at = NOW()
WHERE code = 'PRO_PACKAGE';

DELETE FROM tbl_plan_package_item
WHERE package_id = (SELECT id FROM tbl_plan_package WHERE code = 'PRO_PACKAGE');

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
SELECT p.id, pr.id, 1, FALSE
FROM tbl_plan_package p
JOIN tbl_product pr ON pr.code = 'QR_AGENT'
WHERE p.code = 'PRO_PACKAGE';

ALTER TABLE tbl_product
    ALTER COLUMN scope_code SET NOT NULL;
