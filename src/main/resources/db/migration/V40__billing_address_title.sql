ALTER TABLE tbl_billing_address
    ADD COLUMN IF NOT EXISTS title VARCHAR(80);

UPDATE tbl_billing_address
SET title = COALESCE(
        NULLIF(TRIM(legal_name), ''),
        NULLIF(TRIM(CONCAT_WS(' ', name, surname)), ''),
        'Adres'
    )
WHERE title IS NULL OR TRIM(title) = '';

ALTER TABLE tbl_billing_address
    ALTER COLUMN title SET DEFAULT 'Adres';

ALTER TABLE tbl_billing_address
    ALTER COLUMN title SET NOT NULL;
