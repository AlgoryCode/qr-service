ALTER TABLE tbl_product
    ADD COLUMN IF NOT EXISTS unit_price NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS vat_rate NUMERIC(5, 2) NOT NULL DEFAULT 20.00;

ALTER TABLE tbl_plan_package
    ADD COLUMN IF NOT EXISTS subtotal NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS vat_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;

UPDATE tbl_plan_package
SET subtotal = price,
    vat_amount = 0
WHERE subtotal = 0
  AND price > 0;
