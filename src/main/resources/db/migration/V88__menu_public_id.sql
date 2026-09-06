CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE tbl_menu
    ADD COLUMN IF NOT EXISTS public_id VARCHAR(32);

UPDATE tbl_menu
SET public_id = rtrim(translate(encode(gen_random_bytes(16), 'base64'), '+/', '-_'), '=')
WHERE public_id IS NULL OR btrim(public_id) = '';

ALTER TABLE tbl_menu
    ALTER COLUMN public_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_menu_public_id'
    ) THEN
        ALTER TABLE tbl_menu
            ADD CONSTRAINT uk_menu_public_id UNIQUE (public_id);
    END IF;
END $$;
