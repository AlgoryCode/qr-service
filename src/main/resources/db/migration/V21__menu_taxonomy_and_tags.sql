CREATE TABLE IF NOT EXISTS tbl_main_category (
    id          BIGINT       PRIMARY KEY,
    slug        VARCHAR(64)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP(6),
    updated_at  TIMESTAMP(6),
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_main_category_slug UNIQUE (slug)
);

CREATE TABLE IF NOT EXISTS tbl_sub_category (
    id                BIGINT       PRIMARY KEY,
    main_category_id  BIGINT       NOT NULL,
    slug              VARCHAR(64)  NOT NULL,
    name              VARCHAR(255) NOT NULL,
    sort_order        INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMP(6),
    updated_at        TIMESTAMP(6),
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_sub_category_main
        FOREIGN KEY (main_category_id) REFERENCES tbl_main_category (id),
    CONSTRAINT uk_sub_category_slug UNIQUE (slug)
);

CREATE INDEX IF NOT EXISTS idx_sub_category_main_id
    ON tbl_sub_category (main_category_id);

CREATE TABLE IF NOT EXISTS tbl_menu_tag (
    id          BIGINT       PRIMARY KEY,
    slug        VARCHAR(64)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP(6),
    updated_at  TIMESTAMP(6),
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_menu_tag_slug UNIQUE (slug)
);

