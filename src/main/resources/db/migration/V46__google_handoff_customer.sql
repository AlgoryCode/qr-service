ALTER TABLE tbl_google_auth_handoff_ticket ALTER COLUMN intent TYPE varchar(32);
ALTER TABLE tbl_google_auth_handoff_ticket ADD COLUMN IF NOT EXISTS principal_type varchar(16) NOT NULL DEFAULT 'APP';
