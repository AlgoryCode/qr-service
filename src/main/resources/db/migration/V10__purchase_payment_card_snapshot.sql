ALTER TABLE tbl_purchase
    ADD COLUMN IF NOT EXISTS payment_method_id BIGINT;

ALTER TABLE tbl_purchase
    ADD COLUMN IF NOT EXISTS card_brand VARCHAR(64);

ALTER TABLE tbl_purchase
    ADD COLUMN IF NOT EXISTS card_last_four VARCHAR(8);
