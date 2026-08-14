-- Retire free/corporate tiers and expire legacy free purchases.

UPDATE tbl_plan_package
SET active = FALSE,
    purchasable = FALSE,
    trial_eligible = FALSE,
    updated_at = NOW()
WHERE code IN ('FREE_PACKAGE', 'CORPORATE_PACKAGE');

UPDATE tbl_purchase
SET status = 'EXPIRED'
WHERE purchase_type = 'FREE'
  AND status = 'ACTIVE';

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'MENU_PRODUCT', 'Menu Urun Hakki', 'Dijital menude tanimlanabilecek urun sayisi', TRUE, 'MENU_PRODUCT_OWNER', TRUE, 2.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'MENU_PRODUCT');

INSERT INTO tbl_product (code, name, description, active, scope_code, consumable, unit_price, vat_rate, created_at, updated_at)
SELECT 'CUSTOM_DESIGN', 'Ozel Tasarim Menu', 'Butik marka tasarimi hazirlama hakki', TRUE, 'CUSTOM_DESIGN_OWNER', FALSE, 199.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'CUSTOM_DESIGN');

UPDATE tbl_product
SET name = 'Menu Urun Hakki',
    description = 'Dijital menude tanimlanabilecek urun sayisi',
    scope_code = 'MENU_PRODUCT_OWNER',
    unit_price = 2.00,
    vat_rate = 20.00,
    active = TRUE,
    consumable = TRUE,
    updated_at = NOW()
WHERE code = 'MENU_PRODUCT';

UPDATE tbl_product
SET name = 'Ozel Tasarim Menu',
    description = 'Butik marka tasarimi hazirlama hakki',
    scope_code = 'CUSTOM_DESIGN_OWNER',
    unit_price = 199.00,
    vat_rate = 20.00,
    active = TRUE,
    consumable = FALSE,
    updated_at = NOW()
WHERE code = 'CUSTOM_DESIGN';
