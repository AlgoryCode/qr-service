BEGIN;

WITH base AS (
    SELECT COALESCE(MAX(sort_order), 0) AS value
    FROM tbl_menu_category
    WHERE menu_id = 16
      AND is_deleted = FALSE
      AND slug NOT LIKE 'drink-%'
), ranked AS (
    SELECT c.id,
           base.value + ROW_NUMBER() OVER (ORDER BY c.id) AS new_sort_order
    FROM tbl_menu_category c
    CROSS JOIN base
    WHERE c.menu_id = 16
      AND c.is_deleted = FALSE
      AND c.slug LIKE 'drink-%'
)
UPDATE tbl_menu_category c
SET sort_order = ranked.new_sort_order,
    updated_at = NOW()
FROM ranked
WHERE c.id = ranked.id;

COMMIT;
