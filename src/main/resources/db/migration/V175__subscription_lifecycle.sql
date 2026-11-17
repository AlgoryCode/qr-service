-- Subscription lifecycle: every merchant that ever had a package keeps one. Trials, grants and
-- unrenewed paid packages fall back to FREE_PACKAGE; a failed renewal keeps the paid package open
-- for a grace period first.

-- 1. What kind of package the merchant is on, and when a payment grace period ends.
ALTER TABLE active_package ADD COLUMN IF NOT EXISTS subscription_kind VARCHAR(16);
ALTER TABLE active_package ADD COLUMN IF NOT EXISTS grace_ends_at TIMESTAMP;

UPDATE active_package
SET subscription_kind = CASE
        WHEN package_code = 'FREE_PACKAGE' THEN 'FREE'
        WHEN package_code = 'ULTIMATE_TRIAL_PACKAGE' THEN 'TRIAL'
        WHEN purchase_id IS NOT NULL AND purchase_id > 0 THEN 'PAID'
        ELSE 'GRANT'
    END
WHERE subscription_kind IS NULL;

CREATE INDEX IF NOT EXISTS idx_active_package_lifecycle
    ON active_package (status, subscription_kind, period_end);

-- 2. Cluster-wide lock for scheduled jobs (renewals, expiry, lifecycle...).
CREATE TABLE IF NOT EXISTS scheduler_lock (
    name         VARCHAR(64)  PRIMARY KEY,
    locked_until TIMESTAMP    NOT NULL,
    locked_at    TIMESTAMP    NOT NULL,
    locked_by    VARCHAR(255) NOT NULL
);

-- 3. Append-only history of subscription changes.
CREATE TABLE IF NOT EXISTS subscription_history (
    id                    BIGSERIAL PRIMARY KEY,
    merchant_id           BIGINT       NOT NULL,
    event_type            VARCHAR(32)  NOT NULL,
    package_code          VARCHAR(128),
    previous_package_code VARCHAR(128),
    status                VARCHAR(32),
    ends_at               TIMESTAMP,
    detail                VARCHAR(500),
    actor                 VARCHAR(64)  NOT NULL,
    dedupe_key            VARCHAR(128),
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_subscription_history_dedupe UNIQUE (dedupe_key)
);

CREATE INDEX IF NOT EXISTS idx_subscription_history_merchant
    ON subscription_history (merchant_id, created_at);

-- 4. The permanent free package every merchant falls back to.
INSERT INTO tbl_plan_package (
    code, name, description, features,
    price, subtotal, vat_amount, currency,
    active, validity_days, priority,
    purchasable, system_managed, yearly_price,
    billing_period, created_at, updated_at
)
SELECT
    'FREE_PACKAGE',
    'Ücretsiz',
    'Süresiz ücretsiz paket',
    '["1 şube","1 menü","30 ürün","1 QR kod"]'::jsonb,
    0.00, 0.00, 0.00, 'TRY',
    TRUE, 36500, 10,
    FALSE, TRUE, NULL,
    'MONTHLY',
    NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_plan_package WHERE code = 'FREE_PACKAGE');

UPDATE tbl_plan_package
SET active = TRUE,
    purchasable = FALSE,
    system_managed = TRUE,
    price = 0.00,
    subtotal = 0.00,
    vat_amount = 0.00,
    updated_at = NOW()
WHERE code = 'FREE_PACKAGE';

DELETE FROM tbl_plan_package_item
WHERE package_id = (SELECT id FROM tbl_plan_package WHERE code = 'FREE_PACKAGE');

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT p.id, pr.id, v.quantity, FALSE
FROM tbl_plan_package p
JOIN (VALUES ('QR_BRANCH', 1), ('QR_MENU', 1), ('MENU_PRODUCT', 30), ('QR_CREATE', 1)) AS v(code, quantity)
    ON TRUE
JOIN tbl_product pr ON pr.code = v.code
WHERE p.code = 'FREE_PACKAGE';

-- 5. Free smart reports per week become a package line instead of a config value. Every package
--    that includes smart reporting gets 1 per week, which is today's configured default.
INSERT INTO tbl_product (
    code, name, description, active, scope_code, type_id, feature_code,
    consumable, addon_purchasable, requires_count_sync, unit_price, vat_rate, created_at, updated_at
)
SELECT 'SMART_REPORT_QUOTA', 'Haftalik Akilli Rapor', 'Paketle gelen haftalik ucretsiz akilli rapor sayisi',
       TRUE, 'SMART_REPORTING_OWNER', 'PACKAGE_PRODUCT', 'SMART_REPORT_QUOTA', TRUE, FALSE, FALSE, 0.00, 20.00, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tbl_product WHERE code = 'SMART_REPORT_QUOTA');

INSERT INTO tbl_plan_package_item (package_id, product_id, quantity, unlimited)
SELECT item.package_id, quota.id, 1, FALSE
FROM tbl_plan_package_item item
JOIN tbl_product reporting ON reporting.id = item.product_id AND reporting.code = 'SMART_REPORTING'
JOIN tbl_product quota ON quota.code = 'SMART_REPORT_QUOTA'
WHERE NOT EXISTS (
    SELECT 1 FROM tbl_plan_package_item existing
    WHERE existing.package_id = item.package_id AND existing.product_id = quota.id
);
