INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted)
VALUES (82, 1, 'su', 'Su', 9, NOW(), NOW(), FALSE)
ON CONFLICT (id) DO UPDATE SET
    main_category_id = EXCLUDED.main_category_id,
    slug = EXCLUDED.slug,
    name = EXCLUDED.name,
    sort_order = EXCLUDED.sort_order,
    updated_at = NOW(),
    is_deleted = FALSE;

ALTER TABLE tbl_table_bill
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(10) NULL;
