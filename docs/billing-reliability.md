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
- Immediate refund (subscription only): `POST /purchases/{id}/cancel-with-refund` within cooling window (monthly 7d / yearly 14d)
- Plain cancel: `POST /purchases/{id}/cancel` for trial / pending / one-time — **no money refund** for `ONE_TIME` / `BANK_INSTALLMENT`
- Do not use `POST /billing/subscriptions/{id}/cancel` (rejected)

Refund saga: `NONE` → `PENDING` (`refund_pending_at`) → gateway refund → local cancel (`COMPLETED`). Concurrent `/cancel-with-refund` while `PENDING` is rejected.

If gateway refund succeeds but local complete crashes, `RefundReconcileScheduler` completes when payment `remaining==0`. Stuck `PENDING` past `billing.refund.pending-stuck-minutes` with remaining balance rolls back to `NONE`.

If remote subscription cancel fails after refund, status `NEEDS_RECONCILE` and scheduler retries.

MQ `payment.refunded` / `payment.chargeback`: revoke installment; when purchase ends `EXPIRED`/`CANCELLED`, set refund metadata, deactivate menus, best-effort remote subscription cancel.

## Ownership

payment-service `GET /payments/{conversationId}` and `POST /payments/refund` require `X-Account-Id` and assert `source_metadata.userId`.
