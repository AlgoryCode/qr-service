ALTER TABLE tbl_menu_order
    ALTER COLUMN table_id DROP NOT NULL;

ALTER TABLE tbl_menu_order
    ALTER COLUMN table_session_id DROP NOT NULL;
