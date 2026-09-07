CREATE TABLE IF NOT EXISTS tbl_trial_log (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    package_id     BIGINT       NOT NULL,
    package_code   VARCHAR(64)  NOT NULL,
    started_at     TIMESTAMP    NOT NULL,
    ends_at        TIMESTAMP    NOT NULL,
    duration_days  INTEGER      NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_trial_log_user_id UNIQUE (user_id)
);

CREATE INDEX IF NOT EXISTS idx_trial_log_status_ends_at ON tbl_trial_log (status, ends_at);

ALTER TABLE tbl_fulfillment
    ALTER COLUMN purchase_id DROP NOT NULL;

ALTER TABLE tbl_fulfillment
    ADD COLUMN IF NOT EXISTS trial_log_id BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS uk_fulfillment_trial_log_id
    ON tbl_fulfillment (trial_log_id)
    WHERE trial_log_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_fulfillment_source'
    ) THEN
        ALTER TABLE tbl_fulfillment
            ADD CONSTRAINT chk_fulfillment_source
                CHECK (
                    (purchase_id IS NOT NULL AND trial_log_id IS NULL)
                    OR (purchase_id IS NULL AND trial_log_id IS NOT NULL)
                );
    END IF;
END $$;

INSERT INTO tbl_trial_log (
    user_id,
    package_id,
    package_code,
    started_at,
    ends_at,
    duration_days,
    status
)
SELECT
    latest.user_id,
    latest.package_id,
    latest.package_code,
    COALESCE(latest.starts_at, latest.purchased_at),
    COALESCE(latest.expires_at, NOW()),
    GREATEST(
        1,
        CAST(EXTRACT(DAY FROM (COALESCE(latest.expires_at, NOW()) - COALESCE(latest.starts_at, latest.purchased_at))) AS INTEGER)
    ),
    CASE
        WHEN latest.status = 'ACTIVE' AND latest.expires_at IS NOT NULL AND latest.expires_at > NOW()
            THEN 'ACTIVE'
        ELSE 'ENDED'
    END
FROM (
    SELECT DISTINCT ON (p.user_id)
        p.user_id,
        p.package_id,
        p.package_code,
        p.starts_at,
        p.purchased_at,
        p.expires_at,
        p.status
    FROM tbl_purchase p
    WHERE p.purchase_type = 'TRIAL'
    ORDER BY p.user_id, p.purchased_at DESC
) latest
WHERE NOT EXISTS (
    SELECT 1 FROM tbl_trial_log existing WHERE existing.user_id = latest.user_id
);

UPDATE tbl_fulfillment f
SET trial_log_id = t.id,
    purchase_id = NULL
FROM tbl_trial_log t
JOIN tbl_purchase p
    ON p.user_id = t.user_id
   AND p.purchase_type = 'TRIAL'
WHERE f.purchase_id = p.id
  AND f.trial_log_id IS NULL;

UPDATE tbl_fulfillment_detail d
SET source = 'ONBOARDING_PACKAGE'
FROM tbl_fulfillment f
WHERE d.fulfillment_id = f.id
  AND f.trial_log_id IS NOT NULL
  AND d.source = 'PACKAGE_INCLUDE';

UPDATE tbl_purchase
SET status = 'EXPIRED',
    purchase_type = 'SYSTEM_GRANT'
WHERE purchase_type = 'TRIAL';
