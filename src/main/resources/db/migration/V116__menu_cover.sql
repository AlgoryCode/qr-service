ALTER TABLE tbl_menu ADD COLUMN IF NOT EXISTS cover_url VARCHAR(1024);
ALTER TABLE tbl_menu ADD COLUMN IF NOT EXISTS cover_key VARCHAR(255);

INSERT INTO tbl_menu_theme (code, name, description, preview_meta, sort_order, active)
VALUES (
    'lilas-doux',
    'Lilas Doux',
    'Beyaz, yumuşak eflatun menü teması',
    '{"swatch":"#E0117A"}'::jsonb,
    150,
    TRUE
)
ON CONFLICT (code) DO NOTHING;
