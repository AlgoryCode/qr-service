-- Every live tbl_user row must have a tbl_merchant (auth JWT merchantId).
-- Repoint panel/fulfillment foreign keys from legacy tbl_user.id to tbl_merchant.id by email match.
-- Does not delete tbl_user rows or merge duplicate business data automatically.

INSERT INTO tbl_merchant (
    id,
    role_id,
    first_name,
    last_name,
    email,
    phone,
    password,
    provider,
    provider_subject,
    active,
    email_verified,
    email_verification_code_hash,
    email_verification_expires_at,
    email_verification_sent_at,
    email_verification_attempts,
    email_verification_locked_until,
    two_factor_enabled,
    notify_email_important,
    notify_scan_alerts,
    notify_weekly_report,
    notify_marketing_emails,
    notify_push_browser,
    business_type,
    usage_purpose,
    registration_ip_address,
    registration_user_agent,
    registration_device,
    registration_device_type,
    created_at,
    updated_at
)
SELECT u.id,
       r.id,
       u.first_name,
       u.last_name,
       u.email,
       u.phone,
       u.password,
       COALESCE(u.provider, 'BASIC'),
       u.provider_subject,
       TRUE,
       COALESCE(u.email_verified, FALSE),
       u.email_verification_code_hash,
       u.email_verification_expires_at,
       u.email_verification_sent_at,
       COALESCE(u.email_verification_attempts, 0),
       u.email_verification_locked_until,
       COALESCE(u.two_factor_enabled, FALSE),
       COALESCE(u.notify_email_important, TRUE),
       COALESCE(u.notify_scan_alerts, TRUE),
       COALESCE(u.notify_weekly_report, FALSE),
       COALESCE(u.notify_marketing_emails, FALSE),
       COALESCE(u.notify_push_browser, FALSE),
       u.business_type,
       u.usage_purpose,
       u.registration_ip_address,
       u.registration_user_agent,
       u.registration_device,
       u.registration_device_type,
       COALESCE(u.created_at, NOW()),
       COALESCE(u.updated_at, NOW())
FROM tbl_user u
         CROSS JOIN tbl_roles r
WHERE u.deleted_at IS NULL
  AND r.code = 'MERCHANT'
  AND NOT EXISTS (
    SELECT 1
    FROM tbl_merchant m
    WHERE m.deleted_at IS NULL
      AND (m.id = u.id OR lower(trim(m.email)) = lower(trim(u.email)))
)
  AND NOT EXISTS (
    SELECT 1
    FROM tbl_merchant m
    WHERE m.deleted_at IS NULL
      AND u.phone IS NOT NULL
      AND m.phone IS NOT NULL
      AND m.phone = u.phone
      AND lower(trim(m.email)) <> lower(trim(u.email))
);

SELECT setval(
    'tbl_merchant_id_seq',
    GREATEST(COALESCE((SELECT MAX(id) FROM tbl_merchant), 1), 1)
);

CREATE TEMP TABLE merchant_user_id_map ON COMMIT DROP AS
SELECT u.id AS legacy_user_id,
       m.id AS merchant_id
FROM tbl_user u
         INNER JOIN tbl_merchant m
                    ON lower(trim(m.email)) = lower(trim(u.email))
WHERE u.deleted_at IS NULL
  AND m.deleted_at IS NULL
  AND u.id <> m.id;

-- Fulfillment: prefer existing row on merchant_id (unique user_id on active_package).
UPDATE active_package ap
SET user_id = map.merchant_id,
    updated_at = NOW()
FROM merchant_user_id_map map
WHERE ap.user_id = map.legacy_user_id
  AND NOT EXISTS (
    SELECT 1 FROM active_package existing WHERE existing.user_id = map.merchant_id
);

DELETE FROM active_package ap
    USING merchant_user_id_map map
WHERE ap.user_id = map.legacy_user_id
  AND EXISTS (
    SELECT 1 FROM active_package existing WHERE existing.user_id = map.merchant_id
);

UPDATE package_fulfillment pf
SET user_id = map.merchant_id,
    updated_at = NOW()
FROM merchant_user_id_map map
WHERE pf.user_id = map.legacy_user_id;

UPDATE user_entitlement ue
SET user_id = map.merchant_id,
    updated_at = NOW()
FROM merchant_user_id_map map
WHERE ue.user_id = map.legacy_user_id;

UPDATE tbl_session s
SET subject_id = map.merchant_id
FROM merchant_user_id_map map
WHERE s.subject_type = 'MERCHANT'
  AND s.subject_id = map.legacy_user_id;

DO $$
DECLARE
    col RECORD;
    excluded_tables TEXT[] := ARRAY [
        'tbl_user',
        'tbl_customer',
        'tbl_admin',
        'tbl_dashboard_user',
        'tbl_merchant',
        'flyway_schema_history'
        ];
BEGIN
    FOR col IN
        SELECT c.table_schema AS schema_name,
               c.table_name,
               c.column_name
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.column_name IN ('user_id', 'used_by_user_id')
          AND NOT (c.table_name = ANY (excluded_tables))
          AND c.table_name NOT IN ('active_package')
        LOOP
            EXECUTE format(
                    'UPDATE %I.%I AS t SET %I = map.merchant_id FROM merchant_user_id_map map '
                        || 'WHERE t.%I = map.legacy_user_id',
                    col.schema_name,
                    col.table_name,
                    col.column_name,
                    col.column_name
                    );
        END LOOP;

    FOR col IN
        SELECT c.table_schema AS schema_name,
               c.table_name
        FROM information_schema.columns c
        WHERE c.table_schema = 'public'
          AND c.column_name = 'merchant_id'
          AND c.table_name <> 'tbl_merchant'
        LOOP
            EXECUTE format(
                    'UPDATE %I.%I AS t SET merchant_id = map.merchant_id FROM merchant_user_id_map map '
                        || 'WHERE t.merchant_id = map.legacy_user_id',
                    col.schema_name,
                    col.table_name
                    );
        END LOOP;
END $$;
