# Fulfillment modülü (monolit)

Paket atama, aktif entitlement ve `/api/v1/users/{id}/…` fulfillment API'leri `com.ael.algoryqrservice.fulfillmentservice` paketinde qr-service ile aynı JVM'de çalışır.

## Veritabanı

- Tek Postgres: `DB_URL`. Katalog tabloları (`tbl_plan_package`, `tbl_product`) qr-service Flyway'inde.
- Runtime tablolar: `V150__fulfillment_runtime_tables.sql` (`package_fulfillment`, `active_package`, `user_entitlement`).

## Dahili entegrasyon

- `qrservice` tarafı `FulfillmentServiceClient` ile doğrudan `FulfillmentService` ve `EntitlementUsageService` bean'lerini kullanır (HTTP veya Rabbit yok).
- Ödeme sonrası paket tanımı: `FulfillmentPaymentBridge` → `FulfillmentService.fulfillPaidPackage` (ödeme akışından doğrudan çağrı).

Paket kalemleri yalnızca katalog modelinden (`PlanPackage` + items) türetilir; atama isteklerinde satır listesi gönderilmez.

## API

- `GET/POST /api/v1/users/{userId}/package`, entitlements, consume/release vb. harici istemciler için aynı kalır.
