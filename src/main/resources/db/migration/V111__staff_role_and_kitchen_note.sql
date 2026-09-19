-- Staff role on waiter accounts (WAITER | KITCHEN) and kitchen notes on orders.
-- Safe to run manually on stage/prod while Flyway remains disabled.

ALTER TABLE tbl_menu_waiter
    ADD COLUMN IF NOT EXISTS staff_role VARCHAR(16) NOT NULL DEFAULT 'WAITER';

ALTER TABLE tbl_menu_order
    ADD COLUMN IF NOT EXISTS kitchen_note TEXT;
