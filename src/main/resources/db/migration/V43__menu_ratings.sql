ALTER TABLE tbl_menu
    ADD COLUMN IF NOT EXISTS rating_avg NUMERIC(3, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rating_count BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS tbl_menu_rating (
    id           BIGSERIAL PRIMARY KEY,
    menu_id      BIGINT       NOT NULL,
    score        SMALLINT     NOT NULL,
    comment      VARCHAR(500),
    ip_address   VARCHAR(45)  NOT NULL,
    user_agent   TEXT,
    device_type  VARCHAR(10)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    CONSTRAINT chk_menu_rating_score CHECK (score BETWEEN 1 AND 5),
    CONSTRAINT fk_menu_rating_menu
        FOREIGN KEY (menu_id) REFERENCES tbl_menu (menu_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_menu_rating_menu_ip
    ON tbl_menu_rating (menu_id, ip_address);

CREATE INDEX IF NOT EXISTS idx_menu_rating_menu
    ON tbl_menu_rating (menu_id);

CREATE INDEX IF NOT EXISTS idx_menu_rating_menu_created
    ON tbl_menu_rating (menu_id, created_at);

CREATE INDEX IF NOT EXISTS idx_menu_product_rating_menu_created
    ON tbl_menu_product_rating (menu_id, created_at);
