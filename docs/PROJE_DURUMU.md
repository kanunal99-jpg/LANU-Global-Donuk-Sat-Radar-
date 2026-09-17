# LANU Global Donuk Satış Radarı — Proje Durumu ve Yol Haritası

> Bu belge gerçekleşen işleri ve henüz planlanan/istenen işleri birbirinden ayırır. Planlanan maddeler tamamlanmış özellik olarak kabul edilmez.

## 1. Projenin amacı

LANU Global Donuk Satış Radarı; Global Donuk Gıda'nın HORECA satış faaliyetleri için gerçek ve kaynaklandırılmış işletme keşfi, saha planlama, satış fırsatı analizi, müşteri takibi ve satış yönetimi sağlayan şehir bağımsız bir Android + web/dashboard ekosistemi olarak geliştirilecektir.

İlk aktif coğrafya İstanbul'dur. Mimari ülke → şehir → ilçe → mahalle → işletme hiyerarşisini destekleyecek şekilde hazırlanır.

## 2. Gerçekleşen işler

### Proje temeli
- Android proje iskeleti ve Gradle/CI yapısı mevcut.
- Proje adı: `Lanu Global Donuk Satış Radarı`.
- GitHub ana dalı: `main`.
- Proje anayasası `docs/ANAYASA.md` altında tutuluyor.
- Kaynak gerçekliği kuralları yazılı hale getirildi.

### CI ve build düzeltmeleri
- Java/Kotlin JVM hedef uyumsuzluğu giderildi.
- Java 17 hedefi standartlaştırıldı.
- Kotlin 2.x ile Compose Compiler yapılandırması düzeltildi.
- Kotlin Compose plugin uygulandı ve Compose build özelliği etkinleştirildi.
- Material 3 deneysel API kullanımındaki CI problemi giderildi.
- `gradle assembleDebug` başarılı şekilde çalışıyor.

### APK dağıtımı
- GitHub Actions başarılı Android build üretiyor.
- APK artifact olarak yükleniyor.
- APK için SHA-256 hesaplanıyor.
- Başarılı `main` CI sonrası `Latest APK` GitHub Release otomatik güncelleniyor.
- Sabit APK dosyası: `Lanu-Global-Donuk-Satis-Radari-latest.apk`.
- Sabit SHA dosyası: `Lanu-Global-Donuk-Satis-Radari-latest.apk.sha256`.
- README içinde sabit APK indirme bağlantısı bulunuyor.

### Veri gerçekliği — yeni temel katman
- `DataQuality` ile doğrulanmış resmi, doğrulanmış harici, tahmini, kullanıcı girişi, eski ve doğrulanmamış veri ayrımı tanımlandı.
- `BusinessSourceContract` ile kaynak kapsamı, erişim yöntemi, kullanım izni, toplu erişim ve alan listesi uygulama öncesi sözleşmeye bağlandı.
- Kaynak kullanım izni doğrulanmadan üretim bağlantısı kurulmasını engelleyen validation eklendi.
- `BusinessSourceAdapter` dış kaynak ile uygulama domain'i arasına ingestion sınırı olarak eklendi.
- Adapter'dan gelen kayıtlar domain'e alınmadan `VerifiedBusinessValidator` üzerinden savunmacı biçimde doğrulanıyor; geçersiz kayıtlar güvenli şekilde eleniyor.
- `BusinessRepositoryFactory` henüz doğrulanmış ve yapılandırılmış gerçek kaynak bulunmadığı için güvenli boş repository döndürüyor.
- Böylece kaynak erişimi hazır değilken uygulamanın sahte işletme verisi göstermesi engellenmiş oldu.

### Kaynak adayları
- İTO Bilgi Bankası ve Türkiye Ticaret Sicili Gazetesi için resmi kaynak/erişim doğrulama kayıtları `docs/VERI_KAYNAKLARI.md` altında tutuluyor.
- Bu kaynaklar henüz kullanım şartları ve erişim yöntemi üretim entegrasyonu için doğrulanmadığından uygulamaya işletme kaydı aktarılmıyor.

## 3. Kullanıcı tarafından istenen ürün kapsamı

### HORECA müşteri keşfi
- İstanbul ile başlama.
- Şehir değiştirme / şehir ekleme.
- İlçe ve mahalle filtreleri.
- Harita + liste görünümü.
- Gerçek potansiyel HORECA işletmelerini kaynaklandırılmış verilerle göstermek.
- İşletmeye dokununca detaylı rapor açmak.

