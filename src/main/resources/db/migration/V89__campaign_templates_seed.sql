-- Same seed as scripts/seed_campaign_templates.sql for environments that apply Flyway files manually.

CREATE TABLE IF NOT EXISTS tbl_campaign_template (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    icon VARCHAR(40),
    config_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_order INT NOT NULL DEFAULT 0
);

INSERT INTO tbl_campaign_template (code, name, description, icon, config_schema, sort_order)
VALUES (
    'STAMP_CARD',
    'Damga kartı',
    'Belirli ürünlerden N adet alınca ödül ürün verilir.',
    'stamp',
    '{"fields":[{"key":"targetProductIds","type":"productIds"},{"key":"requiredQuantity","type":"number"},{"key":"reward","type":"reward"}]}'::jsonb,
    10
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO tbl_campaign_template (code, name, description, icon, config_schema, sort_order)
VALUES (
    'SPEND_THRESHOLD',
    'Harcama eşiği',
    'Haftalık veya aylık harcama eşiğine ulaşınca ödül ürün verilir.',
    'spend',
    '{"fields":[{"key":"thresholdAmount","type":"number"},{"key":"period","type":"enum","values":["WEEKLY","MONTHLY"]},{"key":"reward","type":"reward"}]}'::jsonb,
    20
)
ON CONFLICT (code) DO NOTHING;
