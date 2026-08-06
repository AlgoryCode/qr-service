INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted)
VALUES (82, 10, 'dondurmalar', 'Dondurmalar', 5, NOW(), NOW(), FALSE)
ON CONFLICT (id) DO UPDATE SET
    main_category_id = EXCLUDED.main_category_id,
    slug = EXCLUDED.slug,
    name = EXCLUDED.name,
    sort_order = EXCLUDED.sort_order,
    updated_at = NOW(),
    is_deleted = FALSE;

INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted)
VALUES (83, 10, 'soguk_tatlilar', 'Soğuk Tatlılar', 6, NOW(), NOW(), FALSE)
ON CONFLICT (id) DO UPDATE SET
    main_category_id = EXCLUDED.main_category_id,
    slug = EXCLUDED.slug,
    name = EXCLUDED.name,
    sort_order = EXCLUDED.sort_order,
    updated_at = NOW(),
    is_deleted = FALSE;

INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted)
VALUES (84, 10, 'cikolatali_tatlilar', 'Çikolatalı Tatlılar', 7, NOW(), NOW(), FALSE)
ON CONFLICT (id) DO UPDATE SET
    main_category_id = EXCLUDED.main_category_id,
    slug = EXCLUDED.slug,
    name = EXCLUDED.name,
    sort_order = EXCLUDED.sort_order,
    updated_at = NOW(),
    is_deleted = FALSE;

INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted)
VALUES (85, 10, 'pasta_cesitleri', 'Pastalar', 8, NOW(), NOW(), FALSE)
ON CONFLICT (id) DO UPDATE SET
    main_category_id = EXCLUDED.main_category_id,
    slug = EXCLUDED.slug,
    name = EXCLUDED.name,
    sort_order = EXCLUDED.sort_order,
    updated_at = NOW(),
    is_deleted = FALSE;

INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted)
VALUES (86, 10, 'kek_cesitleri', 'Kekler', 9, NOW(), NOW(), FALSE)
ON CONFLICT (id) DO UPDATE SET
    main_category_id = EXCLUDED.main_category_id,
    slug = EXCLUDED.slug,
    name = EXCLUDED.name,
    sort_order = EXCLUDED.sort_order,
    updated_at = NOW(),
    is_deleted = FALSE;

UPDATE tbl_menu_products
SET sub_category_id = 82,
    updated_at = NOW()
WHERE sub_category_id = 38
  AND is_deleted = FALSE
  AND LOWER(name) LIKE '%dondurma%';

UPDATE tbl_menu_products
SET sub_category_id = 84,
    updated_at = NOW()
WHERE sub_category_id = 38
  AND is_deleted = FALSE
  AND (
      LOWER(name) LIKE '%çikolata%'
      OR LOWER(name) LIKE '%cikolata%'
      OR LOWER(name) LIKE '%profiterol%'
  );

UPDATE tbl_menu_products
SET sub_category_id = 83,
    updated_at = NOW()
WHERE sub_category_id = 38
  AND is_deleted = FALSE;

UPDATE tbl_menu_products
SET sub_category_id = 84,
    updated_at = NOW()
WHERE sub_category_id = 39
  AND is_deleted = FALSE
  AND (
      LOWER(name) LIKE '%çikolata%'
      OR LOWER(name) LIKE '%cikolata%'
  );

UPDATE tbl_menu_products
SET sub_category_id = 86,
    updated_at = NOW()
WHERE sub_category_id = 39
  AND is_deleted = FALSE
  AND LOWER(name) LIKE '%kek%';

UPDATE tbl_menu_products
SET sub_category_id = 85,
    updated_at = NOW()
WHERE sub_category_id = 39
  AND is_deleted = FALSE;

UPDATE tbl_sub_category
SET is_deleted = TRUE,
    updated_at = NOW()
WHERE id IN (38, 39);
