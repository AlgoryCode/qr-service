ALTER TABLE tbl_purchase DROP CONSTRAINT IF EXISTS tbl_purchase_purchase_type_check;

ALTER TABLE tbl_purchase
    ADD CONSTRAINT tbl_purchase_purchase_type_check
        CHECK (purchase_type IN ('FREE', 'TRIAL', 'PAID', 'SYSTEM_GRANT', 'ADD_ON'));
