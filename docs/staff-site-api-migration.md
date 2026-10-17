# algoryqr-staff-site — canonical `/staff/*`

Personel mobil uygulama (`algoryqr-staff-site`, prod örn. `https://staff.algorycode.com`) qr-service ile **yalnızca** `/staff/*` ve `/api/v1/auth/staff/*` üzerinden konuşur.

## BFF (Next.js)

| Browser | Upstream (qr-service) |
|---------|------------------------|
| `POST /api/staff/auth/login` | `POST /api/v1/auth/staff/login` |
| `GET /api/staff/auth/me` | `GET /api/v1/auth/access` |
| `GET/POST /api/staff/orders/**` | `/staff/orders/**` |
| `/api/staff/kitchen/**` | `/staff/kitchen/**` |
| `/api/staff/courier/**` | `/staff/courier/**` |
| `/api/staff/bills/**` | `/staff/bills/**` |
| `/api/staff/commissions/**` | `/staff/commissions/**` |
| `/api/staff/campaigns/**` | `/staff/campaigns/**` |
| `/api/staff/ops/**` (ileride) | `/staff/ops/**` |

Kaldırıldı: `/api/waiter/*` route’ları ve client’ta `/api/waiter-panel/*` (merchant panel dashboard-site’ta).

## Deploy sırası

1. **qr-service** ( `/staff/*` + geçici `/waiter/*` alias — eski bundle için).
2. **algoryqr-staff-site** (canonical `/api/staff/*` BFF).
3. Smoke aşağı; qr-service log’da staff trafiği için `[compat] deprecated path` **olmamalı**.

## Prod smoke (rol başına)

Staff JWT (BFF cookie) sonrası:

| Rol | Kontrol |
|-----|---------|
| Tümü | `POST /api/staff/auth/login` → 200; `GET /api/staff/auth/me` → rol + branch |
| WAITER | `GET /api/staff/orders/pending`, `GET /api/staff/orders/tables`, `GET /api/staff/bills/tables/{tableId}/open` |
| KITCHEN | `GET /api/staff/kitchen/orders` |
| COURIER | `GET /api/staff/courier/orders` |

## Env

- `API_BASE_URL` / `API_UPSTREAM` → qr-service (ör. `https://prod.qrapi.algorycode.com`).
- Tarayıcı doğrudan API host’a giderse qr-service `CORS_ALLOWED_ORIGINS` içinde staff origin olmalı — [`DEPLOYMENT.md`](../DEPLOYMENT.md).

## Alias kaldırma

Dashboard-site + staff-site prod doğrulandıktan sonra: [`dashboard-site-api-migration.md`](dashboard-site-api-migration.md) “Remove aliases” adımları.
