ALTER TABLE tbl_restaurant_area
    ADD COLUMN IF NOT EXISTS user_id bigint;

ALTER TABLE tbl_restaurant_area
    ALTER COLUMN menu_id DROP NOT NULL;

UPDATE tbl_restaurant_area AS area
SET user_id = menu.user_id
FROM tbl_menu AS menu
WHERE area.menu_id = menu.menu_id
  AND area.user_id IS NULL;

ALTER TABLE tbl_restaurant_table
    ADD COLUMN IF NOT EXISTS user_id bigint;

ALTER TABLE tbl_restaurant_table
    ALTER COLUMN menu_id DROP NOT NULL;

UPDATE tbl_restaurant_table AS layout_table
SET user_id = menu.user_id
FROM tbl_menu AS menu
WHERE layout_table.menu_id = menu.menu_id
  AND layout_table.user_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_restaurant_area_user
    ON tbl_restaurant_area (user_id);

CREATE INDEX IF NOT EXISTS idx_restaurant_table_user
    ON tbl_restaurant_table (user_id)
    WHERE COALESCE(deleted, false) = false;
