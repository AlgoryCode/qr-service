CREATE TABLE IF NOT EXISTS tbl_menu_category (
    category_id BIGSERIAL PRIMARY KEY,
    menu_id     BIGINT       NOT NULL,
    parent_id   BIGINT,
    name        VARCHAR(255) NOT NULL,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP(6),
    updated_at  TIMESTAMP(6),
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_menu_category_parent
        FOREIGN KEY (parent_id) REFERENCES tbl_menu_category (category_id)
);

CREATE INDEX IF NOT EXISTS idx_menu_category_menu_id
    ON tbl_menu_category (menu_id);

CREATE INDEX IF NOT EXISTS idx_menu_category_parent_id
    ON tbl_menu_category (parent_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_menu_category_menu_parent_name
    ON tbl_menu_category (menu_id, COALESCE(parent_id, 0), LOWER(name))
    WHERE is_deleted = FALSE;

ALTER TABLE tbl_menu_products
    ADD COLUMN IF NOT EXISTS category_id BIGINT;

ALTER TABLE tbl_menu_products
    DROP CONSTRAINT IF EXISTS fk_menu_product_category;

ALTER TABLE tbl_menu_products
    ADD CONSTRAINT fk_menu_product_category
        FOREIGN KEY (category_id) REFERENCES tbl_menu_category (category_id);

INSERT INTO tbl_menu_category (menu_id, parent_id, name, sort_order, created_at, updated_at, is_deleted)
SELECT DISTINCT
    p.menu_id,
    NULL::BIGINT,
    TRIM(p.category),
    0,
    NOW(),
    NOW(),
    FALSE
FROM tbl_menu_products p
WHERE p.is_deleted = FALSE
  AND p.category IS NOT NULL
  AND TRIM(p.category) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM tbl_menu_category c
      WHERE c.menu_id = p.menu_id
        AND c.parent_id IS NULL
        AND LOWER(c.name) = LOWER(TRIM(p.category))
        AND c.is_deleted = FALSE
  );

UPDATE tbl_menu_products p
SET category_id = c.category_id
FROM tbl_menu_category c
WHERE p.is_deleted = FALSE
  AND p.category_id IS NULL
  AND p.category IS NOT NULL
  AND TRIM(p.category) <> ''
  AND c.menu_id = p.menu_id
  AND c.parent_id IS NULL
  AND LOWER(c.name) = LOWER(TRIM(p.category))
  AND c.is_deleted = FALSE;
