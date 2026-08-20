UPDATE tbl_table_bill b
SET tip_amount = src.tip_total
FROM (
    SELECT source_bill_id AS bill_id, SUM(amount) AS tip_total
    FROM tbl_user_accounting_entry
    WHERE source_type = 'BILL_TIP'
      AND source_bill_id IS NOT NULL
    GROUP BY source_bill_id
) src
WHERE b.id = src.bill_id
  AND (b.tip_amount IS NULL OR b.tip_amount = 0);

UPDATE tbl_table_bill b
SET tip_amount = e.tip_amount
FROM tbl_user_accounting_entry e
WHERE e.source_type = 'BILL_SALE'
  AND e.source_bill_id = b.id
  AND e.tip_amount IS NOT NULL
  AND e.tip_amount > 0
  AND (b.tip_amount IS NULL OR b.tip_amount = 0);
