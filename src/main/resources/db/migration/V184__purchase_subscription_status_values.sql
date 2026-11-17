-- The generated check constraint predates GRACE_PERIOD and SUSPENDED, so a failed renewal
-- (which moves the purchase into grace) could not be saved.
ALTER TABLE tbl_purchase DROP CONSTRAINT IF EXISTS tbl_purchase_subscription_status_check;
ALTER TABLE tbl_purchase ADD CONSTRAINT tbl_purchase_subscription_status_check
    CHECK (subscription_status IN ('PENDING', 'ACTIVE', 'PAST_DUE', 'GRACE_PERIOD', 'SUSPENDED', 'CANCELLED', 'EXPIRED'));
