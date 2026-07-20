# Katalog Seed + Deneme Paketleri

## Model

Ayrı trial entity yok. Deneme, admin’de tanımlanan **normal paket**tir:

| Alan | Anlam |
|------|--------|
| `trialEligible=true` | Kullanıcı deneme seçicisinde görür ve seçebilir |
| `purchasable=true` | Aynı paket ücretli satın alınabilir |
| `validityDays` | Deneme süresi (= paket süresi) |
| `items` / `features` | Deneme hakları ve UI maddeleri |

Başlatınca oluşan kayıt: `PurchaseType.TRIAL`, `price=0`, `ACTIVE`; haklar paketin içeriğidir.

## Yönetim

| Kontrol | Nasıl |
|---------|--------|
| Hangi paketler denemede | Paket formunda **Deneme olarak sun** (`trialEligible`) |
| Süre | `validityDays` |
| Haklar | Paket `items` |
| UI maddeleri | Paket `features` |
| Denemeyi kapat | `trialEligible=false` veya `active=false` (yeni start reddedilir; süren denemeler `expiresAt`’e kadar devam) |
| Seed / reset | JSON import |

İleride “sadece deneme SKU” da aynı tabloda tanımlanabilir (`purchasable=false`, `trialEligible=true`, kısa `validityDays`).

## Seed dosyaları

- `src/main/resources/seed/catalog-tiers.json` — ürün + Free / Pro / Ultimate
- `src/main/resources/seed/catalog-tiers.sql` — opsiyonel manuel SQL

Varsayılan içerik:

- **Free**: 5× `QR_CREATE`, fiyat 0, `purchasable=false`, `systemManaged=true`, `trialEligible=false`
- **Pro**: 30 QR + 1 menü + 1 agent, ~199 TRY / 30 gün, `trialEligible=true`
- **Ultimate**: 100 QR + 5 menü + 3 agent + analytics, ~499 TRY / 30 gün, `trialEligible=true`

Fiyatlar import’ta ürün `unitPrice` + KDV üzerinden hesaplanır; Free/systemManaged 0 zorlanır; Pro/Ultimate için JSON `lockPrice` kullanılabilir.

## Import API

```http
POST /admin/catalog/import?useClasspathSeed=true
Authorization: Bearer <admin>
```

veya body ile JSON document:

```http
POST /admin/catalog/import
Content-Type: application/json

{ "products": [...], "packages": [...] }
```

Yanıt: `{ productsUpserted, packagesUpserted, packageCodes }`.

Admin dashboard: Paketler → **Seed katalogu içe aktar**.

## Trial API

| Method | Path | Davranış |
|--------|------|----------|
| GET | `/trials/eligible-packages` | `trialEligible && active && !systemManaged` paketler |
| POST | `/trials` | `{ "packageId" }` ile TRIAL başlat |
| GET | `/trials/status` | `AVAILABLE` / `ACTIVE` / `TRIAL_EXPIRED` + bitiş bilgisi |

Legacy: `/trials/digital-menu-pro` yeni servise delege edilir.

### Backend kurallar

1. Kullanıcı başına tek deneme (`uk_purchase_trial_user`).
2. Paket `active && trialEligible`; Free / `systemManaged` hedef olamaz.
3. Aktif ücretli usable paket varken start → 400.
4. Start: TRIAL ACTIVE, `expiresAt = now + validityDays`, entitlement grant, diğer ACTIVE → SUPERSEDED.
5. Status bitişte expire + Free restore; yanıtta `packageId/name`, `expiresAt`, `daysUntilExpiry`.

### Örnek

```http
POST /trials
{ "packageId": 12 }
```

```json
{
  "lifecycle": "ACTIVE",
  "packageId": 12,
  "packageName": "Pro",
  "expiresAt": "2026-08-18T12:00:00",
  "daysUntilExpiry": 30,
  "price": 0,
  "currency": "TRY"
}
```

## Kullanıcı UI

1. `GET /trials/status` → `AVAILABLE` ise eligible kartlar.
2. Seçim → `POST /trials` + paket süresi/hakları onayı.
3. `ACTIVE` banner: paket adı + bitiş / kalan gün.
4. `TRIAL_EXPIRED` veya deneme kullanılmış → start gizli; ücretli satın almaya yönlendir.
