# Sipariş akışı: garson, mutfak, kurye

Garson, mutfak ve kurye arasında siparişin nasıl ilerleyeceğine dair kararlar. Hedef: garson için akış
"masaya dokun → ürün ekle → gönder" olarak kalsın; nereye gideceği, kimin haberdar olacağı arka planda
kendiliğinden belirlensin. Durum: **karar notu**, henüz uygulanmadı (bugünkü davranış en altta).

## 1. Siparişin kanalı: kimse seçmez

Kanal, siparişin nereden başladığından anlaşılır.

| Sipariş nereden geliyor | Kanal | Seçim |
|---|---|---|
| Garson masa ekranından girdi | Masa | Yok |
| Müşteri masadaki QR'dan verdi | Masa | Yok |
| Online mağaza, Yemeksepeti, Uber Eats | Paket veya gel-al | Yok (müşteri seçmiş gelir) |
| Telefon | Paket veya gel-al | Telefona bakan kişi, bir kez ("Paket / Gel-al", varsayılan paket) |

- Masa siparişinde kurye hiç devreye girmez; garsonun ekranında kurye seçeneği yoktur.
- Telefon siparişi masa ekranından değil, ayrı **"Telefon siparişi"** ekranından girilir. Bu ekranı kullanma
  yetkisi personel bazında açılır (kasa, yetkili garson).
- Masadaki müşterinin "kalanı paketleyin" isteği kurye işi değildir; paketleme notudur.

## 2. Kalem nereye gider: kategori bazlı hazırlık istasyonu

Garson sipariş verdiğinde hangi kalemin mutfağa gideceğine **ürünün kategorisi** karar verir. Garson bir şey
seçmez.

| İstasyon | Örnek kategori | Ne olur |
|---|---|---|
| **Mutfak** | Izgara, Döner, Tatlı | Mutfağa gider (ekran, yazıcı ya da takipsiz; bkz. bölüm 3) |
| **Bar** | Sıcak içecek, Kahve, Kokteyl | Bara gider (ekran, yazıcı ya da takipsiz) |
| **Direkt servis** | Su, Kutu içecek, Meşrubat | Hiçbir istasyona gitmez; garson kendisi getirir, kalem anında hazır sayılır |

Örnek: Masa 4 → 2 Ayran (Direkt) + 1 Köfte (Mutfak). Ayranları garson hemen getirir; köfte mutfak ekranına
düşer, hazır olunca garsona haber gelir.

Ayar kuralları:

- **Tek istasyonlu işletmede hiçbir şey sorulmaz**; her şey o istasyona gider. Seçim ancak ikinci istasyon
  eklenince görünür.
- İstasyon **kategoriye** atanır, ürün kategoriden miras alır. Yeni ürün eklerken bir şey seçilmez.
- Kategori oluşturulurken adından istasyon önerilir ("İçecek/Kahve" → Bar, "Su/Meşrubat" → Direkt, diğerleri →
  Mutfak). Yanlışsa değiştirilir. AI menü içe aktarma da öneri yapabilir.
- Ürün formunda kapalı "Gelişmiş" bölümünde **ürün bazlı istisna**: "Hazırlık yeri: Kategoriden (Bar) ▾".
- Kurulumda tek ekranda **kategori × istasyon** tablosu ile toplu düzenleme.

## 3. Mutfak aç/kapa ayarı yok

Ayrı bir "mutfak aç/kapa" anahtarı olmaz; gördüğü üç iş başka yerden çıkar:

1. **Mutfak var mı?** En az bir kategori "Mutfak" istasyonuna atanmışsa vardır. Ayrı ayar yok.
2. **Nasıl takip ediliyor?** Elle seçilmez, bağlı cihaz belirler:
   - İstasyon tableti personel uygulamasına o istasyonun hesabıyla girmişse → **ekran**: tam takip, "Hazır"
     garsona geri döner.
   - Eşleştirilmiş istasyon yazıcısı varsa → **fiş** (ekranla birlikte de olabilir).
   - İkisi de yoksa → **takipsiz**: kalem hesaba yazılır, garson "servis edildi"yi kendisi işaretler.
   - Tablet bir süredir bağlanmıyorsa sipariş engellenmez: kendiliğinden yazıcıya, o da yoksa takipsize düşer ve
     garsona "Mutfak ekranı bağlı değil, siparişi sözlü iletin" uyarısı gider.
