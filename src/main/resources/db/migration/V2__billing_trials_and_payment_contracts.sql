ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS purchase_type VARCHAR(16);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS payment_style VARCHAR(24);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS subscription_id VARCHAR(128);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS subscription_status VARCHAR(24);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS billing_address_id BIGINT;
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS billing_type VARCHAR(16);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS billing_name VARCHAR(255);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS billing_surname VARCHAR(255);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS billing_legal_name VARCHAR(255);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS billing_tckn VARCHAR(11);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS billing_vkn VARCHAR(10);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS billing_tax_office VARCHAR(255);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS billing_mersis VARCHAR(255);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS billing_country VARCHAR(255);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS billing_city VARCHAR(255);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS billing_district VARCHAR(255);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS billing_address VARCHAR(1000);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS billing_postcode VARCHAR(16);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS billing_email VARCHAR(255);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS billing_phone VARCHAR(32);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS billing_taxpayer_invoice BOOLEAN;

UPDATE tbl_purchase
SET purchase_type = CASE WHEN package_code = 'FREE_PACKAGE' THEN 'FREE' ELSE 'PAID' END
WHERE purchase_type IS NULL;

UPDATE tbl_purchase
SET payment_style = CASE WHEN installment_count > 1 THEN 'SUBSCRIPTION' ELSE 'ONE_TIME' END
WHERE payment_style IS NULL;

ALTER TABLE tbl_purchase ALTER COLUMN purchase_type SET NOT NULL;
ALTER TABLE tbl_purchase ALTER COLUMN payment_style SET NOT NULL;
ALTER TABLE tbl_purchase ALTER COLUMN purchase_type SET DEFAULT 'PAID';
ALTER TABLE tbl_purchase ALTER COLUMN payment_style SET DEFAULT 'ONE_TIME';

CREATE UNIQUE INDEX IF NOT EXISTS uk_purchase_trial_user
    ON tbl_purchase (user_id)
    WHERE purchase_type = 'TRIAL';

CREATE TABLE IF NOT EXISTS tbl_billing_address (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL,
    name VARCHAR(255),
    surname VARCHAR(255),
    legal_name VARCHAR(255),
    tckn VARCHAR(11),
    vkn VARCHAR(10),
    tax_office VARCHAR(255),
    mersis VARCHAR(255),
    country VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    district VARCHAR(255) NOT NULL,
    address VARCHAR(1000) NOT NULL,
    postcode VARCHAR(16) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    taxpayer_invoice BOOLEAN NOT NULL DEFAULT FALSE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_billing_address_user ON tbl_billing_address (user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_billing_address_default_user
    ON tbl_billing_address (user_id)
    WHERE is_default = TRUE;

CREATE TABLE IF NOT EXISTS tbl_purchase_reminder (
    id BIGSERIAL PRIMARY KEY,
    purchase_id BIGINT NOT NULL,
    reminder_type VARCHAR(64) NOT NULL,
    event_id UUID NOT NULL UNIQUE,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_purchase_reminder_purchase_type UNIQUE (purchase_id, reminder_type)
);