CREATE TABLE IF NOT EXISTS tbl_menu_product_tag (
    product_id  BIGINT NOT NULL,
    tag_id      BIGINT NOT NULL,
    PRIMARY KEY (product_id, tag_id),
    CONSTRAINT fk_menu_product_tag_product
        FOREIGN KEY (product_id) REFERENCES tbl_menu_products (product_id) ON DELETE CASCADE,
    CONSTRAINT fk_menu_product_tag_tag
        FOREIGN KEY (tag_id) REFERENCES tbl_menu_tag (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_menu_product_tag_tag
    ON tbl_menu_product_tag (tag_id);

ALTER TABLE tbl_menu_products
    ADD COLUMN IF NOT EXISTS sub_category_id BIGINT;

INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (1, 'icecekler', 'İçecekler', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (1, 1, 'sicak_icecekler', 'Sıcak İçecekler', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (2, 1, 'soguk_icecekler', 'Soğuk İçecekler', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (3, 1, 'taze_sikilmis_meyve_sulari', 'Taze Sıkılmış Meyve Suları', 3, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (4, 1, 'fermente_icecekler', 'Fermente İçecekler', 4, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (5, 1, 'alkollu_icecekler', 'Alkollü İçecekler', 5, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (6, 1, 'sutlu_icecekler', 'Sütlü İçecekler', 6, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (2, 'corbalar', 'Çorbalar', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (7, 2, 'et_suyu_corbalar', 'Et Suyu Çorbalar', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (8, 2, 'kremali_corbalar', 'Kremalı Çorbalar', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (9, 2, 'deniz_urunu_corbalar', 'Deniz Ürünü Çorbalar', 3, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (3, 'baslangiclar', 'Başlangıçlar', 3, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (10, 3, 'soguk_baslangiclar', 'Soğuk Başlangıçlar', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (11, 3, 'sicak_baslangiclar', 'Sıcak Başlangıçlar', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (12, 3, 'salatalar', 'Salatalar', 3, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (4, 'mezeler', 'Mezeler', 4, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (13, 4, 'soguk_mezeler', 'Soğuk Mezeler', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (14, 4, 'sicak_mezeler', 'Sıcak Mezeler', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (15, 4, 'zeytinyaglilar', 'Zeytinyağlılar', 3, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (5, 'ana_yemekler', 'Ana Yemekler', 5, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (16, 5, 'et_yemekleri', 'Et Yemekleri', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (17, 5, 'tavuk_yemekleri', 'Tavuk Yemekleri', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (18, 5, 'vejeteryan_ana_yemekler', 'Vejeteryan Ana Yemekler', 3, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (19, 5, 'vegan_ana_yemekler', 'Vegan Ana Yemekler', 4, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (20, 5, 'makarna_cesitleri', 'Makarna Çeşitleri', 5, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (21, 5, 'pilav_cesitleri', 'Pilav Çeşitleri', 6, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (6, 'pideler', 'Pideler', 6, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (22, 6, 'kiyma_pideler', 'Kıymalı Pideler', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (23, 6, 'peynirli_pideler', 'Peynirli Pideler', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (24, 6, 'karisik_pideler', 'Karışık Pideler', 3, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (7, 'pizzalar', 'Pizzalar', 7, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (25, 7, 'klasik_pizzalar', 'Klasik Pizzalar', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (26, 7, 'ozel_pizzalar', 'Özel Pizzalar', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (27, 7, 'ince_kruvasan_pizzalar', 'İnce Kruvasan Pizzalar', 3, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (8, 'sandvicler', 'Sandviçler', 8, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (28, 8, 'klasik_sandvicler', 'Klasik Sandviçler', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (29, 8, 'tostlar', 'Tostlar', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (30, 8, 'wraplar', 'Wraplar', 3, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (9, 'burgerler', 'Burgerler', 9, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (31, 9, 'klasik_burgerler', 'Klasik Burgerler', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (32, 9, 'ozel_burgerler', 'Özel Burgerler', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (33, 9, 'vejeteryan_burgerler', 'Vejeteryan Burgerler', 3, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (10, 'tatlilar', 'Tatlılar', 10, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (34, 10, 'sutlu_tatlilar', 'Sütlü Tatlılar', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (35, 10, 'serbetli_tatlilar', 'Şerbetli Tatlılar', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (36, 10, 'hamur_isi_tatlilar', 'Hamur İşi Tatlılar', 3, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (37, 10, 'meyveli_tatlilar', 'Meyveli Tatlılar', 4, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (38, 10, 'dondurma_ve_soguk_tatlilar', 'Dondurma ve Soğuk Tatlılar', 5, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (39, 10, 'pasta_ve_kek_cesitleri', 'Pasta ve Kek Çeşitleri', 6, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (11, 'kahvalti', 'Kahvaltı', 11, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (40, 11, 'serpme_kahvalti', 'Serpme Kahvaltı', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (41, 11, 'omlet_ve_yumurta_cesitleri', 'Omlet ve Yumurta Çeşitleri', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (42, 11, 'kahvaltilik_tatlilar', 'Kahvaltılık Tatlılar', 3, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (12, 'yan_urunler', 'Yan Ürünler', 12, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (43, 12, 'ekmek_ve_hamur_isleri', 'Ekmek ve Hamur İşleri', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (44, 12, 'soslar', 'Soslar', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (45, 12, 'garniturler', 'Garnitürler', 3, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (13, 'durum_ve_doner', 'Dürüm ve Döner', 13, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (46, 13, 'et_durum', 'Et Dürüm', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (47, 13, 'tavuk_durum', 'Tavuk Dürüm', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (48, 13, 'doner_porsiyon', 'Döner Porsiyon', 3, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (49, 13, 'iskender', 'İskender', 4, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (14, 'izgaralar', 'Izgaralar', 14, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (50, 14, 'kirmizi_et_izgara', 'Kırmızı Et Izgara', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (51, 14, 'tavuk_izgara', 'Tavuk Izgara', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (52, 14, 'karisik_izgara', 'Karışık Izgara', 3, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (15, 'deniz_urunleri', 'Deniz Ürünleri', 15, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (53, 15, 'balik_cesitleri', 'Balık Çeşitleri', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (54, 15, 'kabuklu_deniz_urunleri', 'Kabuklu Deniz Ürünleri', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (55, 15, 'meze_tarzi_deniz_urunleri', 'Meze Tarzı Deniz Ürünleri', 3, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (16, 'atistirmaliklar', 'Atıştırmalıklar', 16, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (56, 16, 'patates_kizartmasi_cesitleri', 'Patates Kızartması Çeşitleri', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (57, 16, 'kizartmalar', 'Kızartmalar', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (58, 16, 'cips_ve_nachos', 'Cips ve Nachos', 3, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (17, 'kokteyller', 'Kokteyller', 17, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (59, 17, 'alkollu_kokteyller', 'Alkollü Kokteyller', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (60, 17, 'alkolsuz_kokteyller', 'Alkolsüz Kokteyller (Mocktail)', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (18, 'asya_mutfagi', 'Asya Mutfağı', 18, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (61, 18, 'noodle_cesitleri', 'Noodle Çeşitleri', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (62, 18, 'sushi', 'Sushi', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (63, 18, 'wok_yemekleri', 'Wok Yemekleri', 3, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_main_category (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (19, 'cocuk_menusu', 'Çocuk Menüsü', 19, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (64, 19, 'cocuk_ana_yemekleri', 'Çocuk Ana Yemekleri', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_sub_category (id, main_category_id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (65, 19, 'cocuk_tatlilari', 'Çocuk Tatlıları', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET main_category_id=EXCLUDED.main_category_id, slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_menu_tag (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (1, 'glutensiz', 'Glutensiz', 1, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_menu_tag (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (2, 'vegan', 'Vegan', 2, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_menu_tag (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (3, 'vejeteryan', 'Vejeteryan', 3, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_menu_tag (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (4, 'acili', 'Acılı', 4, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_menu_tag (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (5, 'seker_ilavesiz', 'Şeker İlavesiz', 5, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
INSERT INTO tbl_menu_tag (id, slug, name, sort_order, created_at, updated_at, is_deleted) VALUES (6, 'laktozsuz', 'Laktozsuz', 6, NOW(), NOW(), FALSE) ON CONFLICT (id) DO UPDATE SET slug=EXCLUDED.slug, name=EXCLUDED.name, sort_order=EXCLUDED.sort_order, updated_at=NOW(), is_deleted=FALSE;
UPDATE tbl_menu_products p
SET sub_category_id = s.id
FROM tbl_sub_category s
WHERE p.sub_category_id IS NULL
  AND p.is_deleted = FALSE
  AND s.is_deleted = FALSE
  AND p.category IS NOT NULL
  AND LOWER(TRIM(p.category)) = LOWER(TRIM(s.name));

UPDATE tbl_menu_products p
SET sub_category_id = s.id
FROM tbl_menu_category c
JOIN tbl_sub_category s ON LOWER(TRIM(c.name)) = LOWER(TRIM(s.name)) AND s.is_deleted = FALSE
WHERE p.sub_category_id IS NULL
  AND p.is_deleted = FALSE
  AND p.category_id = c.category_id
  AND c.is_deleted = FALSE;

UPDATE tbl_menu_products
SET sub_category_id = 45
WHERE sub_category_id IS NULL;

ALTER TABLE tbl_menu_products ALTER COLUMN sub_category_id SET NOT NULL;

ALTER TABLE tbl_menu_products DROP CONSTRAINT IF EXISTS fk_menu_product_sub_category;
ALTER TABLE tbl_menu_products
    ADD CONSTRAINT fk_menu_product_sub_category
        FOREIGN KEY (sub_category_id) REFERENCES tbl_sub_category (id);

ALTER TABLE tbl_menu_products DROP CONSTRAINT IF EXISTS fk_menu_product_category;
ALTER TABLE tbl_menu_products DROP COLUMN IF EXISTS category_id;
ALTER TABLE tbl_menu_products DROP COLUMN IF EXISTS category;
