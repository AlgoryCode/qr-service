ALTER TABLE tbl_bill_payment
    ADD COLUMN IF NOT EXISTS split_share_number INT NULL;

ALTER TABLE tbl_bill_payment
    ADD COLUMN IF NOT EXISTS split_person_count INT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_bill_payment_split_share
    ON tbl_bill_payment (bill_id, split_share_number)
    WHERE split_share_number IS NOT NULL;
