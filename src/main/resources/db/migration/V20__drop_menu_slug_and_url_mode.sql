UPDATE tbl_menu
SET public_slug = NULL
WHERE public_slug IS NOT NULL;

ALTER TABLE tbl_menu DROP COLUMN IF EXISTS public_slug;
ALTER TABLE tbl_menu DROP COLUMN IF EXISTS url_mode;
