-- Menus gain a delivery channel. STORE menus back an online-order storefront and
-- have no QR code, so qr_id must become nullable.
-- Safe to run manually on stage/prod while Flyway remains disabled.

ALTER TABLE tbl_menu
    ADD COLUMN IF NOT EXISTS channel VARCHAR(16) NOT NULL DEFAULT 'QR';

ALTER TABLE tbl_menu
    ALTER COLUMN qr_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_menu_channel_user
    ON tbl_menu (channel, user_id);
