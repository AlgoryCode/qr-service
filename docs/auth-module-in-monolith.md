# Auth modülü (eski algoryqr-authservice)

Kimlik ve oturum akışı `com.ael.algoryqrservice.qrservice.authservice` paketinde qr-service ile aynı JVM’de çalışır.

## API (değişmedi)

- `POST /api/v1/auth/login`, `/register`, `/tokens/refresh`, Google uçları vb.
- İç uçlar: `/internal/credentials/**`, `/internal/merchants/{id}`, `/internal/staff/**`

## Veritabanı

- Tek Postgres: `spring.datasource.*` (`DB_URL`, varsayılan `algoryqrdb`).
- Auth şeması ana Flyway ile gelir: `db/migration` içinde `V143__auth_schema.sql` … `V149__auth_demo_account_password.sql`.
- Auth JPA ayrı persistence unit kullanır (`authEntityManagerFactory`); QR `tbl_merchant_staff` ile auth entity eşlemesi aynı tabloda çakışmasın diye birimler ayrı tutulur.

Prod’da veri hâlâ `algoryqr-authdb` içindeyse, tek seferlik taşıma gerekir (eski authservice `scripts/MIGRATION.md`).

## Dağıtım

Prod/stage’de `AUTH_BASE_URL` qr-api host’una işaret edebilir (`https://prod.qrapi.algorycode.com`). Ayrı `prod.auth.algorycode.com` servisi kapatılabilir.

Coolify env: `DB_*`, `JWT_SECRET`, `GOOGLE_CLIENT_*` qr-service container’ında tanımlı olmalı. `AUTH_DB_*` artık kullanılmıyor.
