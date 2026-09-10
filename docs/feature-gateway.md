# Feature Product Gateway

`/features/{PRODUCT_CODE}/...` istekleri icin paket/urun kapisi. Ayrı bir API gateway servisi yoktur; kapı `qr-service` icinde servlet filter + scope aspect + entitlement/service kotasıdır.

Frontend BFF (`algoryqr-web-site` `/api/features/...`) yalnizca proxy eder; paket kontrolu Next.js tarafinda yapilmaz.

## Uc katman

| Katman | Sorumluluk | Yer |
|--------|------------|-----|
| 1. Membership gate | Oturum `ALLOW` + urun aktif pakette mi | `ProductAccessGatewayFilter` |
| 2. Scope gate | `*_OWNER` scope | `@RequiresProductScope` + `ProductScopeAspect` |
| 3. Quota / consume | Tüketilebilir: `EntitlementService.consume`; scope-only + period: or. smart-report kotasi | Feature domain service |

### Katman 1 — `ProductAccessGatewayFilter`

- Yalnizca URI icinde `/features/` olan isteklerde calisir (`FEATURES_PREFIX`).
- Product code = `/features/` sonrasi ilk path segment (or. `SMART_REPORTING`).
- JWT owner gerekir; customer / waiter / anonim → 403.
- `SessionAccessService.resolve(userId)` → `ALLOW` degilse 403 + access-session govdesi.
- `PackageProductCatalog.containsProduct(packageCode, productCode)` false ise 403 (`PRODUCT_NOT_IN_PACKAGE`).
- **Consume yapmaz.** Basarisiz islemde hak dusumunu geri almak filter katmaninda guvenilmez; membership-only kalir.

Security zinciri: `JwtAuthenticationFilter` → `ProductAccessGatewayFilter` (`SecurityConfig`). Servlet auto-registration kapalı; filter yalnizca Spring Security chain icinde.

### Katman 2 — `@RequiresProductScope`

Controller metodunda scope zorunlulugu. JWT claim veya `EntitlementService.requireScope` ile dogrulanir.

### Katman 3 — Consume / period kota

| Urun tipi | Davranis | Ornek |
|-----------|----------|--------|
| `consumable=true` | Service girisinde `EntitlementService.consume(userId, productCode, amount)`; yan etki basarisizsa `release` | `QR_CREATE` via `QrService` |
| `consumable=false` + period kota | Scope + event/period assert; fulfillment qty dusmez | `SMART_REPORTING` via `SmartReportService.assertQuotaAvailable` |
| `consumable=false` scope-only | Yalnizca scope | `WAITER_PANEL`, `SMART_ASSISTANT` |

## Mevcut feature prefixleri

| Product code | Controller | Ornek create path |
|--------------|------------|-------------------|
| `QR_MENU` | `FeatureQrController` | `POST /features/QR_MENU/qrs` |
| `QR_BRANCH` | `FeatureBranchController` | `POST /features/QR_BRANCH/branches` |
| `SMART_REPORTING` | `FeatureSmartReportingController` | `POST /features/SMART_REPORTING/branches/{id}/reports`, `POST /features/SMART_REPORTING/menus/{id}/reports` |
| `AI_MENU_IMPORT` | `FeatureAiMenuImportController` | `POST /features/AI_MENU_IMPORT/menus/{menuId}/ai-import/jobs` |

## Yeni feature urunu ekleme sozlesmesi

1. Katalogda urun tanimla (`PackageCatalogService` / `CatalogProducts`): `consumable` true/false, `scopeCode`, isteğe bagli `featureCode`.
2. Ilgili paket(ler)in `PlanPackageItem` listesine ekle.
3. `@RequestMapping("/features/{PRODUCT_CODE}")` controller yaz; create islemlerini bu prefix altina koy.
4. Metotta `@RequiresProductScope(CatalogScopes.*_OWNER)` kullan.
5. Domain service girisinde:
   - consumable → `entitlementService.consume(...)` (hata/rollback'ta `release`)
   - period kota → mevcut smart-report kalibi gibi assert + usage log
   - scope-only → ek kota yok

## Akilli Rapor (`SMART_REPORTING`)

- Membership: filter (paket + urun pakette).
- Scope: `@RequiresProductScope(SMART_REPORTING_OWNER)`.
- Hak: `SmartReportQuotaProperties` (WEEK/1 ucretsiz) + `assertQuotaAvailable`; ucretsiz bitince `SMART_REPORTING_ADDON` (200 TL) consume; fulfillment ledger qty dusmez (base product `consumable=false`).
- Kullanim izi: `logFeatureUsage` / lastUsage (UI); ucretsiz kota event sayimi + opsiyonel addon credits.

## Bilerek yapilmayanlar

- Ayrı Spring Cloud Gateway / nginx hop
- Next.js BFF'te paket kontrolu
- Filter icinde otomatik `EntitlementService.consume`
- `SMART_REPORTING`'i `consumable=true` yapip ledger'a tasima (ayri is)

## Ilgili siniflar

- `security/ProductAccessGatewayFilter.java`
- `security/RequiresProductScope.java`, `security/ProductScopeAspect.java`
- `access/SessionAccessService.java`, `access/PackageProductCatalog.java`
- `service/EntitlementService.java`
- `service/SmartReportService.java` (period kota)
- `controller/Feature*Controller.java`
