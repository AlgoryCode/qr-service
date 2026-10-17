-- Monolith auth uses tbl_merchant (JWT merchantId). Copy existing panel accounts from tbl_user.

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
    WHERE m.id = u.id
       OR lower(trim(m.email)) = lower(trim(u.email))
);

SELECT setval(
    'tbl_merchant_id_seq',
    GREATEST(COALESCE((SELECT MAX(id) FROM tbl_merchant), 1), 1)
);
