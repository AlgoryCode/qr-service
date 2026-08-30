CREATE TABLE tbl_menu_category (
    id          BIGSERIAL PRIMARY KEY,
    menu_id     BIGINT NOT NULL,
    slug        VARCHAR(64) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_menu_category_menu_slug UNIQUE (menu_id, slug)
);

CREATE INDEX idx_menu_category_menu
    ON tbl_menu_category (menu_id)
    WHERE COALESCE(is_deleted, false) = false;

CREATE TABLE tbl_menu_sub_category (
    id                BIGSERIAL PRIMARY KEY,
    menu_id           BIGINT NOT NULL,
    menu_category_id  BIGINT NOT NULL,
    slug              VARCHAR(64) NOT NULL,
    name              VARCHAR(255) NOT NULL,
    sort_order        INTEGER NOT NULL DEFAULT 0,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    is_deleted        BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_menu_sub_category_menu_slug UNIQUE (menu_id, slug),
    CONSTRAINT fk_menu_sub_category_menu_category
        FOREIGN KEY (menu_category_id) REFERENCES tbl_menu_category (id)
);

CREATE INDEX idx_menu_sub_category_menu
    ON tbl_menu_sub_category (menu_id)
    WHERE COALESCE(is_deleted, false) = false;

CREATE INDEX idx_menu_sub_category_parent
    ON tbl_menu_sub_category (menu_category_id)
    WHERE COALESCE(is_deleted, false) = false;

CREATE TABLE tmp_v7_needed_sub (
    menu_id     BIGINT NOT NULL,
    old_sub_id  BIGINT NOT NULL,
    old_main_id BIGINT NOT NULL,
    PRIMARY KEY (menu_id, old_sub_id)
);

INSERT INTO tmp_v7_needed_sub (menu_id, old_sub_id, old_main_id)
SELECT DISTINCT p.menu_id, s.id, s.main_category_id
FROM tbl_menu_products p
JOIN tbl_sub_category s ON s.id = p.sub_category_id;

INSERT INTO tmp_v7_needed_sub (menu_id, old_sub_id, old_main_id)
SELECT DISTINCT p.menu_id, s.id, s.main_category_id
FROM tbl_menu_product_pairing pp
JOIN tbl_menu_products p ON p.product_id = pp.product_id
JOIN tbl_sub_category s ON s.id = pp.target_sub_category_id
WHERE pp.target_sub_category_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO tmp_v7_needed_sub (menu_id, old_sub_id, old_main_id)
SELECT DISTINCT p.menu_id, s.id, s.main_category_id
FROM tbl_menu_product_pairing pp
JOIN tbl_menu_products p ON p.product_id = pp.product_id
JOIN tbl_sub_category s ON s.main_category_id = pp.target_main_category_id
WHERE pp.target_main_category_id IS NOT NULL
ON CONFLICT DO NOTHING;

CREATE TABLE tmp_v7_main_map (
    menu_id     BIGINT NOT NULL,
    old_main_id BIGINT NOT NULL,
    new_main_id BIGINT NOT NULL,
    PRIMARY KEY (menu_id, old_main_id)
);

INSERT INTO tbl_menu_category (menu_id, slug, name, sort_order, created_at, updated_at, is_deleted)
SELECT DISTINCT
    n.menu_id,
    m.slug,
    m.name,
    COALESCE(m.sort_order, 0),
    COALESCE(m.created_at, NOW()),
    COALESCE(m.updated_at, NOW()),
    FALSE
FROM tmp_v7_needed_sub n
JOIN tbl_main_category m ON m.id = n.old_main_id
ON CONFLICT (menu_id, slug) DO NOTHING;

INSERT INTO tmp_v7_main_map (menu_id, old_main_id, new_main_id)
SELECT n.menu_id, n.old_main_id, c.id
FROM (
    SELECT DISTINCT menu_id, old_main_id FROM tmp_v7_needed_sub
) n
JOIN tbl_main_category m ON m.id = n.old_main_id
JOIN tbl_menu_category c ON c.menu_id = n.menu_id AND c.slug = m.slug;

CREATE TABLE tmp_v7_sub_map (
    menu_id    BIGINT NOT NULL,
    old_sub_id BIGINT NOT NULL,
    new_sub_id BIGINT NOT NULL,
    PRIMARY KEY (menu_id, old_sub_id)
);

INSERT INTO tbl_menu_sub_category (
    menu_id, menu_category_id, slug, name, sort_order, created_at, updated_at, is_deleted
)
SELECT
    n.menu_id,
    mm.new_main_id,
    s.slug,
    s.name,
    COALESCE(s.sort_order, 0),
    COALESCE(s.created_at, NOW()),
    COALESCE(s.updated_at, NOW()),
    FALSE
