-- Staff usernames are unique per merchant so the same login can exist at different businesses.

DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = current_schema()
          AND t.relname = 'tbl_merchant_staff'
          AND c.contype = 'u'
          AND (
            SELECT array_agg(a.attname ORDER BY a.attname)
            FROM unnest(c.conkey) AS col(attnum)
            JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = col.attnum
          ) = ARRAY['username']
    LOOP
        EXECUTE format('ALTER TABLE tbl_merchant_staff DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

ALTER TABLE tbl_merchant_staff
    DROP CONSTRAINT IF EXISTS uk_merchant_staff_merchant_username;

ALTER TABLE tbl_merchant_staff
    ADD CONSTRAINT uk_merchant_staff_merchant_username UNIQUE (merchant_id, username);
