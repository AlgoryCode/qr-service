-- Admin panel security: two-factor sign-in, last login, and an audit trail of every change made
-- through the panel.
ALTER TABLE tbl_dashboard_user ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(64);
ALTER TABLE tbl_dashboard_user ADD COLUMN IF NOT EXISTS totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tbl_dashboard_user ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS admin_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    actor_id        BIGINT,
    actor_name      VARCHAR(255),
    actor_role      VARCHAR(32),
    method          VARCHAR(10)    NOT NULL,
    path            VARCHAR(512)   NOT NULL,
    action          VARCHAR(128)   NOT NULL,
    target_type     VARCHAR(64),
    target_id       VARCHAR(64),
    status          INT            NOT NULL,
    outcome         VARCHAR(16)    NOT NULL,
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(512),
    request_body    TEXT,
    created_at      TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_log_created ON admin_audit_log (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_log_actor ON admin_audit_log (actor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_log_target ON admin_audit_log (target_type, target_id);

-- Admin roles beyond ADMIN (the generated check constraint only allowed ADMIN).
ALTER TABLE tbl_dashboard_user DROP CONSTRAINT IF EXISTS tbl_dashboard_user_role_check;
ALTER TABLE tbl_dashboard_user ADD CONSTRAINT tbl_dashboard_user_role_check
    CHECK (role IN ('ADMIN', 'FINANCE', 'SUPPORT', 'VIEWER'));
