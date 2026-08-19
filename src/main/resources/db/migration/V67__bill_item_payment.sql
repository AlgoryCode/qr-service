ALTER TABLE tbl_table_bill_item
    ADD COLUMN IF NOT EXISTS paid_quantity INT NOT NULL DEFAULT 0;

ALTER TABLE tbl_table_bill_item
    ADD CONSTRAINT chk_table_bill_item_paid_qty
    CHECK (paid_quantity >= 0 AND paid_quantity <= quantity);

CREATE TABLE tbl_bill_payment (
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

CREATE INDEX idx_bill_payment_bill ON tbl_bill_payment (bill_id);
CREATE INDEX idx_bill_payment_paid_at ON tbl_bill_payment (paid_at);
CREATE INDEX idx_bill_payment_bill_item ON tbl_bill_payment (bill_item_id);
