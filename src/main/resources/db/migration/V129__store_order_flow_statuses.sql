-- Allow kitchen / waiter / courier handover statuses on store orders.
-- COURIER_DELIVERED_TO_CUSTOMER is 29 chars, so the status columns grow to 32.

ALTER TABLE store_orders ALTER COLUMN status TYPE VARCHAR(32);
ALTER TABLE store_order_status_history ALTER COLUMN from_status TYPE VARCHAR(32);
ALTER TABLE store_order_status_history ALTER COLUMN to_status TYPE VARCHAR(32);

ALTER TABLE store_orders DROP CONSTRAINT IF EXISTS store_orders_status_check;
ALTER TABLE store_order_status_history DROP CONSTRAINT IF EXISTS store_order_status_history_from_status_check;
ALTER TABLE store_order_status_history DROP CONSTRAINT IF EXISTS store_order_status_history_to_status_check;

ALTER TABLE store_orders
    ADD CONSTRAINT store_orders_status_check
    CHECK (status::text = ANY (ARRAY[
        'PENDING',
        'CONFIRMED',
        'PREPARING',
        'READY',
        'ON_THE_WAY',
        'DELIVERED',
        'REJECTED',
        'CANCELLED',
        'KITCHEN_PREPARING',
        'KITCHEN_PREPARED',
        'KITCHEN_DELIVERED_TO_WAITER',
        'KITCHEN_DELIVERED_TO_COURIER',
        'WAITER_TAKEN',
        'COURIER_TAKEN',
        'WAITER_DELIVERED_TO_CUSTOMER',
        'WAITER_DELIVERED_TO_COURIER',
        'COURIER_DELIVERED_TO_CUSTOMER'
    ]::text[]));
