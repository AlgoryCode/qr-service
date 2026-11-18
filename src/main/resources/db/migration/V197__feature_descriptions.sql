-- Merchant-facing descriptions for each feature, shown on the subscription page next to the name.
-- Only fills empty descriptions so texts edited from the admin panel are kept.
UPDATE features f
SET description = v.description, updated_at = NOW()
FROM (VALUES
    ('QR_BRANCH',          'Panelde yönetebileceğiniz şube sayısı.'),
    ('QR_MENU',            'Şubelerinizde yayınlayabileceğiniz dijital menü sayısı.'),
    ('QR_CREATE',          'Menü dışında oluşturabileceğiniz QR kodlar (link, Wi-Fi, iletişim vb.).'),
    ('MENU_PRODUCT',       'Menülerinize ekleyebileceğiniz ürün sayısı.'),
    ('KAFE_MENU',          'Kafeler için hazır menü şablonu.'),
    ('WAITER_PANEL',       'Garsonlarınız masadan sipariş alır, adisyon açar ve hesabı kapatır.'),
    ('ONLINE_ORDER',       'Paket servis için online sipariş mağazası, sipariş paneli ve kurye takibi.'),
    ('CUSTOM_DESIGN',      'Menünüz markanıza özel renk ve tasarımla hazırlanır.'),
    ('AI_MENU_IMPORT',     'Menü fotoğrafından ürünleri yapay zekâ ile otomatik çıkarır.'),
    ('SMART_ASSISTANT',    'Müşterileriniz menüde yapay zekâ asistanına soru sorup öneri alır.'),
    ('SMART_SUMMARY',      'Satış ve sipariş verilerinizin kısa günlük özeti.'),
    ('SMART_REPORTING',    'Satışlarınızı yorumlayan, öneri sunan haftalık akıllı raporlar.'),
    ('SMART_REPORT_QUOTA', 'Her hafta ücretsiz oluşturabileceğiniz akıllı rapor sayısı.')
) AS v(feature_key, description)
WHERE f.feature_key = v.feature_key
  AND (f.description IS NULL OR btrim(f.description) = '');
