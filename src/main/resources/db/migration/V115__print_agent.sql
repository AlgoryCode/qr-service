ALTER TABLE tbl_branch
    ADD COLUMN IF NOT EXISTS print_kitchen_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS tbl_print_agent_device (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT       NOT NULL,
    branch_id       BIGINT       NOT NULL,
    device_name     VARCHAR(128) NOT NULL,
    device_token_hash VARCHAR(128) NOT NULL,
    printer_name    VARCHAR(255),
    agent_version   VARCHAR(64),
    last_seen_at    TIMESTAMP,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_print_agent_device_token UNIQUE (device_token_hash)
);

CREATE INDEX IF NOT EXISTS idx_print_agent_device_owner_branch
    ON tbl_print_agent_device (owner_user_id, branch_id);

CREATE TABLE IF NOT EXISTS tbl_print_pairing_code (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT       NOT NULL,
    branch_id       BIGINT       NOT NULL,
    code_hash       VARCHAR(128) NOT NULL,
    expires_at      TIMESTAMP    NOT NULL,
    used_at         TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_print_pairing_code_hash UNIQUE (code_hash)
);

CREATE INDEX IF NOT EXISTS idx_print_pairing_code_owner
    ON tbl_print_pairing_code (owner_user_id, branch_id);

CREATE TABLE IF NOT EXISTS tbl_print_job (
    id                      BIGSERIAL PRIMARY KEY,
    owner_user_id           BIGINT       NOT NULL,
    branch_id               BIGINT,
    device_id               BIGINT,
    source_type             VARCHAR(32)  NOT NULL,
    source_id               VARCHAR(64)  NOT NULL,
    job_type                VARCHAR(32)  NOT NULL,
    status                  VARCHAR(32)  NOT NULL,
    payload_json            JSONB        NOT NULL,
    idempotency_key         VARCHAR(160) NOT NULL,
    attempts                INT          NOT NULL DEFAULT 0,
    last_error              TEXT,
    claimed_by_device_id    BIGINT,
    claimed_at              TIMESTAMP,
    completed_at            TIMESTAMP,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_print_job_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_print_job_pending_branch
    ON tbl_print_job (branch_id, status, created_at);

CREATE INDEX IF NOT EXISTS idx_print_job_owner_status
    ON tbl_print_job (owner_user_id, status, created_at DESC);
