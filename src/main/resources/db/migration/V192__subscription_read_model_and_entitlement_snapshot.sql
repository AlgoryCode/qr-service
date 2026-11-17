-- 1. active_package becomes the one place to read a merchant's subscription: the billing fields that
--    today live on the purchase are mirrored here (kept in step by the application on every change).
ALTER TABLE active_package ADD COLUMN IF NOT EXISTS plan_version_id BIGINT REFERENCES plan_versions (id);
ALTER TABLE active_package ADD COLUMN IF NOT EXISTS billing_status VARCHAR(32);
ALTER TABLE active_package ADD COLUMN IF NOT EXISTS cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE active_package ADD COLUMN IF NOT EXISTS provider_subscription_id VARCHAR(255);
ALTER TABLE active_package ADD COLUMN IF NOT EXISTS entitlement_version BIGINT NOT NULL DEFAULT 0;

UPDATE active_package ap
SET plan_version_id = p.plan_version_id,
    billing_status = p.subscription_status,
    cancel_at_period_end = p.cancel_at_period_end,
    provider_subscription_id = p.subscription_id
FROM tbl_purchase p
WHERE p.id = ap.purchase_id;

-- 2. What each merchant may use right now, one row per feature: limits added up across every open
--    grant (package lines, modules, add-ons, admin gifts), unlimited if any grant is. Rebuilt whenever
--    a grant or the package status changes; access checks read only this.
CREATE TABLE IF NOT EXISTS merchant_entitlements (
    merchant_id BIGINT      NOT NULL,
    feature_key VARCHAR(64) NOT NULL,
    limit_value BIGINT      NOT NULL DEFAULT 0,
    unlimited   BOOLEAN     NOT NULL DEFAULT FALSE,
    grants      INTEGER     NOT NULL DEFAULT 0,
    valid_until TIMESTAMP,
    updated_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (merchant_id, feature_key)
);

INSERT INTO merchant_entitlements (merchant_id, feature_key, limit_value, unlimited, grants, valid_until)
SELECT e.merchant_id, e.feature_code,
       SUM(CASE WHEN e.unlimited THEN 0 ELSE e.quantity END),
       BOOL_OR(e.unlimited), COUNT(*), MIN(e.expires_at)
FROM user_entitlement e
JOIN active_package ap ON ap.merchant_id = e.merchant_id
WHERE e.status = 'ACTIVE'
  AND (e.expires_at IS NULL OR e.expires_at > NOW())
  AND ap.status IN ('ACTIVE', 'GRACE_PERIOD')
GROUP BY e.merchant_id, e.feature_code
ON CONFLICT (merchant_id, feature_key) DO NOTHING;

UPDATE active_package SET entitlement_version = 1 WHERE entitlement_version = 0;
