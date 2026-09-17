-- Visual floor-plan coordinates for restaurant tables.
-- Safe to run manually on stage/prod while Flyway remains disabled.

ALTER TABLE tbl_restaurant_table
    ADD COLUMN IF NOT EXISTS layout_x DOUBLE PRECISION;

ALTER TABLE tbl_restaurant_table
    ADD COLUMN IF NOT EXISTS layout_y DOUBLE PRECISION;

ALTER TABLE tbl_restaurant_table
    ADD COLUMN IF NOT EXISTS layout_rotation INTEGER;

ALTER TABLE tbl_restaurant_table
    ADD COLUMN IF NOT EXISTS layout_shape VARCHAR(20);
