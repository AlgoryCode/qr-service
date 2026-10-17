# Monolit entegrasyon özeti

Tek Spring Boot uygulaması (`AlgoryqrServiceApplication`) altında dört domain kökü aynı JVM ve veritabanında çalışır:

| Modül | Paket kökü | Dahili erişim (qrservice → modül) |
|-------|------------|-----------------------------------|
| Auth | `authservice` | `AuthMerchantClient`, `AuthCredentialClient`, `AuthStaffClient` → auth **use case** bean'leri |
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
