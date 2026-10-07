# LANU Global Donuk Satış Radarı — ANAYASA

## 1. Amaç
Bu proje; Türkiye genelindeki sektör bağımsız işletmeleri ve potansiyel satış noktalarını gerçek, kaynaklandırılmış ve izlenebilir verilerle keşfetmek; şehir/ilçe/mahalle/nokta düzeyinde analiz etmek; saha planlama, harita, CRM, teklif, sipariş ve satış takibini tek üründe yürütmek için geliştirilir. HORECA, FMCG, bayi, distribütör, market, restoran ve benzeri gruplar ana sistemin alt segmentleridir; ürün yalnızca HORECA ile sınırlandırılamaz.

## 2. Coğrafya
Mimari İstanbul'a sabitlenemez. Hiyerarşi: ülke → il → ilçe → mahalle → işletme/nokta.
- Türkiye'nin 81 ili yerel kanonik katalogda bulunmalıdır.
- İlçe kataloğu 973 idari ilçe kapsamını korur; 922 kaymakamlık + 51 "Merkez" ilçe ayrımı testle doğrulanır.
- İstanbul 39 ilçedir: Anadolu Yakası 14, Avrupa Yakası 25; listeler kesişemez.
- Çevrimiçi idari kaynak yerel kanonik listeyle tam eşleşmiyorsa kullanıcıya karışık/eksik liste gösterilmez; doğrulanmış yerel fallback kullanılır.
- İl/ilçe kataloğunun tam olması, işletme arama sonucunun o bölgedeki bütün işletmeleri kapsadığı anlamına gelmez. Kaynak kapsamı ayrıca belirtilir.
- Yeni il veya veri kaynağı desteği, ana navigasyon ve veri modelini yeniden tasarlamayı gerektirmemelidir.

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


## 21. KATMANLI DAYANIKLILIK — DEĞİŞMEZ PROJE STANDARDI
Her kritik zincir şu sırayı uygular:

`ANA SERVİS → ALTERNATİF → GERÇEK FALLBACK → HATA YÖNETİMİ → GÜVENLİ VARSAYILAN → LOG/İZLEME → SMOKE TEST`

Bu kural özellikle AI/LLM, harici API, veritabanı, ödeme/kota, görsel analiz, dosya/storage, authentication, backend↔frontend, APK↔backend ve deploy/production zincirlerinde zorunludur. Fallback yalnız teorik kod yolu değil, test edilmiş çalışan yol olmalıdır.

## 22. Maliyet kontrolü — ZORUNLU
- Öncelik ücretsiz, açık kaynak ve yerel çözümlerdedir; ardından uygun free-tier kullanılır.
- Kullanıcının açık onayı olmadan ücret doğuran abonelik, API, altyapı, veri paketi veya servis başlatılamaz.
- Ücretsiz çözüm daha düşük kapsam sağlıyorsa fark açıkça raporlanır; kapsam uydurulmaz.
- Ücretli seçenek yalnız maliyet, kapsam, kullanım hakkı ve teknik gereksinimler doğrulandıktan sonra önerilebilir.

## 23. Girdi güvenliği ve veri sözleşmesi
Tüm kullanıcı, dosya, API ve dış kaynak girdilerinde uygun seviyede:
`validate → sanitize → normalize → log`
uygulanır.
- İl/ilçe, koordinat, telefon, VKN/TCKN, URL, fiyat, para birimi, dosya MIME/boyut ve kimlik alanları doğrulanır.
- Hatalı veri sessizce kalıcı kayda alınmaz.
- Tabela/nokta adı ile ticari unvan aynı alan kabul edilemez.
- İlan olayı ile şirketin bugünkü durumu ayrı tutulur.
- Ticaret Sicili ilan sayısı işletme sayısı olarak kullanılamaz.

## 24. Performans ve kullanıcı deneyimi kapısı — ZORUNLU
- Ana sekme değişimi ağır veri analizi, rota hesabı, duplicate taraması, ağ çağrısı veya dosya işlemini UI thread üzerinde bekleyemez.
- Ekran önce çizilir; büyük hesaplar arka planda, lazy/on-demand veya parça parça yapılır.
- Binlerce kayıt için O(n²) yaklaşım varsa ölçeklenebilir algoritma/fallback zorunludur.
- Büyük listeler LazyColumn/LazyRow veya eşdeğer sanallaştırılmış bileşen kullanır.
- Harita binlerce marker'ı doğrudan yükleyemez; clustering/chunking ve görünür alan stratejisi kullanılır.
- Kullanıcının ana navigasyon tuşuna basması sonucu görünür ekran makul cihaz/emülatör kabul testinde gecikmeden açılmalıdır; performans regresyonu release engelidir.
- Yükleme gerekiyorsa boş/donmuş ekran yerine açık durum mesajı gösterilir.
- Kullanıcı başka sekmeye geçtiğinde artık gerekli olmayan ağır iş iptal edilebilir olmalıdır.

## 25. Kurumsal UI/UX standardı
- Alt navigasyon ve ana CRM sekmeleri tek satır, kısa ve tutarlı isimler kullanır; kritik etiketler dar ekranda anlamsız şekilde bölünemez.
- Gereksiz büyük boşluk, gereksiz kart/padding ve ekranda değer üretmeyen dikey alan bırakılmaz.
- Birincil görevler ilk ekranda görünür; ikincil/gelişmiş filtreler gerektiğinde açılır.
- Formlar mantıksal gruplara ayrılır, zorunlu/opsiyonel alanlar açıkça belirtilir.
- Renk, tipografi, köşe, boşluk ve buton hiyerarşisi LANU kurumsal temasına bağlı kalır.
- Erişilebilirlik ve büyük yazı ölçeği korunur; bunun için kritik navigasyon etiketleri kısaltılır, bilgi kaybettirecek zorla küçültme yapılmaz.
- UI değişiklikleri ekran görüntüsü/Compose smoke ile kontrol edilir.

## 26. Yayın disiplini — HER ÖNEMLİ SÜRÜM
- Bitirilen özellik önce test edilir; test doğruysa main'e alınır.
- Main CI tamamen yeşil olmadan release güncellenmez.
- Android içeren her önemli yayınlanabilir sürümün APK'sı GitHub Releases/Yayınlar altında cihazdan doğrudan indirilebilir olmalıdır.
- APK'nın yalnız build edilmiş olması yeterli değildir: release'e eklenmiş, indirilebilir ve SHA/build-info ile doğrulanmış olmalıdır.
- Başarısız yeni build eski çalışan release'i bozamaz.
- Release commit'i ile APK build-info commit'i aynı olmalıdır.

## 27. Tamamlama tanımı
Bir iş yalnız kod yazıldığı için bitmiş sayılmaz. Tamamlama döngüsü:
`Araştır → kök nedeni bul → düzelt → uygula → build → test → doğrula → deploy → production/smoke test → kanıtla`

Kritik hata görülürse bir sonraki özelliğe geçmeden önce kök neden giderilir. Ürün çıktısı hazır olana kadar bu döngü sürdürülür.
