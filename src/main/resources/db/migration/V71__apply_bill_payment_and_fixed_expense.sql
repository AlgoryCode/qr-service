-- V67/V68 were recorded in flyway_schema_history but never applied on this database.
-- Re-apply idempotently so schema matches the application entities.

ALTER TABLE tbl_table_bill_item
    ADD COLUMN IF NOT EXISTS paid_quantity INT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_table_bill_item_paid_qty'
    ) THEN
        ALTER TABLE tbl_table_bill_item
            ADD CONSTRAINT chk_table_bill_item_paid_qty
            CHECK (paid_quantity >= 0 AND paid_quantity <= quantity);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS tbl_bill_payment (
    id BIGSERIAL PRIMARY KEY,
    bill_id BIGINT NOT NULL,
    bill_item_id BIGINT NULL,
    waiter_id BIGINT NULL,
    payment_method VARCHAR(10) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    quantity_paid INT NOT NULL DEFAULT 0,
    is_tip BOOLEAN NOT NULL DEFAULT FALSE,
    paid_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_bill_payment_bill FOREIGN KEY (bill_id) REFERENCES tbl_table_bill(id) ON DELETE CASCADE,
    CONSTRAINT fk_bill_payment_item FOREIGN KEY (bill_item_id) REFERENCES tbl_table_bill_item(id) ON DELETE SET NULL,
    CONSTRAINT fk_bill_payment_waiter FOREIGN KEY (waiter_id) REFERENCES tbl_menu_waiter(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_bill_payment_bill ON tbl_bill_payment (bill_id);
CREATE INDEX IF NOT EXISTS idx_bill_payment_paid_at ON tbl_bill_payment (paid_at);
CREATE INDEX IF NOT EXISTS idx_bill_payment_bill_item ON tbl_bill_payment (bill_item_id);

CREATE TABLE IF NOT EXISTS tbl_menu_fixed_expense (
    id BIGSERIAL PRIMARY KEY,
    menu_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    daily_amount NUMERIC(12, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_menu_fixed_expense_menu FOREIGN KEY (menu_id) REFERENCES tbl_menu(menu_id) ON DELETE CASCADE,
    CONSTRAINT chk_menu_fixed_expense_daily_amount CHECK (daily_amount >= 0.01)
);

CREATE INDEX IF NOT EXISTS idx_menu_fixed_expense_menu ON tbl_menu_fixed_expense (menu_id, active);
