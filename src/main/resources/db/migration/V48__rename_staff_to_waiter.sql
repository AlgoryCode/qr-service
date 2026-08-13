ALTER TABLE IF EXISTS tbl_menu_staff RENAME TO tbl_menu_waiter;
ALTER TABLE IF EXISTS tbl_menu_staff_session RENAME TO tbl_menu_waiter_session;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'tbl_menu_waiter_session' AND column_name = 'staff_id'
  ) THEN
    ALTER TABLE tbl_menu_waiter_session RENAME COLUMN staff_id TO waiter_id;
  END IF;
END $$;

ALTER INDEX IF EXISTS idx_menu_staff_menu RENAME TO idx_menu_waiter_menu;
ALTER INDEX IF EXISTS idx_menu_staff_owner RENAME TO idx_menu_waiter_owner;
ALTER INDEX IF EXISTS idx_menu_staff_session_staff RENAME TO idx_menu_waiter_session_waiter;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_menu_staff_username') THEN
    ALTER TABLE tbl_menu_waiter RENAME CONSTRAINT uk_menu_staff_username TO uk_menu_waiter_username;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_menu_staff_menu') THEN
    ALTER TABLE tbl_menu_waiter RENAME CONSTRAINT fk_menu_staff_menu TO fk_menu_waiter_menu;
  END IF;
END $$;

-- order claim column
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'tbl_menu_order' AND column_name = 'waiter_staff_id'
  ) THEN
    ALTER TABLE tbl_menu_order RENAME COLUMN waiter_staff_id TO waiter_id;
  END IF;
END $$;

ALTER INDEX IF EXISTS idx_menu_order_waiter RENAME TO idx_menu_order_waiter_id;
