# Paket yapısı (algoryqr-service monolith)

`com.ael.algoryqrservice` altında yalnızca **servis kökleri** ve bootstrap vardır:

| Kök | Açıklama |
|-----|----------|
| `authservice` | Kimlik, oturum, JWT (`/api/v1/auth`); merchant/staff **persistence yok** |
| `fulfillmentservice` | Eski qr-fulfillment-service (paket, entitlement) |
| `paymentservice` | Eski payment-service (PayTR, kart, abonelik, outbox) |
| `merchantservice` | İşletme kimliği (`tbl_merchant`) |
| `staffservice` | Personel (`tbl_merchant_staff`), `/staff` API |
| `customerservice` | Müşteri (`tbl_customer`), `/customer` API |
| `adminservice` | Yönetim paneli (`tbl_dashboard_user`), `/dashboard/auth/*`, `/admin/**` |
| `qrservice` | Ana QR domain (menü, sipariş, billing, entegrasyon, …) |
| *(dosya)* `AlgoryqrServiceApplication` | Spring Boot giriş noktası |

Modül içi alt paketler (`qrservice.menu`, `qrservice.billing`, …) domain sınırlarını korur; üst seviyede dağınık `billing`, `menu` vb. paket yoktur.

REST özeti (merchant/staff/customer): [rest-domains-merchant-staff-customer.md](rest-domains-merchant-staff-customer.md).

## Entity tekilleştirme

- **Paket kataloğu:** `qrservice.model.Product`, `PlanPackage`, …
- **Menü ürünleri:** `qrservice.product.model.*` (çift JPA eşlemesi yok)

## Monolit entegrasyon

Tüm modüller arası çağrılar için bkz. [monolith-integration.md](monolith-integration.md).

## Scriptler

- `scripts/restructure-service-packages.py` — modülleri `qrservice` altına taşır (tek seferlik / yeni ortam).
- `scripts/dedupe-entities.py` — yinelenen entity sınıflarını temizler.
- `scripts/merge-payment-module.py` / `scripts/fix-payment-after-merge.py` — payment-service kaynak kopyası ve merge sonrası düzeltmeler.
