# Menü ziyaret analitiği

Public menü QR taramalarında session tabanlı olaylar toplanır; dashboard `/dashboard/analitik` bu veriyi raporlar.

## Tablolar

- `tbl_menu_analytics_session` — client UUID, `menu_id`, `started_at`, `last_seen_at`, `device_type`, `ip_hash`, `user_agent`
- `tbl_menu_analytics_event` — `session_id`, `menu_id`, `event_type`, `category_id`, `product_id`, `sequence`, `occurred_at`

`event_type`: `MENU_OPEN` | `CATEGORY_VIEW` | `PRODUCT_VIEW`

Migration: `V16__menu_analytics_events.sql`

## Yazma API (public)

`POST /analytics/menu/{menuId}/events` — `permitAll`

```json
{
  "sessionId": "uuid",
  "deviceType": "MOBILE",
  "events": [
    { "type": "MENU_OPEN", "sequence": 1, "occurredAt": "2026-07-18T12:00:00" },
    { "type": "CATEGORY_VIEW", "categoryId": 1, "sequence": 2 },
    { "type": "PRODUCT_VIEW", "productId": 9, "categoryId": 1, "sequence": 3 }
  ]
}
```

Kurallar:

- Menü aktif ve `publicAccessEnabled` olmalı
- En fazla 50 olay / istek
- Session yoksa oluşturulur; `last_seen_at` güncellenir
- IP hash’lenir
- `deviceType`: client gönderir (`MOBILE` | `TABLET` | `DESKTOP`); yoksa UA’dan çıkarılır
- Gerçek tarayıcı UA: `X-Client-User-Agent` (BFF axios UA’sını ezmesin diye)

BFF: `POST /api/analytics/menu/{menuId}/events`

## Okuma API (owner)

`GET /analytics/menu/{menuId}/report?from=&to=` — scope `QR_ANALYTICS_OWNER`

Dönen başlıca alanlar: `kpis`, `daily`, `hourly`, `devices`, `topProducts`, `topCategories`, `categoryProductTree`, `sampleJourneys`, `funnel`.

BFF: `GET /api/analytics/menu/{menuId}/report`

## Client instrumentation

- Session: `localStorage` anahtarı `algory_menu_sid_{menuId}`
- Sequence: `algory_menu_seq_{menuId}`
- `MENU_OPEN` mount’ta; kategori seçimi / scroll; ürün detay veya kart tıklaması

## Entitlement

Ürün: `QR_ANALYTICS` / scope: `QR_ANALYTICS_OWNER`
