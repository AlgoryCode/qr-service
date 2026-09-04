BEGIN;

SELECT setval('tbl_menu_category_id_seq', COALESCE((SELECT MAX(id) FROM tbl_menu_category), 1), TRUE);
SELECT setval('tbl_menu_sub_category_id_seq', COALESCE((SELECT MAX(id) FROM tbl_menu_sub_category), 1), TRUE);

DO $$
DECLARE
    r RECORD;
    category_id BIGINT;
    new_sub_category_id BIGINT;
BEGIN
    FOR r IN
        SELECT * FROM (VALUES
            ('drink-red-wines', 'Kırmızı Şaraplar', ARRAY[779,780,781,782,783,784,785,786,787]::BIGINT[]),
            ('drink-white-wines', 'Beyaz Şaraplar', ARRAY[792,793,794,795,796,797,798,799]::BIGINT[]),
            ('drink-champagne-prosecco', 'Şampanya / Prosecco', ARRAY[788,789,790,791]::BIGINT[]),
            ('drink-rose-wines', 'Roze Şaraplar', ARRAY[800,801]::BIGINT[]),
            ('drink-semi-sweet-wines', 'Yarı Tatlı Şaraplar', ARRAY[802,803]::BIGINT[]),
            ('drink-beers', 'Biralar', ARRAY[860,861,862,863]::BIGINT[]),
            ('drink-raki', 'Rakılar', ARRAY[864,865,866,867,868,869,870,871,872,873,874]::BIGINT[]),
            ('drink-vodkas', 'Vodkalar', ARRAY[875,876,877,878,879,880,881]::BIGINT[]),
            ('drink-whiskies', 'Viskiler', ARRAY[882,883,884,885,886,887,888,889,890,891,892,893,894,895,896,897]::BIGINT[]),
            ('drink-cocktails', 'Kokteyller', ARRAY[723,724,725,726,727,728,729,730,731,732,733,734]::BIGINT[]),
            ('drink-liqueurs', 'Likörler', ARRAY[735,736,737,738,739,740,741,742]::BIGINT[]),
            ('drink-gin', 'Gin', ARRAY[743,744,745,746,747]::BIGINT[]),
            ('drink-tequila', 'Tekila', ARRAY[748,749,750]::BIGINT[]),
            ('drink-cognac', 'Konyak', ARRAY[751,752,753]::BIGINT[]),
            ('drink-cold-drinks', 'Soğuk İçecekler', ARRAY[835,836,837,838,839,840,841,842,843,844,845,846,847,848,849]::BIGINT[]),
            ('drink-hot-drinks', 'Sıcak İçecekler', ARRAY[850,851,852,853,854,855,856,857,858,859]::BIGINT[])
        ) AS x(slug, name, product_ids)
    LOOP
        INSERT INTO tbl_menu_category
            (menu_id, user_id, slug, name, sort_order, created_at, updated_at, is_deleted)
        VALUES
            (16, 20, r.slug, r.name, 0, NOW(), NOW(), FALSE)
        ON CONFLICT (menu_id, slug) DO UPDATE
            SET user_id = EXCLUDED.user_id, name = EXCLUDED.name,
                updated_at = NOW(), is_deleted = FALSE
        RETURNING id INTO category_id;

        INSERT INTO tbl_menu_sub_category
            (menu_id, menu_category_id, slug, name, sort_order, created_at, updated_at, is_deleted)
        VALUES
            (16, category_id, r.slug, r.name, 0, NOW(), NOW(), FALSE)
        ON CONFLICT (menu_id, slug) DO UPDATE
            SET menu_category_id = EXCLUDED.menu_category_id, name = EXCLUDED.name,
                updated_at = NOW(), is_deleted = FALSE
        RETURNING id INTO new_sub_category_id;

        UPDATE tbl_menu_products
        SET sub_category_id = new_sub_category_id, updated_at = NOW()
        WHERE menu_id = 16 AND product_id = ANY(r.product_ids);
    END LOOP;
END $$;

-- Önceki hatalı ürün-başına-kategori kayıtlarını kaldır; diğer yemek kategorilerine dokunma.
UPDATE tbl_menu_sub_category
SET is_deleted = TRUE, updated_at = NOW()
WHERE menu_id = 16 AND slug LIKE 'icecek-%';

UPDATE tbl_menu_category
SET is_deleted = TRUE, updated_at = NOW()
WHERE menu_id = 16 AND slug LIKE 'icecek-%';

-- Eski toplu İçecekler kategorisi aktif kalmasın; diğer ana kategoriler korunur.
UPDATE tbl_menu_sub_category
SET is_deleted = TRUE, updated_at = NOW()
WHERE menu_id = 16 AND menu_category_id = 39;

UPDATE tbl_menu_category
SET is_deleted = TRUE, updated_at = NOW()
WHERE menu_id = 16 AND id = 39;

COMMIT;
