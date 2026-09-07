# Katalog Seed + Deneme Paketleri

## Model

Ayrı trial entity yok. Deneme, admin’de tanımlanan **normal paket**tir:

| Alan | Anlam |
|------|--------|
| `active && !purchasable && !systemManaged` | Kullanıcı deneme seçicisinde görür ve seçebilir |
| `validityDays` | Deneme süresi (gün); ücretli abonelik süresi ile aynı alan |
| `purchasable=true` | Ücretli satın alınabilir paket |
| `items` / `features` | Deneme hakları ve UI maddeleri |

Başlatınca oluşan kayıt: `PurchaseType.TRIAL`, `price=0`, `ACTIVE`, `expiresAt = now + validityDays`; haklar paketin `items` içeriğidir.

Deneme paketi için `validityDays >= 1` zorunludur. `trialEligible` / `trialDays` kolonları yoktur.

## Yönetim

| Kontrol | Nasıl |
|---------|--------|
| Hangi paketler denemede | `active && !purchasable && !systemManaged` (öncelik desc) |
| Süre | `validityDays` |
| Haklar | Paket `items` |
| UI maddeleri | Paket `features` |
| Denemeyi kapat | `purchasable=true`, `active=false` veya `systemManaged=true` (yeni start reddedilir; süren denemeler `expiresAt`’e kadar devam) |
| Seed / reset | JSON import |

## Seed dosyaları

- `src/main/resources/seed/catalog-tiers.json` — ürün + Başlangıç / Pro / Ultimate / Ultimate Deneme
- `src/main/resources/seed/catalog-tiers.sql` — opsiyonel manuel SQL
- `src/main/resources/seed/apply-ultimate-trial-package.sql` — stage/prod manuel upsert + trial kolon drop

### Ürünler

| Kod | Açıklama |
|-----|----------|
| `QR_CREATE` | QR oluşturma kotası |
| `QR_MENU` | Dijital menü (yayın, geri bildirim, rezervasyon) |
| `MENU_PRODUCT` | Menüde tanımlanabilecek ürün sayısı |
| `WAITER_PANEL` | Garson paneli, masa, sipariş ve müşteri yönetimi (Ultimate) |
| `SMART_REPORTING` | Ciro takibi ve akıllı raporlar |
| `SMART_ASSISTANT` | Akıllı asistan (Ultimate) |
| `SMART_SUMMARY` | Akıllı özet (Ultimate) |
| `CUSTOM_DESIGN` | Özel tasarım menü (Ultimate) |
| `AI_MENU_IMPORT` | Menü fotoğrafından AI ile ürün çıkarma (Ultimate) |

### Satılabilir paketler

- **Başlangıç** (`STARTER_PACKAGE`): 5× `QR_CREATE`, 1× `QR_MENU`, 50× `MENU_PRODUCT` — 299 TRY/ay, yıllık 2988 TRY, `purchasable=true`
- **Pro** (`PRO_PACKAGE`): sınırsız `QR_CREATE`, `QR_MENU`, `MENU_PRODUCT` + `SMART_REPORTING` — 599 TRY/ay, yıllık 5643 TRY, `purchasable=true`
- **Ultimate** (`ULTIMATE_PACKAGE`): Pro + `SMART_ASSISTANT`, `SMART_SUMMARY`, `CUSTOM_DESIGN`, `WAITER_PANEL`, `AI_MENU_IMPORT` — 3450 TRY/ay (KDV dahil), yıllık 31823.57 TRY, `purchasable=true`
- **Ultimate Deneme** (`ULTIMATE_TRIAL_PACKAGE`): Ultimate ile aynı haklar — `purchasable=false`, `validityDays=15`

Garson sipariş/adisyon modülü yalnızca Ultimate pakette (`WAITER_PANEL` / `WAITER_PANEL_OWNER`). Başlangıç ve Pro paketlerinde bu özellik yoktur.

`FREE_PACKAGE` ve `CORPORATE_PACKAGE` seed'de `active=false`; yeni kullanıcılara otomatik paket verilmez.

Fiyatlar import'ta ürün `unitPrice` + KDV üzerinden hesaplanır; satılabilir paketler için JSON `lockPrice` / `yearlyPrice` kullanılabilir.

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

`ULTIMATE_TRIAL_PACKAGE` ayrıca Flyway `V91` / `V92` ile deploy’da otomatik eklenir; seed import şart değildir.

## Trial API

| Method | Path | Davranış |
|--------|------|----------|
| GET | `/trials/eligible-packages` | `active && !purchasable && !systemManaged` paketler (`validityDays` dahil) |
| POST | `/trials` | `{ "packageId" }` ile TRIAL başlat |
| GET | `/trials/status` | `AVAILABLE` / `ACTIVE` / `TRIAL_EXPIRED` + bitiş bilgisi |

Legacy: `POST /trials/digital-menu-pro` denemeye açık en yüksek öncelikli paketi başlatır (`ULTIMATE_TRIAL_PACKAGE`).

### Admin deneme uzatma

| Method | Path | Davranış |
|--------|------|----------|
| POST | `/admin/users/{id}/trial/extend` | `{ "days": 30 }` — aktif denemeye gün ekler, bitmiş denemeyi yeniden açar veya Ultimate denemesi başlatır |

Admin uzatması aktif ücretli paket varken reddedilir; deneme hakkı bayrakları sıfırlanır.

### Backend kurallar

1. Kullanıcı başına tek deneme (`uk_purchase_trial_user` + `tbl_user.trial_end_date` / `trial_used`).
2. Paket `active && !purchasable && !systemManaged` ve `validityDays >= 1`; Free / `systemManaged` hedef olamaz.
3. Aktif ücretli usable paket varken start → 400.
4. Start: TRIAL ACTIVE, `expiresAt = now + validityDays`, entitlement grant, diğer ACTIVE → SUPERSEDED; `trial_used` ve `trial_end_date` **başlangıçta set edilmez**.
5. Bitiş: `expiresAt` sonrası `trial_end_date = expiresAt`, `trial_used = true`; entitlement usable değildir; `expirePurchase` menü erişimini senkronize eder.
6. Kontrol: `trial_end_date IS NOT NULL` veya `trial_used = true` → deneme kullanılmış sayılır.

### Örnek

```http
POST /trials
{ "packageId": 12 }
```

```json
{
  "lifecycle": "ACTIVE",
  "packageId": 12,
  "packageName": "Ultimate Deneme",
  "expiresAt": "2026-08-06T12:00:00",
  "daysUntilExpiry": 15,
  "price": 0.00,
  "currency": "TRY"
}
```

## Kullanıcı UI

1. `GET /trials/status` → `AVAILABLE` ise eligible kartlar (`validityDays` göster).
2. Seçim → `POST /trials` + paket süresi/hakları onayı.
3. `ACTIVE` banner: paket adı + bitiş / kalan gün.
4. `TRIAL_EXPIRED` veya deneme kullanılmış → start gizli; ücretli satın almaya yönlendir.
