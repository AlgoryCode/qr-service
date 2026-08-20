ALTER TABLE tbl_menu_waiter
    ADD COLUMN IF NOT EXISTS commission_scope VARCHAR(20);

UPDATE tbl_menu_waiter
SET commission_scope = 'PER_ITEM'
WHERE commission_enabled = TRUE
  AND commission_scope IS NULL;

ALTER TABLE tbl_table_bill_item
    ADD COLUMN IF NOT EXISTS commission_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE tbl_table_bill
    ADD COLUMN IF NOT EXISTS commission_settled_at TIMESTAMP;

ALTER TABLE tbl_table_bill
    ADD COLUMN IF NOT EXISTS commission_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;
