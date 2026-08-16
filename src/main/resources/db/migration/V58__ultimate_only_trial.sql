UPDATE tbl_plan_package SET
    trial_eligible = FALSE,
    trial_days = NULL,
    updated_at = NOW()
WHERE code IN ('STARTER_PACKAGE', 'PRO_PACKAGE');

UPDATE tbl_plan_package SET
    trial_eligible = TRUE,
    trial_days = 30,
    updated_at = NOW()
WHERE code = 'ULTIMATE_PACKAGE';
