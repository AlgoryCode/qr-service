ALTER TABLE tbl_purchase DROP CONSTRAINT IF EXISTS tbl_purchase_payment_mode_check;

ALTER TABLE tbl_purchase
    ADD CONSTRAINT tbl_purchase_payment_mode_check
        CHECK (payment_mode IS NULL OR payment_mode IN ('DIRECT', 'THREE_DS', 'CHECKOUT_FORM'));
