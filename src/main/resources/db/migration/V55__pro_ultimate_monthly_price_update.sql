UPDATE tbl_plan_package SET
    price = 599.00,
    subtotal = 499.17,
    vat_amount = 99.83,
    yearly_price = 5643.00,
    updated_at = NOW()
WHERE code = 'PRO_PACKAGE';

UPDATE tbl_plan_package SET
    price = 999.00,
    subtotal = 832.50,
    vat_amount = 166.50,
    yearly_price = 9215.00,
    updated_at = NOW()
WHERE code = 'ULTIMATE_PACKAGE';
