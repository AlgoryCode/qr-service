# Coolify Deployment

## qr-service (algoryqr-service)

| Variable | Example | Description |
|----------|---------|-------------|
| `APP_SERVICE_NAME` | `qr-service` | RabbitMQ routing key prefix |
| `PAYMENT_SERVICE_URL` | `http://payment-service:8080` | Internal payment-service URL |
| `RABBITMQ_HOST` | `rabbitmq` | RabbitMQ hostname |
| `RABBITMQ_PORT` | `5672` | RabbitMQ port |
| `RABBITMQ_USERNAME` | `guest` | RabbitMQ user |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ password |
| `PAYMENT_EVENTS_EXCHANGE` | `payment.events` | Topic exchange name |
| `PAYMENT_SUCCESS_QUEUE` | `qr-service.payment.success` | Success consumer queue |
| `PAYMENT_FAILED_QUEUE` | `qr-service.payment.failed` | Failed consumer queue |
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
- payment-service publishes to `payment.events` with routing key `{serviceName}.payment.success|failed`
- qr-service binds queues to `qr-service.payment.success` and `qr-service.payment.failed`
- Scale payment-service replicas: Docker internal LB handles HTTP; shared DB required
- Scale qr-service replicas: competing consumers on same RabbitMQ queues
