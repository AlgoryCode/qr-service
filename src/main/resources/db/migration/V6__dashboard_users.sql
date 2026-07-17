CREATE TABLE IF NOT EXISTS tbl_dashboard_user (
    id              BIGSERIAL PRIMARY KEY,
    first_name      VARCHAR(255) NOT NULL,
    last_name       VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password        VARCHAR(255) NOT NULL,
    role            VARCHAR(32)  NOT NULL DEFAULT 'ADMIN',
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    CONSTRAINT uk_dashboard_user_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS tbl_dashboard_user_session (
    id                  UUID PRIMARY KEY,
    dashboard_user_id   BIGINT       NOT NULL,
    refresh_token_hash  VARCHAR(255) NOT NULL,
    logged_in_at        TIMESTAMP    NOT NULL,
    access_expires_at   TIMESTAMP    NOT NULL,
    refresh_expires_at  TIMESTAMP    NOT NULL,
    last_activity_at    TIMESTAMP    NOT NULL,
    revoked             BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked_at          TIMESTAMP,
    ip_address          VARCHAR(255),
    user_agent          VARCHAR(512),
    device              VARCHAR(255),
    device_type         VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_dashboard_user_session_user_id
    ON tbl_dashboard_user_session (dashboard_user_id);

CREATE INDEX IF NOT EXISTS idx_dashboard_user_session_revoked
    ON tbl_dashboard_user_session (revoked);

INSERT INTO tbl_dashboard_user (first_name, last_name, email, password, role, active, created_at, updated_at)
SELECT
    COALESCE(u.first_name, 'Admin'),
    COALESCE(u.last_name, 'User'),
    LOWER(u.email),
    u.password,
    'ADMIN',
    TRUE,
    NOW(),
    NOW()
FROM tbl_user u
WHERE u.role = 'ADMIN'
  AND u.password IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM tbl_dashboard_user d WHERE LOWER(d.email) = LOWER(u.email)
  );

UPDATE tbl_user
SET role = 'USER'
WHERE role = 'ADMIN';
