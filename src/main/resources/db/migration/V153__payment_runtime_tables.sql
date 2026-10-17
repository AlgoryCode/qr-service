-- Payment module runtime tables (from payment-service schema-postgresql.sql).
-- V141 dropped legacy copies; recreate for monolith payment module.

CREATE TABLE IF NOT EXISTS tbl_payment (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL UNIQUE,
    provider VARCHAR(32) NOT NULL DEFAULT 'PAYTR',
    payment_id VARCHAR(64),
    payment_transaction_id VARCHAR(64),
    gateway_token VARCHAR(128),
    account_id VARCHAR(64),
    basket_id VARCHAR(64) NOT NULL,
    price NUMERIC(19, 2) NOT NULL,
    paid_price NUMERIC(19, 2) NOT NULL,
    refunded_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    currency VARCHAR(8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payment_type VARCHAR(32) NOT NULL,
    error_code VARCHAR(64),
    error_message VARCHAR(512),
    service_name VARCHAR(64),
    source_reference_id VARCHAR(64),
    source_metadata VARCHAR(4000),
    buyer_email VARCHAR(255),
    buyer_name VARCHAR(255),
    payment_style VARCHAR(32) NOT NULL DEFAULT 'ONE_TIME',
    bank_installment_count INTEGER,
    subscription_id VARCHAR(64),
    billing_cycle_number INTEGER,
    period_start DATE,
    period_end DATE,
    verification_only BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tbl_payment_payment_id ON tbl_payment (payment_id);
CREATE INDEX IF NOT EXISTS idx_tbl_payment_status ON tbl_payment (status);
CREATE INDEX IF NOT EXISTS idx_tbl_payment_transaction_id ON tbl_payment (payment_transaction_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_tbl_payment_gateway_token ON tbl_payment (gateway_token);
CREATE INDEX IF NOT EXISTS idx_tbl_payment_status_created_at ON tbl_payment (status, created_at);
CREATE INDEX IF NOT EXISTS idx_tbl_payment_provider ON tbl_payment (provider);
CREATE INDEX IF NOT EXISTS idx_tbl_payment_service_name ON tbl_payment (service_name);

CREATE TABLE IF NOT EXISTS tbl_payment_method (
    id BIGSERIAL PRIMARY KEY,
    owner_reference_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    card_user_key VARCHAR(255) NOT NULL,
    card_token VARCHAR(255) NOT NULL,
    card_alias VARCHAR(255),
    last_four_digits VARCHAR(4),
    card_association VARCHAR(64),
    card_family VARCHAR(64),
    bank_name VARCHAR(128),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payment_method_provider_token UNIQUE (provider, card_user_key, card_token)
);

CREATE INDEX IF NOT EXISTS idx_payment_method_owner ON tbl_payment_method (owner_reference_id);
CREATE INDEX IF NOT EXISTS idx_payment_method_account_active ON tbl_payment_method (account_id, deleted_at);

CREATE TABLE IF NOT EXISTS tbl_subscription (
    id VARCHAR(64) PRIMARY KEY,
    account_id VARCHAR(64) NOT NULL,
    service_name VARCHAR(64) NOT NULL,
    source_reference_id VARCHAR(64),
    payment_method_id BIGINT,
    initial_conversation_id VARCHAR(64) NOT NULL UNIQUE,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    billing_interval_months INTEGER NOT NULL,
    total_cycle_count INTEGER,
    completed_cycle_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    next_charge_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    request_payload VARCHAR(16000) NOT NULL,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_subscription_method FOREIGN KEY (payment_method_id) REFERENCES tbl_payment_method(id)
);

CREATE INDEX IF NOT EXISTS idx_subscription_account ON tbl_subscription (account_id, status);
CREATE INDEX IF NOT EXISTS idx_subscription_due ON tbl_subscription (status, next_charge_at);

CREATE TABLE IF NOT EXISTS tbl_payment_event_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    service_name VARCHAR(64) NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    payload VARCHAR(16000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payment_outbox_pending ON tbl_payment_event_outbox (status, next_attempt_at);
