-- Phase B–G restaurant analytics schema

ALTER TABLE tbl_menu_order
    ADD COLUMN IF NOT EXISTS created_by_waiter_id BIGINT,
    ADD COLUMN IF NOT EXISTS cancelled_by_waiter_id BIGINT,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(64),
    ADD COLUMN IF NOT EXISTS cancel_reason_note TEXT,
    ADD COLUMN IF NOT EXISTS order_source VARCHAR(16),
    ADD COLUMN IF NOT EXISTS analytics_session_id UUID,
    ADD COLUMN IF NOT EXISTS prepared_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS ready_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS served_at TIMESTAMP;

UPDATE tbl_menu_order
SET order_source = CASE WHEN waiter_id IS NULL THEN 'QR' ELSE 'WAITER' END
WHERE order_source IS NULL;

UPDATE tbl_menu_order
SET created_by_waiter_id = waiter_id
WHERE created_by_waiter_id IS NULL AND waiter_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_menu_order_analytics_session ON tbl_menu_order (analytics_session_id);
CREATE INDEX IF NOT EXISTS idx_menu_order_order_source ON tbl_menu_order (menu_id, order_source);

ALTER TABLE tbl_restaurant_table
    ADD COLUMN IF NOT EXISTS capacity INTEGER;

ALTER TABLE tbl_table_bill
    ADD COLUMN IF NOT EXISTS cover_count INTEGER;

CREATE TABLE IF NOT EXISTS tbl_order_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    menu_id         BIGINT NOT NULL,
    order_id        BIGINT NOT NULL,
    bill_id         BIGINT,
    waiter_id       BIGINT,
    action          VARCHAR(32) NOT NULL,
    detail_json     TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_order_audit_order ON tbl_order_audit_log (order_id, created_at);
CREATE INDEX IF NOT EXISTS idx_order_audit_menu ON tbl_order_audit_log (menu_id, created_at);

CREATE TABLE IF NOT EXISTS tbl_bill_adjustment (
    id              BIGSERIAL PRIMARY KEY,
    menu_id         BIGINT NOT NULL,
    bill_id         BIGINT NOT NULL,
    order_id        BIGINT,
    waiter_id       BIGINT,
    adjustment_type VARCHAR(16) NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL,
    reason          VARCHAR(64),
    reason_note     TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bill_adjustment_bill ON tbl_bill_adjustment (bill_id, created_at);
CREATE INDEX IF NOT EXISTS idx_bill_adjustment_menu ON tbl_bill_adjustment (menu_id, created_at);

CREATE TABLE IF NOT EXISTS tbl_work_shift (
    id                  BIGSERIAL PRIMARY KEY,
    branch_id           BIGINT NOT NULL,
    menu_id             BIGINT,
    opened_by_waiter_id BIGINT NOT NULL,
    closed_by_waiter_id BIGINT,
    opened_at           TIMESTAMP NOT NULL,
    closed_at           TIMESTAMP,
    opening_float       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    closing_cash        NUMERIC(12, 2),
    note                TEXT,
    status              VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_work_shift_branch_status ON tbl_work_shift (branch_id, status);
CREATE INDEX IF NOT EXISTS idx_work_shift_opened_at ON tbl_work_shift (branch_id, opened_at);

CREATE TABLE IF NOT EXISTS tbl_work_shift_waiter (
    shift_id  BIGINT NOT NULL REFERENCES tbl_work_shift (id) ON DELETE CASCADE,
    waiter_id BIGINT NOT NULL,
    PRIMARY KEY (shift_id, waiter_id)
);
