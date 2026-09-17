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

## 17. Denetim eksikleri — ZORUNLU ÖDEV LİSTESİ
Aşağıdaki eksikler denetimde tespit edilmiş olup tamamlanmadan ürün tamamlanmış kabul edilmez:
- Gerçek işletme veri kaynağı entegrasyonu ve kaynak/lisans doğrulaması.
- Ülke → şehir → ilçe → mahalle → işletme veri modeli ve İstanbul'un doğrulanmış ilçe/mahalle kapsamı.
- Gerçek HORECA işletme keşfi; arama, filtreleme, liste ve harita akışı.
- İşletme detay raporu ve kaynak/son doğrulama metadata'sı.
- Gerçek Global Donuk ürün kataloğu ve ürün-işletme eşleştirmesi.
- Saha CRM: müşteri, ziyaret, görüşme, not, numune, teklif, sipariş, aktif/kayıp durumları.
- Kalıcı backend, kimlik doğrulama, yetkilendirme ve güvenli veri erişimi.
- Offline-first yerel veri katmanı, kuyruklu senkronizasyon ve conflict çözümü.
- Dashboard gerçek veri pipeline'ı, KPI hesapları, dönem karşılaştırması ve drill-down.
- Satış hunisinin gerçek CRM durumlarına bağlanması.
- İlçe → mahalle → işletme drill-down ve fırsat yoğunluk analizi.
- AI satış koçluğu yalnızca doğrulanmış veriye dayalı ve kaynak gerçeklerinden ayrı olacak şekilde uygulanması.
- Kaynak doğrulama, deduplikasyon, veri kalite ve stale-data kontrolleri.
- Kritik akışlar için unit/integration/UI/smoke testleri.
- Gerçek cihaz kurulumu, offline/online geçişi, saha akışları ve release APK doğrulaması.

## 18. Hatalı işe geçişi engelleyen kapılar — ZORUNLU
- Kaynak doğrulanmadan gerçek müşteri/işletme verisi ürün ekranına alınamaz.
- Veri modeli olmadan UI'da sahte veya hard-coded işletme/KPI gösterilemez.
- Gerçek veri kaynağı ve lisans şartları incelenmeden scraping veya harita entegrasyonu yapılamaz.
- Kaynağı ve doğrulama zamanı olmayan kritik veri gerçekmiş gibi sunulamaz.
- Tahmin, kullanıcı girdisi ve doğrulanmış gerçek veri aynı alan/etiket altında birleştirilemez.
- Backend güvenliği kurulmadan üretim müşteri verisi kullanılmaz.
- Offline veri güvenliği kurulmadan saha CRM'si tamamlandı kabul edilmez.
- Senkronizasyon ve conflict testi olmadan offline-first tamamlandı denilemez.
- Dashboard veri pipeline'ı olmadan KPI, dönüşüm veya satış rakamı gösterilemez.
- Funnel aşamaları gerçek CRM durumlarına bağlanmadan funnel tamamlandı sayılamaz.
- Gerçek ürün kataloğu doğrulanmadan ürün önerisi/eşleştirmesi yayınlanamaz.
- AI, doğrulanmış katalog ve işletme verisi dışında gerçekmiş gibi bilgi üretemez; doğrulanmış veri yoksa güvenli boş sonuç vermelidir.
- Kritik özellik için uygun test yoksa özellik tamamlanmış sayılmaz.
- CI kırmızıysa merge/release ve “tamamlandı” statüsü yoktur.
- Release artifact ve SHA doğrulanmadan APK yayınlanmış kabul edilmez.
- Gerçek cihazda temel kritik akışlar doğrulanmadan saha kullanıma hazır denilemez.
- Hata sessizce yutulamaz; kullanıcıya güvenli durum gösterilmeli ve teknik hata loglanmalıdır.
- Başarısız ana kaynakta alternatif/fallback zinciri doğrulanmadan kritik servis tek kaynağa bağımlı bırakılamaz.

## 19. Denetim ve görev yürütme standardı
- Her ödev maddesi kod → test → CI → doğrulama kanıtı ile kapatılır.
- Sadece dokümana madde eklemek veya UI çizmek ödevi tamamlamaz.
- Her kapatılan madde için ilgili commit, test/CI sonucu ve gerekiyorsa APK artifact'i kayıt altına alınır.
- Bir maddede hata tespit edilirse sonraki katmana geçilmez; hata düzeltilir, test edilir ve yeniden CI doğrulaması yapılır.
- Hata oluşturan değişiklik “geçici olarak çalışıyor” kabul edilmez; kök neden giderilir.
- Denetimde bulunan eksik veya riskli iş, tamamlandı olarak işaretlenemez.
- Yeni özellik geliştirmeden önce mevcut denetim ödevlerinin durumu güncellenir.

## 20. Geliştirme sırası — ZORUNLU
`kaynak/lisans → veri modeli → veri doğrulama → repository/backend → güvenlik → UI → hata/fallback → test → CI → gerçek cihaz → release → durum güncellemesi`

Bu sırayı atlayarak üst katmanda geliştirme yapmak, sonraki aşamalarda hatayı büyütme riski taşıdığı için kabul edilmez.