FROM tmp_v7_needed_sub n
JOIN tbl_sub_category s ON s.id = n.old_sub_id
JOIN tmp_v7_main_map mm ON mm.menu_id = n.menu_id AND mm.old_main_id = n.old_main_id
ON CONFLICT (menu_id, slug) DO NOTHING;

INSERT INTO tmp_v7_sub_map (menu_id, old_sub_id, new_sub_id)
SELECT n.menu_id, n.old_sub_id, sc.id
FROM tmp_v7_needed_sub n
JOIN tbl_sub_category s ON s.id = n.old_sub_id
JOIN tbl_menu_sub_category sc ON sc.menu_id = n.menu_id AND sc.slug = s.slug;

INSERT INTO tbl_menu_category (menu_id, slug, name, sort_order, created_at, updated_at, is_deleted)
SELECT DISTINCT p.menu_id, 'diger', 'Diğer', 9999, NOW(), NOW(), FALSE
FROM tbl_menu_products p
LEFT JOIN tmp_v7_sub_map m
    ON m.menu_id = p.menu_id AND m.old_sub_id = p.sub_category_id
WHERE m.new_sub_id IS NULL
ON CONFLICT (menu_id, slug) DO NOTHING;

INSERT INTO tbl_menu_sub_category (
    menu_id, menu_category_id, slug, name, sort_order, created_at, updated_at, is_deleted
)
SELECT c.menu_id, c.id, 'diger', 'Diğer', 9999, NOW(), NOW(), FALSE
FROM tbl_menu_category c
WHERE c.slug = 'diger'
  AND EXISTS (
        SELECT 1
        FROM tbl_menu_products p
        LEFT JOIN tmp_v7_sub_map m
            ON m.menu_id = p.menu_id AND m.old_sub_id = p.sub_category_id
        WHERE p.menu_id = c.menu_id
          AND m.new_sub_id IS NULL
  )
ON CONFLICT (menu_id, slug) DO NOTHING;

ALTER TABLE tbl_menu_products
    DROP CONSTRAINT IF EXISTS fk_menu_products_descriptor_category;

ALTER TABLE tbl_menu_products
    DROP COLUMN IF EXISTS descriptor_category_id;

ALTER TABLE tbl_menu_products
    DROP CONSTRAINT IF EXISTS fk_menu_products_sub_category;

ALTER TABLE tbl_menu_products
    DROP CONSTRAINT IF EXISTS fk_menu_product_sub_category;

ALTER TABLE tbl_menu_products
    DROP CONSTRAINT IF EXISTS fk_menu_products_menu_sub_category;

UPDATE tbl_menu_products p
SET sub_category_id = m.new_sub_id
FROM tmp_v7_sub_map m
WHERE p.menu_id = m.menu_id
  AND p.sub_category_id = m.old_sub_id;

UPDATE tbl_menu_products p
SET sub_category_id = sc.id
FROM tbl_menu_sub_category sc
WHERE sc.menu_id = p.menu_id
  AND sc.slug = 'diger'
  AND NOT EXISTS (
        SELECT 1
        FROM tmp_v7_sub_map m
        WHERE m.menu_id = p.menu_id
          AND m.old_sub_id = p.sub_category_id
  )
  AND NOT EXISTS (
        SELECT 1
        FROM tbl_menu_sub_category existing
        WHERE existing.id = p.sub_category_id
  );

UPDATE tbl_menu_product_pairing pp
SET target_sub_category_id = m.new_sub_id
FROM tbl_menu_products p, tmp_v7_sub_map m
WHERE pp.product_id = p.product_id
  AND m.menu_id = p.menu_id
  AND m.old_sub_id = pp.target_sub_category_id
  AND pp.target_sub_category_id IS NOT NULL;

UPDATE tbl_menu_product_pairing pp
SET target_main_category_id = m.new_main_id
FROM tbl_menu_products p, tmp_v7_main_map m
WHERE pp.product_id = p.product_id
  AND m.menu_id = p.menu_id
  AND m.old_main_id = pp.target_main_category_id
  AND pp.target_main_category_id IS NOT NULL;

DO $$
DECLARE
    orphan_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO orphan_count
    FROM tbl_menu_products p
    WHERE NOT EXISTS (
        SELECT 1 FROM tbl_menu_sub_category sc WHERE sc.id = p.sub_category_id
    );
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'V7 migration left % products without menu-scoped subcategory', orphan_count;
    END IF;
END $$;

ALTER TABLE tbl_menu_products
    ADD CONSTRAINT fk_menu_products_menu_sub_category
        FOREIGN KEY (sub_category_id) REFERENCES tbl_menu_sub_category (id);

DROP TABLE tmp_v7_needed_sub;
DROP TABLE tmp_v7_main_map;
DROP TABLE tmp_v7_sub_map;
