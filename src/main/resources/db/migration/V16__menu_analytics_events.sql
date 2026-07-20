CREATE TABLE tbl_menu_analytics_session (
    id              UUID         PRIMARY KEY,
    menu_id         BIGINT       NOT NULL,
    started_at      TIMESTAMP    NOT NULL,
    last_seen_at    TIMESTAMP    NOT NULL,
    device_type     VARCHAR(16)  NOT NULL,
    ip_hash         VARCHAR(64),
    user_agent      VARCHAR(512)
);

CREATE INDEX idx_menu_analytics_session_menu_started
    ON tbl_menu_analytics_session (menu_id, started_at);

CREATE TABLE tbl_menu_analytics_event (
    id              BIGSERIAL PRIMARY KEY,
    session_id      UUID         NOT NULL REFERENCES tbl_menu_analytics_session (id),
    menu_id         BIGINT       NOT NULL,
    event_type      VARCHAR(32)  NOT NULL,
    category_id     BIGINT,
    product_id      BIGINT,
    sequence        INT          NOT NULL DEFAULT 0,
    occurred_at     TIMESTAMP    NOT NULL,
    CONSTRAINT tbl_menu_analytics_event_type_check
        CHECK (event_type IN ('MENU_OPEN', 'CATEGORY_VIEW', 'PRODUCT_VIEW'))
);

CREATE INDEX idx_menu_analytics_event_menu_occurred
    ON tbl_menu_analytics_event (menu_id, occurred_at);

CREATE INDEX idx_menu_analytics_event_session_seq
    ON tbl_menu_analytics_event (session_id, sequence);

CREATE INDEX idx_menu_analytics_event_menu_type_product
    ON tbl_menu_analytics_event (menu_id, event_type, product_id);
