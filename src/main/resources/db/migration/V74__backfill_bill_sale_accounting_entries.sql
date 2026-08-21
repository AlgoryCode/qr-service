INSERT INTO tbl_user_accounting_entry (
    user_id,
    entry_type,
    title,
    amount,
    currency,
    occurred_at,
    note,
    menu_id,
    source_type,
    source_bill_id,
    source_order_id,
    created_by_waiter_id,
    order_amount,
    tip_amount,
    created_at,
    updated_at
)
SELECT
    m.user_id,
    'GELIR',
    'Adisyon - ' || COALESCE(t.name, 'Masa'),
    b.total_amount,
    COALESCE(NULLIF(b.currency, ''), 'TRY'),
    COALESCE(b.closed_at, b.updated_at, NOW()),
    NULL,
    b.menu_id,
    'BILL_SALE',
    b.id,
    CASE
        WHEN (
            SELECT COUNT(DISTINCT i.source_order_id)
            FROM tbl_table_bill_item i
            WHERE i.bill_id = b.id
              AND i.source_order_id IS NOT NULL
        ) = 1
        THEN (
            SELECT MIN(i.source_order_id)
            FROM tbl_table_bill_item i
            WHERE i.bill_id = b.id
              AND i.source_order_id IS NOT NULL
        )
        ELSE NULL
    END,
    b.closed_by_waiter_id,
    b.total_amount,
    NULL,
    NOW(),
    NOW()
FROM tbl_table_bill b
JOIN tbl_menu m ON m.menu_id = b.menu_id
LEFT JOIN tbl_restaurant_table t ON t.id = b.table_id
WHERE b.status = 'CLOSED'
  AND b.total_amount IS NOT NULL
  AND b.total_amount > 0
  AND m.user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM tbl_user_accounting_entry e
      WHERE e.source_type = 'BILL_SALE'
        AND e.source_bill_id = b.id
  );
