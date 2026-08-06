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
| `SMART_REPORT_QUEUE` | `smart_report.generate` | Outbound queue for AI report generation |
| `SMART_REPORT_EVENTS_EXCHANGE` | `smart_report.events` | Topic exchange for AI status events |
| `SMART_REPORT_EVENTS_QUEUE` | `qr-service.smart_report.events` | Inbound smart report status queue |
| `SMART_REPORT_EVENTS_ROUTING_KEY` | `smart_report.status` | Binding routing key for status events |
| `PAYMENT_PENDING_TIMEOUT_MINUTES` | `30` | PENDING purchase timeout |
| `PUSH_NOTIFICATION_EXCHANGE` | `push-notification-exchange` | Password-change email notifications |
| `PUSH_NOTIFICATION_ROUTING_KEY` | `push-notification.request` | Inbound routing key |
| `PASSWORD_CHANGE_CODE_VALIDITY_MINUTES` | `5` | OTP validity |
| `EMAIL_CHANGE_CODE_VALIDITY_MINUTES` | `5` | Email change OTP validity |
| `STORAGE_S3_ENDPOINT` | `http://10.0.2.2:8333` | Internal S3 API (container network IP:8333). Access key doluysa Filer yerine S3 kullanılır. |
| `STORAGE_ACCESS_KEY` | SeaweedFS S3 identity access key | `SERVICE_USER_S3` veya Admin'de eklenen identity'nin access key'i |
| `STORAGE_SECRET_KEY` | SeaweedFS S3 identity secret | `SERVICE_PASSWORD_S3` veya identity secret |
| `STORAGE_FILER_URL` | `http://localhost:8888` (local only) | Production'da gerekmez (S3 mode). |
| `STORAGE_BUCKET` | `qr-product-images` | Bucket adı |
| `STORAGE_PUBLIC_BASE_URL` | `http://s3-<id>.<ip>.sslip.io/qr-product-images` | Public image URL prefix (Traefik 80/443; `:8333` yok) |
| `STORAGE_MAX_FILE_SIZE` | `5MB` | Multipart upload limit |
| `STORAGE_MAX_FILE_SIZE_BYTES` | `5242880` | Upload validation limit in bytes |

## SeaweedFS (product images)

Coolify one-click SeaweedFS = `weed server -s3` → production'da **S3 API (8333)** kullanın. Filer (8888) Traefik arkasında PUT ile genelde kırılır.

| Setting | Value |
|---------|--------|
| Upload | qr-service → S3 `PutObject` (`STORAGE_S3_ENDPOINT`) |
| Read | Public S3 path-style URL (anonymous `Read` Coolify `base-config.json`'da açık) |
| Auth | S3 identity (`Admin`/`Write`/`Read` actions) |
| Bucket | `STORAGE_BUCKET` (yoksa ilk upload'da oluşturulur) |

**URL kuralları:**

| Env | Doğru değer | Yanlış |
|-----|-------------|--------|
| `STORAGE_S3_ENDPOINT` | Internal: `http://10.0.2.2:8333` (S3'ün dinlediği IP) | `sslip.io:8333`, hostname DNS (`10.0.1.12`), Filer `:8888` |
| `STORAGE_PUBLIC_BASE_URL` | `http://s3-o7ihrq9fv36xov8qznoykmyp.185.184.210.52.sslip.io/qr-product-images` | `:8333` eklemek, `/buckets/` path'i (Filer stili) |

**Coolify networking (zorunlu):** S3 çoğu zaman yalnızca stack network IP'sinde dinler (ör. `10.0.2.2:8333`). Hostname başka IP'ye düşerse (`10.0.1.12`) → refused. Timeout = qr-service o network'te değil.

Host'ta (SSH):

```bash
docker inspect seaweedfs-master-o7ihrq9fv36xov8qznoykmyp --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}} {{$v.IPAddress}}{{"\n"}}{{end}}'
docker ps --format '{{.Names}}' | grep -iE 'qr|algory'
docker network connect o7ihrq9fv36xov8qznoykmyp <qr-service-container-adı>
```

Coolify UI: qr-service + SeaweedFS → **Connect to Predefined Network** ON → redeploy. qr-service'ten `wget -S -O- --timeout=5 http://10.0.2.2:8333/` → **403** beklenir.

**Coolify qr-service env (S3 + identity):**

```env
STORAGE_S3_ENDPOINT=http://10.0.2.2:8333
STORAGE_ACCESS_KEY=<SeaweedFS S3 access key>
STORAGE_SECRET_KEY=<SeaweedFS S3 secret>
STORAGE_BUCKET=qr-product-images
STORAGE_PUBLIC_BASE_URL=http://s3-o7ihrq9fv36xov8qznoykmyp.185.184.210.52.sslip.io/qr-product-images
```

- Access key / secret: Coolify → SeaweedFS → `SERVICE_USER_S3` / `SERVICE_PASSWORD_S3`, **veya** Admin UI'da eklediğin identity'nin credentials'ı.
- `STORAGE_S3_ENDPOINT` değerine `STORAGE_S3_ENDPOINT=` tekrar yazma.
- S3 mode açıkken `STORAGE_FILER_URL` gerekmez (boş bırak / sil).
- Public URL'de `:8333` yok (Traefik 80/443).

Local (Filer, S3 key olmadan):

```bash
docker compose -f docker-compose.seaweedfs.yml up -d
```

```env
STORAGE_FILER_URL=http://localhost:8888
STORAGE_BUCKET=qr-product-images
STORAGE_PUBLIC_BASE_URL=http://localhost:8888/buckets/qr-product-images
```

See [`docs/product-image-upload.md`](docs/product-image-upload.md) for API and frontend integration.

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
- Wire contract (JSON body + required headers): [`docs/payment-events-contract.md`](docs/payment-events-contract.md)
- Failed payment events → `qr-service.payment.events.dlq`; after consumer fix, republish DLQ or reconcile purchase (e.g. purchase `46`)
- qr-service publishes smart report jobs to `smart_report.generate`; AI consumes
- AI publishes status to `smart_report.events` with routing key `smart_report.status`; qr-service consumes `qr-service.smart_report.events`
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

- Products: `QR_CREATE`, `SMART_ASSISTANT`, `SMART_SUMMARY`, `SMART_REPORTING`
- `PRO_PACKAGE`: 30 QR create + Smart Assistant; `priority=100`; `trialEligible=true`
- `ULTIMATE_PACKAGE`: 100 QR create + Smart Assistant + Smart Summary + Smart Reporting; `priority=200`; `trialEligible=true`
- Startup only ensures `FREE_PACKAGE` (5 QR create); it does not overwrite PRO/Ultimate

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

