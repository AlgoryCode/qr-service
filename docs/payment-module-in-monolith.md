# Payment modülü (monolit)

PayTR ve abonelik akışları `com.ael.algoryqrservice.paymentservice` paketinde qr-service ile aynı JVM'de çalışır.

## Dahili entegrasyon

- `qrservice.billing` `PaymentServiceClient` üzerinden doğrudan `PaymentService`, `SubscriptionService`, `PaymentMethodService` ve `CardStorageService` bean'lerini çağırır; loopback HTTP veya `PAYMENT_SERVICE_URL` gerekmez.
- Ödeme olayları: outbox → `PaymentModuleQrEventBridge` → qr billing event handler'ları (aynı uygulama içi).

Harici PayTR / gateway yapılandırması `payment.gateway.*` altında kalır.

## API

- `/payments/*`, `/api/v1/payment-methods`, `/api/v1/subscriptions` endpoint'leri değişmeden sunulur.
