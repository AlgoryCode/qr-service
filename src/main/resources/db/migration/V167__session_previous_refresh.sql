-- The refresh token a rotation replaced stays usable for a short grace window, so a client that lost the
-- rotation response (dropped connection, app killed) is not signed out. Presented after the window it means the
-- token was copied, and the session is revoked.
ALTER TABLE tbl_session ADD COLUMN IF NOT EXISTS previous_refresh_token_hash VARCHAR(255);
ALTER TABLE tbl_session ADD COLUMN IF NOT EXISTS previous_refresh_valid_until TIMESTAMP;
