-- Legacy QR tbl_merchant_staff uses staff_role (WAITER/KITCHEN/COURIER); auth JPA expects role_id -> tbl_roles.

ALTER TABLE tbl_merchant_staff
    ADD COLUMN IF NOT EXISTS role_id BIGINT;

UPDATE tbl_merchant_staff s
SET role_id = r.id
FROM tbl_roles r
WHERE s.role_id IS NULL
  AND r.code = s.staff_role;

UPDATE tbl_merchant_staff s
SET role_id = r.id
FROM tbl_roles r
WHERE s.role_id IS NULL
  AND r.code = 'WAITER';

ALTER TABLE tbl_merchant_staff
    ALTER COLUMN role_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_merchant_staff_role_id'
          AND conrelid = 'tbl_merchant_staff'::regclass
    ) THEN
        ALTER TABLE tbl_merchant_staff
            ADD CONSTRAINT fk_merchant_staff_role_id
                FOREIGN KEY (role_id) REFERENCES tbl_roles (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_merchant_staff_role ON tbl_merchant_staff (role_id);

CREATE OR REPLACE FUNCTION sync_merchant_staff_role_columns()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
DECLARE
    resolved_code TEXT;
BEGIN
    IF NEW.staff_role IS NOT NULL
        AND (
            NEW.role_id IS NULL
            OR (TG_OP = 'UPDATE'
                AND NEW.staff_role IS DISTINCT FROM OLD.staff_role
                AND NEW.role_id IS NOT DISTINCT FROM OLD.role_id)
        ) THEN
        SELECT id
        INTO NEW.role_id
        FROM tbl_roles
        WHERE code = NEW.staff_role
        LIMIT 1;
    ELSIF NEW.role_id IS NOT NULL
        AND (
            NEW.staff_role IS NULL
            OR (TG_OP = 'UPDATE' AND NEW.role_id IS DISTINCT FROM OLD.role_id)
        ) THEN
        SELECT code
        INTO resolved_code
        FROM tbl_roles
        WHERE id = NEW.role_id
        LIMIT 1;
        IF resolved_code IS NOT NULL THEN
            NEW.staff_role := resolved_code;
        END IF;
    END IF;

    IF NEW.role_id IS NULL THEN
        SELECT id
        INTO NEW.role_id
        FROM tbl_roles
        WHERE code = COALESCE(NEW.staff_role, 'WAITER')
        LIMIT 1;
    END IF;

    IF NEW.staff_role IS NULL THEN
        NEW.staff_role := 'WAITER';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_merchant_staff_role_columns ON tbl_merchant_staff;
CREATE TRIGGER trg_merchant_staff_role_columns
    BEFORE INSERT OR UPDATE OF staff_role, role_id
    ON tbl_merchant_staff
    FOR EACH ROW
    EXECUTE FUNCTION sync_merchant_staff_role_columns();
