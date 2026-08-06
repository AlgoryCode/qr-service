CREATE TABLE IF NOT EXISTS tbl_menu_allergen (
    id          BIGINT       PRIMARY KEY,
    slug        VARCHAR(64)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP(6),
    updated_at  TIMESTAMP(6),
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_menu_allergen_slug UNIQUE (slug)
);

CREATE TABLE IF NOT EXISTS tbl_menu_product_allergen (
    product_id   BIGINT NOT NULL,
    allergen_id  BIGINT NOT NULL,
    PRIMARY KEY (product_id, allergen_id),
    CONSTRAINT fk_menu_product_allergen_product
        FOREIGN KEY (product_id) REFERENCES tbl_menu_products (product_id) ON DELETE CASCADE,
    CONSTRAINT fk_menu_product_allergen_allergen
        FOREIGN KEY (allergen_id) REFERENCES tbl_menu_allergen (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_menu_product_allergen_allergen
    ON tbl_menu_product_allergen (allergen_id);

INSERT INTO tbl_menu_allergen (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES
    (1,  'gluten',                'Glüten içeren tahıllar',           1,  NOW(), NOW(), FALSE),
    (2,  'kabuklular',             'Kabuklular',                       2,  NOW(), NOW(), FALSE),
    (3,  'yumurta',                'Yumurta',                          3,  NOW(), NOW(), FALSE),
    (4,  'balik',                  'Balık',                            4,  NOW(), NOW(), FALSE),
    (5,  'yer_fistigi',            'Yer fıstığı',                      5,  NOW(), NOW(), FALSE),
    (6,  'soya',                   'Soya',                             6,  NOW(), NOW(), FALSE),
    (7,  'sut',                    'Süt',                              7,  NOW(), NOW(), FALSE),
    (8,  'sert_kabuklu_yemisler',  'Sert kabuklu yemişler',            8,  NOW(), NOW(), FALSE),
    (9,  'kereviz',                'Kereviz',                          9,  NOW(), NOW(), FALSE),
    (10, 'hardal',                 'Hardal',                          10,  NOW(), NOW(), FALSE),
    (11, 'susam',                  'Susam',                           11,  NOW(), NOW(), FALSE),
    (12, 'sulfur_dioksit',         'Kükürt dioksit / sülfitler',       12,  NOW(), NOW(), FALSE),
    (13, 'aci_bakla',              'Acı bakla',                       13,  NOW(), NOW(), FALSE),
    (14, 'yumusakcalar',           'Yumuşakçalar',                    14,  NOW(), NOW(), FALSE)
ON CONFLICT (id) DO UPDATE SET
    slug = EXCLUDED.slug,
    name = EXCLUDED.name,
    sort_order = EXCLUDED.sort_order,
    updated_at = NOW(),
    is_deleted = FALSE;
