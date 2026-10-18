ALTER TABLE tbl_table_bill DROP CONSTRAINT IF EXISTS tbl_table_bill_status_check;

ALTER TABLE tbl_table_bill
    ADD CONSTRAINT tbl_table_bill_status_check
    CHECK (status::text = ANY (ARRAY[
        'EMPTY',
        'OPEN',
        'CLOSED',
        'CANCELLED'
    ]::text[]));