### İşletme detay raporu
Her işletme için mümkün olan alanlar kaynak durumu ile birlikte gösterilecek:
- İşletme adı ve türü.
- Adres / şehir / ilçe / mahalle.
- Telefon, web ve diğer iletişim kanalları.
- Harita konumu.
- Kaynak ve son doğrulama tarihi.
- Çalışan sayısı: gerçekse kaynaklı, değilse tahmin olarak etiketli.
- Satış potansiyeli: kriterleri açıklanmış tahmin.
- Global Donuk ürün eşleşmeleri.
- İşletmeye yaklaşım / satış konuşması önerileri.
- Arama, WhatsApp, navigasyon ve web aksiyonları.

### Saha CRM akışı
- Potansiyel müşteri oluşturma.
- Ziyaret planlama ve kaydı.
- Görüşme sonucu.
- Notlar.
- Numune süreci.
- Teklif oluşturma/takip.
- Sipariş takibi.
- Aktif müşteri / kaybedilen müşteri durumu.
- Senkronizasyon başarısız olduğunda yerel veri kaybını önleme.

### Satış hunisi
`Potansiyel → Ziyaret → Görüşme → Teklif → Numune → Sipariş → Aktif Müşteri`

- Huni aşamaları dashboard'da tıklanabilir olacak.
- Bir aşamaya tıklanınca o aşamadaki işletmeler listelenecek.

### Dashboard / Power BI benzeri görünüm
Bilgisayar gerektirmeden telefondan kullanılabilecek web/mobil dashboard hedefleniyor.

Dashboard kapsamı:
- Toplam müşteri.
- Potansiyel müşteri.
- Ziyaretler.
- Aramalar.
- Açık teklifler.
- Siparişler.
- Satış toplamı.
- Dönüşüm oranları.
- Gün/hafta/ay karşılaştırması.
- Hedef/gerçekleşen.
- Trendler.
- Müşteri başına satış.
- Aktif/prospect/won/lost müşteri analizi.
- İlçe/mahalle yoğunluk analizi.
- Harita üzerinde fırsat dağılımı.
- İlçe → mahalle → işletme drill-down.

### Veri ve backend hedefi
- Telefon-first mimari.
- Android uygulama + bulut veri katmanı + mobil web dashboard.
- Uygun bir aşamada Supabase benzeri yönetilebilir backend kullanılabilir.
- Gerçek veri kaynakları ve kullanım lisansları doğrulanacak.
- Uydurma müşteri, çalışan, satış, ciro, fiyat veya trafik verisi kullanılmayacak.

### Satış koçluğu / yapay zeka hedefi
- İşletme türü, menü sinyalleri ve doğrulanmış operasyonel ihtiyaçlardan hareketle satış yaklaşımı önerileri.
- Ürün eşleştirme.
- Görüşme için soru önerileri.
- Teklif sonrası takip önerileri.
- Yapay zeka çıktısı kaynak gerçeklerinin yerine geçmeyecek.

## 4. Henüz tamamlanmış kabul edilmeyenler

Aşağıdakiler planlanan/istenen kapsamdır; kod ve CI kanıtı oluşmadan tamamlanmış sayılmaz:

- Gerçek işletme veri kaynaklarının üretim entegrasyonu ve erişim/lisans onayı.
- İstanbul ilçe/mahalle bazlı doğrulanmış HORECA işletme veri seti.
- Harita üzerinde canlı işletme keşfi.
- İşletme detay raporunun tam veri modeli ve ekranları.
- Saha CRM ekranlarının tamamı.
- Ziyaret/teklif/numune/sipariş veri modelinin kalıcı backend ile tamamlanması.
- Dashboard veri pipeline'ı ve drill-down ekranları.
- Hedef/gerçekleşen satış analitiği.
- Gerçek Global Donuk ürün kataloğu ile ürün eşleştirme.
- Offline-first senkronizasyon ve conflict yönetiminin üretim seviyesinde tamamlanması.
- Kaynak doğrulama, deduplikasyon ve veri kalite kontrollerinin genişletilmesi.
- Kritik akışların unit/integration/UI/smoke test kapsamının artırılması.
- Release APK'nın gerçek cihaz üzerinde kurulum ve temel saha akışlarının doğrulanması.

## 5. Geliştirme kuralı

Her yeni özellik şu sırayı izler:

`Kaynak/veri tasarımı → kod → uygun test → CI → başarılı build → doğrulama → release → durum belgesini güncelleme`

Bir özellik CI başarısızken tamamlanmış sayılmaz.

## 6. Kaynak gerçekliği standardı

- Gerçek veri, tahmin ve kullanıcı girişi ayrı etiketlenir.
- Her kritik veri mümkünse kaynağı ve son doğrulama zamanı ile tutulur.
- Kaynaksız kritik veri gerçekmiş gibi yayınlanmaz.
- Veri sağlayıcının şartlarını ihlal eden toplu scraping mimarinin temeli yapılamaz.
