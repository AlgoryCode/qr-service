-- Idempotent menu theme catalog + user assignment tables.
-- Safe to run manually on stage/prod while Flyway remains disabled.
-- Assignment choice: user-level (tbl_user) — menus belong to members via userId.

CREATE TABLE IF NOT EXISTS tbl_menu_theme (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    preview_meta JSONB,
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    CONSTRAINT uk_menu_theme_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS tbl_user_theme_assignment (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    theme_id BIGINT NOT NULL,
    assigned_by BIGINT,
    assigned_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_theme_assignment UNIQUE (user_id, theme_id),
    CONSTRAINT fk_user_theme_assignment_theme
        FOREIGN KEY (theme_id) REFERENCES tbl_menu_theme (id)
);

CREATE INDEX IF NOT EXISTS idx_user_theme_assignment_user_id
    ON tbl_user_theme_assignment (user_id);

CREATE INDEX IF NOT EXISTS idx_user_theme_assignment_theme_id
    ON tbl_user_theme_assignment (theme_id);

-- Built-in FE theme keys (CatalogThemes.PRESET_THEME_IDS)
INSERT INTO tbl_menu_theme (code, name, description, preview_meta, sort_order, active)
VALUES
    ('soft', 'Soft', 'Yumuşak ve sade menü teması', '{"swatch":"#F5F0EB"}'::jsonb, 10, TRUE),
    ('classic', 'Klasik', 'Klasik restoran menü teması', '{"swatch":"#1F2937"}'::jsonb, 20, TRUE),
    ('luxury', 'Lüks', 'Premium koyu lüks tema', '{"swatch":"#111827"}'::jsonb, 30, TRUE),
    ('petite-patisserie', 'Petite Patisserie', 'Pastane / tatlı evi teması', '{"swatch":"#F9A8D4"}'::jsonb, 40, TRUE),
    ('folio-rouge', 'Folio Rouge', 'Kırmızı folio şık tema', '{"swatch":"#B91C1C"}'::jsonb, 50, TRUE),
    ('lucite-gris', 'Lucite Gris', 'Modern gri lucite tema', '{"swatch":"#9CA3AF"}'::jsonb, 60, TRUE),
    ('rubric', 'Rubric', 'Rubric tipografi teması', '{"swatch":"#7C2D12"}'::jsonb, 70, TRUE),
    ('bigarade', 'Bigarade', 'Turuncu bigarade tema', '{"swatch":"#EA580C"}'::jsonb, 80, TRUE),
    ('elixir', 'Elixir', 'Elixir cocktail teması', '{"swatch":"#6D28D9"}'::jsonb, 90, TRUE),
    ('tech-gourmet', 'Tech Gourmet', 'Teknolojik gurme tema', '{"swatch":"#0EA5E9"}'::jsonb, 100, TRUE),
    ('modern-bistro', 'Modern Bistro', 'Modern bistro teması', '{"swatch":"#15803D"}'::jsonb, 110, TRUE),
    ('clever-dish-scribe', 'Clever Dish Scribe', 'Akıllı şef yazı teması', '{"swatch":"#0369A1"}'::jsonb, 120, TRUE),
    ('maison-noir', 'Maison Noir', 'Siyah maison lüks tema', '{"swatch":"#0A0A0A"}'::jsonb, 130, TRUE),
    ('kahve-sokagi', 'Kahve Sokağı', 'Kahve / kafe teması', '{"swatch":"#78350F"}'::jsonb, 140, TRUE)
ON CONFLICT (code) DO NOTHING;
