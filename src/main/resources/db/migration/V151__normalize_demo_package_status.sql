-- Legacy demo assignments used status DEMO; packages are normal ACTIVE subscriptions.
UPDATE active_package SET status = 'ACTIVE' WHERE status = 'DEMO';

UPDATE tbl_purchase
SET purchase_type = 'SYSTEM_GRANT'
WHERE purchase_type = 'TRIAL';
