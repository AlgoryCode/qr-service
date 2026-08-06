# Payment events wire contract

Cross-service contract between **payment-service** (publisher) and **qr-service** (consumer).

Mirrored in payment-service: `docs/payment-events-contract.md`.

## Topology

| Item | Value |
|------|--------|
| Exchange | `payment.events` (topic, durable) |
| Routing key | `{serviceName}.payment.events` (example: `qr-service.payment.events`) |
| Queue (qr-service) | `qr-service.payment.events` |
| DLQ | `qr-service.payment.events.dlq` |
| Body | JSON object (schema below) |

## Required AMQP metadata (publisher)

| Property / header | Value |
|-------------------|--------|
| `contentType` | `application/json` |
| `__TypeId__` | `payment.completed` |
| `eventType` | Business event type from body (when non-blank) |
| `purchaseId` | `sourceReferenceId` when present |

Constants: `com.ael.algoryqrservice.messaging.payment.PaymentEventWireContract` (same string values as payment-service).

## Consumer deserialization (qr-service)

Schema-first: `PaymentEventConsumer` receives raw `Message`, then `JacksonPaymentEventPayloadConverter` maps body bytes → `PaymentCompletedEventDto` via `ObjectMapper`.

- Does **not** depend on `__TypeId__` or `contentType`
- Rejects empty body / missing `eventId` / missing `eventType` without requeue
- Dispatch: `PaymentEventHandler` strategies via `PaymentEventHandlerRegistry`

## Payload fields

| Field | Type | Notes |
|-------|------|--------|
| `eventId` | string | Inbox idempotency key |
| `eventType` | string | See catalog |
| `occurredAt` | string | ISO-8601 |
| `paymentId` | string | |
| `conversationId` | string | |
| `serviceName` | string | |
| `sourceReferenceId` | string | Usually purchase id |
| `sourceMetadata` | object | Opaque map |
| `purchaseId` / `userId` / `packageId` / `packageCode` | string | |
| `paymentStyle` | string | |
| `bankInstallmentCount` | number | nullable |
| `subscriptionId` | string | nullable |
| `billingCycleNumber` | number | nullable |
| `periodStart` / `periodEnd` | string | date when applicable |
| `amount` | number | |
| `currency` | string | |
| `errorCode` | string | publisher-only; ignored if absent |
| `failureReason` | string | nullable |

Consumer-reserved (may be absent): `installmentId`, `installmentNumber`, `installmentCount`, `subscriptionStatus`.

## eventType → handler (qr-service)

| `eventType` | Handler |
|-------------|---------|
| `payment.success` | `PaymentSuccessEventHandler` → `handlePaymentSuccess` |
| `payment.installment.paid` | same |
| `payment.subscription.paid` | same |
| `payment.failed` | `PaymentFailedEventHandler` → `handlePaymentFailed` |
| `payment.installment.failed` | same |
| `payment.subscription.failed` | same |
| `payment.subscription.past_due` | same |
| `payment.installment.overdue` | `PaymentOverdueEventHandler` |
| `payment.refunded` | `PaymentRefundedEventHandler` |
| `payment.chargeback` | same |
| `subscription.cancelled_at_period_end` | `SubscriptionCancelledAtPeriodEndHandler` |
| other | `InvalidPaymentEventException` → reject, no requeue |

Published today by payment-service: `payment.success`, `payment.failed`, `payment.subscription.paid`, `payment.subscription.failed`, `payment.subscription.past_due`, `payment.refunded`. Installment / chargeback / cancel-at-period-end are reserved.

## Example

```json
{
  "eventId": "3fee541d-c952-3dab-bacd-9dc6ad3aac67",
  "eventType": "payment.subscription.paid",
  "occurredAt": "2026-08-06T13:38:57.660816024Z",
  "paymentId": "37153617",
  "conversationId": "qr-purchase-46-7934bcb9",
  "serviceName": "qr-service",
  "sourceReferenceId": "46",
  "purchaseId": "46",
  "userId": "10",
  "packageId": "3",
  "packageCode": "ULTIMATE_PACKAGE",
  "paymentStyle": "SUBSCRIPTION",
  "subscriptionId": "04f8ade5-92cb-3936-bf4b-c170204b7333",
  "billingCycleNumber": 1,
  "periodStart": "2026-08-06",
  "periodEnd": "2026-09-04",
  "amount": 649.0,
  "currency": "TRY"
}
```

## Ops: stuck purchase after DLQ

1. Deploy qr-service with schema-first consumer (and preferably payment-service with wire headers).
2. Republish message from `qr-service.payment.events.dlq`, **or**
3. Run purchase reconcile for the `purchaseId` (e.g. purchase `46`) if inbox/event cannot be replayed cleanly.
