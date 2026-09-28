ALTER TABLE tbl_user
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE nsp.nspname = 'public'
          AND rel.relname = 'tbl_user'
          AND con.contype = 'u'
          AND array_length(con.conkey, 1) = 1
          AND EXISTS (
              SELECT 1
              FROM unnest(con.conkey) AS key(attnum)
              JOIN pg_attribute att
                ON att.attrelid = rel.oid
               AND att.attnum = key.attnum
              WHERE att.attname = 'email'
          )
    LOOP
        EXECUTE format('ALTER TABLE tbl_user DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_email_live
    ON tbl_user (email)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS stage_trial_assignment (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT    NOT NULL,
    branch_id  BIGINT    NOT NULL,
    menu_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_stage_trial_assignment_user UNIQUE (user_id)
);
