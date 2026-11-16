-- Reservations belong to a branch (the venue), not to a menu.
--
-- The table was first created by Hibernate (ddl-auto=update) and never had a migration, so a fresh
-- database would not have it: create it here when it is missing, with the columns the entity maps.
CREATE TABLE IF NOT EXISTS tbl_menu_reservation (
    id             BIGSERIAL PRIMARY KEY,
    menu_id        BIGINT,
    customer_name  VARCHAR(120) NOT NULL,
    phone          VARCHAR(40),
    email          VARCHAR(255),
    party_size     INTEGER      NOT NULL,
    reservation_at TIMESTAMP    NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    note           VARCHAR(500),
    ip_address     VARCHAR(45)  NOT NULL,
    user_agent     TEXT,
    device_type    VARCHAR(10)  NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL
);

ALTER TABLE tbl_menu_reservation ADD COLUMN IF NOT EXISTS branch_id BIGINT;

-- Existing reservations take the branch of the menu they were made from. Reservations of menus without
-- a branch keep branch_id NULL; they stay reachable through the menu endpoints.
UPDATE tbl_menu_reservation r
SET branch_id = m.branch_id
FROM tbl_menu m
WHERE r.branch_id IS NULL
  AND r.menu_id = m.menu_id
  AND m.branch_id IS NOT NULL;

-- A reservation taken in the panel for a branch need not name a menu.
ALTER TABLE tbl_menu_reservation ALTER COLUMN menu_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_menu_reservation_menu_status_at
    ON tbl_menu_reservation (menu_id, status, reservation_at);
CREATE INDEX IF NOT EXISTS idx_menu_reservation_branch_status_at
    ON tbl_menu_reservation (branch_id, status, reservation_at);
