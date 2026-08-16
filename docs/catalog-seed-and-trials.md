# Katalog Seed + Deneme Paketleri

## Model

Ayrı trial entity yok. Deneme, admin’de tanımlanan **normal paket**tir:

| Alan | Anlam |
|------|--------|
| `trialEligible=true` | Kullanıcı deneme seçicisinde görür ve seçebilir |
| `trialDays` | Deneme süresi (gün); ücretli `validityDays`’ten bağımsız |
| `purchasable=true` | Aynı paket ücretli satın alınabilir |
| `validityDays` | Ücretli abonelik / satın alma süresi |
| `items` / `features` | Deneme hakları ve UI maddeleri |

Başlatınca oluşan kayıt: `PurchaseType.TRIAL`, `price=0`, `ACTIVE`, `expiresAt = now + trialDays`; haklar paketin `items` içeriğidir.

`trialEligible=true` ise `trialDays` zorunludur ve `1..min(validityDays, 30)` aralığında olmalıdır.

## Yönetim

| Kontrol | Nasıl |
|---------|--------|
| Hangi paketler denemede | Paket formunda **Deneme olarak sun** (`trialEligible`) |
| Süre | `trialDays` |
| Haklar | Paket `items` |
| UI maddeleri | Paket `features` |
| Denemeyi kapat | `trialEligible=false` veya `active=false` (yeni start reddedilir; süren denemeler `expiresAt`’e kadar devam) |
| Seed / reset | JSON import |

## Seed dosyaları

- `src/main/resources/seed/catalog-tiers.json` — ürün + Başlangıç / Pro / Ultimate
- `src/main/resources/seed/catalog-tiers.sql` — opsiyonel manuel SQL

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

### Satılabilir paketler

- **Başlangıç** (`STARTER_PACKAGE`): 5× `QR_CREATE`, 1× `QR_MENU`, 50× `MENU_PRODUCT` — 299 TRY/ay, yıllık 2988 TRY, `trialEligible=false`
- **Pro** (`PRO_PACKAGE`): sınırsız `QR_CREATE`, `QR_MENU`, `MENU_PRODUCT` + `SMART_REPORTING` — 599 TRY/ay, yıllık 5643 TRY, `trialEligible=false`
- **Ultimate** (`ULTIMATE_PACKAGE`): Pro + `SMART_ASSISTANT`, `SMART_SUMMARY`, `CUSTOM_DESIGN`, `WAITER_PANEL` — 999 TRY/ay, yıllık 9215 TRY, `trialEligible=true`, `trialDays=30`

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

## Trial API

| Method | Path | Davranış |
|--------|------|----------|
| GET | `/trials/eligible-packages` | `trialEligible && active && !systemManaged` paketler (`trialDays` dahil) |
| POST | `/trials` | `{ "packageId" }` ile TRIAL başlat |
| GET | `/trials/status` | `AVAILABLE` / `ACTIVE` / `TRIAL_EXPIRED` + bitiş bilgisi |

Legacy: `POST /trials/digital-menu-pro` denemeye açık en yüksek öncelikli paketi başlatır (Ultimate).

### Admin deneme uzatma

| Method | Path | Davranış |
|--------|------|----------|
| POST | `/admin/users/{id}/trial/extend` | `{ "days": 30 }` — aktif denemeye gün ekler, bitmiş denemeyi yeniden açar veya Ultimate denemesi başlatır |

Admin uzatması aktif ücretli paket varken reddedilir; deneme hakkı bayrakları sıfırlanır.

### Backend kurallar

1. Kullanıcı başına tek deneme (`uk_purchase_trial_user` + `tbl_user.trial_end_date` / `trial_used`).
2. Paket `active && trialEligible` ve geçerli `trialDays`; Free / `systemManaged` hedef olamaz.
3. Aktif ücretli usable paket varken start → 400.
4. Start: TRIAL ACTIVE, `expiresAt = now + trialDays`, entitlement grant, diğer ACTIVE → SUPERSEDED; `trial_used` ve `trial_end_date` **başlangıçta set edilmez**.
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
  "packageName": "Pro",
  "expiresAt": "2026-08-06T12:00:00",
  "daysUntilExpiry": 7,
  "price": 249.00,
  "currency": "TRY"
}
```

## Kullanıcı UI

1. `GET /trials/status` → `AVAILABLE` ise eligible kartlar (`trialDays` göster).
2. Seçim → `POST /trials` + paket süresi/hakları onayı.
3. `ACTIVE` banner: paket adı + bitiş / kalan gün.
4. `TRIAL_EXPIRED` veya deneme kullanılmış → start gizli; ücretli satın almaya yönlendir.
