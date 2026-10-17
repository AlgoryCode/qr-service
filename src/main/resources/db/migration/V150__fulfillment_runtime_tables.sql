-- Runtime fulfillment state (from qr-fulfillment-service). Catalog lives in tbl_plan_package / tbl_product.

CREATE TABLE IF NOT EXISTS package_fulfillment (
    id                  BIGSERIAL PRIMARY KEY,
    event_id            VARCHAR(128)  NOT NULL,
    event_type          VARCHAR(64)   NOT NULL,
    purchase_id         BIGINT        NOT NULL,
    user_id             BIGINT        NOT NULL,
    package_id          BIGINT,
    package_code        VARCHAR(128),
    conversation_id     VARCHAR(256),
    payment_id          VARCHAR(128),
    subscription_id     VARCHAR(128),
    billing_cycle_number INTEGER,
    period_start        DATE,
    period_end          DATE,
    amount              NUMERIC(19, 2),
    currency            VARCHAR(8),
    status              VARCHAR(32)   NOT NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_package_fulfillment_event_id UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_package_fulfillment_user_id ON package_fulfillment (user_id);
CREATE INDEX IF NOT EXISTS idx_package_fulfillment_purchase_id ON package_fulfillment (purchase_id);

CREATE TABLE IF NOT EXISTS active_package (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT        NOT NULL,
    fulfillment_id      BIGINT        NOT NULL,
    purchase_id         BIGINT        NOT NULL,
    package_id          BIGINT,
    package_code        VARCHAR(128),
    period_start        DATE,
    period_end          DATE,
    status              VARCHAR(32)   NOT NULL,
    activated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_active_package_user_id UNIQUE (user_id),
    CONSTRAINT fk_active_package_fulfillment
        FOREIGN KEY (fulfillment_id) REFERENCES package_fulfillment (id)
);

CREATE INDEX IF NOT EXISTS idx_active_package_package_code ON active_package (package_code);

CREATE TABLE IF NOT EXISTS user_entitlement (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    fulfillment_id  BIGINT       NOT NULL,
    purchase_id     BIGINT,
    product_id      BIGINT,
    product_type_id VARCHAR(32),
    feature_code    VARCHAR(64)  NOT NULL,
    scope_code      VARCHAR(64),
    quantity        INTEGER      NOT NULL DEFAULT 0,
    unlimited       BOOLEAN      NOT NULL DEFAULT FALSE,
    used_quantity   INTEGER      NOT NULL DEFAULT 0,
    source          VARCHAR(32)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    starts_at       TIMESTAMP,
    expires_at      TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_entitlement_fulfillment
        FOREIGN KEY (fulfillment_id) REFERENCES package_fulfillment (id)
);

CREATE INDEX IF NOT EXISTS idx_user_entitlement_user_id ON user_entitlement (user_id);
CREATE INDEX IF NOT EXISTS idx_user_entitlement_user_feature ON user_entitlement (user_id, feature_code);
CREATE INDEX IF NOT EXISTS idx_user_entitlement_user_scope ON user_entitlement (user_id, scope_code);
CREATE INDEX IF NOT EXISTS idx_user_entitlement_fulfillment ON user_entitlement (fulfillment_id);
