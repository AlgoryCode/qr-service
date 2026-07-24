ALTER TABLE tbl_purchase
    ADD COLUMN cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE tbl_purchase
    ADD COLUMN current_period_paid_at TIMESTAMP;

ALTER TABLE tbl_purchase
    ADD COLUMN refunded_at TIMESTAMP;

ALTER TABLE tbl_purchase_log DROP CONSTRAINT IF EXISTS tbl_purchase_log_action_check;

ALTER TABLE tbl_purchase_log
    ADD CONSTRAINT tbl_purchase_log_action_check
        CHECK (action IN (
            'PURCHASE_STARTED',
            'PURCHASE_PAYMENT_PENDING',
            'PURCHASE_COMPLETED',
            'PURCHASE_PAYMENT_FAILED',
            'PURCHASE_EXPIRED',
            'PURCHASE_CANCELLED',
            'PURCHASE_CANCEL_AT_PERIOD_END',
            'PURCHASE_RENEWAL_RESUMED',
            'PURCHASE_REFUND_STARTED',
            'PURCHASE_REFUND_COMPLETED',
            'ENTITLEMENT_GRANTED',
            'ENTITLEMENT_CONSUMED',
            'PLAN_CHANGE_REQUESTED',
            'PLAN_CHANGE_SCHEDULED',
            'PLAN_CHANGE_PAYMENT_STARTED',
            'PLAN_CHANGE_PAYMENT_FAILED',
            'PLAN_CHANGE_REFUND_STARTED',
            'PLAN_CHANGE_REFUND_COMPLETED',
            'PLAN_CHANGE_COMPLETED',
            'PLAN_CHANGE_CANCELLED',
            'PLAN_CHANGE_ENTITLEMENTS_RESET'
        ));
