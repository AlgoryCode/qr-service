-- Contract step of the entitlement redesign (V190–V194 were the additive "expand" steps).
-- Ships only together with the code that uses the new names: code built before this migration
-- does not start on the schema it leaves behind (Hibernate validates table names).

-- 1. Legacy tables nothing reads any more. Moved aside, not dropped, so their rows can still be
--    looked at or restored; drop the archive schema once nobody needs them.
CREATE SCHEMA IF NOT EXISTS archive;
ALTER TABLE IF EXISTS role_scopes          SET SCHEMA archive;
ALTER TABLE IF EXISTS scopes               SET SCHEMA archive;
ALTER TABLE IF EXISTS market_products      SET SCHEMA archive;
ALTER TABLE IF EXISTS tbl_user_entitlement SET SCHEMA archive;

-- 2. Columns that no longer mean anything: capacity usage is counted live (V190), and a package's
--    billing period lives on its price lines, not on the package.
ALTER TABLE tbl_product      DROP COLUMN IF EXISTS requires_count_sync;
ALTER TABLE tbl_plan_package DROP COLUMN IF EXISTS billing_period;

-- 3. What PurchaseSchemaCompatibilityFixer used to patch at every startup, done once.
ALTER TABLE tbl_purchase     ALTER COLUMN expires_at  DROP NOT NULL;
ALTER TABLE tbl_purchase     ALTER COLUMN starts_at   DROP NOT NULL;
ALTER TABLE tbl_purchase     ALTER COLUMN customer_id DROP NOT NULL;
ALTER TABLE tbl_purchase_log ALTER COLUMN customer_id DROP NOT NULL;
ALTER TABLE tbl_purchase_log ALTER COLUMN purchase_id DROP NOT NULL;
ALTER TABLE tbl_purchase_log ALTER COLUMN merchant_id DROP NOT NULL;

-- 4. Names that say what the tables are. Foreign keys, indexes and sequences follow a rename.
ALTER TABLE active_package           RENAME TO merchant_subscriptions;
ALTER TABLE user_entitlement         RENAME TO entitlement_grants;
ALTER TABLE tbl_plan_package         RENAME TO plans;
ALTER TABLE tbl_plan_package_item    RENAME TO plan_items;
ALTER TABLE tbl_plan_package_addon   RENAME TO plan_addons;
ALTER TABLE tbl_plan_change          RENAME TO plan_changes;
ALTER TABLE tbl_product              RENAME TO catalog_products;
ALTER TABLE tbl_purchase             RENAME TO purchases;
ALTER TABLE tbl_purchase_item        RENAME TO purchase_items;
ALTER TABLE tbl_purchase_log         RENAME TO purchase_logs;
ALTER TABLE tbl_purchase_fulfillment RENAME TO purchase_fulfillments;
ALTER TABLE tbl_coupon               RENAME TO coupons;
ALTER TABLE tbl_coupon_log           RENAME TO coupon_logs;
