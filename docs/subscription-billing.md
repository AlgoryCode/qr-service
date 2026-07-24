# Aylık / Yıllık abonelik

Ücretli checkout yalnızca `billingPeriod: MONTHLY | YEARLY` kabul eder (`paymentStyle=SUBSCRIPTION`).

- Aylık tahsil: `price - monthlyDiscount`
- Yıllık tahsil: `yearlyPrice - yearlyDiscount`
- Banka taksiti kaldırıldı
- Open-ended yenileme (`subscriptionCycleCount` null); job 10:00 / 15:00
- Plan change: IMMEDIATE upgrade proration; downgrade yalnızca NEXT_PERIOD
- Trial: `tbl_user.trial_used`
