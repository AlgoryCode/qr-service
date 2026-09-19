-- Allow kitchen statuses on tbl_menu_order.
-- Safe to run manually on stage/prod while Flyway remains disabled.

ALTER TABLE tbl_menu_order DROP CONSTRAINT IF EXISTS tbl_menu_order_status_check;

ALTER TABLE tbl_menu_order
    ADD CONSTRAINT tbl_menu_order_status_check
    CHECK (status::text = ANY (ARRAY[
        'DRAFT',
        'SUBMITTED',
        'CONFIRMED',
        'PREPARING',
        'READY',
        'SERVED',
        'REJECTED',
        'CANCELLED'
    ]::text[]));
