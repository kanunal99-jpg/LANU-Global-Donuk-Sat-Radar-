# LANU Global Donuk Satış Radarı — Proje Durumu ve Yol Haritası

> `[x]` yalnızca kod/test/CI veya kaynak kanıtı bulunan maddeler içindir. `[ ]` açık ödevdir.

## 1. Tamamlanan temel maddeler

### Proje temeli
- [x] Android proje iskeleti ve Gradle/CI yapısı.
- [x] Proje adı: `Lanu Global Donuk Satış Radarı`.
- [x] GitHub ana dalı: `main`.
- [x] Proje anayasası ve kaynak gerçekliği kuralları.

### CI / APK
- [x] Java 17 / Kotlin / Compose build altyapısı.
- [x] Unit test CI adımı.
- [x] Debug APK CI üretimi.
- [x] APK artifact + SHA-256.
- [x] `latest` release asset yükleme zinciri.
- [x] Release asset doğrulaması: `Lanu-Global-Donuk-Satis-Radari-latest.apk` ve `.sha256` mevcut.

### Veri gerçekliği
- [x] `DataQuality` ayrımı.
- [x] `BusinessSourceContract`.
- [x] `BusinessSourceAdapter` ingestion sınırı.
- [x] `VerifiedBusinessValidator`.
- [x] Repository factory + güvenli boş fallback.
- [x] Kaynak içi deduplikasyon.
- [x] Tazelik (`BusinessFreshnessState`) ayrımı.

## 2. Gerçek veri kaynağı

### OpenStreetMap Nominatim
- [x] Gerçek kullanıcı tetiklemeli arama.
- [x] HTTPS endpoint.
- [x] Özel User-Agent.
- [x] En az 1,1 saniyelik rate-limit.
- [x] 5 dakikalık cache.
- [x] `addressdetails=1`.
- [x] `extratags=1` + `namedetails=1`.
- [x] Kaynakta gerçekten varsa telefon / web / çalışma saatlerini domain'e taşıma.
- [x] Kaynak kapsamının eksiksiz İstanbul verisi olmadığı açıkça belirtiliyor.
- [ ] Gerçek cihazda canlı Nominatim smoke testi.

### İTO / Ticaret Sicili
- [x] Kaynak adayları ve kullanım/erişim riskleri araştırıldı.
- [ ] Resmî erişim ve yeniden kullanım şartları üretim için doğrulanacak.
- [ ] Yetkili erişim olmadan toplu aktarım yapılmayacak.

### Kapsamlı İstanbul HORECA verisi
- [ ] İlçe/mahalle bazında güncel, doğrulanmış ve yeniden kullanım hakkı belirlenmiş veri kümesi.
- [ ] Kapsamlı veri kaynağı olmadan “İstanbul'daki tüm işletmeler” iddiası yapılmayacak.

## 3. HORECA keşif ve işletme raporu

- [x] İstanbul ile başlama.
- [x] Şehir ve ilçe seçimi.
- [x] Gerçek kaynak tetiklemeli işletme araması.
- [x] İşletme adı/kategori/adres/konum/kaynak bilgileri.
- [x] Çalışan sayısı ve satış potansiyelini uydurmama.
- [x] Kategoriye göre saha keşif soruları ve satış görüşme çerçevesi.
- [x] Kaynakta varsa telefon/web/çalışma saatleri.
- [x] Telefon ve web aksiyonları.
- [x] Navigasyon intent'i.
- [ ] Gerçek cihazda rapor + navigasyon akışı.
- [ ] Gerçek Global Donuk ürün kataloğu ile ürün eşleştirme.

## 4. Harita

- [x] Mevcut kullanıcı aramasındaki gerçek koordinatları haritada gösterme kodu.
- [x] Marker + popup.
- [x] Görünür OpenStreetMap atfı ve ODbL ibaresi.
- [x] Toplu şehir taraması/prefetch yapılmıyor.
- [ ] Gerçek cihazda tile/marker/popup smoke testi.
- [ ] Saha ölçeğinde OSM tile kullanımının operasyon kontrolü.

## 5. Saha CRM — açık ödev

