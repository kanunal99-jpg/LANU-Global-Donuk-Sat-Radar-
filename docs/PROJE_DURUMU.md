# LANU Global Donuk Satış Radarı — Proje Durumu ve Yol Haritası

> `[x]` yalnızca kod/test/CI veya kaynak kanıtı bulunan maddeler içindir. `[ ]` açık ödevdir. Bir özellik CI başarısızken tamamlanmış sayılmaz.

## 1. Projenin amacı

LANU Global Donuk Satış Radarı; Global Donuk Gıda'nın HORECA satış faaliyetleri için gerçek ve kaynaklandırılmış işletme keşfi, saha planlama, satış fırsatı analizi, müşteri takibi ve satış yönetimi sağlayan şehir bağımsız bir Android + web/dashboard ekosistemi olacaktır.

İlk aktif coğrafya İstanbul'dur. Mimari ülke → şehir → ilçe → mahalle → işletme hiyerarşisini destekleyecek şekilde hazırlanır.

## 2. Tamamlanan temel maddeler

### Proje temeli
- [x] Android proje iskeleti ve Gradle/CI yapısı.
- [x] Proje adı: `Lanu Global Donuk Satış Radarı`.
- [x] GitHub ana dalı: `main`.
- [x] Proje anayasası `docs/ANAYASA.md`.
- [x] Kaynak gerçekliği kuralları yazılı hale getirildi.

### CI ve build altyapısı
- [x] Java/Kotlin JVM hedef uyumsuzluğu giderildi.
- [x] Java 17 hedefi standartlaştırıldı.
- [x] Kotlin 2.x + Compose Compiler yapılandırması düzeltildi.
- [x] Kotlin Compose plugin ve Compose build etkinleştirildi.
- [x] Material 3 deneysel API kaynaklı CI problemi giderildi.
- [x] `gradle test` ve `gradle assembleDebug` daha önce başarılı CI koşusunda doğrulandı.
- [x] Debug APK artifact üretimi daha önce başarılı CI koşusunda doğrulandı.

### APK dağıtımı
- [x] GitHub Actions APK üretim zinciri mevcut.
- [x] APK için SHA-256 üretimi mevcut.
- [x] `latest` release otomasyonu mevcut.
- [ ] Güncel commit için `latest` release asset yükleme adımı yeniden doğrulanıyor; son denemede release metadata güncellendi ancak asset yükleme başarısız kaldı.

### Veri gerçekliği temeli
- [x] `DataQuality` ile kaynaklı/tahmini/kullanıcı girişi/eski/doğrulanmamış veri ayrımı.
- [x] `BusinessSourceContract` ile kaynak kapsamı, erişim yöntemi, kullanım izni ve alan listesi.
- [x] Kullanım izni doğrulanmadan üretim bağlantısını engelleyen validation.
- [x] `BusinessSourceAdapter` ingestion sınırı.
- [x] `VerifiedBusinessValidator` ile domain'e giriş doğrulaması.
- [x] Güvenli repository factory yolu ve boş repository fallback.
- [x] Kaynak içi deterministik deduplikasyon.
- [x] `BusinessFreshnessState` ile tazeliğin veri kalitesinden ayrılması.
- [x] Gelecek tarihli doğrulamaların reddedilmesi.

## 3. Gerçek veri kaynağı durumu

### OpenStreetMap Nominatim
- [x] Gerçek kullanıcı tetiklemeli arama adapter'ı.
- [x] HTTPS endpoint ve kaynak sözleşmesi.
- [x] Özel User-Agent.
- [x] En az 1,1 saniyelik istemci rate-limit.
- [x] 5 dakikalık arama cache'i.
- [x] `addressdetails=1` ile adres alanları.
- [x] Araştırma sonucuna göre `extratags=1` + `namedetails=1` desteği kodlandı.
- [ ] Bu yeni iletişim alanlarının güncel CI/release doğrulaması.
- [ ] Gerçek cihazda canlı Nominatim smoke testi.

