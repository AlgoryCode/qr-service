-- V2: Add behavioral flag columns to tbl_product.
-- addon_purchasable: true when this product can be purchased standalone as an add-on.
-- requires_count_sync: true when the entitlement usage count must be synced from a
--   domain resource (QR codes, menus, menu products) before consumption.

ALTER TABLE tbl_product
    ADD COLUMN IF NOT EXISTS addon_purchasable   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS requires_count_sync BOOLEAN NOT NULL DEFAULT FALSE;

-- addon_purchasable: products that CatalogProducts.isAddonPurchasable() previously hard-coded.
UPDATE tbl_product
SET addon_purchasable = TRUE
WHERE code IN ('QR_MENU_ADDON', 'QR_BRANCH_ADDON', 'QR_CREATE', 'QR_MENU', 'QR_BRANCH', 'MENU_PRODUCT');

-- requires_count_sync: products whose used-quota must be derived from real domain entities.
UPDATE tbl_product
SET requires_count_sync = TRUE
WHERE code IN ('QR_CREATE', 'QR_MENU', 'MENU_PRODUCT');

-- Ensure featureCode is set for base products that previously had NULL.
-- This makes featureCode the stable grouping key for category-level entitlement logic.
UPDATE tbl_product SET feature_code = 'QR_CREATE'        WHERE code = 'QR_CREATE'        AND feature_code IS NULL;
UPDATE tbl_product SET feature_code = 'MENU_PRODUCT'     WHERE code = 'MENU_PRODUCT'     AND feature_code IS NULL;
UPDATE tbl_product SET feature_code = 'SMART_REPORTING'  WHERE code = 'SMART_REPORTING'  AND feature_code IS NULL;
UPDATE tbl_product SET feature_code = 'SMART_ASSISTANT'  WHERE code = 'SMART_ASSISTANT'  AND feature_code IS NULL;
UPDATE tbl_product SET feature_code = 'SMART_SUMMARY'    WHERE code = 'SMART_SUMMARY'    AND feature_code IS NULL;
UPDATE tbl_product SET feature_code = 'CUSTOM_DESIGN'    WHERE code = 'CUSTOM_DESIGN'    AND feature_code IS NULL;
UPDATE tbl_product SET feature_code = 'WAITER_PANEL'     WHERE code = 'WAITER_PANEL'     AND feature_code IS NULL;
