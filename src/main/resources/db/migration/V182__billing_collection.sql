-- Collection follow-up for merchants who stopped paying: one case per unpaid debt, with every
-- status change (reminder, formal notice, legal follow-up, paid...) kept as an event.
CREATE TABLE IF NOT EXISTS billing_collection_case (
    id              BIGSERIAL PRIMARY KEY,
    merchant_id     BIGINT         NOT NULL,
    purchase_id     BIGINT,
    status          VARCHAR(32)    NOT NULL,
    amount          NUMERIC(12, 2),
    currency        VARCHAR(3),
    due_since       TIMESTAMP,
    opened_at       TIMESTAMP      NOT NULL,
    status_at       TIMESTAMP      NOT NULL,
    closed_at       TIMESTAMP,
    created_at      TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP
);

-- A merchant has at most one open case.
CREATE UNIQUE INDEX IF NOT EXISTS uk_billing_collection_case_open
    ON billing_collection_case (merchant_id)
    WHERE closed_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_billing_collection_case_status ON billing_collection_case (status);

CREATE TABLE IF NOT EXISTS billing_collection_event (
    id              BIGSERIAL PRIMARY KEY,
    case_id         BIGINT         NOT NULL REFERENCES billing_collection_case (id),
    from_status     VARCHAR(32),
    to_status       VARCHAR(32)    NOT NULL,
    note            VARCHAR(2000),
    actor           VARCHAR(255)   NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_billing_collection_event_case ON billing_collection_event (case_id, created_at);
