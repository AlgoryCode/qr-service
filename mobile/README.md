# AlgoryQR Mobil

Kotlin Multiplatform + Compose Multiplatform (Android + iOS) ile yazılmış iki uygulama. İkisi de bu repodaki
`qr-service` backend'ine bağlanır. Tasarım dili AlgoryCar'dan alındı (ince çizgili Phosphor ikonları, ikon
kareleri, çizgili kartlar, aura arka plan), marka rengi kırmızı.

| Modül | Ne yapar |
|---|---|
| `shared/` | Tema ve bileşenler, Ktor istemcisi (token yenileme dahil), oturum saklama (Android Keystore / iOS Keychain), ortak modeller |
| `businessApp/` | **AlgoryQR İşletme**: işletme sahibi için özet/ciro, siparişler, masalar, personel yönetimi, rezervasyonlar |
| `staffApp/` | **AlgoryQR Personel**: role göre açılır. Garson: masalar, sipariş onay/servis, yeni sipariş, hesap kapatma, prim. Mutfak: hazırlık kuyruğu. Kurye: teslimatlar |
| `iosBusinessApp/`, `iosStaffApp/` | iOS kabuk projeleri (xcodegen ile `project.yml`'den üretilir) |

## Giriş

- **İşletme**: `POST /api/v1/auth/login` (e-posta veya kullanıcı adı). Yalnızca `MERCHANT` rolü kabul edilir.
- **Personel**: `POST /api/v1/auth/staff/login` (İşletme No + kullanıcı adı + şifre). `WAITER`, `KITCHEN`,
  `COURIER` rollerine göre farklı sekmeler açılır. İşletme No, işletme uygulamasında Hesap ekranında yazar.
- Rol ve kimlikler `GET /api/v1/auth/access` ile alınır; süresi dolan erişim token'ı `tokens/refresh` ile bir
  kez yenilenip istek tekrarlanır.

Canlı ekranlar (siparişler, mutfak, masalar, teslimatlar) backend'de personel için push kanalı olmadığından
10–20 saniyede bir yenilenir.

## Çalıştırma

JDK 21 gerekir. Debug Android derlemesi emülatördeki host makineye (`10.0.2.2:8080`) bağlanır; adresler
`gradle.properties` içindeki `apiBaseUrl` / `releaseApiBaseUrl` ile değişir.

```bash
./gradlew :businessApp:installDebug
```

```bash
./gradlew :staffApp:installDebug
```

iOS: `iosBusinessApp/` veya `iosStaffApp/` içinde `xcodegen generate` çalıştırıp `.xcodeproj`'u Xcode ile açın.
iOS derlemeleri şimdilik stage API'ye (`https://stage.qrapi.algorycode.com`) bağlanır.
