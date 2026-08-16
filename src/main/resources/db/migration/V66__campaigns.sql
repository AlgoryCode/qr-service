CREATE TABLE IF NOT EXISTS tbl_campaign_template (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(40)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    description     TEXT,
    icon            VARCHAR(40),
    config_schema   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    sort_order      INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_campaign_template_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS tbl_campaign (
    id              BIGSERIAL PRIMARY KEY,
    menu_id         BIGINT       NOT NULL,
    business_id     BIGINT       NOT NULL,
    template_code   VARCHAR(40)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    slogan          VARCHAR(255),
    starts_at       TIMESTAMP    NOT NULL,
    ends_at         TIMESTAMP    NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    config          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT fk_campaign_menu
        FOREIGN KEY (menu_id) REFERENCES tbl_menu (menu_id) ON DELETE CASCADE,
    CONSTRAINT chk_campaign_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_campaign_menu_status
    ON tbl_campaign (menu_id, status);

CREATE INDEX IF NOT EXISTS idx_campaign_business_id
    ON tbl_campaign (business_id);

CREATE TABLE IF NOT EXISTS tbl_campaign_progress (
    id              BIGSERIAL PRIMARY KEY,
    campaign_id     BIGINT       NOT NULL,
    customer_id     BIGINT       NOT NULL,
    state           JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uk_campaign_progress_campaign_customer UNIQUE (campaign_id, customer_id),
    CONSTRAINT fk_campaign_progress_campaign
        FOREIGN KEY (campaign_id) REFERENCES tbl_campaign (id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign_progress_customer
        FOREIGN KEY (customer_id) REFERENCES tbl_customer (id) ON DELETE CASCADE,
    CONSTRAINT chk_campaign_progress_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'REWARDED'))
);

CREATE INDEX IF NOT EXISTS idx_campaign_progress_customer
    ON tbl_campaign_progress (customer_id);

CREATE TABLE IF NOT EXISTS tbl_campaign_reward (
    id              BIGSERIAL PRIMARY KEY,
    campaign_id     BIGINT       NOT NULL,
    customer_id     BIGINT       NOT NULL,
    progress_id     BIGINT,
    order_id        BIGINT,
    reward_type     VARCHAR(30)  NOT NULL,
    reward_payload  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    issued_at       TIMESTAMP    NOT NULL,
    redeemed_at     TIMESTAMP,
    redeemed_order_id BIGINT,
    CONSTRAINT fk_campaign_reward_campaign
        FOREIGN KEY (campaign_id) REFERENCES tbl_campaign (id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign_reward_customer
        FOREIGN KEY (customer_id) REFERENCES tbl_customer (id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign_reward_progress
        FOREIGN KEY (progress_id) REFERENCES tbl_campaign_progress (id) ON DELETE SET NULL,
    CONSTRAINT chk_campaign_reward_status
        CHECK (status IN ('AVAILABLE', 'REDEEMED', 'EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_campaign_reward_customer
    ON tbl_campaign_reward (customer_id, status);

CREATE TABLE IF NOT EXISTS tbl_campaign_reward_claim (
    id              BIGSERIAL PRIMARY KEY,
    token           VARCHAR(64)  NOT NULL,
    campaign_id     BIGINT       NOT NULL,
    order_id        BIGINT       NOT NULL,
    menu_id         BIGINT       NOT NULL,
    reward_id       BIGINT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    customer_id     BIGINT,
    expires_at      TIMESTAMP    NOT NULL,
    claimed_at      TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uk_campaign_reward_claim_token UNIQUE (token),
    CONSTRAINT fk_campaign_reward_claim_campaign
        FOREIGN KEY (campaign_id) REFERENCES tbl_campaign (id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign_reward_claim_order
        FOREIGN KEY (order_id) REFERENCES tbl_menu_order (id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign_reward_claim_customer
        FOREIGN KEY (customer_id) REFERENCES tbl_customer (id) ON DELETE SET NULL,
    CONSTRAINT chk_campaign_reward_claim_status
        CHECK (status IN ('PENDING', 'CLAIMED', 'EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_campaign_reward_claim_order
    ON tbl_campaign_reward_claim (order_id);

CREATE TABLE IF NOT EXISTS tbl_campaign_event_log (
    id              BIGSERIAL PRIMARY KEY,
    campaign_id     BIGINT       NOT NULL,
    order_id        BIGINT       NOT NULL,
    customer_id     BIGINT,
    event_type      VARCHAR(40)  NOT NULL,
    payload         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uk_campaign_event_log_campaign_order UNIQUE (campaign_id, order_id),
    CONSTRAINT fk_campaign_event_log_campaign
        FOREIGN KEY (campaign_id) REFERENCES tbl_campaign (id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign_event_log_order
        FOREIGN KEY (order_id) REFERENCES tbl_menu_order (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tbl_campaign_manual_grant (
    id              BIGSERIAL PRIMARY KEY,
    campaign_id     BIGINT       NOT NULL,
    menu_id         BIGINT       NOT NULL,
    waiter_id       BIGINT       NOT NULL,
    customer_id     BIGINT       NOT NULL,
    customer_email  VARCHAR(255) NOT NULL,
    action          VARCHAR(30)  NOT NULL,
    quantity        INT,
    order_id        BIGINT,
    note            TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    CONSTRAINT fk_campaign_manual_grant_campaign
        FOREIGN KEY (campaign_id) REFERENCES tbl_campaign (id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign_manual_grant_waiter
        FOREIGN KEY (waiter_id) REFERENCES tbl_menu_waiter (id) ON DELETE CASCADE,
    CONSTRAINT fk_campaign_manual_grant_customer
        FOREIGN KEY (customer_id) REFERENCES tbl_customer (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_campaign_manual_grant_menu
    ON tbl_campaign_manual_grant (menu_id, created_at DESC);

INSERT INTO tbl_campaign_template (code, name, description, icon, config_schema, sort_order)
VALUES
(
    'STAMP_CARD',
    'Damga Kartı',
    'Belirli ürünlerden N adet alana ödül',
    'stamp',
    '{
      "type": "object",
      "required": ["targetProductIds", "requiredQuantity", "reward"],
      "properties": {
        "targetProductIds": { "type": "array", "items": { "type": "integer" } },
        "requiredQuantity": { "type": "integer", "minimum": 1 },
        "reward": { "type": "object" },
        "resetAfterReward": { "type": "boolean" }
      }
    }'::jsonb,
    1
),
(
    'SPEND_THRESHOLD',
    'Harcama Eşiği',
    'Belirli periyotta X TL harcayana ödül',
    'spend',
    '{
      "type": "object",
      "required": ["thresholdAmount", "period", "reward"],
      "properties": {
        "thresholdAmount": { "type": "number", "minimum": 1 },
        "period": { "type": "string", "enum": ["WEEKLY", "MONTHLY"] },
        "scope": { "type": "object" },
        "reward": { "type": "object" }
      }
    }'::jsonb,
    2
)
ON CONFLICT (code) DO NOTHING;
