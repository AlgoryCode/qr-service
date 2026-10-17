CREATE TABLE IF NOT EXISTS tbl_demo_accounts (
    id                  BIGSERIAL PRIMARY KEY,
    username            VARCHAR(64)  NOT NULL,
    user_id             BIGINT       NOT NULL REFERENCES tbl_merchant (id),
    trial_package_code  VARCHAR(64)  NOT NULL DEFAULT 'ULTIMATE_TRIAL_PACKAGE',
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMP    NOT NULL,
    CONSTRAINT uk_demo_accounts_username UNIQUE (username),
    CONSTRAINT uk_demo_accounts_user_id UNIQUE (user_id)
);

CREATE INDEX IF NOT EXISTS idx_demo_accounts_expires_at ON tbl_demo_accounts (expires_at);
