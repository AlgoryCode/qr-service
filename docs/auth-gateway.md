# Auth Gateway

Kayit ve giris akisinin kapisi. Ayri bir API gateway servisi yoktur; kapi `qr-service` icinde Spring Security zincirine takili servlet filter'lardir (`feature-gateway.md` ile ayni kalip).

Kural: **BASIC saglayicili hesap e-postasini onaylamadan hicbir korumali uca erisemez.**

## Zincir

```
AuthRateLimitGatewayFilter -> JwtAuthenticationFilter -> ProductAccessGatewayFilter
                                                     -> EmailVerificationGatewayFilter
```

| Katman | Sorumluluk | Yer |
|--------|------------|-----|
| 1. IP throttle | Kaba kuvvet / spam ucu kapatma, 429 | `AuthRateLimitGatewayFilter` |
| 2. Kimlik | JWT + oturum aktif mi | `JwtAuthenticationFilter` |
| 3. E-posta kapisi | Onaysiz BASIC hesabi 403 ile durdur | `EmailVerificationGatewayFilter` |
| 4. Kod denemesi | Yanlis kod sayaci + kilit | `EmailVerificationAttemptGuard` |
| 5. Sifre denemesi | Hesap bazli hatali giris kilidi | `LoginAttemptGuard` |

### Katman 1 — `AuthRateLimitGatewayFilter`

- Yalnizca POST ve kural tablosunda olan uclarda calisir; en uzun eslesen kural kazanir.
- Sayac anahtari istemci IP'si (`RequestUtils.resolveClientIp`, `X-Forwarded-For` ilk deger).
- Caffeine `expireAfterWrite` = sabit pencere; pencere ilk istekte baslar.
- Asildiginda `429` + `Retry-After` + `{"code":"TOO_MANY_REQUESTS"}`.

| Uc | Limit |
|----|-------|
| `/auth/login`, `/customer/auth/login`, `/waiter/auth/login`, `/admin/auth/sessions` | 10 / 5 dk |
| `/auth/register`, `/customer/auth/register` | 5 / 1 saat |
| `/auth/email-verification/resend` | 5 / 1 saat |
| `/auth/email-verification/verify` | 10 / 15 dk |

`/auth/refresh` ve `/admin/auth/sessions/refresh` limitlenmez (normal trafikte sik cagrilir).

Kapatma: `app.auth-gateway.rate-limit-enabled=false` (`AUTH_GATEWAY_RATE_LIMIT_ENABLED`). Sayaclar surec belleginde; instance basina ayridir.

IP guveni: `X-Forwarded-For`'un ilk degeri kullanilir (oturum kayitlariyla ayni kaynak). Zincir bir ters vekil tarafindan yazilmiyorsa bu deger istemci tarafindan sahtelenebilir; bu yuzden hesap bazli korumalar (Katman 4 ve 5) IP'den bagimsiz calisir.

### Katman 3 — `EmailVerificationGatewayFilter`

- Allowlist prefiksleri (kapi disi): `/auth/`, `/account/email-verification`, `/customer/`, `/waiter/`, `/admin/`, `/google-auth/`, `/oauth2/`, `/actuator/`, `/healthcheck`, `/error`.
- Kimliksiz istek veya customer/waiter principal → kapi karismaz, normal authz calisir.
- Owner principal icin `EmailVerificationGate.isVerificationPending(userId)` → true ise `403` + `{"code":"EMAIL_NOT_VERIFIED"}`.
- `EmailVerificationGate` yalnizca **dogrulanmis** kullanicilari Caffeine'de 10 dk tutar; onaysiz kullanici her istekte DB'den okunur, boylece baska bir instance uzerinden yapilan onay aninda gecerli olur.
- Login zaten `AuthService.requireEmailVerified` ile engellenir. Kapi, login oncesinde uretilmis token/refresh oturumlari ve mobil istemciler icin ikinci savunma hattidir.

### Katman 4 — Kod denemesi

- Yanlis kod: `EmailVerificationAttemptGuard.registerFailure` sayaci artirir. Ana islem `BadRequestException` ile geri alindigi icin sayac `REQUIRES_NEW` islemde yazilir.
- `app.email-verification.max-attempts` (5) asilinca kod hash'i silinir ve `email_verification_locked_until` = simdi + `lock-minutes` (15).
- Kilit acikken ne dogrulama ne yeni kod istegi kabul edilir.
- Yeni kod uretiminde sayac sifirlanir; ust sinir Katman 1'deki IP limitidir.

### Katman 5 — `LoginAttemptGuard`

- Anahtar normalize edilmis e-posta; sayac Caffeine'de `app.auth-gateway.login-failure-window-minutes` (15) boyunca yasar.
- `app.auth-gateway.login-max-failures` (10) hatali denemeden sonra `TooManyRequestsException` → `429`.
- Basarili kimlik dogrulamada sayac silinir. Sayac `AuthenticationException` yakalanarak artirilir; is kurallarindan dusen hatalar (Google hesabi, garson hesabi) sayaci etkilemez.
- Bellek ici oldugu icin instance basinadir; yatay olcekte limit instance sayisiyla carpilir.

## Public / korumali uc listesi (`SecurityConfig`)

`permitAll` olan `/auth` uclari yalnizca sunlardir (POST): `register`, `login`, `refresh`, `logout`, `email-verification/resend`, `email-verification/verify`. `/auth/sessions`, `/auth/sessions/{id}` ve `/auth/access-profile` kimlik ister.

## Frontend sozlesmesi (`algoryqr-web-site`)

- Kayit → `persistPendingVerificationEmail` + `/login?verify=1`; login ekrani kod formunu acar.
- Login `403 EMAIL_NOT_VERIFIED` → kod formu (public uc oldugu icin interceptor karismaz).
- Diger uclarda `403 EMAIL_NOT_VERIFIED` → cerez temizligi + `/login?verify=1` (`site-same-origin-axios`).

## Bilerek yapilmayanlar

- Ayri Spring Cloud Gateway / nginx hop
- Dagitik (Redis) rate limit sayaci
- Rate limit anahtarinda e-posta kullanimi (govde okumasi gerektirir; hesap bazli koruma Katman 4'tedir)
- Next.js BFF'te e-posta onay kontrolu (karar tek yerde, `qr-service`)

## Ilgili siniflar

- `security/AuthRateLimitGatewayFilter.java`, `security/LoginAttemptGuard.java`
- `security/EmailVerificationGatewayFilter.java`, `security/EmailVerificationGate.java`
- `service/EmailVerificationService.java`, `service/EmailVerificationAttemptGuard.java`
- `service/AuthService.java` (login kapisi), `exception/AuthErrorCodes.java`
- `db/migration/V104__email_verification_attempt_limit.sql`
