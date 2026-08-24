ALTER TABLE tbl_purchase DROP CONSTRAINT IF EXISTS tbl_purchase_purchase_type_check;

ALTER TABLE tbl_purchase
    ADD CONSTRAINT tbl_purchase_purchase_type_check
        CHECK (purchase_type IN ('FREE', 'TRIAL', 'PAID', 'SYSTEM_GRANT', 'ADD_ON'));

UPDATE tbl_purchase
SET billing_period = 'MONTHLY',
    billing_interval_months = COALESCE(billing_interval_months, 1)
WHERE billing_period IS NULL;

ALTER TABLE tbl_purchase
    ALTER COLUMN billing_period SET NOT NULL;
