# REST — merchant / staff / customer

Paket kökleri (`com.ael.algoryqrservice` altında, `qrservice` **dışında**): `merchantservice`, `staffservice`, `customerservice`, `adminservice`.

Public API prefix’leri domain adıyla başlar. Auth oturumu `/api/v1/auth/*` (authservice).

| Prefix | Örnek |
|--------|--------|
| `/merchant` | `GET /merchant/menus/{menuId}/customers` |
| `/internal/merchant` | `GET /internal/merchant/{merchantId}`, `POST .../soft-delete` |
| `/staff` | `/staff/panel`, `/staff/orders`, `/staff/kitchen`, `/staff/bills`, … |
| `/internal/staff` | `PUT /internal/staff/{staffId}` |
| `/customer` | `/customer/account/profile`, üyelik ve ödüller |
| `/dashboard/auth` | `POST /dashboard/auth/login`, `/me`, refresh, logout (`adminservice`) |
| `/admin` | `/admin/users`, `/admin/purchases`, `/admin/coupons`, tema/taxonomy, … (`ROLE_ADMIN`) |

**Geçici uyumluluk (dashboard-site prod geçişi):** `/waiter-panel`, `/waiter/*` ve `/internal/merchants/*` aynı handler’lara alias olarak bağlıdır. Kaldırma adımları: [`dashboard-site-api-migration.md`](dashboard-site-api-migration.md).

Domain-scoped hata gövdesi: `{ "message", "errors?", "code?" }` — `*ApiExceptionHandler` + `DomainRestExceptionAdvice`.
