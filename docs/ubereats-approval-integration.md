# Uber Eats Ürün Entegrasyonu ve Onay Akışı

## Amaç

İşletmenin kendi menüsündeki veya Uber Eats menüsündeki ürünleri iki sistem arasında taşımak.

Hiçbir ürün AI sonucu gelir gelmez canlı menüye veya Uber Eats’e aktarılmayacak. Tüm ürünler önce işletmenin inceleyip onaylayacağı **Onay Bekleyen Ürünler** ekranına düşecek.

`qr-agent` bu akışa dahil edilmeyecek.

## Servis sorumlulukları

### qr-service

- Kullanıcıdan aktarım isteğini alır.
- Menü snapshot’ı oluşturur.
- RabbitMQ’ya job mesajı gönderir.
- AI-service sonucunu alır.
- Ürünleri onay bekleyenler tablosuna kaydeder.
- Onay bekleyen ürünleri listeler.
- İşletme onayından sonra yayın mesajı üretir.
- Kendi menüsüne yazma ve Uber Eats’e gönderme işlemlerini yönetir.

### ai-service

- RabbitMQ’dan AI dönüşüm job’ını alır.
- Ürünleri OpenAI Batch API formatına çevirir.
- Batch input dosyasını yükler.
- Batch oluşturur.
- Batch durumunu periyodik olarak takip eder.
- Sonuçları doğrular.
- Tamamlanan sonucu RabbitMQ ile qr-service’e gönderir.

### qr-agent

Bu akışta kullanılmayacak.

## Genel akış

```text
Kullanıcı
   ↓
qr-service aktarım endpoint’i
   ↓
Menü snapshot + integration job
   ↓
RabbitMQ: integration.ai.requested
   ↓
ai-service
   ↓
OpenAI Batch API
   ↓
ai-service poller
   ↓
RabbitMQ: integration.ai.completed
   ↓
qr-service
   ↓
WAITING_APPROVAL ürünleri
   ↓
İşletme incelemesi
   ↓
Onay
   ↓
RabbitMQ: integration.publish.requested
   ↓
Kendi menüsü / Uber Eats
```

## Temel kurallar

1. İç ürün ve kategori veri modeli değiştirilmeyecek.
2. Uber dönüşümü adapter/mapper katmanında yapılacak.
3. AI hiçbir zaman fiyat, stok, ürün ID veya fotoğraf URL’si üretemeyecek.
4. AI yalnızca kategori, ürün adı, açıklama ve modifier eşleştirme önerisi üretecek.
5. Fiyat, stok, fotoğraf ve iç ID değerleri qr-service snapshot’ından alınacak.
6. AI sonucu doğrudan canlı tabloya yazılmayacak.
7. Onay olmadan hiçbir yayınlama yapılmayacak.
8. Aynı job veya ürün iki kez oluşturulamayacak.
9. Tüm işlemler tenant/menu kapsamıyla sınırlandırılacak.
10. Hatalı tek ürün tüm aktarımı durdurmayacak; ürün bazında hata tutulacak.

## Job durumları

```text
QUEUED
AI_PROCESSING
BATCH_SUBMITTED
BATCH_IN_PROGRESS
BATCH_COMPLETED
WAITING_APPROVAL
APPROVED
REJECTED
PUBLISHING_INTERNAL
PUBLISHING_UBEREATS
PUBLISHED
PARTIALLY_PUBLISHED
FAILED
```

## RabbitMQ mesajları

### AI job mesajı

Queue: `integration.ai.requested`

```json
{
  "jobId": "uuid",
  "tenantId": 21,
  "menuId": 17,
  "direction": "EXPORT_TO_UBEREATS",
  "snapshotVersion": 1,
  "attempt": 1
}
```

Mesaj içinde tüm ürün datası taşınmamalı. Ürün snapshot’ı qr-service DB’de tutulmalı; mesaj sadece job kimliğini taşımalı.

### AI tamamlandı mesajı

Queue: `integration.ai.completed`

```json
{
  "jobId": "uuid",
  "menuId": 17,
  "direction": "EXPORT_TO_UBEREATS",
  "status": "COMPLETED",
  "products": [
    {
      "customId": "product-1120",
      "sourceProductId": "1120",
      "confidence": 0.98,
      "mapping": {
        "category": "Cold Appetizers",
        "subcategory": "Cold Mezes",
        "translatedName": "Haydari",
        "translatedDescription": "Yogurt-based appetizer",
        "modifierGroupIds": []
      },
      "warnings": []
    }
  ],
  "errors": []
}
```

