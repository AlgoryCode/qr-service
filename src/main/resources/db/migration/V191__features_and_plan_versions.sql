-- 1. Features: what a package or add-on can grant, and how it is measured.
--    BOOLEAN  on/off capability            LIMIT    how many may exist at once (counted live)
--    METERED  uses per period (reset)      CREDIT   prepaid units that are spent and not renewed
CREATE TABLE IF NOT EXISTS features (
    feature_key  VARCHAR(64)  PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    kind         VARCHAR(16)  NOT NULL,
    unit         VARCHAR(32),
    reset_period VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    parent_key   VARCHAR(64)  REFERENCES features (feature_key),
    description  VARCHAR(500),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT features_kind_check CHECK (kind IN ('BOOLEAN', 'LIMIT', 'METERED', 'CREDIT')),
    CONSTRAINT features_reset_check CHECK (reset_period IN ('NONE', 'WEEK', 'MONTH'))
);

INSERT INTO features (feature_key, name, kind, unit, reset_period, parent_key) VALUES
    ('QR_BRANCH',       'Şube',                  'LIMIT',   'şube',    'NONE', NULL),
    ('QR_MENU',         'QR menü',               'LIMIT',   'menü',    'NONE', NULL),
    ('MENU_PRODUCT',    'Menü ürünü',            'LIMIT',   'ürün',    'NONE', NULL),
    ('QR_CREATE',       'QR kod',                'LIMIT',   'QR kod',  'NONE', NULL),
    ('SMART_REPORTING', 'Akıllı rapor',          'BOOLEAN', NULL,      'NONE', NULL),
    ('SMART_ASSISTANT', 'Akıllı asistan',        'BOOLEAN', NULL,      'NONE', NULL),
    ('SMART_SUMMARY',   'Akıllı özet',           'BOOLEAN', NULL,      'NONE', NULL),
    ('CUSTOM_DESIGN',   'Özel tasarım',          'BOOLEAN', NULL,      'NONE', NULL),
    ('WAITER_PANEL',    'Garson paneli',         'BOOLEAN', NULL,      'NONE', NULL),
    ('AI_MENU_IMPORT',  'AI ile menü aktarımı',  'BOOLEAN', NULL,      'NONE', NULL),
    ('ONLINE_ORDER',    'Online sipariş',        'BOOLEAN', NULL,      'NONE', NULL),
    ('KAFE_MENU',       'Kafe menüsü',           'BOOLEAN', NULL,      'NONE', NULL)
ON CONFLICT (feature_key) DO NOTHING;

INSERT INTO features (feature_key, name, kind, unit, reset_period, parent_key) VALUES
    ('SMART_REPORT_QUOTA', 'Haftalık ücretsiz akıllı rapor', 'METERED', 'rapor', 'WEEK', 'SMART_REPORTING')
ON CONFLICT (feature_key) DO NOTHING;

-- Anything else a product already grants becomes an on/off feature named after the product.
INSERT INTO features (feature_key, name, kind)
SELECT DISTINCT ON (p.feature_code) p.feature_code, p.name, 'BOOLEAN'
FROM tbl_product p
WHERE NOT EXISTS (SELECT 1 FROM features f WHERE f.feature_key = p.feature_code)
ORDER BY p.feature_code, p.id;

ALTER TABLE tbl_product
    ADD CONSTRAINT fk_product_feature FOREIGN KEY (feature_code) REFERENCES features (feature_key);

-- 2. Plan versions: what a package granted and cost at a point in time. Never edited; a change to a
--    package's lines or prices opens a new version. A purchase keeps the version it was bought on,
--    so renewals grant exactly what the merchant agreed to.
CREATE TABLE IF NOT EXISTS plan_versions (
    id               BIGSERIAL     PRIMARY KEY,
    package_id       BIGINT        NOT NULL REFERENCES tbl_plan_package (id),
    version          INTEGER       NOT NULL,
    price            NUMERIC(12,2) NOT NULL,
    yearly_price     NUMERIC(12,2),
    monthly_discount NUMERIC(12,2) NOT NULL DEFAULT 0,
    yearly_discount  NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency         VARCHAR(3)    NOT NULL,
    validity_days    INTEGER       NOT NULL,
    note             VARCHAR(255),
    created_by       VARCHAR(128),
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_plan_version UNIQUE (package_id, version)
);

CREATE TABLE IF NOT EXISTS plan_version_items (
    id              BIGSERIAL PRIMARY KEY,
    plan_version_id BIGINT    NOT NULL REFERENCES plan_versions (id) ON DELETE CASCADE,
    product_id      BIGINT    NOT NULL REFERENCES tbl_product (id),
    quantity        INTEGER   NOT NULL,
    unlimited       BOOLEAN   NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_plan_version_items_version ON plan_version_items (plan_version_id);

ALTER TABLE tbl_plan_package ADD COLUMN IF NOT EXISTS current_version_id BIGINT REFERENCES plan_versions (id);
ALTER TABLE tbl_purchase ADD COLUMN IF NOT EXISTS plan_version_id BIGINT REFERENCES plan_versions (id);
CREATE INDEX IF NOT EXISTS idx_purchase_plan_version ON tbl_purchase (plan_version_id);

-- Today's packages become version 1, and every purchase is pinned to it.
INSERT INTO plan_versions (package_id, version, price, yearly_price, monthly_discount, yearly_discount,
                           currency, validity_days, note, created_by)
SELECT id, 1, price, yearly_price, monthly_discount, yearly_discount, currency,
       validity_days, 'İlk sürüm', 'system'
FROM tbl_plan_package
WHERE current_version_id IS NULL;

INSERT INTO plan_version_items (plan_version_id, product_id, quantity, unlimited)
SELECT v.id, i.product_id, i.quantity, i.unlimited
FROM plan_versions v
JOIN tbl_plan_package_item i ON i.package_id = v.package_id
WHERE v.version = 1;

UPDATE tbl_plan_package p SET current_version_id = v.id
FROM plan_versions v
WHERE v.package_id = p.id AND v.version = 1 AND p.current_version_id IS NULL;

UPDATE tbl_purchase pu SET plan_version_id = p.current_version_id
FROM tbl_plan_package p
WHERE p.id = pu.package_id AND pu.plan_version_id IS NULL;
