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
- [x] CRM + Room değişiklikleri için CI Run #88: unit test + debug APK + artifact + release başarıyla tamamlandı.
- [x] CI Run #110: sync queue düzeltmesi sonrası unit test + debug APK + artifact + latest APK publish başarıyla tamamlandı.
- [x] CI Run #123: Android instrumentation smoke testleri + Room CRM persistans testi + WorkManager scheduling testi + release artifact/latest APK zinciri başarıyla tamamlandı.

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
- [x] CI emülatöründe Activity/Compose başlangıç ve temel kullanıcı etkileşimi smoke testi.

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
- [x] Android instrumentation ile uygulama açılışı ve temel arama doğrulaması.
- [ ] Gerçek Global Donuk ürün kataloğu ile ürün eşleştirme; public SKU/gramaj/fiyat kataloğu bulunmadığı için ürün uydurulmadı.

## 4. Harita

- [x] Mevcut kullanıcı aramasındaki gerçek koordinatları haritada gösterme kodu.
- [x] Marker + popup.
- [x] Görünür OpenStreetMap atfı ve ODbL ibaresi.
- [x] Toplu şehir taraması/prefetch yapılmıyor.
- [ ] Gerçek cihazda tile/marker/popup smoke testi.
- [x] Harita HTML/OSM attribution/gerçek koordinat marker sözleşmesi unit test ile doğrulandı.
- [ ] Saha ölçeğinde OSM tile kullanımının operasyon kontrolü.

## 5. Saha CRM

- [x] Potansiyel müşteri kalıcı Room kaydı.
- [x] CRM müşteri gözlem akışı.
- [x] Ziyaret/görüşme/numune/teklif/sipariş aktiviteleri için domain modeli ve kayıt kuyruğu.
- [x] Aktif/kaybedilen müşteri durumları için aşama modeli.
- [x] Offline-first yerel kayıt: önce cihaz veritabanı, sonra senkronizasyon kuyruğu.
- [x] Aşama geçiş geçmişinin kalıcı tutulması.
- [x] Conflict çözümleyici ve sürüm alanları.
- [x] Supabase Auth + RLS korumalı CRM push/pull adapter kodlandı ve WorkManager'a bağlandı.
- [ ] Gerçek kullanıcı hesabıyla uçtan uca bulut senkronizasyon saha testi.
- [x] Sürüm tabanlı müşteri/next-action/opportunity merge kuralları kodlandı.
- [ ] Gerçek conflict çözümü için iki gerçek cihaz + aynı kullanıcı backend testi.

## 6. Satış hunisi

`Potansiyel → Ziyaret → Görüşme → Teklif → Numune → Sipariş → Aktif Müşteri`

- [x] Temel dashboard funnel görselleştirmesi.
- [x] Kalıcı CRM aşama verisi.
- [x] Aşama geçiş geçmişi.
- [ ] Huni aşamalarını ayrıntılı işletme listelerine bağlama.
- [ ] Gerçek ziyaret/teklif/sipariş aktivitelerinden dönüşüm oranları.

## 7. Dashboard

- [x] İlk KPI/funnel/filtre ekranı.
- [x] Dashboard müşteri sayısını gerçek yerel CRM kayıtlarından okuyor.
- [ ] Gerçek backend veri pipeline'ı.
- [x] İlçe → mevcut arama sonuçlarından mahalle drill-down filtresi.
- [ ] Kalıcı kapsamlı mahalle veri kümesi ve backend drill-down.
- [ ] Gerçek ziyaret/arama/teklif/sipariş metrikleri.
- [ ] Gün/hafta/ay karşılaştırması.
- [ ] Hedef/gerçekleşen.
- [ ] Gerçek dönüşüm oranları.
- [ ] Müşteri başına satış.
- [ ] Gerçek fırsat yoğunluğu haritası.

## 8. Backend / offline

- [x] Yerel kalıcı CRM veri katmanı.
- [x] Yerel senkronizasyon kuyruğu ve payload sürümü.
- [x] Conflict çözümleme çekirdeği.
- [ ] Bulut veri katmanı.
- [ ] Kullanıcı sahipliği ve yetkilendirme.
- [ ] RLS / veri erişim sınırları.
- [x] WorkManager senkronizasyon worker'ı, CONNECTED constraint, unique periodic scheduling ve exponential backoff kodlandı/test edildi.
- [ ] Yetkili backend bağlandıktan sonra gerçek senkronizasyon worker'ı uçtan uca doğrulanacak.
- [ ] Conflict yönetiminin backend ile uçtan uca testi.

## 9. Global Donuk ürün kataloğu

- [x] HORECA kullanım senaryosu araştırıldı.
- [ ] Gerçek Global Donuk ürün kataloğu doğrulanacak; yetkili SKU/gramaj/koli kaynağı bekleniyor.
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
- [x] `extratags` parser doğrudan birim testleri.
- [x] CRM aşama/conflict/persistans testleri.
- [x] CRM + Room CI build doğrulaması.
- [x] Sync engine retry/conflict/permanent-failure/no-backend davranışları unit testlerle doğrulandı.
- [x] WorkManager scheduler ve worker kodu CI debug APK build zincirinden geçti.
- [x] UI/integration smoke testleri: Activity/Compose + WorkManager + Room instrumentation.
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
- Room yerel CRM, sync queue, stage history ve conflict çekirdeği eklendi.
- Arama sonucu → CRM kaydı → dashboard veri zinciri bağlandı.
- CI Run #88 ile unit test + debug APK + release zinciri doğrulandı.
- CI Run #110 ile sync queue düzeltmesi sonrası unit test + debug APK + artifact + latest APK publish zinciri tekrar doğrulandı.
- CI Run #123 ile Android emülatör smoke gate'i dahil tam CI → APK → artifact → latest APK publish zinciri doğrulandı.
- Run #123 artifact SHA-256: `22df4cfc9ba997a6e43e515d52b1f8f4d03505d6dba07ad6d4735ad3d9d60176`.

## 13. Geliştirme kuralı

`Kaynak/veri tasarımı → araştırma → kod → test → CI → başarılı build → doğrulama → release → tik`

Bir özellik kanıtlanmadan tamamlanmış sayılmaz.


## 2026-09-20 — Navigasyon, manuel nokta ve rutin planlama

- AppSection tabanlı Radar / Manuel Nokta / Rutin menüleri eklendi.
- Üst menüde Geri / İleri kontrolleri eklendi; bölüm geçmişi ile ileri-geri dolaşım destekleniyor.
- Manuel müşteri/nokta kaydı: ad, açık adres, il, ilçe, mahalle, X=boylam ve Y=enlem.
- CRM customer modeline address/latitude/longitude eklendi; Room 4→5 migration eklendi.
- Mevcut gerçek Nominatim işletmeleri CRM'e kaydedilirken kaynak koordinatları da CRM'de tutuluyor.
- Yakınlık bazlı rutin motoru Haversine mesafesi + greedy nearest-neighbor yaklaşımı ile gerçek koordinatlı müşteri havuzundan rota sıralıyor.
- Rutin ekranı şehir/ilçe kapsamı, başlangıç müşterisi seçimi, adım mesafesi ve kümülatif mesafeyi gösteriyor.
- Supabase CRM customer tablosuna address/latitude/longitude alanları ve indeks eklendi; push/pull adapter bu alanları senkronize ediyor.
- Manuel nokta ve rota için unit/instrumentation testleri eklendi.
