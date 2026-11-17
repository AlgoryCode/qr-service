-- Payment reminders sent to members (by the daily job or an admin), one per purchase, kind and period.
CREATE TABLE IF NOT EXISTS billing_reminder (
    id          BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT       NOT NULL,
    purchase_id BIGINT,
    kind        VARCHAR(32)  NOT NULL,
    period_end  TIMESTAMP,
    email       VARCHAR(255),
    sent_by     VARCHAR(128) NOT NULL,
    sent_at     TIMESTAMP    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_billing_reminder_merchant ON billing_reminder (merchant_id, sent_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_billing_reminder_auto
    ON billing_reminder (purchase_id, kind, period_end) WHERE sent_by = 'system';

-- Admin-editable e-mail texts; a missing row means the built-in default.
CREATE TABLE IF NOT EXISTS email_template (
    template_key VARCHAR(64)  PRIMARY KEY,
    subject      VARCHAR(255) NOT NULL,
    body         TEXT         NOT NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_by   VARCHAR(128),
    updated_at   TIMESTAMP    NOT NULL
);
