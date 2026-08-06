ALTER TABLE tbl_menu_products
    ADD COLUMN IF NOT EXISTS chef_recommended BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS rating_avg NUMERIC(3, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rating_count BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_menu_products_menu_chef
    ON tbl_menu_products (menu_id, chef_recommended);

UPDATE tbl_menu_products p
SET chef_recommended = TRUE
WHERE EXISTS (
    SELECT 1
    FROM tbl_menu_product_tag mpt
    JOIN tbl_menu_tag mt ON mt.id = mpt.tag_id
    WHERE mpt.product_id = p.product_id
      AND mt.slug = 'sef_ozel'
      AND mt.is_deleted = FALSE
);

CREATE TABLE IF NOT EXISTS tbl_menu_product_rating (
    id               BIGSERIAL PRIMARY KEY,
    menu_id          BIGINT       NOT NULL,
    menu_product_id  BIGINT       NOT NULL,
    score            SMALLINT     NOT NULL,
    comment          VARCHAR(500),
    ip_address       VARCHAR(45)  NOT NULL,
    user_agent       TEXT,
    device_type      VARCHAR(10)  NOT NULL,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL,
    CONSTRAINT chk_menu_product_rating_score CHECK (score BETWEEN 1 AND 5),
    CONSTRAINT fk_menu_product_rating_product
        FOREIGN KEY (menu_product_id) REFERENCES tbl_menu_products (product_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_menu_product_rating_product_ip
    ON tbl_menu_product_rating (menu_product_id, ip_address);

CREATE INDEX IF NOT EXISTS idx_menu_product_rating_menu
    ON tbl_menu_product_rating (menu_id);

CREATE INDEX IF NOT EXISTS idx_menu_product_rating_product
    ON tbl_menu_product_rating (menu_product_id);
