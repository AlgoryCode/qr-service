# Coolify Deployment

## qr-service (algoryqr-service)

| Variable | Example | Description |
|----------|---------|-------------|
| `APP_SERVICE_NAME` | `qr-service` | RabbitMQ routing key prefix |
| `GOOGLE_CLIENT_ID` | `....apps.googleusercontent.com` | Google OAuth client ID |
| `GOOGLE_CLIENT_SECRET` | `GOCSPX-...` | Google OAuth client secret |
| `GOOGLE_CALLBACK_URL` | `https://prod.qrapi.algorycode.com/google-auth/callback` | API OAuth callback (Google authorized redirect URI). Must be `/google-auth/callback`, never `/auth/google/callback` or the Next.js URL. |
| `GOOGLE_FRONTEND_CALLBACK_URL` | `https://qr.algorycode.com/api/auth/google/callback` | Next.js handoff callback after Google login. Must include `/api/auth/google/callback`. |
| `PAYMENT_SERVICE_URL` | `http://payment-service:8080` | Internal payment-service URL |
| `RABBITMQ_HOST` | `rabbitmq` | RabbitMQ hostname |
| `RABBITMQ_PORT` | `5672` | RabbitMQ port |
| `RABBITMQ_USERNAME` | `guest` | RabbitMQ user |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ password |
| `PAYMENT_EVENTS_EXCHANGE` | `payment.events` | Topic exchange name |
| `PAYMENT_EVENTS_QUEUE` | `qr-service.payment.events` | Payment event consumer queue |
| `PAYMENT_EVENTS_ROUTING_KEY` | `qr-service.payment.events` | Binding routing key |
| `PAYMENT_PENDING_TIMEOUT_MINUTES` | `30` | PENDING purchase timeout |
| `PUSH_NOTIFICATION_EXCHANGE` | `push-notification-exchange` | Password-change email notifications |
| `PUSH_NOTIFICATION_ROUTING_KEY` | `push-notification.request` | Inbound routing key |
| `PASSWORD_CHANGE_CODE_VALIDITY_MINUTES` | `5` | OTP validity |
| `EMAIL_CHANGE_CODE_VALIDITY_MINUTES` | `5` | Email change OTP validity |

## payment-service

| Variable | Example | Description |
|----------|---------|-------------|
| `PAYMENT_EVENTS_EXCHANGE` | `payment.events` | Topic exchange name |
| `PAYMENT_CALLBACK_PUBLIC_URL` | `https://pay-api.example.com/payments/iyzico/callback` | iyzico callback URL |
| `PAYMENT_FRONTEND_REDIRECT_URL` | `https://app.example.com/payment/result` | User redirect after 3DS |
| `RABBITMQ_HOST` | `rabbitmq` | RabbitMQ hostname |
| `IYZICO_API_KEY` | `sandbox-...` | iyzico API key |
| `IYZICO_SECRET_KEY` | `sandbox-...` | iyzico secret |

## Networking

- qr-service calls payment-service via internal DNS: `http://payment-service:8080`
- iyzico calls payment-service via public URL only
- payment-service publishes to `payment.events` with routing key `{serviceName}.payment.events`
- qr-service binds `qr-service.payment.events` and branches on `eventType`
- Scale payment-service replicas: Docker internal LB handles HTTP; shared DB required
- Scale qr-service replicas: competing consumers on same RabbitMQ queues

## Package and product catalog

Packages and products are managed dynamically via admin APIs. Codes are strings (not Java enums).

### System rules

| Rule | Detail |
|------|--------|
| `FREE_PACKAGE` | System-managed; created on startup if missing; admin create/update blocked |
| `purchasable=false` | Package cannot be bought (FREE) |
| `trialEligible=true` | Trial flow uses highest-priority active trial-eligible package |
| `priority` | Higher value wins when selecting the user's active package |
| Product `scopeCode` | Used in JWT claims and `@RequiresProductScope` |
| Product `consumable` | When true, usage decrements entitlement quantity |

### Admin endpoints

- `POST/GET/PUT /admin/products` — product CRUD (`code`, `scopeCode`, `consumable`, `active`)
- `POST/GET/PUT /admin/packages` — package CRUD with nested items (`productId`, `quantity`, `unlimited`)
- `PATCH /admin/packages/{id}/status` — toggle `active` only

### Seed (Flyway V5)

- Products: `QR_CREATE`, `QR_MENU`, `QR_AGENT`, `QR_ANALYTICS`
- `PRO_PACKAGE`: 30 QR create, 1 menu, 1 agent; `priority=100`; `trialEligible=true`
- Startup only ensures `FREE_PACKAGE` (5 QR create); it does not overwrite PRO

### Purchase fulfillment

Successful payment grants entitlements from the purchased package items (quantity/unlimited). Feature endpoints consume or check scopes by product/scope code.

## Dashboard users (qr-dashboard-ui)

App müşteri auth ile dashboard auth tamamen ayrıdır.

| | App (müşteri) | Dashboard (yönetim) |
|---|---|---|
| Tablo | `tbl_user` | `tbl_dashboard_user` |
| Session | `tbl_user_session` | `tbl_dashboard_user_session` |
| Login | `POST /auth/login` | `POST /dashboard/auth/login` |
| Refresh | `POST /auth/refresh` | `POST /dashboard/auth/refresh` |
| Logout | `POST /auth/logout` | `POST /dashboard/auth/logout` |
| Profil | `GET /account/myprofile` | `GET /dashboard/auth/me` |
| Admin API | — | `/admin/**` (`ROLE_ADMIN`) |
| JWT | `principalType=APP` | `principalType=DASHBOARD` |

Local seed:

| Field | Value |
|------|--------|
| Email | `admin@example.com` |
| Password | `Admin123!` |

```sql
UPDATE tbl_dashboard_user
SET password = '$2b$12$gXV92dJoN4DSokp7kxXWee9QoUjUaFx7gKEY2SZen1LnGM5nnILoa'
WHERE email = 'admin@example.com';
```

qr-dashboard-ui yalnızca `/dashboard/auth/*` kullanır. `/auth/login` ve `/account/myprofile` müşteri uygulamasınadır.

