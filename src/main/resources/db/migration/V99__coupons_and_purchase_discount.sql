ALTER TABLE tbl_purchase
    ADD COLUMN IF NOT EXISTS coupon_id BIGINT,
    ADD COLUMN IF NOT EXISTS list_price NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(12, 2);

CREATE TABLE tbl_coupon (
    id                    BIGSERIAL PRIMARY KEY,
    code                  VARCHAR(32)    NOT NULL,
    discount_type         VARCHAR(16)    NOT NULL,
    discount_value        NUMERIC(12, 2) NOT NULL,
    expires_at            TIMESTAMP      NOT NULL,
    valid_from            TIMESTAMP,
    status                VARCHAR(16)    NOT NULL,
    reserved_purchase_id  BIGINT,
    used_purchase_id      BIGINT,
    used_by_user_id       BIGINT,
    used_at               TIMESTAMP,
    created_by_admin_id   BIGINT,
    created_at            TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_coupon_code UNIQUE (code),
    CONSTRAINT chk_coupon_discount_type CHECK (discount_type IN ('PERCENT', 'AMOUNT')),
    CONSTRAINT chk_coupon_status CHECK (status IN ('UNUSED', 'RESERVED', 'USED', 'REVOKED'))
);

CREATE INDEX idx_coupon_status ON tbl_coupon (status);
CREATE INDEX idx_coupon_expires_at ON tbl_coupon (expires_at);

CREATE TABLE tbl_coupon_log (
    id              BIGSERIAL PRIMARY KEY,
    coupon_id       BIGINT       NOT NULL REFERENCES tbl_coupon (id),
    user_id         BIGINT,
    purchase_id     BIGINT,
    action          VARCHAR(16)  NOT NULL,
    list_price      NUMERIC(12, 2),
    discount_amount NUMERIC(12, 2),
    payable         NUMERIC(12, 2),
    message         VARCHAR(1000) NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_coupon_log_action CHECK (action IN ('RESERVED', 'USED', 'RELEASED', 'REJECTED', 'REVOKED'))
);

CREATE INDEX idx_coupon_log_coupon_id ON tbl_coupon_log (coupon_id);
CREATE INDEX idx_coupon_log_purchase_id ON tbl_coupon_log (purchase_id);

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
            'PURCHASE_DEACTIVATED',
            'PURCHASE_REACTIVATED',
            'PURCHASE_EXTENDED',
            'COUPON_APPLIED',
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
            'TRIAL_STARTED',
            'TRIAL_EXTENDED',
            'TRIAL_REACTIVATED',
            'TRIAL_ENDED'
        ]::text[]));
