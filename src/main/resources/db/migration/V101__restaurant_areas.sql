-- Restaurant layout areas (İç Mekan, Teras, …) with tables grouped underneath.
-- Safe to run manually on stage/prod while Flyway remains disabled.

CREATE TABLE IF NOT EXISTS tbl_restaurant_area (
    id BIGSERIAL PRIMARY KEY,
    menu_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_restaurant_area_menu_id
    ON tbl_restaurant_area (menu_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_restaurant_area_menu_name
    ON tbl_restaurant_area (menu_id, lower(name));

ALTER TABLE tbl_restaurant_table
    ADD COLUMN IF NOT EXISTS area_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_restaurant_table_area_id
    ON tbl_restaurant_table (area_id);
