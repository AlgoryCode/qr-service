ALTER TABLE tbl_menu_category
    ADD COLUMN IF NOT EXISTS user_id BIGINT;

UPDATE tbl_menu_category c
SET user_id = m.user_id
FROM tbl_menu m
WHERE m.menu_id = c.menu_id
  AND (c.user_id IS NULL OR c.user_id <> m.user_id);

DO $$
DECLARE
    missing_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO missing_count
    FROM tbl_menu_category
    WHERE user_id IS NULL;
    IF missing_count > 0 THEN
        RAISE EXCEPTION 'V8 migration left % menu categories without user_id', missing_count;
    END IF;
END $$;

ALTER TABLE tbl_menu_category
    ALTER COLUMN user_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_menu_category_user
    ON tbl_menu_category (user_id)
    WHERE COALESCE(is_deleted, false) = false;
