-- Fulfillment columns and leftover merchant-owner index names: user_id -> merchant_id.
-- Values were aligned in V159. V160 renamed columns when user_id still existed.

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT c.table_name
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.column_name = 'user_id'
          AND c.table_name IN ('package_fulfillment', 'active_package', 'user_entitlement')
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns existing
            WHERE existing.table_schema = 'public'
              AND existing.table_name = r.table_name
              AND existing.column_name = 'merchant_id'
        ) THEN
            EXECUTE format('ALTER TABLE %I RENAME COLUMN user_id TO merchant_id', r.table_name);
        END IF;
    END LOOP;
END $$;

DO $$
DECLARE
    rename RECORD;
BEGIN
    FOR rename IN
        SELECT *
        FROM (VALUES
            ('idx_package_fulfillment_user_id', 'idx_package_fulfillment_merchant_id'),
            ('uq_active_package_user_id', 'uq_active_package_merchant_id'),
            ('idx_user_entitlement_user_id', 'idx_user_entitlement_merchant_id'),
            ('idx_user_entitlement_user_feature', 'idx_user_entitlement_merchant_feature'),
            ('idx_user_entitlement_user_scope', 'idx_user_entitlement_merchant_scope'),
            ('idx_purchase_user_id', 'idx_purchase_merchant_id'),
            ('idx_purchase_log_user_id', 'idx_purchase_log_merchant_id'),
            ('idx_plan_change_user_id', 'idx_plan_change_merchant_id'),
            ('idx_user_theme_assignment_user_id', 'idx_user_theme_assignment_merchant_id')
        ) AS names(old_name, new_name)
    LOOP
        IF EXISTS (SELECT 1 FROM pg_class WHERE relname = rename.old_name) THEN
            EXECUTE format('ALTER INDEX %I RENAME TO %I', rename.old_name, rename.new_name);
        ELSIF EXISTS (
            SELECT 1 FROM information_schema.table_constraints
            WHERE constraint_schema = 'public' AND constraint_name = rename.old_name
        ) THEN
            EXECUTE format(
                'ALTER TABLE %I RENAME CONSTRAINT %I TO %I',
                (
                    SELECT table_name FROM information_schema.table_constraints
                    WHERE constraint_schema = 'public' AND constraint_name = rename.old_name
                ),
                rename.old_name,
                rename.new_name
            );
        END IF;
    END LOOP;
END $$;
