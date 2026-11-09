-- A QR's printed address is built from public_code and never changes, so its content can be edited after printing.
-- Older rows stay NULL: their image holds the content directly until they are next edited.
ALTER TABLE tbl_qr ADD COLUMN IF NOT EXISTS public_code VARCHAR(16);
ALTER TABLE tbl_qr ADD COLUMN IF NOT EXISTS tracking_enabled BOOLEAN NOT NULL DEFAULT TRUE;
CREATE UNIQUE INDEX IF NOT EXISTS uk_tbl_qr_public_code ON tbl_qr (public_code) WHERE public_code IS NOT NULL;

CREATE TABLE IF NOT EXISTS qr_scan_daily (
    qr_id      BIGINT NOT NULL,
    scan_date  DATE   NOT NULL,
    scan_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (qr_id, scan_date)
);
