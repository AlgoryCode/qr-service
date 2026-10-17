# Paket yapısı (algoryqr-service monolith)

`com.ael.algoryqrservice` altında yalnızca **servis kökleri** ve bootstrap vardır:

| Kök | Açıklama |
|-----|----------|
| `authservice` | Eski algoryqr-authservice (kimlik, oturum, merchant) |
| `fulfillmentservice` | Eski qr-fulfillment-service (paket, entitlement) |
| `paymentservice` | Eski payment-service (PayTR, kart, abonelik, outbox) |
| `qrservice` | Ana QR domain (menü, sipariş, billing, entegrasyon, …) |
| *(dosya)* `AlgoryqrServiceApplication` | Spring Boot giriş noktası |

Modül içi alt paketler (`qrservice.menu`, `qrservice.billing`, …) domain sınırlarını korur; üst seviyede dağınık `billing`, `menu` vb. paket yoktur.

## Entity tekilleştirme

- **Paket kataloğu:** `qrservice.model.Product`, `PlanPackage`, …
- **Menü ürünleri:** `qrservice.product.model.*` (çift JPA eşlemesi yok)

## Monolit entegrasyon

Tüm modüller arası çağrılar için bkz. [monolith-integration.md](monolith-integration.md).

## Scriptler

- `scripts/restructure-service-packages.py` — modülleri `qrservice` altına taşır (tek seferlik / yeni ortam).
- `scripts/dedupe-entities.py` — yinelenen entity sınıflarını temizler.
- `scripts/merge-payment-module.py` / `scripts/fix-payment-after-merge.py` — payment-service kaynak kopyası ve merge sonrası düzeltmeler.
