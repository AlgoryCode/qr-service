DO $$
DECLARE
    planned record;
BEGIN
    FOR planned IN
        SELECT *
        FROM (VALUES
            ('tbl_menu_waiter', 'tbl_merchant_staff'),
            ('tbl_menu_waiter_session', 'tbl_merchant_staff_session'),
            ('tbl_waiter_commission_record', 'tbl_merchant_staff_commission'),
            ('tbl_work_shift_waiter', 'tbl_work_shift_staff')
        ) AS tables_to_rename(old_name, new_name)
    LOOP
        IF to_regclass(format('public.%I', planned.old_name)) IS NOT NULL
           AND to_regclass(format('public.%I', planned.new_name)) IS NOT NULL THEN
            RAISE EXCEPTION 'both % and % exist', planned.old_name, planned.new_name;
        END IF;
        IF to_regclass(format('public.%I', planned.old_name)) IS NOT NULL THEN
            EXECUTE format('ALTER TABLE public.%I RENAME TO %I', planned.old_name, planned.new_name);
        END IF;
        IF to_regclass(format('public.%I', planned.new_name)) IS NULL THEN
            RAISE EXCEPTION '% is missing', planned.new_name;
        END IF;
    END LOOP;

    FOR planned IN
        SELECT *
        FROM (VALUES
            ('tbl_merchant_staff', 'owner_user_id', 'merchant_id'),
            ('tbl_merchant_staff_session', 'waiter_id', 'staff_id'),
            ('tbl_menu_order', 'waiter_id', 'staff_id'),
            ('tbl_menu_order', 'created_by_waiter_id', 'created_by_staff_id'),
            ('tbl_menu_order', 'cancelled_by_waiter_id', 'cancelled_by_staff_id'),
            ('tbl_table_bill', 'opened_by_waiter_id', 'opened_by_staff_id'),
            ('tbl_table_bill', 'closed_by_waiter_id', 'closed_by_staff_id'),
            ('tbl_table_bill_item', 'added_by_waiter_id', 'added_by_staff_id'),
            ('tbl_bill_payment', 'waiter_id', 'staff_id'),
            ('tbl_order_audit_log', 'waiter_id', 'staff_id'),
            ('tbl_bill_adjustment', 'waiter_id', 'staff_id'),
            ('tbl_merchant_staff_commission', 'waiter_id', 'staff_id'),
            ('tbl_work_shift', 'opened_by_waiter_id', 'opened_by_staff_id'),
            ('tbl_work_shift', 'closed_by_waiter_id', 'closed_by_staff_id'),
            ('tbl_work_shift_staff', 'waiter_id', 'staff_id'),
            ('tbl_user_accounting_entry', 'created_by_waiter_id', 'created_by_staff_id'),
            ('tbl_campaign_manual_grant', 'waiter_id', 'staff_id'),
            ('tbl_print_agent_device', 'owner_user_id', 'merchant_id'),
            ('tbl_print_pairing_code', 'owner_user_id', 'merchant_id'),
            ('tbl_print_job', 'owner_user_id', 'merchant_id')
        ) AS columns_to_rename(table_name, old_column, new_column)
    LOOP
        IF to_regclass(format('public.%I', planned.table_name)) IS NULL THEN
            RAISE EXCEPTION '% is missing', planned.table_name;
        END IF;
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = planned.table_name
              AND column_name = planned.old_column
        ) THEN
            EXECUTE format(
                'ALTER TABLE public.%I RENAME COLUMN %I TO %I',
                planned.table_name,
                planned.old_column,
                planned.new_column
            );
        END IF;
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = planned.table_name
              AND column_name = planned.new_column
        ) THEN
            RAISE EXCEPTION '%.% is missing', planned.table_name, planned.new_column;
        END IF;
    END LOOP;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'tbl_merchant_staff'
          AND column_name = 'menu_id'
          AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE public.tbl_merchant_staff ALTER COLUMN menu_id DROP NOT NULL;
    END IF;
END $$;
