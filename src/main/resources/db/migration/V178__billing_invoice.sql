-- One row per collected payment or refund; issued to the e-Arşiv provider by InvoiceIssueJob.
CREATE TABLE IF NOT EXISTS billing_invoice (
    id                BIGSERIAL PRIMARY KEY,
    merchant_id       BIGINT         NOT NULL,
    purchase_id       BIGINT         NOT NULL,
    payment_event_id  VARCHAR(128)   NOT NULL,
    kind              VARCHAR(16)    NOT NULL,
    status            VARCHAR(16)    NOT NULL,
    description       VARCHAR(255)   NOT NULL,
    currency          VARCHAR(3)     NOT NULL,
    subtotal          NUMERIC(12, 2) NOT NULL,
    vat_rate          NUMERIC(5, 2)  NOT NULL,
    vat_amount        NUMERIC(12, 2) NOT NULL,
    total             NUMERIC(12, 2) NOT NULL,
    buyer_name        VARCHAR(255),
    buyer_tax_id      VARCHAR(32),
    buyer_tax_office  VARCHAR(128),
    buyer_address     VARCHAR(1024),
    buyer_email       VARCHAR(255),
    provider          VARCHAR(32),
    provider_ref      VARCHAR(128),
    invoice_number    VARCHAR(64),
    document_url      VARCHAR(1024),
    attempts          INT            NOT NULL DEFAULT 0,
    last_error        VARCHAR(1024),
    issued_at         TIMESTAMP,
    created_at        TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP,
    CONSTRAINT uk_billing_invoice_event UNIQUE (payment_event_id, kind)
);

CREATE INDEX IF NOT EXISTS idx_billing_invoice_merchant ON billing_invoice (merchant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_billing_invoice_status ON billing_invoice (status);
