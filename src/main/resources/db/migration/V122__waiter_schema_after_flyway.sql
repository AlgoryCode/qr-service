ALTER TABLE IF EXISTS tbl_merchant_staff
    ADD COLUMN IF NOT EXISTS staff_role VARCHAR(16) NOT NULL DEFAULT 'WAITER';

ALTER TABLE IF EXISTS tbl_branch
    ADD COLUMN IF NOT EXISTS kitchen_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE IF EXISTS tbl_menu_order
    ADD COLUMN IF NOT EXISTS kitchen_note TEXT;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'tbl_merchant_staff'
          AND column_name = 'menu_id'
          AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE tbl_merchant_staff ALTER COLUMN menu_id DROP NOT NULL;
    END IF;

    IF to_regclass(format('%I.%I', current_schema(), 'tbl_merchant_staff')) IS NULL
        OR to_regclass(format('%I.%I', current_schema(), 'tbl_branch')) IS NULL THEN
        RETURN;
    END IF;

    UPDATE tbl_branch b
    SET kitchen_enabled = TRUE
    WHERE EXISTS (
        SELECT 1
        FROM tbl_merchant_staff w
        WHERE w.branch_id = b.id
          AND w.staff_role = 'KITCHEN'
    );
END $$;
