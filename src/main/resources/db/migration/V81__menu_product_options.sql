CREATE TABLE tbl_menu_product_option_group (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT NOT NULL,
    name        VARCHAR(120) NOT NULL,
    min_select  INTEGER NOT NULL DEFAULT 0,
    max_select  INTEGER NOT NULL DEFAULT 1,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_menu_product_option_group_select
        CHECK (min_select >= 0 AND max_select >= 1 AND min_select <= max_select)
);

CREATE INDEX idx_menu_product_option_group_product
    ON tbl_menu_product_option_group (product_id);

ALTER TABLE tbl_menu_product_option_group
    ADD CONSTRAINT fk_menu_product_option_group_product
        FOREIGN KEY (product_id) REFERENCES tbl_menu_products (product_id) ON DELETE CASCADE;

CREATE TABLE tbl_menu_product_option (
    id           BIGSERIAL PRIMARY KEY,
    group_id     BIGINT NOT NULL,
    name         VARCHAR(120) NOT NULL,
    price_delta  NUMERIC(12, 2) NOT NULL DEFAULT 0,
    available    BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order   INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_menu_product_option_group
    ON tbl_menu_product_option (group_id);

ALTER TABLE tbl_menu_product_option
    ADD CONSTRAINT fk_menu_product_option_group
        FOREIGN KEY (group_id) REFERENCES tbl_menu_product_option_group (id) ON DELETE CASCADE;

ALTER TABLE tbl_menu_order_item
    ADD COLUMN selected_options JSONB NOT NULL DEFAULT '[]'::jsonb;
