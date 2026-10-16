-- Remove legacy / orphan tables not referenced by qr-service (validated against stage algoryqrdb).
-- Kept: auth session tables, chat_* (external AI), alembic_version, tbl_qr / tbl_qr_type (active QR data).

DROP TABLE IF EXISTS smart_report_events CASCADE;
DROP TABLE IF EXISTS smart_report_jobs CASCADE;

DROP TABLE IF EXISTS menu_import_jobs CASCADE;

DROP TABLE IF EXISTS tbl_payments CASCADE;
DROP TABLE IF EXISTS tbl_processed_payment_event CASCADE;
DROP TABLE IF EXISTS tbl_subscription CASCADE;

DROP TABLE IF EXISTS tbl_payment_event_outbox CASCADE;
DROP TABLE IF EXISTS tbl_payment CASCADE;
DROP TABLE IF EXISTS tbl_payment_method CASCADE;

DROP TABLE IF EXISTS tbl_purchase_reminder CASCADE;
DROP TABLE IF EXISTS tbl_password_change_code CASCADE;
