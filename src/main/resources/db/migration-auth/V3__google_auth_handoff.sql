CREATE TABLE tbl_google_auth_handoff (
    id              UUID PRIMARY KEY,
    ticket_hash     VARCHAR(64)  NOT NULL,
    subject_type    VARCHAR(32)  NOT NULL,
    subject_id      BIGINT       NOT NULL,
    audience        VARCHAR(32)  NOT NULL,
    intent          VARCHAR(32)  NOT NULL,
    expires_at      TIMESTAMP    NOT NULL,
    consumed        BOOLEAN      NOT NULL DEFAULT FALSE,
    consumed_at     TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_google_auth_handoff_hash UNIQUE (ticket_hash)
);

CREATE INDEX idx_google_auth_handoff_subject ON tbl_google_auth_handoff (subject_type, subject_id);
