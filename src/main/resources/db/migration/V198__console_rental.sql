-- Konsol Müşteri Takip: saatlik oyun konsolu kiralama.

-- Şubenin varsayılan saatlik ücreti; konsolda ücret yoksa bu kullanılır.
CREATE TABLE tbl_console_rate_setting (
    id                   BIGSERIAL PRIMARY KEY,
    merchant_id          BIGINT         NOT NULL,
    branch_id            BIGINT         NOT NULL,
    default_hourly_rate  NUMERIC(12, 2),
    created_at           TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted           BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_console_rate_setting_branch UNIQUE (branch_id),
    CONSTRAINT chk_console_rate_setting_rate CHECK (default_hourly_rate IS NULL OR default_hourly_rate >= 0)
);

CREATE TABLE tbl_game_console (
    id           BIGSERIAL PRIMARY KEY,
    merchant_id  BIGINT         NOT NULL,
    branch_id    BIGINT         NOT NULL,
    name         VARCHAR(120)   NOT NULL,
    hourly_rate  NUMERIC(12, 2),
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted   BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_game_console_rate CHECK (hourly_rate IS NULL OR hourly_rate >= 0)
);

CREATE INDEX idx_game_console_branch
    ON tbl_game_console (branch_id, merchant_id)
    WHERE is_deleted = FALSE;

CREATE TABLE tbl_console_session (
    id                BIGSERIAL PRIMARY KEY,
    merchant_id       BIGINT         NOT NULL,
    branch_id         BIGINT         NOT NULL,
    console_id        BIGINT         NOT NULL,
    customer_name     VARCHAR(120),
    note              VARCHAR(500),
    hourly_rate       NUMERIC(12, 2) NOT NULL,
    started_at        TIMESTAMP      NOT NULL,
    ended_at          TIMESTAMP,
    duration_minutes  INTEGER,
    amount            NUMERIC(12, 2),
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    is_deleted        BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_console_session_console
        FOREIGN KEY (console_id) REFERENCES tbl_game_console (id)
);

-- Bir konsolda aynı anda tek açık oturum.
CREATE UNIQUE INDEX uq_console_session_active
    ON tbl_console_session (console_id)
    WHERE ended_at IS NULL AND is_deleted = FALSE;

CREATE INDEX idx_console_session_branch_started
    ON tbl_console_session (branch_id, merchant_id, started_at DESC, id DESC)
    WHERE is_deleted = FALSE;
