-- Banners shown at the top of the member app.
CREATE TABLE IF NOT EXISTS platform_announcement (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(120)  NOT NULL,
    body         VARCHAR(600),
    link_url     VARCHAR(500),
    link_label   VARCHAR(40),
    tone         VARCHAR(16)   NOT NULL DEFAULT 'INFO' CHECK (tone IN ('INFO', 'SUCCESS', 'WARNING')),
    audience     VARCHAR(16)   NOT NULL DEFAULT 'ALL' CHECK (audience IN ('ALL', 'PAYING', 'FREE')),
    starts_at    TIMESTAMP     NOT NULL,
    ends_at      TIMESTAMP,
    dismissible  BOOLEAN       NOT NULL DEFAULT TRUE,
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by   VARCHAR(128),
    created_at   TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP     NOT NULL DEFAULT now(),
    CHECK (ends_at IS NULL OR ends_at > starts_at)
);
CREATE INDEX IF NOT EXISTS idx_platform_announcement_window ON platform_announcement (active, starts_at);

-- Help centre questions shown in the member app.
CREATE TABLE IF NOT EXISTS faq_entry (
    id          BIGSERIAL PRIMARY KEY,
    category    VARCHAR(60)   NOT NULL,
    question    VARCHAR(240)  NOT NULL,
    answer      TEXT          NOT NULL,
    sort_order  INTEGER       NOT NULL DEFAULT 0,
    published   BOOLEAN       NOT NULL DEFAULT TRUE,
    updated_by  VARCHAR(128),
    updated_at  TIMESTAMP     NOT NULL DEFAULT now()
);
