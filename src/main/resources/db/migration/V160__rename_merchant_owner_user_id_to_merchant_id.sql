-- Rename merchant-owner foreign keys: user_id -> merchant_id (values already aligned via V159).

DO $$
DECLARE
    r RECORD;
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'tbl_coupon' AND column_name = 'used_by_user_id'
    ) THEN
        ALTER TABLE tbl_coupon RENAME COLUMN used_by_user_id TO used_by_merchant_id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'merchants' AND column_name = 'user_id'
    ) THEN
        ALTER TABLE merchants RENAME COLUMN user_id TO owner_merchant_id;
    END IF;

    FOR r IN
        SELECT c.table_name
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.column_name = 'user_id'
          AND c.table_name NOT IN (
              'tbl_user',
              'tbl_customer',
              'tbl_admin',
              'tbl_dashboard_user',
              'tbl_merchant',
              'merchants'
          )
        LOOP
            EXECUTE format(
                'ALTER TABLE %I RENAME COLUMN user_id TO merchant_id',
                r.table_name
            );
        END LOOP;
END $$;
