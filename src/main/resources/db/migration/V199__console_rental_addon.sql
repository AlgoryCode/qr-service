-- Konsol takip, ayrı satılan bir eklenti: CONSOLE_RENTAL özelliği ve onu veren aylık ürün.
INSERT INTO features (feature_key, name, kind, unit, reset_period, parent_key, description) VALUES
    ('CONSOLE_RENTAL', 'Konsol takip', 'BOOLEAN', NULL, 'NONE', NULL,
     'Oyun konsollarını saatlik kiralayın: oturum başlatın, bitirin, ödenecek tutarı görün.')
ON CONFLICT (feature_key) DO NOTHING;

INSERT INTO catalog_products (
    code, name, description, active, scope_code, type_id, feature_code,
    consumable, addon_purchasable, billing_type, unit_price, vat_rate, created_at, updated_at
)
SELECT
    'CONSOLE_RENTAL',
    'Konsol Takip',
    'Oyun konsolu kiralama takibi: konsol ve saatlik ücret tanımlama, oturum başlat/bitir, tutar hesaplama',
    TRUE,
    'CONSOLE_RENTAL_OWNER',
    'ADDON_PRODUCT',
    'CONSOLE_RENTAL',
    FALSE,
    TRUE,
    'RECURRING',
    149.00,
    20.00,
    NOW(),
    NOW()
WHERE NOT EXISTS (SELECT 1 FROM catalog_products WHERE code = 'CONSOLE_RENTAL');
