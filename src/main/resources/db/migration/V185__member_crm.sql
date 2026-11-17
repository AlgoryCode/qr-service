-- Admin-side notes, tags and manual grants on a member (merchant).
CREATE TABLE IF NOT EXISTS member_note (
    id          BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT       NOT NULL,
    body        TEXT         NOT NULL,
    pinned      BOOLEAN      NOT NULL DEFAULT FALSE,
    author_id   BIGINT,
    author_name VARCHAR(128),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_member_note_merchant ON member_note (merchant_id, pinned DESC, created_at DESC);

CREATE TABLE IF NOT EXISTS member_tag (
    merchant_id BIGINT      NOT NULL,
    tag         VARCHAR(40) NOT NULL,
    created_by  VARCHAR(128),
    created_at  TIMESTAMP   NOT NULL,
    PRIMARY KEY (merchant_id, tag)
);
CREATE INDEX IF NOT EXISTS idx_member_tag_tag ON member_tag (tag);

CREATE TABLE IF NOT EXISTS admin_grant (
    id           BIGSERIAL PRIMARY KEY,
    merchant_id  BIGINT       NOT NULL,
    purchase_id  BIGINT       NOT NULL,
    product_id   BIGINT       NOT NULL,
    product_code VARCHAR(64)  NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity     INT          NOT NULL,
    days         INT,
    reason       TEXT         NOT NULL,
    granted_by   VARCHAR(128) NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    expires_at   TIMESTAMP,
    revoked_at   TIMESTAMP,
    revoked_by   VARCHAR(128)
);
CREATE INDEX IF NOT EXISTS idx_admin_grant_merchant ON admin_grant (merchant_id, created_at DESC);