### İstanbul Ticaret Odası / Türkiye Ticaret Sicili
- [x] Kaynak adayları ve erişim/kullanım riskleri belgelenmiş durumda.
- [ ] Resmî erişim ve yeniden kullanım şartlarının üretim entegrasyonu için doğrulanması.
- [ ] Yetkili erişim sağlanmadan toplu veri entegrasyonu yapılmayacak.

### İstanbul için kapsamlı HORECA veri kümesi
- [ ] İlçe/mahalle bazında güncel, doğrulanmış ve yeniden kullanım hakkı belirlenmiş veri kümesi.
- [ ] “İstanbul'daki tüm işletmeler” iddiasını destekleyecek kapsamlı kaynak.

## 4. Kullanıcı tarafından istenen ürün kapsamı

### HORECA müşteri keşfi
- [x] İstanbul ile başlama.
- [x] Şehir değiştirme / şehir ekleme mimari başlangıcı.
- [x] İlçe filtresi.
- [x] Gerçek kaynak tetiklemeli işletme araması.
- [ ] Mahalle bazında kapsamlı veri kaynağı ve filtreleme.
- [ ] CI sonrası canlı harita yüzeyinin doğrulanması.
- [ ] Kapsamlı HORECA veri seti.

### İşletme detay raporu
- [x] İşletme adı, tür/kategori, şehir, ilçe, mahalle, adres.
- [x] Koordinat ve kaynak/son doğrulama bilgisi.
- [x] Çalışan sayısı kaynakta yoksa uydurmama kuralı.
- [x] Satış potansiyelini kaynak verisi olmadan hesaplamama kuralı.
- [x] Kategoriye göre saha keşif soruları ve satış görüşme çerçevesi.
- [x] Kaynakta bulunursa telefon/web/çalışma saatlerini taşıyan veri modeli ve ekran alanları.
- [x] Telefon ve web için güvenli Android aksiyonları.
- [ ] CI sonrası rapor + navigasyon akışının gerçek cihaz doğrulaması.
- [ ] Gerçek Global Donuk ürün kataloğu ile doğrulanmış ürün eşleştirme.

### Harita
- [ ] CI/release doğrulaması bekleyen gerçek arama sonuçları haritası.
- [x] Harita yalnızca mevcut kullanıcı aramasındaki koordinatları kullanacak şekilde tasarlandı.
- [x] Görünür OpenStreetMap atfı eklendi.
- [ ] Gerçek cihazda tile + marker + popup smoke testi.
- [ ] OSM tile kullanımının saha ölçeği/yoğunluğu için nihai operasyon kontrolü.

## 5. Saha CRM — açık ödev

- [ ] Potansiyel müşteri kaydı.
- [ ] Ziyaret planlama ve ziyaret sonucu.
- [ ] Görüşme notları.
- [ ] Numune süreci.
- [ ] Teklif oluşturma/takip.
- [ ] Sipariş takibi.
- [ ] Aktif müşteri / kaybedilen müşteri durumu.
- [ ] Yerel veri kaybına dayanıklı offline-first kayıt.
- [ ] Bulut senkronizasyonu.
- [ ] Conflict çözümü.

## 6. Satış hunisi — açık ödev

`Potansiyel → Ziyaret → Görüşme → Teklif → Numune → Sipariş → Aktif Müşteri`

- [x] Dashboard'da temel satış huni görselleştirmesinin ilk katmanı mevcut.
- [ ] Huni aşamalarının tıklanabilir işletme listelerine bağlanması.
- [ ] Her aşama için kalıcı CRM verisi.
- [ ] Aşama geçiş geçmişi ve zaman damgaları.

## 7. Dashboard / Power BI benzeri görünüm — açık ödev

