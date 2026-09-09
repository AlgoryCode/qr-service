ALTER TABLE tbl_smart_report_events
    ALTER COLUMN menu_id DROP NOT NULL;

ALTER TABLE tbl_smart_report_events
    ALTER COLUMN menu_name DROP NOT NULL;

ALTER TABLE tbl_smart_report_events
    ADD COLUMN IF NOT EXISTS branch_id BIGINT;

ALTER TABLE tbl_smart_report_events
    ADD COLUMN IF NOT EXISTS branch_name VARCHAR(255);
