-- Auth module tables (merged from algoryqr-authservice). QR schema already owns tbl_merchant_staff.

CREATE TABLE IF NOT EXISTS tbl_roles (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_roles_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS tbl_scopes (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(128) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_scopes_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS tbl_role_scopes (
    role_id     BIGINT NOT NULL REFERENCES tbl_roles (id),
    scope_id    BIGINT NOT NULL REFERENCES tbl_scopes (id),
    CONSTRAINT pk_role_scopes PRIMARY KEY (role_id, scope_id)
);

CREATE TABLE IF NOT EXISTS tbl_merchant (
    id                              BIGINT PRIMARY KEY,
    role_id                         BIGINT       NOT NULL REFERENCES tbl_roles (id),
    first_name                      VARCHAR(255) NOT NULL,
    last_name                       VARCHAR(255),
    email                           VARCHAR(255) NOT NULL,
    phone                           VARCHAR(64),
    password                        VARCHAR(255),
    provider                        VARCHAR(32)  NOT NULL DEFAULT 'BASIC',
    provider_subject                VARCHAR(128),
    active                          BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified                  BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verification_code_hash    VARCHAR(255),
    email_verification_expires_at   TIMESTAMP,
    email_verification_sent_at      TIMESTAMP,
    email_verification_attempts     INT          NOT NULL DEFAULT 0,
    email_verification_locked_until TIMESTAMP,
    two_factor_enabled              BOOLEAN      NOT NULL DEFAULT FALSE,
    notify_email_important          BOOLEAN      NOT NULL DEFAULT TRUE,
    notify_scan_alerts              BOOLEAN      NOT NULL DEFAULT TRUE,
    notify_weekly_report            BOOLEAN      NOT NULL DEFAULT FALSE,
    notify_marketing_emails         BOOLEAN      NOT NULL DEFAULT FALSE,
    notify_push_browser             BOOLEAN      NOT NULL DEFAULT FALSE,
    trial_used                      BOOLEAN      NOT NULL DEFAULT FALSE,
    trial_end_date                  TIMESTAMP,
    business_type                   VARCHAR(32),
    usage_purpose                   VARCHAR(64),
    registration_ip_address         VARCHAR(64),
    registration_user_agent         VARCHAR(512),
    registration_device             VARCHAR(128),
    registration_device_type        VARCHAR(64),
    created_at                      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_merchant_email UNIQUE (email),
    CONSTRAINT uk_merchant_phone UNIQUE (phone),
    CONSTRAINT uk_merchant_provider_subject UNIQUE (provider_subject)
);

CREATE SEQUENCE IF NOT EXISTS tbl_merchant_id_seq;
DO $$
BEGIN
    ALTER SEQUENCE tbl_merchant_id_seq OWNED BY tbl_merchant.id;
EXCEPTION
    WHEN feature_not_supported OR object_not_in_prerequisite_state THEN
        NULL;
END $$;

CREATE TABLE IF NOT EXISTS tbl_customer (
    id               BIGINT PRIMARY KEY,
    role_id          BIGINT       NOT NULL REFERENCES tbl_roles (id),
    first_name       VARCHAR(255) NOT NULL,
    last_name        VARCHAR(255),
    email            VARCHAR(255) NOT NULL,
    phone            VARCHAR(64),
    password         VARCHAR(255),
    provider         VARCHAR(32)  NOT NULL DEFAULT 'BASIC',
    provider_subject VARCHAR(128),
    avatar_key       VARCHAR(64),
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_customer_email UNIQUE (email),
    CONSTRAINT uk_customer_phone UNIQUE (phone),
    CONSTRAINT uk_customer_provider_subject UNIQUE (provider_subject)
);

CREATE SEQUENCE IF NOT EXISTS tbl_customer_id_seq;
DO $$
BEGIN
    ALTER SEQUENCE tbl_customer_id_seq OWNED BY tbl_customer.id;
EXCEPTION
    WHEN feature_not_supported OR object_not_in_prerequisite_state THEN
        NULL;
END $$;

CREATE TABLE IF NOT EXISTS tbl_admin (
    id         BIGINT PRIMARY KEY,
    role_id    BIGINT       NOT NULL REFERENCES tbl_roles (id),
    first_name VARCHAR(255) NOT NULL,
    last_name  VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_admin_email UNIQUE (email)
);

CREATE SEQUENCE IF NOT EXISTS tbl_admin_id_seq;
DO $$
BEGIN
    ALTER SEQUENCE tbl_admin_id_seq OWNED BY tbl_admin.id;
EXCEPTION
    WHEN feature_not_supported OR object_not_in_prerequisite_state THEN
        NULL;
END $$;

CREATE TABLE IF NOT EXISTS tbl_session (
    id                  UUID PRIMARY KEY,
    subject_type        VARCHAR(32)  NOT NULL,
    subject_id          BIGINT       NOT NULL,
    role_code           VARCHAR(64)  NOT NULL,
    refresh_token_hash  VARCHAR(255) NOT NULL,
    logged_in_at        TIMESTAMP    NOT NULL,
    access_expires_at   TIMESTAMP    NOT NULL,
    refresh_expires_at  TIMESTAMP    NOT NULL,
    last_activity_at    TIMESTAMP    NOT NULL,
    revoked             BOOLEAN      NOT NULL DEFAULT FALSE,
    ip_address          VARCHAR(64),
    user_agent          VARCHAR(512),
    device              VARCHAR(128),
    device_type         VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_session_subject ON tbl_session (subject_type, subject_id);
CREATE INDEX IF NOT EXISTS idx_session_refresh ON tbl_session (refresh_token_hash);
