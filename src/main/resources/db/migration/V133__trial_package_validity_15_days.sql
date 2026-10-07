UPDATE tbl_plan_package
SET validity_days = 15,
    description = '15 gunluk Ultimate deneme paketi',
    updated_at = NOW()
WHERE code = 'ULTIMATE_TRIAL_PACKAGE';
