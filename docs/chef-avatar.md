# Şef avatar kataloğu (SeaweedFS)

## Özet

Menü ayarlarında seçilebilir şef avatarları `qr-product-images` bucket’ında `chief_avatars/` prefix’i altında tutulur.
Katalog `app.chef-avatars.items` ile tanımlanır; uygulama açılışında classpath kaynağı storage’da yoksa seed edilir.

## API

| Method | Path | Auth | Açıklama |
|--------|------|------|----------|
| `GET` | `/menu/chef-avatars` | Public | `{ key, label, imageUrl }[]` |
| `PATCH` | `/menu/{menuId}` | JWT (menü sahibi) | `chefName`, `chefAvatarKey` |

`MenuProfileResponse` alanları:

- `chefName` — saklanan özel isim (`null` = varsayılan)
- `chefDisplayName` — boşsa **Akıllı Şef**
- `chefAvatarKey` — katalog anahtarı (`null` = `default`)
- `chefAvatarUrl` — public SeaweedFS URL

## Object key örneği

```
chief_avatars/default.png
```

Public URL:

```
{STORAGE_PUBLIC_BASE_URL}/chief_avatars/default.png
```
