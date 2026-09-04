-- Sequence hizalama: prod'da eski importlar sequence değerlerini geride bırakmış.
SELECT setval('tbl_menu_category_id_seq', COALESCE((SELECT MAX(id) FROM tbl_menu_category), 1), TRUE);
SELECT setval('tbl_menu_sub_category_id_seq', COALESCE((SELECT MAX(id) FROM tbl_menu_sub_category), 1), TRUE);

DO $$
DECLARE
    r RECORD;
    category_id BIGINT;
    new_sub_category_id BIGINT;
    drink_count INTEGER := 0;
BEGIN
    FOR r IN
        SELECT p.product_id, p.name, p.sort_order, p.menu_id
        FROM tbl_menu_products p
        JOIN tbl_menu_sub_category sc ON sc.id = p.sub_category_id
        JOIN tbl_menu_category c ON c.id = sc.menu_category_id
        WHERE p.menu_id = 16
          AND c.id = 39
          AND COALESCE(p.is_deleted, FALSE) = FALSE
        ORDER BY p.sort_order, p.product_id
    LOOP
        INSERT INTO tbl_menu_category
            (menu_id, user_id, slug, name, sort_order, created_at, updated_at, is_deleted)
        VALUES
            (16, 20, 'icecek-' || r.product_id, r.name, r.sort_order, NOW(), NOW(), FALSE)
        ON CONFLICT (menu_id, slug) DO UPDATE
            SET user_id = EXCLUDED.user_id,
                name = EXCLUDED.name,
                sort_order = EXCLUDED.sort_order,
                updated_at = NOW(),
                is_deleted = FALSE
        RETURNING id INTO category_id;

        INSERT INTO tbl_menu_sub_category
            (menu_id, menu_category_id, slug, name, sort_order, created_at, updated_at, is_deleted)
        VALUES
            (16, category_id, 'icecek-' || r.product_id, r.name, 0, NOW(), NOW(), FALSE)
        ON CONFLICT (menu_id, slug) DO UPDATE
            SET menu_category_id = EXCLUDED.menu_category_id,
                name = EXCLUDED.name,
                updated_at = NOW(),
                is_deleted = FALSE
        RETURNING id INTO new_sub_category_id;

        UPDATE tbl_menu_products
        SET sub_category_id = new_sub_category_id,
            updated_at = NOW()
        WHERE product_id = r.product_id
          AND menu_id = 16;

        drink_count := drink_count + 1;
    END LOOP;

    IF drink_count = 0 THEN
        RAISE EXCEPTION 'No active drink products found for menu 16';
    END IF;
END $$;

UPDATE tbl_menu_sub_category
SET is_deleted = TRUE, updated_at = NOW()
WHERE menu_id = 16 AND menu_category_id = 39;

UPDATE tbl_menu_category
SET is_deleted = TRUE, updated_at = NOW()
WHERE menu_id = 16 AND id = 39;
