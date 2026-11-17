-- Additive only: the code still running on older deployments must keep working until this release
-- ships. Removals live in the contract migration of the same release.

-- Every product names the feature it grants; when it was left empty it was the product code.
UPDATE tbl_product SET feature_code = code WHERE feature_code IS NULL OR feature_code = '';
ALTER TABLE tbl_product ALTER COLUMN feature_code SET NOT NULL;

-- QR_MENU_ADDON was a package standing in for an add-on; the QR_MENU_ADDON product replaces it.
-- FREE and PREMIUM are packages from before FREE_PACKAGE; nothing sells them.
UPDATE tbl_plan_package SET active = FALSE, purchasable = FALSE, updated_at = NOW()
WHERE code IN ('QR_MENU_ADDON', 'FREE', 'PREMIUM') AND (active OR purchasable);
