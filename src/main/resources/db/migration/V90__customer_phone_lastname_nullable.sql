-- Align tbl_customer with Customer entity (phone/last_name optional on register).
ALTER TABLE tbl_customer ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE tbl_customer ALTER COLUMN last_name DROP NOT NULL;
