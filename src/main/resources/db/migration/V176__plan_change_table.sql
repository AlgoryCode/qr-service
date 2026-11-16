-- tbl_plan_change was only ever created by Hibernate ddl-auto; create it explicitly so a fresh
-- database validates, and bring older copies (keyed by user_id) in line with the entity.
CREATE TABLE IF NOT EXISTS tbl_plan_change (
    id                      BIGSERIAL PRIMARY KEY,
    merchant_id             BIGINT         NOT NULL,
    from_purchase_id        BIGINT         NOT NULL,
    from_package_id         BIGINT         NOT NULL,
    to_package_id           BIGINT         NOT NULL,
    direction               VARCHAR(16)    NOT NULL,
    timing                  VARCHAR(16)    NOT NULL,
    status                  VARCHAR(32)    NOT NULL,
    charge_amount           NUMERIC(12, 2) NOT NULL,
    refund_amount           NUMERIC(12, 2) NOT NULL DEFAULT 0,
    currency                VARCHAR(3)     NOT NULL DEFAULT 'TRY',
    payment_method_id       BIGINT,
    payment_conversation_id VARCHAR(128),
    payment_id              VARCHAR(64),
    effective_at            TIMESTAMP      NOT NULL,
    resulting_purchase_id   BIGINT,
    warning_ack             BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP,
    completed_at            TIMESTAMP
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'tbl_plan_change' AND column_name = 'user_id') THEN
        ALTER TABLE tbl_plan_change ALTER COLUMN user_id DROP NOT NULL;
        ALTER TABLE tbl_plan_change ADD COLUMN IF NOT EXISTS merchant_id BIGINT;
        UPDATE tbl_plan_change SET merchant_id = user_id WHERE merchant_id IS NULL;
        ALTER TABLE tbl_plan_change ALTER COLUMN merchant_id SET NOT NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_plan_change_merchant_id ON tbl_plan_change (merchant_id);
CREATE INDEX IF NOT EXISTS idx_plan_change_status_effective ON tbl_plan_change (status, effective_at);
