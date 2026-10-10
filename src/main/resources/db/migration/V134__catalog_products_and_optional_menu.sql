ALTER TABLE tbl_menu
    ALTER COLUMN branch_id DROP NOT NULL;

ALTER TABLE tbl_menu_products
    ALTER COLUMN menu_id DROP NOT NULL;

ALTER TABLE tbl_menu_products
    ALTER COLUMN sub_category_id DROP NOT NULL;

ALTER TABLE tbl_menu_products
    ADD COLUMN IF NOT EXISTS user_id bigint;

UPDATE tbl_menu_products p
SET user_id = m.user_id
FROM tbl_menu m
WHERE p.menu_id = m.menu_id
  AND p.user_id IS NULL;
