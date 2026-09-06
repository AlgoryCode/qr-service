ALTER TABLE tbl_purchase_log
    DROP CONSTRAINT IF EXISTS tbl_purchase_log_action_check;

ALTER TABLE tbl_purchase_log
    ADD CONSTRAINT tbl_purchase_log_action_check
        CHECK (action::text = ANY (ARRAY[
            'PURCHASE_STARTED',
            'PURCHASE_PAYMENT_PENDING',
            'PURCHASE_COMPLETED',
            'PURCHASE_PAYMENT_FAILED',
            'PURCHASE_EXPIRED',
            'PURCHASE_CANCELLED',
            'PURCHASE_CANCEL_AT_PERIOD_END',
            'PURCHASE_RENEWAL_RESUMED',
            'PURCHASE_DEBT_PAYMENT_STARTED',
            'PURCHASE_REFUND_STARTED',
            'PURCHASE_REFUND_COMPLETED',
            'ENTITLEMENT_GRANTED',
            'ENTITLEMENT_CONSUMED',
            'ENTITLEMENT_RESTORED',
            'PLAN_CHANGE_REQUESTED',
            'PLAN_CHANGE_SCHEDULED',
            'PLAN_CHANGE_PAYMENT_STARTED',
            'PLAN_CHANGE_PAYMENT_FAILED',
            'PLAN_CHANGE_REFUND_STARTED',
            'PLAN_CHANGE_REFUND_COMPLETED',
            'PLAN_CHANGE_COMPLETED',
            'PLAN_CHANGE_CANCELLED',
            'PLAN_CHANGE_ENTITLEMENTS_RESET',
            'TRIAL_EXTENDED'
        ]::text[]));
