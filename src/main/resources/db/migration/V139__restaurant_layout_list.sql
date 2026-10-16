CREATE TABLE IF NOT EXISTS tbl_restaurant_layout (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    branch_id BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_restaurant_layout_user
    ON tbl_restaurant_layout (user_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_restaurant_layout_user_branch
    ON tbl_restaurant_layout (user_id, branch_id)
    WHERE branch_id IS NOT NULL;

ALTER TABLE tbl_restaurant_area
    ADD COLUMN IF NOT EXISTS layout_id BIGINT;

ALTER TABLE tbl_restaurant_table
    ADD COLUMN IF NOT EXISTS layout_id BIGINT;

INSERT INTO tbl_restaurant_layout (user_id, name, branch_id, created_at, updated_at)
SELECT owners.user_id, 'Düzenim', NULL, NOW(), NOW()
FROM (
    SELECT user_id
    FROM tbl_restaurant_area
    WHERE user_id IS NOT NULL
      AND layout_id IS NULL
    UNION
    SELECT user_id
    FROM tbl_restaurant_table
    WHERE user_id IS NOT NULL
      AND layout_id IS NULL
      AND COALESCE(deleted, false) = false
) AS owners
WHERE NOT EXISTS (
    SELECT 1
    FROM tbl_restaurant_layout AS existing
    WHERE existing.user_id = owners.user_id
);

UPDATE tbl_restaurant_area AS area
SET layout_id = layout.id
FROM tbl_restaurant_layout AS layout
WHERE area.user_id = layout.user_id
  AND area.layout_id IS NULL
  AND layout.name = 'Düzenim';

UPDATE tbl_restaurant_table AS layout_table
SET layout_id = layout.id
FROM tbl_restaurant_layout AS layout
WHERE layout_table.user_id = layout.user_id
  AND layout_table.layout_id IS NULL
  AND COALESCE(layout_table.deleted, false) = false
  AND layout.name = 'Düzenim';

CREATE INDEX IF NOT EXISTS idx_restaurant_area_layout
    ON tbl_restaurant_area (layout_id);

CREATE INDEX IF NOT EXISTS idx_restaurant_table_layout
    ON tbl_restaurant_table (layout_id)
    WHERE COALESCE(deleted, false) = false;
