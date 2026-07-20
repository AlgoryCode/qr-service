CREATE TABLE tbl_plan_change (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT       NOT NULL,
    from_purchase_id        BIGINT       NOT NULL,
    from_package_id         BIGINT       NOT NULL,
    to_package_id           BIGINT       NOT NULL,
    direction               VARCHAR(16)  NOT NULL,
    timing                  VARCHAR(16)  NOT NULL,
    status                  VARCHAR(32)  NOT NULL,
    charge_amount           NUMERIC(12, 2) NOT NULL,
    currency                VARCHAR(3)   NOT NULL DEFAULT 'TRY',
    payment_method_id       BIGINT,
    payment_conversation_id VARCHAR(128),
    payment_id              VARCHAR(64),
    effective_at            TIMESTAMP    NOT NULL,
    resulting_purchase_id   BIGINT,
    warning_ack             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP,
    completed_at            TIMESTAMP
);

CREATE INDEX idx_plan_change_user_id ON tbl_plan_change (user_id);
CREATE INDEX idx_plan_change_status_effective ON tbl_plan_change (status, effective_at);

CREATE UNIQUE INDEX uq_plan_change_user_scheduled
    ON tbl_plan_change (user_id)
    WHERE status = 'SCHEDULED';
