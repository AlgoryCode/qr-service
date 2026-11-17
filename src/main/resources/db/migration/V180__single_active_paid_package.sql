-- A second package paid while one is active is cancelled with this reason and refunded.
ALTER TABLE tbl_purchase DROP CONSTRAINT IF EXISTS tbl_purchase_cancellation_reason_check;
ALTER TABLE tbl_purchase ADD CONSTRAINT tbl_purchase_cancellation_reason_check
    CHECK (cancellation_reason IN ('PAYMENT_TIMEOUT', 'MANUAL', 'DUPLICATE_PACKAGE'));

-- A merchant holds at most one active paid package. Checked at commit, so a plan change can
-- activate the new package and supersede the old one inside the same transaction. If existing
-- rows already break the rule the constraint is skipped with a warning instead of failing the
-- deployment; clean the rows up and add it by hand.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM tbl_purchase
        WHERE status = 'ACTIVE' AND purchase_type = 'PAID' AND package_id IS NOT NULL AND merchant_id IS NOT NULL
        GROUP BY merchant_id
        HAVING count(*) > 1
    ) THEN
        RAISE WARNING 'tbl_purchase_one_active_paid_package not added: merchants with several active paid packages exist';
    ELSIF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tbl_purchase_one_active_paid_package') THEN
        ALTER TABLE tbl_purchase
            ADD CONSTRAINT tbl_purchase_one_active_paid_package
            EXCLUDE USING btree (merchant_id WITH =)
            WHERE (status = 'ACTIVE' AND purchase_type = 'PAID' AND package_id IS NOT NULL)
            DEFERRABLE INITIALLY DEFERRED;
    END IF;
END $$;
