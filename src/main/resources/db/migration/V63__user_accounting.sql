CREATE TABLE IF NOT EXISTS tbl_user_accounting_entry (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT         NOT NULL,
    entry_type           VARCHAR(10)    NOT NULL,
    title                VARCHAR(200)   NOT NULL,
    amount               NUMERIC(12, 2) NOT NULL,
    currency             VARCHAR(8)     NOT NULL DEFAULT 'TRY',
    occurred_at          TIMESTAMP      NOT NULL,
    note                 VARCHAR(500),
    menu_id              BIGINT,
    source_type          VARCHAR(20)    NOT NULL DEFAULT 'MANUAL',
    source_bill_id       BIGINT,
    created_by_waiter_id BIGINT,
    created_at           TIMESTAMP      NOT NULL,
    updated_at           TIMESTAMP      NOT NULL,
    CONSTRAINT chk_user_accounting_entry_type
        CHECK (entry_type IN ('GELIR', 'GIDER', 'BORC')),
    CONSTRAINT chk_user_accounting_entry_amount
        CHECK (amount > 0),
    CONSTRAINT chk_user_accounting_entry_source_type
        CHECK (source_type IN ('MANUAL', 'BILL_SALE', 'BILL_TIP')),
    CONSTRAINT fk_user_accounting_entry_user
        FOREIGN KEY (user_id) REFERENCES tbl_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_accounting_entry_menu
        FOREIGN KEY (menu_id) REFERENCES tbl_menu (menu_id) ON DELETE SET NULL,
    CONSTRAINT fk_user_accounting_entry_bill
        FOREIGN KEY (source_bill_id) REFERENCES tbl_table_bill (id) ON DELETE SET NULL,
    CONSTRAINT fk_user_accounting_entry_waiter
        FOREIGN KEY (created_by_waiter_id) REFERENCES tbl_menu_waiter (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_user_accounting_entry_user_occurred
    ON tbl_user_accounting_entry (user_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_accounting_entry_user_type_occurred
    ON tbl_user_accounting_entry (user_id, entry_type, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_accounting_entry_menu
    ON tbl_user_accounting_entry (menu_id);

ALTER TABLE tbl_table_bill
    ADD COLUMN IF NOT EXISTS tip_amount NUMERIC(12, 2);