3. **Mutfak kapandı** = o ürünler şu an satılmıyor. Bu, kategori/ürün **satış saatleri** ile çözülür (mutfak
   23:00'te kapanır, kahvaltı 12:00'ye kadar, tatlılar 15:00'ten sonra). Saat dışındaki ürünler QR menüde ve
   garson ekranında soluk ve seçilemez görünür; diğer kategoriler satılmaya devam eder. Anlık durum için
   "tükendi" işareti kullanılır.

Kurallar:

- İşletme sahibinin ayarladığı tek şey kategorinin **hazırlık yeri** ve gerekirse **satış saati**.
- Açık siparişi olan bir kategorinin hazırlık yeri değiştirilemez (bugünkü `kitchenCloseGuard` mantığı korunur).
- Geçişte bugünkü `kitchenEnabled` kaldırılır; değeri kategori atamalarına çevrilir
  (`true` → hazırlık gerektiren kategoriler "Mutfak", `false` → "Direkt servis").

| Durum | Mutfak kategorisindeki ürün |
|---|---|
| Satış saatinde, mutfak tableti bağlı | Normal akış: ekran → Hazır → garsona bildirim |
| Satış saatinde, tablet yok/bozuk | Satılır; yazıcıya veya takipsiz geçer, garson uyarılır |
| Satış saati dışında / tükendi | Satılamaz; menüde soluk görünür |

## 4. İstasyon ekranı bağlıyken akış

```
Garson "Gönder"
  → Kalemler istasyonlara bölünür (Direkt servis kalemleri anında hazır)
  → İstasyon ekranında fiş: ses + geçen süre sayacı (10 dk sarı, 15 dk kırmızı)
  → Aşçı tek dokunuşla "Hazır" ("Başla" isteğe bağlı)
  → Pas: masanın tüm kalemleri hazır olunca garson tek seferde çağrılır
  → Garsonun telefonu titrer: "Masa 4 hazır" → bildirimden "Aldım" → "Servis edildi"
```

Sahiplik: her açık kalemin her an tek sahibi vardır.

| Kalem durumu | Sahibi | Süre dolunca |
|---|---|---|
| Hazırlanıyor | İstasyon | Fiş kırmızıya döner |
| Hazır, pasta | Masanın garsonu | 60 sn → bölgedeki tüm garsonlar, 3 dk → vardiya sorumlusu |

İstisnalar ilk günden desteklenir:

- **Ürün bitti**: Mutfak tek dokunuşla bildirir; ürün QR menüde ve garson ekranında anında "tükendi" olur,
  bekleyen masaların garsonlarına haber gider.
- **Geri gönder**: Sebebiyle mutfağa döner, öncelikli işaretlenir, rapora kalite verisi olarak düşer.
- **Acele / gecikme**: Garson öne alır; mutfak gecikme bildirir.
- **İptal**: Hazırlanmaya başlamış kalem mutfak onayıyla iptal edilir, fire kaydedilir.

## 5. Kurye (yalnızca paket kanalı)

```
Paket siparişi girer (online veya telefon)
  → Kalemler istasyonlara gider (masa siparişiyle aynı)
  → Sistem kurye atar: mutfağın tahmini bitiş saatine göre
  → Pasta "Paketle" kontrol listesi (kalemler, sos, çatal, peçete) → "Paket hazır"
  → Kuryenin telefonu titrer → teslim alır → Yolda (müşteriye takip bağlantısı)
  → Teslim edildi (kod/fotoğraf; nakitse tahsil edilen tutar)
```

- Ayar: şubede "Kendi kuryem var" (`courierEnabled`) ve kurye listesi. Platform kuryesi geliyorsa (Yemeksepeti /
  Uber Eats teslim ediyorsa) kendi kuryen atanmaz.
- Atama: varsayılan otomatik (boştaki/en erken dönen, aynı yöne gidenler gruplanır); küçük işletmede "kendin al";
  pas veya yönetici elle değiştirebilir.
- Paket hazır olduğu halde kurye gelmezse önce başka kuryeye, sonra yöneticiye devreder.
- Müşteri kapıda yoksa tek dokunuşla arama, ardından "Teslim edilemedi" → yöneticiye düşer.
- Vardiya sonunda kurye bazlı nakit mutabakatı.

## 6. İletişim ve altyapı

- Bildirimler üç seviyede: **çevresel** (ekran renk/sayaç), **eylem kartı** (düğmeli kart),
  **kesintili** (titreşim/kilit ekranı — yalnızca hemen eylem gerekirse).
- Olay akışı: her durum değişikliği olay olarak kaydedilip yayınlanır (RabbitMQ yapılandırmada mevcut).
- Şube bazlı WebSocket kanalı (yoklama biter), arka plandayken FCM/APNs.
- İstemciler bağlantısızken yerel kuyruğa yazar; tekillik anahtarı ile çift sipariş olmaz.
- Yazıcı, olay akışının bir abonesidir.
- Bir personel birden fazla role sahip olabilir (ör. garson + kurye); bugünkü tek `staffRole` genişletilir.

## 7. Ölçümler

Fiş süresi (istasyon), pas bekleme süresi (garson), masaya ilk tabak süresi, fire/geri gönderme oranı,
teslimat süresi. `confirmedAt`, `preparedAt`, `readyAt`, `servedAt` bugün siparişte var.

## 8. Geçiş sırası

1. Mevcut durum geçişlerini olay olarak yayınla + WebSocket/FCM (uçlar bozulmaz).
2. Kategori bazlı istasyon, kalem bazlı durum ve satış saatleri; `kitchenEnabled` kaldırılıp kategori atamalarına çevrilir.
3. Tek dokunuşla "Hazır" (CONFIRMED → READY), sahiplik/devretme, istisnalar.
4. Telefon siparişi ekranı ve yetkisi, kurye ataması, paketleme adımı.
5. Akıllı zamanlama ve raporlar.

## Bugünkü davranış (referans)

- `kitchenEnabled = false`: garson siparişi CONFIRMED başlar; garson CONFIRMED/PREPARING → SERVED yapabilir.
- `kitchenEnabled = true`: CONFIRMED → (mutfak) PREPARING → (mutfak) READY → (garson) SERVED; garson READY
  olmadan servis edemez, mutfak PREPARING'i atlayamaz (`MenuWaiterOrderService`).
- Açık sipariş varken mutfak kapatılamaz (`BranchService` + `kitchenCloseGuard`).
- Durum sipariş düzeyinde tutulur, istasyon ayrımı yoktur; içecekler de mutfak kuyruğuna girer.
- Mobil uygulamalar 10–20 sn yoklama ile güncellenir.