- [x] Mobil dashboard ekranının ilk KPI/funnel/filtre katmanı mevcut.
- [ ] Gerçek backend veri pipeline'ı.
- [ ] İlçe → mahalle → işletme drill-down.
- [ ] Gerçek ziyaret/arama/teklif/sipariş metrikleri.
- [ ] Gün/hafta/ay karşılaştırması.
- [ ] Hedef/gerçekleşen.
- [ ] Gerçek dönüşüm oranları.
- [ ] Müşteri başına satış.
- [ ] Harita üzerinde gerçek fırsat yoğunluğu.

## 8. Veri ve backend — açık ödev

- [ ] Android + bulut veri katmanı + mobil web dashboard.
- [ ] Kalıcı işletme/CRM tabloları.
- [ ] Yetkilendirme ve kullanıcı sahipliği.
- [ ] RLS / veri erişim sınırları.
- [ ] Senkronizasyon kuyruğu ve conflict yönetimi.
- [ ] Offline-first saha kullanım testi.

## 9. Global Donuk ürün kataloğu — açık ödev

- [x] Kurumsal sitedeki HORECA kullanım senaryosu araştırıldı.
- [ ] Güncel ürün/SKU/gramaj gibi doğrulanmış katalog verisi edinilecek.
- [ ] Kaynak ve kullanım şartı doğrulanacak.
- [ ] Ürün eşleştirme motoru gerçek katalog geldikten sonra bağlanacak.
- [ ] Uydurma ürün adı, SKU, fiyat veya gramaj kullanılmayacak.

## 10. Satış koçluğu / yapay zeka — açık ödev

- [x] Kategoriye bağlı saha keşif soruları ve görüşme çerçevesi ilk katmanda mevcut.
- [ ] Doğrulanmış menü/operasyon sinyallerinden ürün ve yaklaşım önerisi.
- [ ] Kullanıcı CRM geçmişini dikkate alan takip önerileri.
- [ ] Yapay zekâ çıktısının kaynak gerçeklerinin yerine geçmesini engelleyen doğrulama katmanı.

## 11. Test ve saha doğrulama

- [x] Veri kalite testleri.
- [x] Repository factory testleri.
- [x] Deduplication/freshness testleri.
- [x] Nominatim sözleşme/query testleri.
- [x] Satış fırsatı testleri.
- [ ] `extratags` parser için doğrudan birim test kapsamının genişletilmesi.
- [ ] UI/integration testleri.
- [ ] Gerçek cihaz kurulumu.
- [ ] Canlı kaynak araması.
- [ ] Harita marker/popup/navigasyon akışı.
- [ ] Ağ yok / kaynak hata / rate-limit / boş sonuç smoke testleri.

## 12. 2026-09-17 araştırma ve uygulama kayıtları

- Araştırma ödevi: `docs/ARASTIRMA_2026-09-17.md`.
- Nominatim `extratags` ve `namedetails` desteği kodlandı.
- Kaynakta gerçekten varsa telefon/web/çalışma saatleri domain'e taşındı.
- İşletme raporunda bu alanlar kaynak yoksa açıkça `Kaynakta yok` gösteriliyor.
- Sonuçlara kullanıcı aramasına bağlı harita yüzeyi eklendi.
- OSM atfı görünür bırakıldı; toplu tarama/prefetch uygulanmadı.
- Güncel CI/release doğrulaması tamamlanmadan bu yeni katmanlar tamamlanmış kabul edilmez.

## 13. Geliştirme kuralı

Her yeni özellik şu sırayı izler:

`Kaynak/veri tasarımı → araştırma → kod → uygun test → CI → başarılı build → doğrulama → release → durum belgesini güncelleme`

## 14. Kaynak gerçekliği standardı

- Gerçek veri, tahmin ve kullanıcı girişi ayrı etiketlenir.
- Her kritik veri mümkünse kaynağı ve son doğrulama zamanı ile tutulur.
- Kaynaksız kritik veri gerçekmiş gibi yayınlanmaz.
- Veri sağlayıcının şartlarını ihlal eden toplu scraping mimarinin temeli yapılamaz.
- Kaynak kapsamı, "İstanbul'daki tüm işletmeler" gibi kanıtlanmamış bir iddiaya dönüştürülemez.
