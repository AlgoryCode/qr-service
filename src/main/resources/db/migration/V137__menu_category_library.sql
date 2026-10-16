ALTER TABLE tbl_menu_category
    ALTER COLUMN menu_id DROP NOT NULL;

ALTER TABLE tbl_menu_sub_category
    ALTER COLUMN menu_id DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_menu_category_library_user_slug
    ON tbl_menu_category (user_id, slug)
    WHERE menu_id IS NULL AND COALESCE(is_deleted, false) = false;

CREATE UNIQUE INDEX IF NOT EXISTS uk_menu_sub_category_library_parent_slug
    ON tbl_menu_sub_category (menu_category_id, slug)
    WHERE menu_id IS NULL AND COALESCE(is_deleted, false) = false;

CREATE INDEX IF NOT EXISTS idx_menu_category_library_user
    ON tbl_menu_category (user_id)
    WHERE menu_id IS NULL AND COALESCE(is_deleted, false) = false;