- [ ] Potansiyel müşteri kaydı.
- [ ] Ziyaret planlama/sonucu.
- [ ] Görüşme notları.
- [ ] Numune.
- [ ] Teklif/takip.
- [ ] Sipariş takibi.
- [ ] Aktif/kaybedilen müşteri durumu.
- [ ] Offline-first kayıt ve veri kaybı koruması.
- [ ] Bulut senkronizasyonu.
- [ ] Conflict çözümü.

## 6. Satış hunisi — açık ödev

`Potansiyel → Ziyaret → Görüşme → Teklif → Numune → Sipariş → Aktif Müşteri`

- [x] Temel dashboard funnel görselleştirmesi.
- [ ] Huni aşamalarını işletme listelerine bağlama.
- [ ] Kalıcı CRM aşama verisi.
- [ ] Aşama geçiş geçmişi.

## 7. Dashboard — açık ödev

- [x] İlk KPI/funnel/filtre ekranı.
- [ ] Gerçek backend veri pipeline'ı.
- [ ] İlçe → mahalle → işletme drill-down.
- [ ] Gerçek ziyaret/arama/teklif/sipariş metrikleri.
- [ ] Gün/hafta/ay karşılaştırması.
- [ ] Hedef/gerçekleşen.
- [ ] Gerçek dönüşüm oranları.
- [ ] Müşteri başına satış.
- [ ] Gerçek fırsat yoğunluğu haritası.

## 8. Backend / offline — açık ödev

- [ ] Kalıcı işletme/CRM veri modeli.
- [ ] Bulut veri katmanı.
- [ ] Kullanıcı sahipliği ve yetkilendirme.
- [ ] RLS / veri erişim sınırları.
- [ ] Senkronizasyon kuyruğu.
- [ ] Conflict yönetimi.
- [ ] Offline-first saha testi.

## 9. Global Donuk ürün kataloğu — açık ödev

- [x] HORECA kullanım senaryosu araştırıldı.
- [ ] Güncel ürün/SKU/gramaj kataloğu doğrulanacak.
- [ ] Kaynak ve kullanım şartı doğrulanacak.
- [ ] Ürün eşleştirme gerçek katalog geldikten sonra bağlanacak.
- [x] Uydurma ürün/SKU/fiyat/gramaj kullanılmıyor.

## 10. Satış koçluğu / yapay zekâ

- [x] Kategoriye bağlı saha keşif soruları ve görüşme çerçevesi.
- [ ] Doğrulanmış menü/operasyon sinyallerinden ürün/öneri.
- [ ] CRM geçmişine göre takip önerileri.
- [ ] AI çıktısı için kaynak doğrulama kapısı.

## 11. Test / saha doğrulama

- [x] Veri kalite testleri.
- [x] Repository factory testleri.
- [x] Deduplication/freshness testleri.
- [x] Nominatim sözleşme/query testleri.
- [x] Satış fırsatı testleri.
- [ ] `extratags` parser için doğrudan birim test kapsamını genişletme.
- [ ] UI/integration testleri.
- [ ] Gerçek cihaz APK kurulumu.
- [ ] Canlı kaynak araması.
- [ ] Harita marker/popup/navigasyon testi.
- [ ] Ağ yok / kaynak hata / rate-limit / boş sonuç smoke testleri.

## 12. 2026-09-17 araştırma ve uygulama kaydı

- Araştırma ödevi: `docs/ARASTIRMA_2026-09-17.md`.
- Nominatim policy + OSM/ODbL + tile kullanım kuralları araştırıldı.
- İBB sosyal nokta veri kaynağı adayı araştırıldı; lisans/erişim doğrulaması olmadan üretime alınmadı.
- Global Donuk HORECA ürün senaryosu araştırıldı; doğrulanmış SKU kataloğu olmadan ürün uydurulmadı.
- `extratags` / `namedetails` kodlandı.
- Kaynakta varsa telefon/web/çalışma saatleri rapora bağlandı.
- Gerçek sonuçlara bağlı harita eklendi.
- OSM atfı görünür tutuldu; toplu tarama/prefetch yapılmadı.

## 13. Geliştirme kuralı

`Kaynak/veri tasarımı → araştırma → kod → test → CI → başarılı build → doğrulama → release → tik`

Bir özellik kanıtlanmadan tamamlanmış sayılmaz.
