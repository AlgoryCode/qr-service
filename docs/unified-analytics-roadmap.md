# Unified Analytics — Faz B–G Yol Haritası

## Faz A (tamamlandı)
Summary + Full endpoint, önceki dönem, masa/devir, co-purchase, coverage flags.

## Faz B (tamamlandı)
- Status: PREPARING / READY / SERVED
- `createdByWaiterId`, `cancelledByWaiterId`, `cancelledAt`, `cancelReason`
- `orderSource` (QR|WAITER), `OrderAuditLog`, `BillAdjustment`
- Waiter: `/waiter/orders/{id}/preparing|ready|served`, cancel reason body
- `/waiter/bill-adjustments`

## Faz C (tamamlandı)
- Event: QR_SCAN, ADD_TO_CART, REMOVE_FROM_CART, CHECKOUT_START, ORDER_SUBMITTED
- `MenuOrder.analyticsSessionId` on submit/cart
- Funnel counts extended; FE `trackMenuAnalyticsEvent` + submit/cart wiring

## Faz D (tamamlandı)
- `RestaurantTable.capacity`, `TableBill.coverCount`
- Tables analytics: occupancy, revenuePerCover

## Faz E (tamamlandı)
- `WorkShift` + waiterIds; `/waiter/shifts/open|current|{id}/close`
- Full.shifts Z-özet

## Faz F (tamamlandı)
- Full.kitchen stage süreleri, gecikme eşiği 20 dk, saatlik yük

## Faz G (tamamlandı)
- Customers: yeni/tekrar anonim (ipHash), üye sipariş payı (PII yok)

## Migration
`V96__restaurant_analytics_phases_b_g.sql` — Flyway şu an `enabled: false`; prod’da migration çalıştırın veya DDL’i manuel uygulayın (`ddl-auto=validate`).
