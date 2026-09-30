-- Kitchen hands a delivery order to a courier staff account (merchant_staff, role COURIER).
-- courier_id keeps pointing at the legacy store_couriers directory.

ALTER TABLE store_orders ADD COLUMN IF NOT EXISTS courier_staff_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_store_order_courier_staff ON store_orders (courier_staff_id, status);