### Yayınlama mesajı

Queue: `integration.publish.requested`

```json
{
  "pendingProductId": "uuid",
  "menuId": 17,
  "publishTargets": [
    "INTERNAL_MENU",
    "UBEREATS"
  ],
  "attempt": 1
}
```

## Veritabanı modeli

### integration_jobs

```text
id UUID PRIMARY KEY
tenant_id BIGINT NOT NULL
menu_id BIGINT NOT NULL
provider VARCHAR(32) NOT NULL
direction VARCHAR(32) NOT NULL
status VARCHAR(32) NOT NULL
snapshot_version INT NOT NULL
external_store_id VARCHAR(128)
ai_batch_id VARCHAR(128)
ai_input_file_id VARCHAR(128)
ai_output_file_id VARCHAR(128)
error_message TEXT
created_at TIMESTAMP NOT NULL
started_at TIMESTAMP
finished_at TIMESTAMP
```

### integration_pending_products

```text
id UUID PRIMARY KEY
job_id UUID NOT NULL
tenant_id BIGINT NOT NULL
menu_id BIGINT NOT NULL
source VARCHAR(32) NOT NULL
source_product_id VARCHAR(128)
product_data JSONB NOT NULL
confidence NUMERIC(5,4)
approval_status VARCHAR(32) NOT NULL
publish_targets JSONB NOT NULL
approved_by BIGINT
approved_at TIMESTAMP
published_product_id BIGINT
uber_item_id VARCHAR(128)
error_message TEXT
created_at TIMESTAMP NOT NULL
updated_at TIMESTAMP NOT NULL
```

Unique constraint:

```text
(job_id, source_product_id)
```

## Onay bekleyen ürünler API’si

Tüm endpoint’ler authenticated olmalı ve menü sahipliği kontrol edilmeli.

### Listeleme

```http
GET /integrations/pending-products/menus/{menuId}?page=0&size=50&status=WAITING_APPROVAL
```

Response:

