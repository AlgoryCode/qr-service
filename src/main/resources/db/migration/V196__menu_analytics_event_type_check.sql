-- Some databases carry an old check constraint on tbl_menu_analytics_event.event_type that
-- allows only MENU_OPEN, CATEGORY_VIEW, PRODUCT_VIEW and SERVES_FILTER. That rejects QR scans,
-- cart, checkout, order and menu assistant events. The enum in code is the source of truth, so
-- drop any check on that column.
DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY (con.conkey)
        WHERE con.conrelid = 'tbl_menu_analytics_event'::regclass
          AND con.contype = 'c'
          AND att.attname = 'event_type'
    LOOP
        EXECUTE format('ALTER TABLE tbl_menu_analytics_event DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;
