-- V119: Modular package cart.
-- A purchase can now bundle the base package with optional module (catalog product)
-- lines that are paid for in the same transaction.
--
-- billing_type on tbl_product decides whether a module keeps being charged on every
-- subscription renewal (RECURRING) or only once for the current period (ONE_TIME).

ALTER TABLE tbl_product
    ADD COLUMN IF NOT EXISTS billing_type VARCHAR(16) NOT NULL DEFAULT 'RECURRING';

ALTER TABLE tbl_product
    DROP CONSTRAINT IF EXISTS chk_product_billing_type;

ALTER TABLE tbl_product
    ADD CONSTRAINT chk_product_billing_type
        CHECK (billing_type IN ('RECURRING', 'ONE_TIME'));

-- Quota top-ups are consumed within the current period, so they are one-off charges.
UPDATE tbl_product
SET billing_type = 'ONE_TIME'
WHERE code IN ('QR_MENU_ADDON', 'QR_BRANCH_ADDON', 'SMART_REPORTING_ADDON');

-- base_price     : package amount before modules, after billing-period resolution
-- modules_total  : sum of every module line total (VAT included)
-- recurring_price : amount to charge on renewal (base + RECURRING module lines)
ALTER TABLE tbl_purchase
    ADD COLUMN IF NOT EXISTS base_price      NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS modules_total   NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS recurring_price NUMERIC(12, 2);

CREATE TABLE IF NOT EXISTS tbl_purchase_item (
    id            BIGSERIAL PRIMARY KEY,
    purchase_id   BIGINT         NOT NULL REFERENCES tbl_purchase (id),
    product_id    BIGINT         NOT NULL REFERENCES tbl_product (id),
    product_code  VARCHAR(64)    NOT NULL,
    product_name  VARCHAR(255)   NOT NULL,
    quantity      INTEGER        NOT NULL,
    unit_price    NUMERIC(12, 2) NOT NULL,
    vat_rate      NUMERIC(5, 2)  NOT NULL,
    line_subtotal NUMERIC(12, 2) NOT NULL,
    line_vat      NUMERIC(12, 2) NOT NULL,
    line_total    NUMERIC(12, 2) NOT NULL,
    billing_type  VARCHAR(16)    NOT NULL,
    unlimited     BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_purchase_item_product UNIQUE (purchase_id, product_id),
    CONSTRAINT chk_purchase_item_quantity CHECK (quantity >= 1),
    CONSTRAINT chk_purchase_item_billing_type CHECK (billing_type IN ('RECURRING', 'ONE_TIME'))
);

CREATE INDEX IF NOT EXISTS idx_purchase_item_purchase_id ON tbl_purchase_item (purchase_id);
CREATE INDEX IF NOT EXISTS idx_purchase_item_product_id ON tbl_purchase_item (product_id);
