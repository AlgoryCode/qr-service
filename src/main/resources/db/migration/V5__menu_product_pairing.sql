CREATE TABLE tbl_menu_product_pairing (
    id                      BIGSERIAL PRIMARY KEY,
    product_id              BIGINT NOT NULL,
    target_product_id       BIGINT,
    target_sub_category_id  BIGINT,
    target_main_category_id BIGINT,
    sort_order              INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_menu_product_pairing_one_target CHECK (
        ((target_product_id IS NOT NULL)::int
            + (target_sub_category_id IS NOT NULL)::int
            + (target_main_category_id IS NOT NULL)::int) = 1
    )
);

CREATE INDEX idx_menu_product_pairing_product
    ON tbl_menu_product_pairing (product_id);

CREATE UNIQUE INDEX uk_menu_product_pairing_target_product
    ON tbl_menu_product_pairing (product_id, target_product_id)
    WHERE target_product_id IS NOT NULL;

CREATE UNIQUE INDEX uk_menu_product_pairing_target_sub
    ON tbl_menu_product_pairing (product_id, target_sub_category_id)
    WHERE target_sub_category_id IS NOT NULL;

CREATE UNIQUE INDEX uk_menu_product_pairing_target_main
    ON tbl_menu_product_pairing (product_id, target_main_category_id)
    WHERE target_main_category_id IS NOT NULL;

ALTER TABLE tbl_menu_product_pairing
    ADD CONSTRAINT fk_menu_product_pairing_product
        FOREIGN KEY (product_id) REFERENCES tbl_menu_products (product_id) ON DELETE CASCADE;

ALTER TABLE tbl_menu_product_pairing
    ADD CONSTRAINT fk_menu_product_pairing_target_product
        FOREIGN KEY (target_product_id) REFERENCES tbl_menu_products (product_id) ON DELETE CASCADE;
