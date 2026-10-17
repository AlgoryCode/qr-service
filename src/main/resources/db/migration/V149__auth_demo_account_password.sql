ALTER TABLE tbl_demo_accounts ADD COLUMN IF NOT EXISTS password VARCHAR(255);

UPDATE tbl_demo_accounts d
SET password = m.password
FROM tbl_merchant m
WHERE m.id = d.user_id
  AND m.password IS NOT NULL;

ALTER TABLE tbl_demo_accounts ALTER COLUMN password SET NOT NULL;

UPDATE tbl_merchant m
SET password = NULL
FROM tbl_demo_accounts d
WHERE d.user_id = m.id;
