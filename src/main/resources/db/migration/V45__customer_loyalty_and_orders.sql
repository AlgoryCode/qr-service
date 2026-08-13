CREATE TABLE IF NOT EXISTS tbl_customer (
    id               BIGSERIAL PRIMARY KEY,
    first_name       VARCHAR(255) NOT NULL,
    last_name        VARCHAR(255),
    email            VARCHAR(255) NOT NULL,
    phone            VARCHAR(255),
    password         VARCHAR(255),
    provider         VARCHAR(16)  NOT NULL DEFAULT 'BASIC',
    provider_subject VARCHAR(128),
    avatar_key       VARCHAR(64),
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    CONSTRAINT uk_customer_email UNIQUE (email),
    CONSTRAINT uk_customer_phone UNIQUE (phone),
    CONSTRAINT uk_customer_provider_subject UNIQUE (provider_subject)
);

CREATE TABLE IF NOT EXISTS tbl_customer_session (
    id                  UUID PRIMARY KEY,
    customer_id         BIGINT       NOT NULL,
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
    device_type         VARCHAR(255),
    CONSTRAINT fk_customer_session_customer
        FOREIGN KEY (customer_id) REFERENCES tbl_customer (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_customer_session_customer_id
    ON tbl_customer_session (customer_id);

CREATE INDEX IF NOT EXISTS idx_customer_session_revoked
    ON tbl_customer_session (revoked);

CREATE TABLE IF NOT EXISTS tbl_customer_membership (
    id          BIGSERIAL PRIMARY KEY,
    customer_id BIGINT      NOT NULL,
    menu_id     BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at   TIMESTAMP   NOT NULL,
    CONSTRAINT uk_customer_membership_customer_menu UNIQUE (customer_id, menu_id),
    CONSTRAINT chk_customer_membership_status
        CHECK (status IN ('ACTIVE', 'LEFT')),
    CONSTRAINT fk_customer_membership_customer
        FOREIGN KEY (customer_id) REFERENCES tbl_customer (id) ON DELETE CASCADE,
    CONSTRAINT fk_customer_membership_menu
        FOREIGN KEY (menu_id) REFERENCES tbl_menu (menu_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_customer_membership_menu_id
    ON tbl_customer_membership (menu_id);

CREATE INDEX IF NOT EXISTS idx_customer_membership_customer_id
    ON tbl_customer_membership (customer_id);

CREATE TABLE IF NOT EXISTS tbl_restaurant_table (
    id               BIGSERIAL PRIMARY KEY,
    menu_id          BIGINT       NOT NULL,
    name             VARCHAR(120) NOT NULL,
    table_number     INT,
    public_token     VARCHAR(64)  NOT NULL,
    qr_image_base64  TEXT,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL,
    CONSTRAINT uk_restaurant_table_public_token UNIQUE (public_token),
    CONSTRAINT fk_restaurant_table_menu
        FOREIGN KEY (menu_id) REFERENCES tbl_menu (menu_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_restaurant_table_menu_id
    ON tbl_restaurant_table (menu_id);

CREATE TABLE IF NOT EXISTS tbl_table_session (
    id             UUID PRIMARY KEY,
    table_id       BIGINT       NOT NULL,
    menu_id        BIGINT       NOT NULL,
    session_token  VARCHAR(64)  NOT NULL,
    expires_at     TIMESTAMP    NOT NULL,
    revoked        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL,
    CONSTRAINT uk_table_session_token UNIQUE (session_token),
    CONSTRAINT fk_table_session_table
        FOREIGN KEY (table_id) REFERENCES tbl_restaurant_table (id) ON DELETE CASCADE,
    CONSTRAINT fk_table_session_menu
        FOREIGN KEY (menu_id) REFERENCES tbl_menu (menu_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_table_session_table_id
    ON tbl_table_session (table_id);

CREATE INDEX IF NOT EXISTS idx_table_session_menu_id
    ON tbl_table_session (menu_id);

CREATE INDEX IF NOT EXISTS idx_table_session_expires_at
    ON tbl_table_session (expires_at);

CREATE TABLE IF NOT EXISTS tbl_menu_order (
    id                BIGSERIAL PRIMARY KEY,
    menu_id           BIGINT         NOT NULL,
    table_id          BIGINT         NOT NULL,
    table_session_id  UUID           NOT NULL,
    customer_id       BIGINT,
    status            VARCHAR(20)    NOT NULL,
    total_amount      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    currency          VARCHAR(8)     NOT NULL DEFAULT 'TRY',
    note              TEXT,
    submitted_at      TIMESTAMP,
    confirmed_at      TIMESTAMP,
    rejected_at       TIMESTAMP,
    created_at        TIMESTAMP      NOT NULL,
    updated_at        TIMESTAMP      NOT NULL,
    CONSTRAINT chk_menu_order_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'CONFIRMED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT fk_menu_order_menu
        FOREIGN KEY (menu_id) REFERENCES tbl_menu (menu_id) ON DELETE CASCADE,
    CONSTRAINT fk_menu_order_table
        FOREIGN KEY (table_id) REFERENCES tbl_restaurant_table (id) ON DELETE CASCADE,
    CONSTRAINT fk_menu_order_table_session
        FOREIGN KEY (table_session_id) REFERENCES tbl_table_session (id) ON DELETE CASCADE,
    CONSTRAINT fk_menu_order_customer
        FOREIGN KEY (customer_id) REFERENCES tbl_customer (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_menu_order_menu_status
    ON tbl_menu_order (menu_id, status);

CREATE INDEX IF NOT EXISTS idx_menu_order_customer_menu
    ON tbl_menu_order (customer_id, menu_id);

CREATE INDEX IF NOT EXISTS idx_menu_order_table_session
    ON tbl_menu_order (table_session_id);

CREATE INDEX IF NOT EXISTS idx_menu_order_submitted_at
    ON tbl_menu_order (submitted_at);

CREATE TABLE IF NOT EXISTS tbl_menu_order_item (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT         NOT NULL,
    product_id    BIGINT         NOT NULL,
    product_name  VARCHAR(255)   NOT NULL,
    unit_price    NUMERIC(12, 2) NOT NULL,
    quantity      INT            NOT NULL,
    note          TEXT,
    line_total    NUMERIC(12, 2) NOT NULL,
    CONSTRAINT chk_menu_order_item_quantity
        CHECK (quantity > 0),
    CONSTRAINT fk_menu_order_item_order
        FOREIGN KEY (order_id) REFERENCES tbl_menu_order (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_menu_order_item_order_id
    ON tbl_menu_order_item (order_id);
