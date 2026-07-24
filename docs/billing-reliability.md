# Billing reliability

## Payment identity

| Field | Role |
|---|---|
| `paymentConversationId` | Purchase anchor (never overwritten after first set). Used in event `validateIdentity` via metadata `purchaseConversationId`. |
| `currentPeriodConversationId` | Latest charge conversation id (cycle). Used for cooling-window refunds. |
| `paymentId` | Latest gateway charge id (Iyzico). Audit / display only for refund wire format. |

Renewals create a new payment-service row (`conversationId = subscriptionId-cycle-N`) and a new gateway `paymentId`. Refunds do not create a new payment; they credit the existing charge row.

## Cancel / refund

- Period-end: `POST /purchases/{id}/cancel-at-period-end` (access until `expiresAt`)
- Immediate refund: `POST /purchases/{id}/cancel-with-refund` within cooling window (monthly 7d / yearly 14d)
- Do not use `POST /billing/subscriptions/{id}/cancel` (rejected)

Refund saga: `PENDING` → gateway refund → local cancel. If remote subscription cancel fails after refund, status `NEEDS_RECONCILE` and `RefundReconcileScheduler` retries.

## Ownership

payment-service `GET /payments/{conversationId}` and `POST /payments/refund` require `X-Account-Id` and assert `source_metadata.userId`.
