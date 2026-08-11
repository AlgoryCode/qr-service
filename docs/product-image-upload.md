# Ürün Görseli Yükleme (SeaweedFS)

## Akış

1. Kullanıcı ürün formunda görsel seçer (`ProductImageField`).
2. Browser `POST /api/menu/{menuId}/products/images` (same-origin, cookie) çağırır.
3. Next.js BFF cookie'deki access token ile qr-service'e multipart iletir.
4. qr-service görseli SeaweedFS Filer'a yükler ve `{ imageUrl, objectKey }` döner.
5. Ürün create/update isteğinde `imageUrl` alanına dönen URL yazılır.
6. Public menü ziyaretçileri görseli `imageUrl` üzerinden doğrudan Filer public URL'sinden yükler (`<img>`).

## qr-service API

| Method | Path | Auth | Body |
|--------|------|------|------|
| `POST` | `/menu/{menuId}/products/images` | JWT (menu sahibi) | `multipart/form-data`, field: `file` |
| `DELETE` | `/menu/{menuId}/products/images?objectKey=` veya `?imageUrl=` | JWT (menu sahibi) | — |

**Upload response:**

```json
{
  "imageUrl": "https://assets.qr.algorycode.com/buckets/qr-product-images/menus/12/uuid.jpg",
  "objectKey": "menus/12/uuid.jpg"
}
```

**Limitler:** JPEG, PNG, WebP; maksimum 5 MB.

## Local SeaweedFS

```bash
docker compose -f docker-compose.seaweedfs.yml up -d
```

Varsayılan erişim:

- Filer API: `http://localhost:8888`
- Public base URL: `http://localhost:8888/buckets/qr-product-images`

qr-service env (local):

```env
STORAGE_FILER_URL=http://localhost:8888
STORAGE_BUCKET=qr-product-images
STORAGE_PUBLIC_BASE_URL=http://localhost:8888/buckets/qr-product-images
```

## Production (Coolify)

Coolify SeaweedFS = S3 API (`8333`). qr-service S3 identity ile yazar; browser public S3 URL'den okur.

```env
STORAGE_S3_ENDPOINT=http://10.0.2.2:8333
STORAGE_ACCESS_KEY=<SERVICE_USER_S3 veya Admin identity access key>
STORAGE_SECRET_KEY=<SERVICE_PASSWORD_S3 veya identity secret>
STORAGE_BUCKET=qr-product-images
STORAGE_PUBLIC_BASE_URL=http://s3-o7ihrq9fv36xov8qznoykmyp.185.184.210.52.sslip.io/qr-product-images
```

1. SeaweedFS ile qr-service aynı Docker network'te olmalı (`o7ihrq9fv36xov8qznoykmyp` → S3 IP `10.0.2.2`).
2. Bucket yoksa ilk upload oluşturur.
3. Public URL Traefik üzerinden (port yok); upload endpoint internal IP `:8333`.
4. Detay: [`DEPLOYMENT.md`](../DEPLOYMENT.md).

## algoryqr-web-site entegrasyonu

Auth modeli: **httpOnly cookie → `/api` BFF → Bearer upstream**. Client'ta access token yok; `NEXT_PUBLIC_API_URL` kullanılmaz.

Canlı dosyalar (algoryqr-web-site):

| Dosya | Rol |
|-------|-----|
| `src/app/api/menu/[menuId]/products/images/route.ts` | Multipart BFF proxy |
| `src/lib/uploadProductImage.ts` | Client helper (`/api/...`, cookie) |
| `src/components/dashboard/menu/ProductImageField.tsx` | Upload UI |
| `MenuProductsPanel` / `DigitalMenuProductDetailView` | Form bağlama |

## Related

Şef avatar kataloğu: [`docs/chef-avatar.md`](chef-avatar.md)

## Menü logosu

| Method | Path | Auth |
|--------|------|------|
| `POST` | `/menu/{menuId}/logo` | JWT | multipart `file` → menü profili |
| `DELETE` | `/menu/{menuId}/logo` | JWT | logoyu temizler |

Object key: `menus/{menuId}/logo/{uuid}.{ext}`


### Ürün formu

1. Görsel seçildiğinde `uploadProductImage(menuId, file)` → same-origin BFF.
2. Dönen `imageUrl`'yi ürün state'ine yazın.
3. `POST /api/menu/{menuId}/products` veya `PUT /api/menu/products/{productId}` ile kaydedin.
4. Görsel kaldırılırsa `deleteProductImage` + form'da `imageUrl` temizlenir.

Public menü şablonları native `<img src={imageUrl}>` kullandığı için `next/image` / `remotePatterns` zorunlu değildir.

### Hata mesajları

| Durum | Mesaj |
|-------|--------|
| 400 + boyut | Görsel boyutu en fazla 5 MB olabilir |
| 400 + format | Desteklenmeyen görsel formatı |
| 401 | Oturum süresi dolmuş |
| 403 | Bu menüye erişim yetkiniz yok |
