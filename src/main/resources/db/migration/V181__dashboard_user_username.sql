-- Admin panel accounts can sign in with a username instead of their e-mail address.
ALTER TABLE tbl_dashboard_user ADD COLUMN IF NOT EXISTS username VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dashboard_user_username_lower
    ON tbl_dashboard_user (LOWER(username))
    WHERE username IS NOT NULL;
