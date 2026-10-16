ALTER TABLE tbl_menu_product_option_group
    ALTER COLUMN product_id DROP NOT NULL;

ALTER TABLE tbl_menu_product_option_group
    ADD COLUMN IF NOT EXISTS user_id BIGINT;

ALTER TABLE tbl_menu_product_option_group
    ADD COLUMN IF NOT EXISTS library_group_id BIGINT;

ALTER TABLE tbl_menu_product_option_group
    DROP CONSTRAINT IF EXISTS ck_menu_product_option_group_owner;

ALTER TABLE tbl_menu_product_option_group
    ADD CONSTRAINT ck_menu_product_option_group_owner
        CHECK (product_id IS NOT NULL OR user_id IS NOT NULL);

CREATE INDEX IF NOT EXISTS idx_menu_product_option_group_user
    ON tbl_menu_product_option_group (user_id);

CREATE INDEX IF NOT EXISTS idx_menu_product_option_group_library
    ON tbl_menu_product_option_group (library_group_id);

ALTER TABLE tbl_menu_product_option_group
    DROP CONSTRAINT IF EXISTS fk_menu_product_option_group_library;

ALTER TABLE tbl_menu_product_option_group
    ADD CONSTRAINT fk_menu_product_option_group_library
        FOREIGN KEY (library_group_id) REFERENCES tbl_menu_product_option_group (id) ON DELETE SET NULL;
