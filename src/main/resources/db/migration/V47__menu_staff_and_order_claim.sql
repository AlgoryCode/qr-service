CREATE TABLE tbl_menu_staff (
  id BIGSERIAL PRIMARY KEY,
  owner_user_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(120) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT uk_menu_staff_username UNIQUE (username),
  CONSTRAINT fk_menu_staff_menu FOREIGN KEY (menu_id) REFERENCES tbl_menu(menu_id) ON DELETE CASCADE
);
CREATE INDEX idx_menu_staff_menu ON tbl_menu_staff(menu_id);
CREATE INDEX idx_menu_staff_owner ON tbl_menu_staff(owner_user_id);

CREATE TABLE tbl_menu_staff_session (
  id UUID PRIMARY KEY,
  staff_id BIGINT NOT NULL REFERENCES tbl_menu_staff(id) ON DELETE CASCADE,
  refresh_token_hash VARCHAR(255) NOT NULL,
  logged_in_at TIMESTAMP NOT NULL,
  access_expires_at TIMESTAMP NOT NULL,
  refresh_expires_at TIMESTAMP NOT NULL,
  last_activity_at TIMESTAMP NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE,
  revoked_at TIMESTAMP,
  ip_address VARCHAR(255),
  user_agent VARCHAR(512),
  device VARCHAR(255),
  device_type VARCHAR(255)
);
CREATE INDEX idx_menu_staff_session_staff ON tbl_menu_staff_session(staff_id);

ALTER TABLE tbl_menu_order ADD COLUMN IF NOT EXISTS waiter_staff_id BIGINT NULL;
ALTER TABLE tbl_menu_order ADD COLUMN IF NOT EXISTS waiter_note TEXT NULL;
CREATE INDEX IF NOT EXISTS idx_menu_order_waiter ON tbl_menu_order(waiter_staff_id);
