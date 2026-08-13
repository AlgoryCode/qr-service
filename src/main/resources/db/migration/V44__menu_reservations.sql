CREATE TABLE IF NOT EXISTS tbl_menu_reservation (
    id              BIGSERIAL PRIMARY KEY,
    menu_id         BIGINT       NOT NULL,
    customer_name   VARCHAR(120) NOT NULL,
    phone           VARCHAR(40),
    email           VARCHAR(255),
    party_size      INT          NOT NULL,
    reservation_at  TIMESTAMP    NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    note            VARCHAR(500),
    ip_address      VARCHAR(45)  NOT NULL,
    user_agent      TEXT,
    device_type     VARCHAR(10)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT chk_menu_reservation_contact
        CHECK (phone IS NOT NULL OR email IS NOT NULL),
    CONSTRAINT chk_menu_reservation_party_size
        CHECK (party_size BETWEEN 1 AND 50),
    CONSTRAINT chk_menu_reservation_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'CANCELED')),
    CONSTRAINT fk_menu_reservation_menu
        FOREIGN KEY (menu_id) REFERENCES tbl_menu (menu_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_menu_reservation_menu_status_at
    ON tbl_menu_reservation (menu_id, status, reservation_at);

CREATE INDEX IF NOT EXISTS idx_menu_reservation_menu_email
    ON tbl_menu_reservation (menu_id, email);

CREATE INDEX IF NOT EXISTS idx_menu_reservation_menu_customer_name
    ON tbl_menu_reservation (menu_id, customer_name);

CREATE INDEX IF NOT EXISTS idx_menu_reservation_menu_created
    ON tbl_menu_reservation (menu_id, created_at);

CREATE INDEX IF NOT EXISTS idx_menu_reservation_menu_ip_created
    ON tbl_menu_reservation (menu_id, ip_address, created_at);
