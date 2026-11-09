-- Sipariş akışları: işletme hazır adımlardan (garson onayı, mutfak, servis, kurye...) kanal bazında akış kurar
-- ve şubelere atar. Sipariş statüleri sabittir; akış yalnızca hangi adımın kimde olduğunu belirler.

CREATE TABLE IF NOT EXISTS tbl_order_flow (
    id            BIGSERIAL PRIMARY KEY,
    merchant_id   BIGINT       NOT NULL,
    name          VARCHAR(120) NOT NULL,
    template_code VARCHAR(40),
    version       INTEGER      NOT NULL DEFAULT 1,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_order_flow_merchant ON tbl_order_flow (merchant_id) WHERE is_deleted = FALSE;

CREATE TABLE IF NOT EXISTS tbl_order_flow_step (
    id         BIGSERIAL PRIMARY KEY,
    flow_id    BIGINT      NOT NULL REFERENCES tbl_order_flow (id) ON DELETE CASCADE,
    channel    VARCHAR(20) NOT NULL,
    step       VARCHAR(40) NOT NULL,
    sort_order INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT uq_order_flow_step UNIQUE (flow_id, channel, step)
);

CREATE TABLE IF NOT EXISTS tbl_order_flow_request (
    id          BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT        NOT NULL,
    flow_id     BIGINT,
    title       VARCHAR(160)  NOT NULL,
    description VARCHAR(2000),
    status      VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    is_deleted  BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_order_flow_request_merchant ON tbl_order_flow_request (merchant_id);

-- Boş bırakılan şube eski davranışla (kitchen_enabled / courier_enabled) çalışmaya devam eder.
ALTER TABLE tbl_branch ADD COLUMN IF NOT EXISTS order_flow_id BIGINT;
