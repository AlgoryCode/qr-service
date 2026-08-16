CREATE TABLE IF NOT EXISTS tbl_site_visit (
    id            BIGSERIAL PRIMARY KEY,
    path          VARCHAR(512)  NOT NULL,
    referrer      VARCHAR(1024),
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(512),
    device        VARCHAR(128),
    device_type   VARCHAR(20),
    country_code  VARCHAR(8),
    country_name  VARCHAR(120),
    region_name   VARCHAR(120),
    city          VARCHAR(120),
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    created_at    TIMESTAMP     NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_site_visit_created_at
    ON tbl_site_visit (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_site_visit_device_type
    ON tbl_site_visit (device_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_site_visit_country
    ON tbl_site_visit (country_code, created_at DESC);
