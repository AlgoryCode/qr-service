-- Address book for signed-in customers ordering from a storefront. Guest orders
-- keep their address only as a snapshot on store_orders.
-- Safe to run manually on stage/prod while Flyway remains disabled.

CREATE TABLE IF NOT EXISTS customer_addresses (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    title VARCHAR(80) NOT NULL,
    full_name VARCHAR(160),
    phone VARCHAR(32),
    address_text TEXT NOT NULL,
    city VARCHAR(80),
    district VARCHAR(80),
    building_no VARCHAR(32),
    floor_no VARCHAR(32),
    door_no VARCHAR(32),
    directions TEXT,
    latitude NUMERIC(10, 7),
    longitude NUMERIC(10, 7),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_customer_address_customer
    ON customer_addresses (customer_id, is_deleted);
