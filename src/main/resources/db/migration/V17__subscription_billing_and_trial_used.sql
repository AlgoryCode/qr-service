ALTER TABLE tbl_plan_package
    ADD COLUMN IF NOT EXISTS monthly_discount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS yearly_price NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS yearly_discount NUMERIC(12, 2) NOT NULL DEFAULT 0;

UPDATE tbl_plan_package
SET yearly_price = ROUND(price * 12, 2)
WHERE yearly_price IS NULL
  AND price IS NOT NULL
  AND purchasable = TRUE
  AND system_managed = FALSE;

ALTER TABLE tbl_user
    ADD COLUMN IF NOT EXISTS trial_used BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE tbl_user u
SET trial_used = TRUE
WHERE EXISTS (
    SELECT 1
    FROM tbl_purchase p
    WHERE p.user_id = u.id
      AND p.purchase_type = 'TRIAL'
);

ALTER TABLE tbl_purchase
    ADD COLUMN IF NOT EXISTS billing_interval_months INTEGER,
    ADD COLUMN IF NOT EXISTS billing_period VARCHAR(16);
