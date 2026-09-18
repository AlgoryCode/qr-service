-- Online order merchants. One active merchant per user; the storefront URL is
-- /store/{store_no}-{slug}-{public_token}.
-- Safe to run manually on stage/prod while Flyway remains disabled.

CREATE SEQUENCE IF NOT EXISTS seq_merchant_store_no START WITH 10000 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS merchants (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    branch_id BIGINT,
    catalog_menu_id BIGINT NOT NULL,
    store_no BIGINT NOT NULL,
    slug VARCHAR(120) NOT NULL,
    public_token VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    business_name VARCHAR(255) NOT NULL,
    legal_name VARCHAR(255),
    tax_office VARCHAR(120),
    tax_number VARCHAR(32),
    phone VARCHAR(32),
    email VARCHAR(160),
    logo_url VARCHAR(1024),
    cover_url VARCHAR(1024),
    address TEXT,
    city VARCHAR(80),
    district VARCHAR(80),
    latitude NUMERIC(10, 7),
    longitude NUMERIC(10, 7),
    min_order_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    delivery_fee NUMERIC(12, 2) NOT NULL DEFAULT 0,
    free_delivery_threshold NUMERIC(12, 2),
    avg_prep_minutes INT NOT NULL DEFAULT 30,
    delivery_radius_km NUMERIC(6, 2),
    delivery_types JSONB NOT NULL DEFAULT '["DELIVERY"]'::jsonb,
    payment_methods JSONB NOT NULL DEFAULT '["CASH_ON_DELIVERY"]'::jsonb,
    working_hours JSONB NOT NULL DEFAULT '[]'::jsonb,
    manually_closed BOOLEAN NOT NULL DEFAULT FALSE,
    order_counter BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(8) NOT NULL DEFAULT 'TRY',
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_merchant_store_no
    ON merchants (store_no);

CREATE UNIQUE INDEX IF NOT EXISTS uk_merchant_public_token
    ON merchants (public_token);

CREATE UNIQUE INDEX IF NOT EXISTS uk_merchant_catalog_menu
    ON merchants (catalog_menu_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_merchant_user_active
    ON merchants (user_id) WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_merchant_branch
    ON merchants (branch_id);
