# Katalog: Urun -> Paket -> Satilabilir (taksitli)

Base: `http://localhost:8055`  
Header: `Authorization: Bearer <ADMIN_TOKEN>`

## A) Tek adimda (onerilen)

Yeni urun(ler) + paket + `active/purchasable` birlikte.

```http
POST /admin/catalog/sellable-packages
```

```json
{
  "packageName": "Dijital Menu Pro",
  "currency": "TRY",
  "validityDays": 30,
  "priority": 10,
  "trialEligible": false,
  "items": [
    {
      "productName": "QR Olusturma",
      "countable": true,
      "quantity": 50,
      "unitPrice": 5.00,
      "vatRate": 20
    },
    {
      "productName": "Menu Erisimi",
      "countable": false,
      "quantity": 1,
      "unitPrice": 49.00,
      "vatRate": 20
    }
  ]
}
```

Fiyat hesabi: satir net = `unitPrice * quantity` (unlimited ise quantity=1)  
`subtotal` = satir netleri toplami  
`vatAmount` = her satirin KDV'si toplami  
`price` = `subtotal + vatAmount` (odenen tutar)

Mevcut urun eklemek icin `productId` kullan:

```json
{
  "packageName": "Pro Plus",
  "price": 399.00,
  "validityDays": 30,
  "items": [
    { "productId": 2, "quantity": 100 },
    { "productName": "Analytics", "countable": false, "quantity": 1 }
  ]
}
```

Sonuc: paket `active=true`, `purchasable=true`.  
`allowedInstallments` / `installmentOptions` fiyata gore dolar (1,2,3,6,9,12).  
Kart BIN taksitleri satin alma ekraninda `/billing/installment-options` ile gelir.

---

## B) Adim adim surec

### 1) Urun olustur

```http
POST /admin/products
```

```json
{
  "name": "QR Olusturma",
  "countable": true,
  "unitPrice": 5.00,
  "vatRate": 20,
  "active": true
}
```

- `code` otomatik
- `scopeCode` otomatik (`*_OWNER`)
- `countable: true` = adet duser
- `unitPrice` = KDV haric birim fiyat
- `vatRate` default `20`

### 2) Paket olustur (draft)

```http
POST /admin/packages
```

```json
{
  "name": "Dijital Menu Pro",
  "validityDays": 30,
  "priority": 10,
  "active": false,
  "purchasable": false,
  "items": []
}
```

Paket `price` artik elle girilmez; urunler eklenince otomatik hesaplanir.

### 3) Pakete urun ekle

```http
POST /admin/packages/{packageId}/items
```

```json
{
  "productId": 12,
  "quantity": 50,
  "unlimited": false
}
```

Cikar:

```http
DELETE /admin/packages/{packageId}/items/{productId}
```

### 4) Aktif + satilabilir yap (publish)

```http
POST /admin/packages/{packageId}/publish
```

```json
{
  "active": true,
  "purchasable": true
}
```

Kontroller:
- en az 1 urun
- fiyat > 0

### 5) Musteri tarafinda gorunur mu?

```http
GET /packages
```

Sadece `active=true` ve `purchasable=true` paketler doner.

### 6) Taksitli satin alma

```http
GET /billing/installment-options?amount=299&currency=TRY&binNumber=454360
POST /purchases
```

`paymentStyle: BANK_INSTALLMENT`, `bankInstallmentCount` / `installmentCount` > 1.

---

## Silme

```http
DELETE /admin/products/{id}
DELETE /admin/packages/{id}
```

Kurallar:
- `FREE_PACKAGE` / `systemManaged` paket silinemez
- Paketin `ACTIVE` veya `PENDING` satin alimi varsa silinemez
- Urun kullanici haklarinda (`entitlement`) varsa silinemez; yoksa paket iceriklerinden otomatik cikarilip silinir

## Alan ozeti

| Alan | Anlam |
|------|--------|
| `countable` | Urun adetli hak mi |
| `purchasable` | Musteri satin alabilir mi |
| `active` | Katalogda aktif mi |
| `allowedInstallments` | Paket fiyati icin UI taksit listesi |
| BIN installment-options | Bankanin kart bazli taksitleri |
