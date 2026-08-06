ALTER TABLE tbl_menu_products
    ADD COLUMN IF NOT EXISTS serves_people_min INTEGER,
    ADD COLUMN IF NOT EXISTS serves_people_max INTEGER;

ALTER TABLE tbl_menu_products
    DROP CONSTRAINT IF EXISTS chk_menu_products_serves_people;

ALTER TABLE tbl_menu_products
    ADD CONSTRAINT chk_menu_products_serves_people
        CHECK (
            (serves_people_min IS NULL AND serves_people_max IS NULL)
                OR (
                serves_people_min IS NOT NULL
                    AND serves_people_max IS NOT NULL
                    AND serves_people_min >= 1
                    AND serves_people_max >= serves_people_min
                )
            );

CREATE INDEX IF NOT EXISTS idx_menu_products_menu_serves
    ON tbl_menu_products (menu_id, serves_people_min, serves_people_max)
    WHERE is_deleted = FALSE;

UPDATE tbl_menu_products
SET serves_people_min = 2,
    serves_people_max = 2
WHERE is_deleted = FALSE
  AND serves_people_min IS NULL
  AND (
    name ILIKE '%(2 Kişilik)%'
        OR name ILIKE '%(2 Kisilik)%'
        OR name ILIKE '%(2 kişilik)%'
    );

UPDATE tbl_menu_products
SET serves_people_min = 1,
    serves_people_max = 1
WHERE is_deleted = FALSE
  AND serves_people_min IS NULL
  AND (
    name ILIKE '%Tek Kişilik%'
        OR name ILIKE '%Tek Kisilik%'
        OR name ILIKE '%Tek kişilik%'
    );

UPDATE tbl_menu_products
SET serves_people_min = 1,
    serves_people_max = 1
WHERE is_deleted = FALSE
  AND serves_people_min IS NULL
  AND serves_people_max IS NULL;

ALTER TABLE tbl_menu_analytics_event
    DROP CONSTRAINT IF EXISTS tbl_menu_analytics_event_type_check;

ALTER TABLE tbl_menu_analytics_event
    ADD CONSTRAINT tbl_menu_analytics_event_type_check
        CHECK (event_type IN ('MENU_OPEN', 'CATEGORY_VIEW', 'PRODUCT_VIEW', 'SERVES_FILTER'));

ALTER TABLE tbl_menu_analytics_event
    ADD COLUMN IF NOT EXISTS serves_people INTEGER;
