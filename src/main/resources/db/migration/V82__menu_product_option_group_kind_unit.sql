ALTER TABLE tbl_menu_product_option_group
    ADD COLUMN kind VARCHAR(32) NOT NULL DEFAULT 'CUSTOM',
    ADD COLUMN unit VARCHAR(16) NOT NULL DEFAULT 'NONE';

ALTER TABLE tbl_menu_product_option_group
    ADD CONSTRAINT ck_menu_product_option_group_kind
        CHECK (kind IN ('SIZE', 'CHOICE', 'EXTRA', 'REMOVAL', 'PORTION', 'CUSTOM'));

ALTER TABLE tbl_menu_product_option_group
    ADD CONSTRAINT ck_menu_product_option_group_unit
        CHECK (unit IN ('NONE', 'PIECE', 'GRAM', 'ML', 'LITRE'));
