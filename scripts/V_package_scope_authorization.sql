ALTER TABLE tbl_plan_package_item
    ADD COLUMN IF NOT EXISTS unlimited BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE tbl_user_entitlement
    ADD COLUMN IF NOT EXISTS unlimited BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE tbl_plan_package
SET code = CASE
    WHEN UPPER(code) IN ('FREE', 'FREE_PACKAGE') THEN 'FREE_PACKAGE'
    WHEN UPPER(code) IN ('PRO', 'PRO_PACKAGE') THEN 'PRO_PACKAGE'
    ELSE code
END;

UPDATE tbl_purchase
SET package_code = CASE
    WHEN UPPER(package_code) IN ('FREE', 'FREE_PACKAGE') THEN 'FREE_PACKAGE'
    WHEN UPPER(package_code) IN ('PRO', 'PRO_PACKAGE') THEN 'PRO_PACKAGE'
    ELSE package_code
END;

BEGIN;

DELETE FROM tbl_payment_event_inbox
WHERE purchase_id IN (
    SELECT id FROM tbl_purchase WHERE package_code = 'MANUAL_QR_GRANT'
);

DELETE FROM tbl_purchase_fulfillment
WHERE purchase_id IN (
    SELECT id FROM tbl_purchase WHERE package_code = 'MANUAL_QR_GRANT'
);

DELETE FROM tbl_user_entitlement
WHERE purchase_id IN (
    SELECT id FROM tbl_purchase WHERE package_code = 'MANUAL_QR_GRANT'
);

DELETE FROM tbl_purchase_log
WHERE purchase_id IN (
    SELECT id FROM tbl_purchase WHERE package_code = 'MANUAL_QR_GRANT'
);

DELETE FROM tbl_purchase
WHERE package_code = 'MANUAL_QR_GRANT';

DELETE FROM tbl_plan_package_item
WHERE package_id IN (
    SELECT id FROM tbl_plan_package WHERE code = 'MANUAL_QR_GRANT'
);

DELETE FROM tbl_plan_package
WHERE code = 'MANUAL_QR_GRANT';

COMMIT;

ALTER TABLE tbl_user
    ADD COLUMN IF NOT EXISTS provider VARCHAR(16) NOT NULL DEFAULT 'BASIC';

ALTER TABLE tbl_user
    ADD COLUMN IF NOT EXISTS provider_subject VARCHAR(128);

ALTER TABLE tbl_user
    ALTER COLUMN password DROP NOT NULL;

ALTER TABLE tbl_user
    ALTER COLUMN phone DROP NOT NULL;

UPDATE tbl_user
SET provider = 'BASIC'
WHERE provider IS NULL;

ALTER TABLE tbl_user
    DROP CONSTRAINT IF EXISTS chk_user_provider;

ALTER TABLE tbl_user
    ADD CONSTRAINT chk_user_provider CHECK (provider IN ('BASIC', 'GOOGLE'));

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_provider_subject
    ON tbl_user (provider_subject)
    WHERE provider_subject IS NOT NULL;
