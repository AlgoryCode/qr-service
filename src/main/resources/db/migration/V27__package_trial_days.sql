ALTER TABLE tbl_plan_package
    ADD COLUMN IF NOT EXISTS trial_days INTEGER;

UPDATE tbl_plan_package
SET trial_days = LEAST(validity_days, 7)
WHERE trial_eligible = TRUE
  AND trial_days IS NULL;

UPDATE tbl_plan_package
SET trial_eligible = TRUE,
    trial_days = 7
WHERE code = 'PRO_PACKAGE';

UPDATE tbl_plan_package
SET trial_eligible = FALSE,
    trial_days = NULL
WHERE code = 'ULTIMATE_PACKAGE';

UPDATE tbl_plan_package
SET trial_eligible = FALSE,
    trial_days = NULL
WHERE code = 'FREE_PACKAGE'
   OR system_managed = TRUE;
