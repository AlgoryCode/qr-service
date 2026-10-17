-- Drop tables no longer read by qr-service / fulfillmentservice (runtime uses
-- package_fulfillment, active_package, user_entitlement). Safe after V150+ is live.

DROP TABLE IF EXISTS tbl_fulfillment_usage_log CASCADE;
DROP TABLE IF EXISTS tbl_fulfillment_detail CASCADE;
DROP TABLE IF EXISTS tbl_trial_log CASCADE;
DROP TABLE IF EXISTS tbl_fulfillment CASCADE;

DROP TABLE IF EXISTS stage_trial_assignment CASCADE;
DROP TABLE IF EXISTS demo_onboarding_assignment CASCADE;

DROP TABLE IF EXISTS tbl_descriptor_category CASCADE;

DROP TABLE IF EXISTS ai_menu_import_drafts CASCADE;
DROP TABLE IF EXISTS ai_menu_import_jobs CASCADE;

DROP TABLE IF EXISTS tbl_work_shift_waiter CASCADE;

ALTER TABLE tbl_user DROP COLUMN IF EXISTS trial_used;
ALTER TABLE tbl_user DROP COLUMN IF EXISTS trial_end_date;
ALTER TABLE tbl_merchant DROP COLUMN IF EXISTS trial_used;
ALTER TABLE tbl_merchant DROP COLUMN IF EXISTS trial_end_date;

-- V108 renamed store_orders -> tbl_merchant_store_order; Hibernate uses store_orders (V114+).
DO $$
DECLARE
    legacy_rows BIGINT;
BEGIN
    IF to_regclass('public.store_orders') IS NULL
        AND to_regclass('public.tbl_merchant_store_order') IS NOT NULL THEN
        ALTER TABLE tbl_merchant_store_order RENAME TO store_orders;
    ELSIF to_regclass('public.store_orders') IS NOT NULL
        AND to_regclass('public.tbl_merchant_store_order') IS NOT NULL THEN
        EXECUTE 'SELECT COUNT(*) FROM tbl_merchant_store_order' INTO legacy_rows;
        IF legacy_rows = 0 THEN
            DROP TABLE tbl_merchant_store_order CASCADE;
        END IF;
    END IF;
END $$;
