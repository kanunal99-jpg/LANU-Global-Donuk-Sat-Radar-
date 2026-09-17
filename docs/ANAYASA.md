# LANU Global Donuk Satış Radarı — ANAYASA

## 1. Amaç
Bu proje, HORECA satış ekiplerinin gerçek ve kaynaklandırılmış işletme verileri üzerinden şehir/ilçe/mahalle bazında müşteri keşfi, saha planlama, satış fırsatı analizi ve CRM takibi yapmasını sağlar.

## 2. Coğrafya
Mimari İstanbul'a sabitlenemez. Hiyerarşi: ülke → şehir → ilçe → mahalle → işletme. İlk aktif şehir İstanbul'dur. Yeni şehir eklemek kod değişikliği gerektirmemelidir.

## 3. Veri doğruluğu
- Gerçek, tahmini ve kullanıcı tarafından girilmiş veri birbirinden ayrılır.
- Kaynağı olmayan bilgi kesin gerçek gibi gösterilmez.
- Çalışan sayısı, ciro, satış potansiyeli ve karar verici bilgisi doğrulanmadıysa tahmin/güven seviyesi ile gösterilir.
- Her kritik veri için kaynak ve mümkünse son doğrulama zamanı tutulur.
- Uydurma işletme, telefon, çalışan sayısı, fiyat, trafik veya satış rakamı üretilmez.

## 4. Satış potansiyeli
Potansiyel skor şeffaf kriterlerden hesaplanır. Skor bir tahmindir; kesin sipariş veya ciro garantisi değildir. Ürün eşleşmesi Global Donuk Gıda'nın gerçek ürün kataloğuna dayanır.

## 5. Kritik yol dayanıklılığı
Kritik servislerde tek bağımlılık olmayacaktır: ana kaynak → alternatif → yerel/cache fallback → hata yönetimi → güvenli varsayılan → log/izleme → smoke test.

## 6. Offline-first
İşletme kayıtları, ziyaretler, notlar ve saha CRM işlemleri ağ olmadan çalışabilmelidir. Senkronizasyon başarısız olduğunda yerel veri kaybolmaz.

## 7. Gizlilik ve güvenlik
Gereksiz kişisel veri toplanmaz. Hassas erişim bilgileri kaynak koda gömülmez. API anahtarları APK içine sabit yazılmaz. KVKK ilkeleri gözetilir.

## 8. Harita ve veri kaynakları
Harita/işletme verileri lisans ve kullanım koşullarına uygun kaynaklardan alınır. Bir kaynağın kullanım şartlarını ihlal edecek toplu scraping temel mimari haline getirilemez.

## 9. Ürün ve satış koçluğu
Uygulama; işletme profili, menü sinyalleri ve operasyonel ihtiyaçlardan hareketle yaklaşım önerir. Öneri gerçek bilgi ile karıştırılmaz.

## 10. Test zorunluluğu
Yeni kritik özellik için unit/integration/UI veya smoke testi uygun seviyede eklenir. CI başarısızken özellik tamamlanmış sayılmaz.

## 11. Release doğrulaması
APK yalnızca gerçek GitHub Actions build'i başarılı olduktan, artifact oluştuğu ve SHA doğrulandığı zaman yayınlanmış kabul edilir.

## 12. Kod kalitesi
Üretim kodunda geçici mock, sahte başarı, hard-coded müşteri verisi ve sessiz hata kabul edilmez. TODO bırakmak yerine açık issue/plan oluşturulur.

## 13. Anayasa ↔ kod
Anayasadaki kritik kurallar dokümantasyon olarak kalmayacak; mümkün olanlar test, lint, CI veya runtime guard ile doğrulanacaktır. Anayasa değişiklikleri gerekçeli commit ile yapılır.

## 14. Değişmez ilke
"Çalışıyor" iddiası yalnızca gerçek kod, gerçek test/CI kaydı ve gerekiyorsa gerçek artifact kanıtı ile yapılır.

## 15. Başarılı CI sonrası APK yayınlama — ZORUNLU
- `main` dalındaki her başarılı Android CI build'i, oluşturduğu doğrulanmış APK'yı GitHub Release içindeki `latest` yayınına otomatik olarak yükler.
- APK yalnızca build ve gerekli yayınlama adımları yeşil olduğunda güncel APK olarak kabul edilir.
- Güncel APK'nın sabit dosya adı: `Lanu-Global-Donuk-Satis-Radari-latest.apk`.
- Güncel SHA-256 dosyası birlikte yayınlanır: `Lanu-Global-Donuk-Satis-Radari-latest.apk.sha256`.
- `latest` Release etiketi, yayınlanan APK'nın üretildiği başarılı commit'i göstermelidir.
- Başarısız, iptal edilmiş veya doğrulanmamış bir build mevcut `latest` APK'nın yerine geçemez.
- README içinde güncel APK için sabit indirme bağlantısı bulunmalıdır.
- APK, Git geçmişine her build'de tekrar tekrar ikili dosya olarak commit edilmez; GitHub Release asset'i dağıtım yüzeyidir.

## 16. Durum ve yol haritası doğruluğu
- Gerçekleşmiş özellikler ile planlanan/istenen özellikler ayrı tutulur.
- Planlanan özellikler tamamlanmış gibi gösterilemez.
- Proje durumu, CI/release kanıtı ve ilgili commitlerle ilişkilendirilir.
