CREATE TABLE IF NOT EXISTS yemeksepeti_connections (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    chain_id VARCHAR(128) NOT NULL,
    vendor_id VARCHAR(128),
    vendor_name VARCHAR(255),
    client_id_encrypted TEXT NOT NULL,
    client_secret_encrypted TEXT NOT NULL,
    webhook_secret_encrypted TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_error TEXT,
    last_synced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yemeksepeti_connections_user
    ON yemeksepeti_connections (user_id);

CREATE INDEX IF NOT EXISTS idx_yemeksepeti_connections_vendor
    ON yemeksepeti_connections (vendor_id);

CREATE INDEX IF NOT EXISTS idx_yemeksepeti_connections_chain
    ON yemeksepeti_connections (chain_id);

CREATE TABLE IF NOT EXISTS yemeksepeti_orders (
    id BIGSERIAL PRIMARY KEY,
    connection_id BIGINT NOT NULL REFERENCES yemeksepeti_connections (id),
    external_order_id VARCHAR(128) NOT NULL,
    package_status VARCHAR(64),
    total_amount NUMERIC(12, 2),
    currency VARCHAR(8) NOT NULL DEFAULT 'TRY',
    customer_name VARCHAR(255),
    customer_phone VARCHAR(64),
    delivery_address TEXT,
    note TEXT,
    package_created_at TIMESTAMP,
    items_json JSONB,
    raw_payload JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_yemeksepeti_orders_connection_external
    ON yemeksepeti_orders (connection_id, external_order_id);

CREATE INDEX IF NOT EXISTS idx_yemeksepeti_orders_connection_status
    ON yemeksepeti_orders (connection_id, package_status);

CREATE INDEX IF NOT EXISTS idx_yemeksepeti_orders_created
    ON yemeksepeti_orders (package_created_at);
