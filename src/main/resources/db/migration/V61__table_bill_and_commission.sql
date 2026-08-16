CREATE TABLE tbl_table_bill (
    id BIGSERIAL PRIMARY KEY,
    menu_id BIGINT NOT NULL,
    table_id BIGINT NOT NULL,
    table_session_id UUID NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    opened_by_waiter_id BIGINT NULL,
    closed_by_waiter_id BIGINT NULL,
    opened_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP NULL,
    total_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    currency VARCHAR(8) NOT NULL DEFAULT 'TRY',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_table_bill_menu FOREIGN KEY (menu_id) REFERENCES tbl_menu(menu_id) ON DELETE CASCADE,
    CONSTRAINT fk_table_bill_table FOREIGN KEY (table_id) REFERENCES tbl_restaurant_table(id) ON DELETE CASCADE,
    CONSTRAINT fk_table_bill_session FOREIGN KEY (table_session_id) REFERENCES tbl_table_session(id) ON DELETE SET NULL,
    CONSTRAINT fk_table_bill_opened_by FOREIGN KEY (opened_by_waiter_id) REFERENCES tbl_menu_waiter(id) ON DELETE SET NULL,
    CONSTRAINT fk_table_bill_closed_by FOREIGN KEY (closed_by_waiter_id) REFERENCES tbl_menu_waiter(id) ON DELETE SET NULL
);

CREATE INDEX idx_table_bill_menu_status ON tbl_table_bill (menu_id, status);
CREATE INDEX idx_table_bill_table_status ON tbl_table_bill (table_id, status);
CREATE UNIQUE INDEX uk_table_bill_open_per_table ON tbl_table_bill (menu_id, table_id) WHERE status = 'OPEN';

CREATE TABLE tbl_table_bill_item (
    id BIGSERIAL PRIMARY KEY,
    bill_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    quantity INT NOT NULL,
    line_total NUMERIC(12, 2) NOT NULL,
    note TEXT NULL,
    source_order_id BIGINT NULL,
    added_by_waiter_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_table_bill_item_bill FOREIGN KEY (bill_id) REFERENCES tbl_table_bill(id) ON DELETE CASCADE,
    CONSTRAINT fk_table_bill_item_order FOREIGN KEY (source_order_id) REFERENCES tbl_menu_order(id) ON DELETE SET NULL,
    CONSTRAINT fk_table_bill_item_waiter FOREIGN KEY (added_by_waiter_id) REFERENCES tbl_menu_waiter(id) ON DELETE SET NULL
);

CREATE INDEX idx_table_bill_item_bill ON tbl_table_bill_item (bill_id);

ALTER TABLE tbl_menu_order
    ADD COLUMN IF NOT EXISTS bill_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS commission_amount NUMERIC(12, 2) NULL;

ALTER TABLE tbl_menu_order
    ADD CONSTRAINT fk_menu_order_bill FOREIGN KEY (bill_id) REFERENCES tbl_table_bill(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_menu_order_bill ON tbl_menu_order (bill_id);

ALTER TABLE tbl_menu_waiter
    ADD COLUMN IF NOT EXISTS commission_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS commission_type VARCHAR(10) NULL,
    ADD COLUMN IF NOT EXISTS commission_value NUMERIC(12, 2) NULL;

CREATE TABLE tbl_waiter_commission_record (
    id BIGSERIAL PRIMARY KEY,
    waiter_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    bill_id BIGINT NULL,
    order_id BIGINT NULL,
    record_type VARCHAR(20) NOT NULL,
    base_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    commission_value_snapshot NUMERIC(12, 2) NOT NULL DEFAULT 0,
    amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    currency VARCHAR(8) NOT NULL DEFAULT 'TRY',
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_waiter_commission_waiter FOREIGN KEY (waiter_id) REFERENCES tbl_menu_waiter(id) ON DELETE CASCADE,
    CONSTRAINT fk_waiter_commission_menu FOREIGN KEY (menu_id) REFERENCES tbl_menu(menu_id) ON DELETE CASCADE,
    CONSTRAINT fk_waiter_commission_bill FOREIGN KEY (bill_id) REFERENCES tbl_table_bill(id) ON DELETE SET NULL,
    CONSTRAINT fk_waiter_commission_order FOREIGN KEY (order_id) REFERENCES tbl_menu_order(id) ON DELETE SET NULL
);

CREATE INDEX idx_waiter_commission_waiter_created ON tbl_waiter_commission_record (waiter_id, created_at);
CREATE INDEX idx_waiter_commission_menu_created ON tbl_waiter_commission_record (menu_id, created_at);
