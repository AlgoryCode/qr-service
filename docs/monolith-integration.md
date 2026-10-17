# Monolit entegrasyon özeti

Tek Spring Boot uygulaması (`AlgoryqrServiceApplication`) altında dört domain kökü aynı JVM ve veritabanında çalışır:

| Modül | Paket kökü | Dahili erişim (qrservice → modül) |
|-------|------------|-----------------------------------|
| Auth | `authservice` | Oturum/JWT; `AuthCredentialClient` → auth use case |
| Merchant (in-process) | `merchantservice` | `AuthMerchantClient` → `FindMerchantUseCase`, … |
| Staff (in-process) | `staffservice` | `AuthStaffClient` → `UpsertStaffUseCase` |
| Customer (in-process) | `customerservice` | `CustomerAccountService`, entity/repo |
| Fulfillment | `fulfillmentservice` | `FulfillmentServiceClient` → `FulfillmentService`, `EntitlementUsageService` |
| Payment | `paymentservice` | `PaymentServiceClient` → `PaymentService`, `SubscriptionService`, `PaymentMethodService`, … |
| QR domain | `qrservice` | Doğrudan servis/repository; harici entegrasyonlar (PayTR, Odeal, Uber Eats, AI servisi, Seaweed) kendi URL/env ile kalır |

## Kaldırılan kalıplar

- `fulfillment.external`, `payment.external`, `AUTH_SERVICE_REMOTE_ENABLED`, loopback `RestClient` ile kendi kendine HTTP
- `ObjectProvider` ile “bean yoksa geç” fulfillment/payment köprüleri
- Fulfillment Rabbit tüketicisi; ödeme outbox → doğrudan `PaymentModuleQrEventBridge`
- `auth.service.*` / `qr.service.*` remote client yapılandırması

## Kaldırılan / kullanılmayan env (Coolify’dan silinebilir)

`PAYMENT_SERVICE_URL`, `FULFILLMENT_EXTERNAL_ENABLED`, `FULFILLMENT_SERVICE_URL`, `FULFILLMENT_SERVICE_AUTH_TOKEN`, `AUTH_SERVICE_REMOTE_ENABLED`, `PAYMENT_DEFAULT_PROVIDER`, `APP_SEED_PACKAGE_CATALOG_ENABLED`

## Paket / erişim

- Katalog: `PlanPackage` + kalemler (`PlanPackageAssignmentPolicy`)
- Ödeme sonrası paket: yalnızca `FulfillmentPaymentBridge`
- Login: `MerchantPackageGate`; feature: `ProductAccessGatewayFilter` + entitlement

## HTTP API

Modül controller'ları (`/api/v1/...`, `/payments/...`, auth endpoint'leri) dış istemciler için aynı kalır; monolit içi çağrılar mümkün olduğunda application katmanına gider.

## İşletme kimliği (`tbl_user` → `tbl_merchant`)

- Giriş ve JWT: `tbl_merchant.id` (`merchantId`).
- `tbl_user`: panel profili / legacy satır; silinmez.
- Flyway **V157**, **V156**, **V158**, **V159**: eksik merchant oluşturma ve panel verisindeki `user_id` / `merchant_id` alanlarını e-posta eşleşmesiyle `tbl_merchant.id`'ye taşıma (satır silmeden).
- Uygulama: `SecurityUtils.getCurrentMerchantId()` ve `/access/context` her zaman merchant id kullanır; profil için `tbl_user` e-posta ile bulunur.
