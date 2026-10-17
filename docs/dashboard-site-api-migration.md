# dashboard-site — staff API path migration

Merchant panel repo: **`algoryqr-dashboard-site`** (`C:\Users\Tarik\Desktop\Services\algoryqr-dashboard-site`, prod typically `https://qr.algorycode.com`).

Client and BFF now use **`/api/staff/panel/**`** → qr-service **`/staff/panel/**`**. Until qr-service alias removal, legacy **`/waiter-panel`** backend paths still work if an old bundle is cached.

## Path mapping

| Legacy (update client) | Canonical (target) |
|------------------------|-------------------|
| `/waiter-panel/**` | `/staff/panel/**` |
| `/waiter/orders/**` | `/staff/orders/**` |
| `/waiter/bills/**` | `/staff/bills/**` |
| `/waiter/kitchen/**` | `/staff/kitchen/**` |
| `/waiter/courier/**` | `/staff/courier/**` |
| `/waiter/commissions/**` | `/staff/commissions/**` |
| `/waiter/campaigns/**` | `/staff/campaigns/**` |
| `/waiter/shifts/**`, `/waiter/bill-adjustments/**`, … | `/staff/ops/**` (same suffix) |
| `GET /internal/merchants/{id}` | `GET /internal/merchant/{id}` |

Unchanged for merchant dashboard:

- `POST /auth/login`, `/auth/refresh`, `/access/session`, `/access/context`
- `/menu/**`, `/purchases/**`, `/analytics/**`, `/store/me/**`
- Staff mobile: `POST /api/v1/auth/staff/login`

## Frontend audit (run in dashboard-site repo)

```bash
rg -n 'waiter-panel|/waiter/|internal/merchants' --glob '!node_modules'
```

Replace paths per table above. Keep `API_BASE_URL` / Next.js `/api` proxy as in [`DEPLOYMENT.md`](../DEPLOYMENT.md).

## Backend compat (temporary)

- Dual `@RequestMapping` on staff panel/order/bill/ops/kitchen/courier/commission/campaign controllers.
- `DeprecatedStaffPathCompatFilter` logs `[compat] deprecated path …` once per request.
- `EmailVerificationGatewayFilter` allowlist includes `/waiter-panel`, `/waiter/` during alias period.
- `InternalMerchantController` also mapped to `/internal/merchants`.

## Remove aliases (after dashboard-site + staff-site prod verified)

**staff-site:** [`staff-site-api-migration.md`](staff-site-api-migration.md) — mobil app canonical `/api/staff/*`; alias kaldırma öncesi compat log kontrolü.

Single follow-up PR in **qr-service**:

1. Revert dual `@RequestMapping` to `/staff/*` only (and `/internal/merchant` only).
2. Remove `/waiter-panel` and `/waiter/` from `EmailVerificationGatewayFilter.ALLOWED_PREFIXES`.
3. Delete `DeprecatedStaffPathCompatFilter` and its `SecurityConfig` registration.
4. Remove `/internal/merchants/*` from `SecurityConfig` matchers.
5. Update [`rest-domains-merchant-staff-customer.md`](rest-domains-merchant-staff-customer.md) to state aliases are gone.
6. Confirm prod logs show no `[compat] deprecated path` hits for 7+ days.

Do **not** remove aliases before **dashboard-site** and **algoryqr-staff-site** production use canonical paths (or you accept breaking old cached bundles).
