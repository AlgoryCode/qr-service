ALTER TABLE tbl_user_accounting_entry
    ADD COLUMN IF NOT EXISTS source_order_id BIGINT;

ALTER TABLE tbl_user_accounting_entry
    DROP CONSTRAINT IF EXISTS chk_user_accounting_entry_source_type;

ALTER TABLE tbl_user_accounting_entry
    ADD CONSTRAINT chk_user_accounting_entry_source_type
        CHECK (source_type IN ('MANUAL', 'BILL_SALE', 'BILL_TIP', 'ORDER_SALE'));

ALTER TABLE tbl_user_accounting_entry
    DROP CONSTRAINT IF EXISTS fk_user_accounting_entry_order;

ALTER TABLE tbl_user_accounting_entry
    ADD CONSTRAINT fk_user_accounting_entry_order
        FOREIGN KEY (source_order_id) REFERENCES tbl_menu_order (id) ON DELETE SET NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_accounting_entry_order_sale
    ON tbl_user_accounting_entry (source_order_id)
    WHERE source_type = 'ORDER_SALE' AND source_order_id IS NOT NULL;

INSERT INTO tbl_user_accounting_entry (
    user_id,
    entry_type,
    title,
    amount,
    currency,
    occurred_at,
    menu_id,
    source_type,
    source_order_id,
    created_at,
    updated_at
)
SELECT
    m.user_id,
    'GELIR',
    'Sipariş #' || o.id,
    o.total_amount,
    COALESCE(NULLIF(TRIM(o.currency), ''), 'TRY'),
    COALESCE(o.confirmed_at, o.submitted_at, o.created_at),
    o.menu_id,
    'ORDER_SALE',
    o.id,
    NOW(),
    NOW()
FROM tbl_menu_order o
JOIN tbl_menu m ON m.menu_id = o.menu_id
WHERE o.status = 'CONFIRMED'
  AND o.total_amount > 0
  AND NOT EXISTS (
      SELECT 1
      FROM tbl_user_accounting_entry e
      WHERE e.source_type = 'ORDER_SALE'
        AND e.source_order_id = o.id
  );
