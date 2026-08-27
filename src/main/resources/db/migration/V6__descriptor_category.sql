CREATE TABLE tbl_descriptor_category (
    id                BIGINT PRIMARY KEY,
    sub_category_id   BIGINT NOT NULL,
    slug              VARCHAR(64) NOT NULL,
    name              VARCHAR(255) NOT NULL,
    sort_order        INTEGER NOT NULL DEFAULT 0,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    is_deleted        BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_descriptor_category_slug UNIQUE (slug)
);

CREATE INDEX idx_descriptor_category_sub
    ON tbl_descriptor_category (sub_category_id);

ALTER TABLE tbl_menu_products
    ADD COLUMN IF NOT EXISTS descriptor_category_id BIGINT NULL;

ALTER TABLE tbl_menu_products
    ADD CONSTRAINT fk_menu_products_descriptor_category
        FOREIGN KEY (descriptor_category_id) REFERENCES tbl_descriptor_category (id);
