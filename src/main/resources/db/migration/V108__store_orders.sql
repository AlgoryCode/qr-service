-- Delivery orders placed through the online-order storefront. Kept apart from
-- tbl_menu_order because that flow is bound to tables, sessions and waiters.
-- Safe to run manually on stage/prod while Flyway remains disabled.

ALTER TABLE IF EXISTS store_orders RENAME TO tbl_merchant_store_order;

CREATE TABLE IF NOT EXISTS tbl_merchant_store_order (
    id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    public_token VARCHAR(32) NOT NULL,
    customer_id BIGINT,
    customer_name VARCHAR(160) NOT NULL,
    customer_phone VARCHAR(32) NOT NULL,
    delivery_type VARCHAR(16) NOT NULL,
    address_text TEXT,
    city VARCHAR(80),
    district VARCHAR(80),
    building_no VARCHAR(32),
    floor_no VARCHAR(32),
    door_no VARCHAR(32),
    directions TEXT,
    latitude NUMERIC(10, 7),
    longitude NUMERIC(10, 7),
    status VARCHAR(24) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    payment_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    subtotal NUMERIC(12, 2) NOT NULL DEFAULT 0,
    delivery_fee NUMERIC(12, 2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    currency VARCHAR(8) NOT NULL DEFAULT 'TRY',
    note TEXT,
    courier_id BIGINT,
    assigned_at TIMESTAMP WITHOUT TIME ZONE,
    confirmed_at TIMESTAMP WITHOUT TIME ZONE,
    preparing_at TIMESTAMP WITHOUT TIME ZONE,
    ready_at TIMESTAMP WITHOUT TIME ZONE,
    dispatched_at TIMESTAMP WITHOUT TIME ZONE,
    delivered_at TIMESTAMP WITHOUT TIME ZONE,
    rejected_at TIMESTAMP WITHOUT TIME ZONE,
    cancelled_at TIMESTAMP WITHOUT TIME ZONE,
    reject_reason TEXT,
    cancel_reason TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

ALTER TABLE tbl_merchant_store_order ADD COLUMN IF NOT EXISTS public_token VARCHAR(32);
UPDATE tbl_merchant_store_order
SET public_token = replace(gen_random_uuid()::text, '-', '')
WHERE public_token IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_store_order_public_token
    ON tbl_merchant_store_order (public_token);

CREATE UNIQUE INDEX IF NOT EXISTS uk_store_order_merchant_no
    ON tbl_merchant_store_order (merchant_id, order_no);

CREATE INDEX IF NOT EXISTS idx_store_order_merchant_status
    ON tbl_merchant_store_order (merchant_id, status);

CREATE INDEX IF NOT EXISTS idx_store_order_merchant_created
    ON tbl_merchant_store_order (merchant_id, created_at);

CREATE INDEX IF NOT EXISTS idx_store_order_customer
    ON tbl_merchant_store_order (customer_id);

CREATE INDEX IF NOT EXISTS idx_store_order_courier
    ON tbl_merchant_store_order (courier_id);

CREATE TABLE IF NOT EXISTS store_order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES tbl_merchant_store_order (id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    quantity INT NOT NULL,
    note TEXT,
    selected_options JSONB NOT NULL DEFAULT '[]'::jsonb,
    line_total NUMERIC(12, 2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_store_order_item_order
    ON store_order_items (order_id);

CREATE TABLE IF NOT EXISTS store_order_status_history (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES tbl_merchant_store_order (id) ON DELETE CASCADE,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    changed_by_type VARCHAR(16) NOT NULL,
    changed_by_id BIGINT,
    note TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_store_order_history_order
    ON store_order_status_history (order_id, created_at);
