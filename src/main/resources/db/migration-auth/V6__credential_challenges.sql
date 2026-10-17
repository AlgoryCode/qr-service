CREATE TABLE tbl_password_change_code (
    id           BIGSERIAL PRIMARY KEY,
    merchant_id  BIGINT       NOT NULL,
    email        VARCHAR(320) NOT NULL,
    code_hash    VARCHAR(255) NOT NULL,
    status       VARCHAR(32)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    expires_at   TIMESTAMP    NOT NULL,
    used_at      TIMESTAMP,
    revoked      BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked_at   TIMESTAMP
);

CREATE INDEX idx_pwd_change_code_merchant ON tbl_password_change_code (merchant_id);
CREATE INDEX idx_pwd_change_code_status ON tbl_password_change_code (status);
