-- store_order_items / history were created against an older parent table name.
-- Hibernate now persists StoreOrder to store_orders, so the FKs must follow.
-- Safe to run manually on stage/prod while Flyway remains disabled.

ALTER TABLE store_order_items DROP CONSTRAINT IF EXISTS store_order_items_order_id_fkey;
ALTER TABLE store_order_items
    ADD CONSTRAINT store_order_items_order_id_fkey
    FOREIGN KEY (order_id) REFERENCES store_orders (id) ON DELETE CASCADE;

ALTER TABLE store_order_status_history DROP CONSTRAINT IF EXISTS store_order_status_history_order_id_fkey;
ALTER TABLE store_order_status_history
    ADD CONSTRAINT store_order_status_history_order_id_fkey
    FOREIGN KEY (order_id) REFERENCES store_orders (id) ON DELETE CASCADE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_store_order_public_token
    ON store_orders (public_token);

CREATE UNIQUE INDEX IF NOT EXISTS uk_store_order_merchant_no
    ON store_orders (merchant_id, order_no);

CREATE INDEX IF NOT EXISTS idx_store_order_merchant_status
    ON store_orders (merchant_id, status);

CREATE INDEX IF NOT EXISTS idx_store_order_merchant_created
    ON store_orders (merchant_id, created_at);

CREATE INDEX IF NOT EXISTS idx_store_order_customer
    ON store_orders (customer_id);

CREATE INDEX IF NOT EXISTS idx_store_order_courier
    ON store_orders (courier_id);
