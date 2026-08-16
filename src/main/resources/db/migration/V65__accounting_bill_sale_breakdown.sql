ALTER TABLE tbl_user_accounting_entry
    ADD COLUMN IF NOT EXISTS order_amount NUMERIC(12, 2);

ALTER TABLE tbl_user_accounting_entry
    ADD COLUMN IF NOT EXISTS tip_amount NUMERIC(12, 2);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_accounting_entry_bill_sale
    ON tbl_user_accounting_entry (source_bill_id)
    WHERE source_type = 'BILL_SALE' AND source_bill_id IS NOT NULL;