```json
{
  "content": [
    {
      "id": "uuid",
      "jobId": "uuid",
      "menuId": 17,
      "source": "UBEREATS",
      "sourceProductId": "uber_item_123",
      "productData": {
        "name": "Haydari",
        "description": "...",
        "price": 350,
        "currency": "TRY",
        "imageUrl": "https://...",
        "category": "Soğuk Mezeler",
        "subcategory": "Soğuk Mezeler",
        "modifiers": []
      },
      "confidence": 0.98,
      "approvalStatus": "WAITING_APPROVAL",
      "publishTargets": [],
      "warnings": []
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

### Ürünü düzenleme

```http
PATCH /integrations/pending-products/menus/{menuId}/{id}
```

İşletme ürün adını, açıklamasını, fiyatını, kategorisini, alt kategorisini, fotoğrafını ve opsiyonlarını değiştirebilmeli.

### Onaylama

```http
POST /integrations/pending-products/menus/{menuId}/{id}/approve
```

```json
{
  "publishTargets": [
    "INTERNAL_MENU",
    "UBEREATS"
  ]
}
```

Onaylama transaction içinde yapılmalı. Onaydan sonra yayın mesajı outbox veya publisher-confirm mekanizmasıyla RabbitMQ’ya gönderilmeli.

### Reddetme

```http
POST /integrations/pending-products/menus/{menuId}/{id}/reject
```

```json
{
  "reason": "Kategori uygun değil"
}
```

### Toplu onay

```http
POST /integrations/pending-products/menus/{menuId}/bulk-approve
```

```json
{
  "productIds": ["uuid-1", "uuid-2"],
  "publishTargets": ["UBEREATS"]
}
```

## AI-service Batch akışı

1. `integration.ai.requested` mesajını al.
2. Job’ı idempotent şekilde kilitle.
3. Snapshot’tan ürünleri al.
4. Her ürün için JSONL satırı oluştur.
5. `custom_id` olarak iç ürün ID’sini kullan.
6. Dosyayı OpenAI Files API’ye `purpose=batch` ile yükle.
7. `/v1/responses` endpoint’i için Batch oluştur.
8. `ai_batch_id` ve file ID’lerini job’a yaz.
9. Mesajı ACK et.
10. Poller batch durumunu düzenli aralıklarla kontrol et.
11. Batch tamamlanınca output/error dosyalarını indir.
12. Her sonucu schema ile doğrula.
13. `integration.ai.completed` mesajını yayınla.

Batch sonucu gelmeden job tamamlanmış kabul edilmemeli.

## OpenAI prompt kuralları

Prompt AI’dan yalnızca yapılandırılmış eşleştirme istemeli.

AI’nın değiştirmemesi gereken alanlar:

```text
sourceProductId
price
currency
available
imageUrl
internalProductId
```

AI output’u strict structured output ile doğrulanmalı. Geçersiz veya eksik cevap ürün bazında `FAILED` olarak işaretlenmeli.

## Uber Eats yayınlama

Onay sonrasında yayın worker’ı:

1. Pending ürünü tekrar okur.
2. İç ürün hedefi varsa mevcut ürün servisleri üzerinden oluşturur/günceller.
3. Uber hedefi varsa Uber adapter’ı ile payload üretir.
4. Uber Eats Menu API’ye gönderir.
5. `uber_item_id` ve yayın durumunu kaydeder.
6. Kısmi sonucu destekler.

Uber’e gönderilen payload’ı AI üretmemeli; payload kod tarafından oluşturulmalı.

## Idempotency ve hata yönetimi

- Her job UUID ile takip edilmeli.
- Her Batch satırının benzersiz `custom_id` değeri olmalı.
- RabbitMQ mesajları en az bir kez teslim edilebileceği için consumer idempotent olmalı.
- DB yazımı tamamlanmadan ACK verilmemeli.
- Retry için exponential backoff uygulanmalı.
- Kalıcı hatalar DLQ’ya gönderilmeli.
- Aynı ürün için ikinci pending kayıt oluşturulmamalı.
- Uber timeout ve 5xx hataları retry edilmeli.
- 4xx validation hataları retry edilmemeli.
- Job ve ürün bazında hata mesajı tutulmalı.

## Güvenlik

- Menü sahipliği her istekte doğrulanmalı.
- Uber client secret DB’de düz metin tutulmamalı.
- Internal RabbitMQ mesajları tenant/menu kapsamı içermeli.
- AI-service callback mesajları service-to-service authentication ile korunmalı.
- Kullanıcı yalnızca kendi menüsünün pending ürünlerini görebilmeli.
- Onaylayan kullanıcı ve zaman bilgisi audit olarak tutulmalı.

## Kabul kriterleri

- [ ] Export isteği HTTP 202 ile job ID döndürüyor.
- [ ] Agent hiçbir şekilde çağrılmıyor.
- [ ] Menü snapshot’ı immutable olarak saklanıyor.
- [ ] AI sonucu doğrudan canlı menüye yazılmıyor.
- [ ] AI sonucu `WAITING_APPROVAL` ürünleri oluşturuyor.
- [ ] Onay ekranı ürünleri kategori, fiyat, fotoğraf ve opsiyonlarıyla gösterebiliyor.
- [ ] İşletme ürünü düzenleyebiliyor.
- [ ] İşletme ürünü reddedebiliyor.
- [ ] İşletme ürünü yalnızca kendi menüsüne yayınlayabiliyor.
- [ ] İşletme ürünü yalnızca Uber Eats’e yayınlayabiliyor.
- [ ] İşletme ürünü iki hedefe birden yayınlayabiliyor.
- [ ] Onay sonrasında RabbitMQ yayın mesajı oluşuyor.
- [ ] Aynı ürün/job tekrar işlendiğinde duplicate oluşmuyor.
- [ ] Partial failure destekleniyor.
- [ ] Retry ve DLQ davranışı test ediliyor.
- [ ] Menü sahipliği ve yetkilendirme test ediliyor.
- [ ] AI output schema validation test ediliyor.
- [ ] Batch polling test ediliyor.

## Uygulama notu

Hedef backend `qr-service` Spring Boot projesidir. Mevcut `qr-agent` içine entegrasyon worker’ı eklenmemeli. `qr-service` üzerindeki mevcut kullanıcı değişiklikleri korunmalı; migration ve yeni sınıflar mevcut kodla çakışmayacak şekilde eklenmelidir.
