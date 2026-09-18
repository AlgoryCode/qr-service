-- Couriers belonging to a merchant. Assignment only in v1; courier login comes later.
-- Safe to run manually on stage/prod while Flyway remains disabled.

CREATE TABLE IF NOT EXISTS store_couriers (
    id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    vehicle_type VARCHAR(24) NOT NULL DEFAULT 'MOTORCYCLE',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_store_courier_merchant
    ON store_couriers (merchant_id, is_deleted);
