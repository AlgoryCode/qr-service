CREATE TABLE tbl_fulfillment (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    purchase_id BIGINT       NOT NULL,
    payment_id  VARCHAR(128),
    package_id  BIGINT       NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    starts_at   TIMESTAMP,
    expires_at  TIMESTAMP,
    migration_key VARCHAR(128),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_fulfillment_purchase_id UNIQUE (purchase_id)
);

CREATE INDEX idx_fulfillment_user_id    ON tbl_fulfillment (user_id);
CREATE INDEX idx_fulfillment_package_id ON tbl_fulfillment (package_id);
CREATE INDEX idx_fulfillment_status     ON tbl_fulfillment (status);

CREATE TABLE tbl_fulfillment_detail (
    id              BIGSERIAL    PRIMARY KEY,
    fulfillment_id  BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    product_id      BIGINT,
    product_type_id VARCHAR(32),
    feature_code    VARCHAR(64)  NOT NULL,
    scope_code      VARCHAR(64),
    quantity        INTEGER      NOT NULL DEFAULT 0,
    unlimited       BOOLEAN      NOT NULL DEFAULT FALSE,
    used_quantity   INTEGER      NOT NULL DEFAULT 0,
    source          VARCHAR(32)  NOT NULL,
    starts_at       TIMESTAMP,
    expires_at      TIMESTAMP,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fd_fulfillment_id ON tbl_fulfillment_detail (fulfillment_id);
CREATE INDEX idx_fd_user_scope     ON tbl_fulfillment_detail (user_id, scope_code);
CREATE INDEX idx_fd_user_feature   ON tbl_fulfillment_detail (user_id, feature_code);

CREATE TABLE tbl_fulfillment_usage_log (
    id             BIGSERIAL   PRIMARY KEY,
    detail_id      BIGINT      NOT NULL,
    user_id        BIGINT      NOT NULL,
    action         VARCHAR(16) NOT NULL,
    amount         INTEGER     NOT NULL,
    reference_type VARCHAR(16),
    reference_id   BIGINT,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ful_detail_id ON tbl_fulfillment_usage_log (detail_id);
CREATE INDEX idx_ful_user_id   ON tbl_fulfillment_usage_log (user_id);
