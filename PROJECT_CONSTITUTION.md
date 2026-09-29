# LANU Global Donuk Satış Radarı — Proje Anayasası

> Bu belge LANU Global Donuk Satış Radarı projesinin kalıcı teknik ve teslim kurallarının tek referansıdır. Kod, PR, Issue, CI/CD, Supabase, Android ve Release kararları bu kurallara göre yürütülür.

## 0. ANAYASA UYGULAMA ZORUNLULUĞU — İSTİSNASIZ

**Her zaman, her işlem bu anayasaya yazılacak ve anayasa esas alınacaktır.**

Her geliştirme oturumunda veya önemli işlem öncesinde bu belge **baştan sona tek tek okunacak** ve işlem kurallarla karşılaştırılacaktır.

Her problem; kök nedeni, araştırması, ayıklanan seçenekleri, uygulanan çözümü, test/onay/doğrulama sonucu ve kanıtıyla kalıcı olarak kaydedilecektir.

Zorunlu problem çözme döngüsü:

**ARAŞTIR → AYIKLA → DÜZELT → ONAYLA → DOĞRULA → KANITLA**

Her teknik problem için **en az 20 farklı bağımsız site/domain ve toplam en az 40 ilgili teknik forum/tartışma/dokümantasyon kaynağı** araştırılacaktır. Alakasız, kopya veya düşük kaliteli kaynaklarla sayı doldurulmaz.

**Bu anayasadaki her kural tek tek uygulanacak ve istisnasız uyulacaktır.** Uygulanamayan kural sessizce atlanmaz; engel, neden, güvenli fallback ve gereken müdahale kaydedilir.

### Kesintisiz çalışma ve teslim zorunluluğu

**Ürün tam teslim olana kadar durmadan çalış.**

Çalışma; yalnız durum kontrolü, CI bekleme veya ara raporla tamamlanmış sayılmaz. Güvenle yapılabilecek bir sonraki iş varsa doğrudan ona geçilir. Problem çıkarsa kök nedene inilerek anayasanın araştırma ve doğrulama döngüsü uygulanır. Kullanıcı müdahalesi, erişim, maliyet onayı veya dış sistem bekleme zorunluluğu gibi gerçek bir blokaj oluşursa blokaj ve güvenli sonraki adım kaydedilir; blokajsız işler devam ettirilir.

“Tam teslim”; kritik özelliklerin tamamlanması, profesyonel UI/UX, kurumsal CRM, Room/offline ve Supabase/cloud dayanıklılığı, testlerin tamamı, PR ve main CI'nin tamamen yeşil olması, main merge, GitHub Release APK, asset indirilebilirliği ve commit/hash eşleşmesinin kanıtlanması demektir.

### TEST/CI BEKLERKEN SÜREKLİ KOD DENETİMİ — BOŞ BEKLEME YASAK

Test, build, instrumentation, Android emulator smoke, GitHub Actions/CI veya başka bir doğrulama işlemi çalışırken **boşta beklenmeyecektir**. Bekleme süresi projenin ikinci bir sürekli kalite kontrol hattı olarak kullanılacaktır.

Zorunlu çalışma şekli:

1. Test/CI başlatıldıktan sonra güvenli biçimde incelenebilen kaynak kod ve proje yapısı tek tek denetlenmeye devam edilir.
2. Denetim rastgele yapılmaz; klasör/modül/dosya sırası izlenir.
3. Kontrolün hangi klasör, modül, dosya veya kod zincirinde kaldığı kalıcı çalışma notuna yazılır.
4. Test sonucu geldiğinde gerekiyorsa test sonucu önceliklendirilir; fakat kod denetimindeki son konum kaybedilmez.
5. Bir sonraki test/CI bekleme penceresinde denetim **tam olarak kaldığı yerden devam eder**.
6. Denetimde hata, eksik özellik, duplicate kaynak, güvenlik açığı, veri kaybı riski, UX problemi, yanlış state yönetimi, hatalı fallback veya test açığı bulunursa problem anında kaydedilir.
7. Güvenle düzeltilebilen hata bekletilmez; **ARAŞTIR → AYIKLA → DÜZELT → ONAYLA → DOĞRULA → KANITLA** döngüsüyle düzeltilir ve uygun test kapsamına alınır.
8. Yeni düzeltme mevcut çalışan CI sonucunu geçersiz kılacak yeni commit oluşturuyorsa eski yeşil sonuç yeni HEAD için kanıt kabul edilmez; güncel commit yeniden test edilir.
9. Bu yöntem repository kaynak ağacı tamamıyla sistematik biçimde incelenene ve kritik zincirler tekrar doğrulanana kadar sürdürülür.
10. Amaç test hattı ile kod denetimini paralel ilerleterek **sürekli kontrol mekanizması** oluşturmaktır; CI bekleme süresi çalışma durması anlamına gelmez.

Kontrol sırası ve kaldığı nokta mümkün olduğunda şu formatta kaydedilir: `AUDIT_CHECKPOINT: <modül/klasör/dosya veya zincir> — <son kontrol edilen nokta> — <sonraki adım>`.

### DERİN ARAŞTIRMA SONUÇLARINI SÜREKLİ DOĞRULAMA VE UYGULAMA — HER TEST PENCERESİNDE ZORUNLU

Derin araştırma, forum/dokümantasyon taraması veya önceki teknik analiz **tek başına gerçek kabul kanıtı değildir**. Her test/build/CI bekleme penceresinde mevcut araştırma sonuçları güncel proje gerçeği ile yeniden çapraz doğrulanacaktır.

Zorunlu doğrulama hattı:

**ARAŞTIRMA BULGUSU → EXACT HEAD → GERÇEK KOD → WORKFLOW/CI → LOG/ARTIFACT → SUPABASE/PRODUCTION DURUMU → KÖK NEDEN → DÜZELT → UYGULA → YENİDEN TEST → KANITLA**

Kurallar:

1. Her test sonucu beklenirken mevcut derin araştırma bulguları güncel exact HEAD ile karşılaştırılır; eski commit, eski workflow veya eski log güncel HEAD için kanıt sayılmaz.
2. Araştırmadaki her kritik varsayım gerçek call-site, gerçek dosya, gerçek workflow, gerçek backend ayarı, log veya artifact ile doğrulanır. Doğrulanamayan varsayım “kesin kök neden” olarak yazılmaz.
3. Repo veya production gerçekliği araştırma raporundan daha yeni ise rapor revize edilir; eski analiz kutsal kabul edilmez.
4. Bir hata bulunursa yalnız raporlanmaz. Güvenli ve yetki kapsamındaki düzeltme doğrudan uygulanır, yeni commit oluşturulur ve ilgili test zinciri yeniden başlatılır.
5. Düzeltme production güvenliğini zayıflatarak yapılamaz. Özellikle Auth/RLS için testi yeşile çevirmek adına anonymous auth açmak, RLS kapatmak, service_role/secret anahtarını mobil uygulamaya veya güvensiz workflow alanına koymak yasaktır.
6. Kimlik doğrulama testlerinde mümkünse kısa ömürlü, otomatik temizlenen ve üretim güvenlik duruşunu bozmayan yöntemler tercih edilir. GitHub Actions için OIDC tabanlı yetkilendirme, broker veya eşdeğer güvenli mekanizma; statik yüksek yetkili sırları workflow'a koymaya tercih edilir.
7. Supabase doğrulamasında Auth → gerçek session → Data API → RLS → iki kullanıcı negatif izolasyon → RPC/idempotency/conflict → refresh/reconnect → API cleanup → geçici auth kullanıcı cleanup zinciri gerçek backend üzerinde test edilir.
8. Test scripti ve workflow secret/token değerlerini loglamaz. Hassas veri yalnız güvenli secret mekanizmasında tutulur; publishable/public key ile service_role/secret birbirine karıştırılmaz.
9. CI beklerken aynı anda supply-chain ve workflow güvenliği de denetlenir. GitHub Actions mümkün olduğunda immutable commit SHA ile pinlenir; kullanılan third-party action sürümleri, izinleri ve minimum permission ilkesi kontrol edilir.
10. Yeni düzeltme ile HEAD değişirse önceki yeşil testler otomatik olarak tarihsel kanıta düşer; yeni HEAD için tüm ilgili kalite kapıları yeniden çalıştırılır.
11. Test sonucu başarısızsa artifact/log doğrudan incelenir; semptom ile kök neden ayrılır. Aynı başarısız komut 2–3 kez kör biçimde tekrarlanmaz; yaklaşım yeniden değerlendirilir.
12. Test sonucu başarılıysa yalnız “green” etiketiyle yetinilmez; başarının gerçekten hedeflenen kabul zincirini çalıştırdığı doğrulanır. Mock, bypass, yanlış branch veya yanlış backend üzerinde yeşil test kabul edilmez.
13. Her bekleme penceresinde kontrol edilen araştırma maddesi ve kalan sonraki adım mümkün olduğunda şu formatta kaydedilir: `RESEARCH_VERIFY_CHECKPOINT: <bulgu> — <kanıt> — <durum> — <sonraki doğrulama>`.
14. Kullanıcı ayrıca “devam” demese bile, test bekleme süresi boyunca bu doğrulama/düzeltme döngüsü sürdürülür; gerçek blokaj yoksa çalışma durmaz.

#### 29 Eylül 2026 doğrulanmış Live Cloud E2E örneği — kalıcı ders

Önceki Live Cloud E2E başarısızlığında görülen `HTTP 422 / Anonymous sign-ins are disabled` semptomu gerçek log ve artifact ile doğrulandı. Doğru çözüm production anonymous auth'u açmak olmadı. Güncel yaklaşımda GitHub Actions OIDC token'ı ile yetkilendirilen `lanu-ci-auth-broker` üzerinden iki geçici doğrulanmış kullanıcı/session oluşturulmakta; test sonunda geçici kullanıcılar silinmektedir.

Bu olaydan çıkarılan kalıcı kurallar:

- CI testini geçirmek için production Auth güvenliği gevşetilmez.
- İki gerçek authenticated aktör ile owner/RLS izolasyonu doğrulanır.
- Versioned mutation RPC üzerinde APPLIED/idempotent retry/CONFLICT davranışı test edilir.
- Refresh token ile reconnect doğrulanır.
- Data API kayıtları ve geçici Auth kullanıcıları cleanup ile sıfırlanır.
- Başarılı workflow mutlaka çalıştığı exact HEAD SHA ile eşleştirilir.
- Araştırma raporundaki “henüz doğrulanmadı” gibi bir bulgu repo gerçeği değiştiğinde yeniden doğrulanır ve güncel kanıta göre revize edilir.

### Gerçek takvim hedefi — 29 Eylül 2026 12:00 Türkiye saati

Kullanıcının belirlediği operasyonel hedef: **29 Eylül 2026 saat 12:00 (Türkiye saati, UTC+3)** itibarıyla ürünün tam teslim kriterlerini tamamlamış olmaktır.

Bu saat bir **hedef teslim zamanıdır**, kalite veya güvenlik kapılarını atlama izni değildir. Saat yaklaşsa veya geçse dahi kırık CI merge edilmez, doğrulanmamış test başarılı sayılmaz, gerçek cihaz/emulator sonucu uydurulmaz, RLS doğrulanmadan güvenli denmez ve commit/hash eşleşmesi doğrulanmadan APK %100 teslim ilan edilmez. Hedefe yetişmek için test/CI bekleme zamanları yukarıdaki sürekli kod denetimiyle aktif kullanılacaktır.

### KANONİK PROJE ENVANTERİ VE TESLİM SÖZLEŞMESİ — HER ZAMAN OKUNACAK, ASLA ATLANMAYACAK

Bu bölüm proje üzerinde herhangi bir kod, dosya, branch, backend, test, CI veya Release işlemi yapılmadan önce okunacak ve doğrulanacaktır. Buradaki kimlikler ve hedefler kullanıcı tarafından belirlenen proje bağlamıdır. Yanlış repo, yanlış Supabase veya yanlış kaynak ağacı üzerinde işlem yapılmayacaktır.

#### Ne istiyoruz?

LANU Global Donuk Satış Radarı; Android cihazlarda çalışan, profesyonel, kurumsal, hızlı, güvenilir, offline/bulut dayanıklı ve gerçek saha satış kullanımına uygun **tam çalışan APK uygulaması** olarak teslim edilecektir. Kullanıcı arayüzü yalnız işlevsel bırakılmayacak; Radar, Dashboard, CRM, formlar, filtreler, müşteri detayları ve ticari akışlar profesyonel ve tutarlı UI/UX seviyesine getirilecektir.

**Hedef: kabul kriterlerinin %100'ü kanıtlanmış profesyonel kurumsal Android APK teslimidir.** “%100” ifadesi tahmini ilerleme yüzdesi değildir; bu anayasadaki tüm kritik kabul kapılarının gerçekten tamamlanıp kanıtlanması anlamına gelir.

#### GitHub repo adı — KANONİK

- Repository: **`kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-`**
- Proje GitHub işlemleri bu repository üzerinde yapılacaktır.
- Aktif PR/branch/main ilişkisi her işlem öncesi doğrulanacaktır.
- Başka benzer isimli repository proje kaynağı kabul edilmeyecektir.

#### Proje adı — KANONİK

- **LANU Global Donuk Satış Radarı**
- Ürün bağlamı: Global Donuk Gıda saha satış radarı + kurumsal CRM + ticari satış akışları.

#### Supabase bağlantısı — KULLANICI TARAFINDAN ONAYLI

Kullanıcı tarafından açıkça onaylanan kanonik Supabase backend:

- Supabase mevcut görünen proje adı: **Willy-Kilo-Takip**
- LANU hedef/işlevsel adı: **LANU GLOBAL / LANU Global Radar Supabase**
- Project ref: **`jolfbmwxmsamzqtxassg`**
- Bölge: **eu-central-1**
- Durum: **LANU için kullanıcı tarafından ONAYLI kanonik Supabase bağlantısıdır.**

Bu backend başka proje sanılarak terk edilmeyecek, silinmeyecek veya LANU dışı olduğu varsayılmayacaktır. Görünen ad değişse bile teknik kimlik project ref ile doğrulanacaktır.

#### Klasör yapısı — KAYIT VE DOĞRULAMA ZORUNLULUĞU

Repository klasör/ağaç yapısı yaşayan proje envanterinin parçasıdır. Her önemli yapısal değişiklikte:

1. Aktif Android kaynak kökü doğrulanır.
2. `app`/modül, `src/main`, `src/test`, `src/androidTest`, Room/data, repository/domain, Compose/UI, Supabase/sync, Gradle, CI/workflow, docs ve migration kaynaklarının gerçek konumları kontrol edilir.
3. Eski/çift/ölü kaynak ağacı varsa hangi ağacın kanonik olduğu kanıtlanmadan silinmez.
4. Taşıma/silme/yeniden adlandırma işlemi ve nedeni anayasaya kaydedilir.
5. Klasör yapısı değişikliği sonrasında build/test/import/referans kırıkları doğrulanır.

#### Kodlar — KAYIT VE İZLENEBİLİRLİK ZORUNLULUĞU

Her önemli kod değişikliği için en az şu kayıt tutulacaktır:

- Problem veya gereksinim
- Etkilenen dosya/modül
- Kök neden
- Uygulanan kod değişikliği
- Veri/migration etkisi
- Güvenlik/offline etkisi
- Test kapsamı
- Commit SHA
- CI/workflow sonucu
- Emulator/gerçek cihaz kanıtı gerekiyorsa sonucu

Kod yalnız derleniyor diye tamamlanmış sayılmaz; gerçek kullanıcı akışına bağlanması ve kabul testinden geçmesi gerekir.

#### Hatalar — EKSİKSİZ KAYIT

Her hata kaydedilecektir. Kayıtta mümkün olduğunda hata mesajı, başarısız test/akış, etkilenen commit/branch, tekrar üretme koşulu, kök neden ve etkisi bulunacaktır. Aynı hata tekrar ederse önceki problem kaydı okunacak; çözüm geçmişi atlanmayacaktır.

#### Çözümler — KANIT ZORUNLULUĞU

Her çözüm için **ARAŞTIR → AYIKLA → DÜZELT → ONAYLA → DOĞRULA → KANITLA** uygulanacaktır. Çözüm yalnız kod değişikliği değildir; test sonucu ve gerçek kabul kanıtı bulunmadan problem kapatılmaz. Bölüm 0'daki 20 bağımsız site / 40 ilgili kaynak standardı teknik problem çözümünde uygulanır.

#### Her zaman okunacak ve hiçbir şey atlanmayacak

Repo kimliği, proje adı, Supabase kimliği/onayı, klasör yapısı, kod değişiklikleri, hatalar, çözümler, testler, CI, migration, UI/UX, Release ve APK teslim durumu bu yaşayan belgede veya açıkça referans verilen kalıcı kayıtta tutulacaktır. **Yeni oturumda geçmiş varsayılmayacak; anayasa baştan sona okunacak. Hiçbir kritik madde atlanmayacaktır.**

#### UI/UX tam teslim kuralı

Ürün teslim edilmeden önce kullanıcı arayüzü uçtan uca düzeltilecektir. Özellikle:

- Radar / Dashboard / CRM bilgi mimarisi
- tekrarlı şehir/ilçe filtrelerinin kaldırılması
- tutarlı navigasyon
- müşteri/lead detayları
- kontak, aktivite, görev/next action, fırsat ve huni
- ürün/fiyat
- teklif ve teklif satırları
- sipariş ve sipariş satırları
- loading/empty/error/offline durumları
- form validasyonları ve anlaşılır hata mesajları
- klavye/scroll/dialog davranışı
- dokunma hedefleri ve küçük ekran uyumu
- saha kullanımında hız ve okunabilirlik

profesyonel kurumsal seviyede doğrulanacaktır.

#### %100 teslim kapısı

Aşağıdakilerin **tamamı** tamamlanmadan ürün %100 teslim edilmiş sayılmaz:

- Kanonik repo ve kaynak ağacı doğrulaması
- Profesyonel kurumsal Android UI/UX
- Kritik CRM ve ticari akışların gerçek UI üzerinden çalışması
- Room/offline source-of-truth
- Kullanıcı tarafından onaylı kanonik Supabase üzerinde Auth/RLS/izolasyon
- Cloud sync/outbox/retry/reconnect/conflict/idempotency
- Doğrulanmış Global Donuk ürün kataloğu
- Validate/sanitize/normalize/log/hata yönetimi/fallback
- Build + lint + unit + instrumentation + Android emulator smoke
- Gerekli gerçek cihaz UX kabulü
- PR CI tamamen yeşil
- Main merge
- Main CI tamamen yeşil
- Güncel main commit için GitHub Release APK
- Release APK'nın gerçekten indirilebilir olması
- Artifact/hash ile kaynak commit eşleşmesinin doğrulanması

Bu kapılardan biri eksikse uygulama “%100 hazır”, “tam çalışan” veya “tam teslim” olarak raporlanmayacaktır.

## 1. Ana çalışma ilkesi — @Düşün

**Ürün çıktı tam hazır hale gelene kadar çalış.**

Araştır → kök nedeni belirle → düzelt → uygula → build → lint/test → instrumentation → Android emulator smoke → doğrula → PR CI → merge → main CI → Release APK → indirilebilirlik/hash doğrulaması.

CI yeşil olması tek başına ürünün hazır olduğu anlamına gelmez.

## 2. Katmanlı dayanıklılık

ANA SERVİS → ALTERNATİF → FALLBACK → HATA YÖNETİMİ → GÜVENLİ VARSAYILAN → LOG/İZLEME → SMOKE TEST.

Tek hata noktası bırakılmaması hedeflenir. Fallback uygulanabilir ve test edilmiş olmalıdır.

## 3. Maliyet kuralı

Öncelik ücretsiz/açık kaynak/yerel çözümlerdir; ardından mevcut free tier kullanılır. Kullanıcı açıkça onay vermeden ücret doğurabilecek yeni servis/proje/kaynak oluşturulmaz.

## 4. Veri güvenliği ve veri kalitesi

Validate → sanitize → normalize → persist/sync → log.

Hatalı veri sessizce başarılı kabul edilmez.

## 5. Android kalite kapısı

Her önemli değişiklikte build, Android lint, unit test, instrumentation test, gerçek Android emulator smoke ve kritik kullanıcı akışı smoke testleri çalıştırılır. CI kırılırsa merge yapılmaz; önce kök neden bulunur ve Bölüm 0 araştırma standardı uygulanır.

## 6. GitHub / PR / main politikası

- Aktif geliştirme PR üzerinden yürütülür.
- PR CI tamamen yeşil olmadan main'e merge edilmez.
- PR kanıtları güncel HEAD ile eşleşmelidir.
- Eski/çift kaynak ve dokümantasyon tutarsızlıkları kontrollü temizlenir.
- Main merge sonrasında main CI ayrıca tamamen yeşil doğrulanır.

## 7. Release / APK teslim kuralı

1. Güncel main commit belirlenir.
2. Main CI tamamen yeşil doğrulanır.
3. O committen APK üretilir.
4. GitHub Releases bölümünde indirilebilir asset yayınlanır.
5. Asset gerçekten indirilerek doğrulanır.
6. APK/artifact hash ile kaynak commit eşleşmesi doğrulanır.

## 8. Ürün bilgi mimarisi

Radar = saha/satış fırsatı keşfi ve hedefleme. Dashboard = KPI, huni, görev, risk ve yönetim görünümü. CRM = müşteri/lead ve ticari ilişkinin operasyonel kaydı. Tekrarlı şehir/ilçe filtreleri kaldırılır; şehir → ilçe ilişkisi tek ve tutarlı mimaride yürütülür.

## 9. Kurumsal CRM kabul kapsamı

Müşteri/lead, yetkili kontak, aktivite, görev/next action, fırsat, satış hunisi, ürün/fiyat, teklif ve teklif satırları, sipariş ve sipariş satırları, müşteri↔ürün↔teklif↔sipariş ticari geçmişi ve saha/rutin akışları gerçek UI/UX üzerinden tamamlanmalıdır.

## 10. Yerel veri ve offline-first

Room güvenilir yerel veri katmanıdır. Cloud doğrulanamıyorsa LOCAL_ONLY/PENDING güvenli varsayılandır. Offline queue, retry/backoff, reconnect sync, idempotency ve conflict çözümü test edilmeden cloud dayanıklılığı tamamlanmış sayılmaz.

## 11. Supabase — KANONİK BACKEND KİMLİĞİ

- Mevcut proje adı: **Willy-Kilo-Takip**
- LANU hedef adı: **LANU GLOBAL / LANU Global Radar Supabase**
- Project ref: **jolfbmwxmsamzqtxassg**
- Bölge: **eu-central-1**
- Kullanıcı onayı: **ONAYLI — LANU backend olarak kullanıcı tarafından açıkça belirlenmiştir.**

**Willy-Kilo-Takip durdurulamaz/silinemez ve başka proje sanılarak terk edilemez. Bu proje LANU Global Donuk Satış Radarı'nın kanonik backend'idir.**

Görünen ad değişse bile kanonik teknik kimlik `jolfbmwxmsamzqtxassg` ref'idir. Kullanıcı açıkça istemedikçe yeni LANU Supabase projesi oluşturulmaz.

## 12. Supabase güvenlik ve çok kullanıcı izolasyonu

Cloud hazır sayılmadan Auth kimliği, tenant/user izolasyonu, tüm exposed tablolarda RLS, SELECT/INSERT/UPDATE/DELETE politikaları, USING + WITH CHECK, iki kullanıcı negatif testleri, Data API/grant, secret/service_role'un Android'e gömülmemesi, cloud sync, offline queue→reconnect→sync, conflict, retry/backoff, idempotency ve log/izleme gerçek backend üzerinde doğrulanır.

## 13. Global Donuk ürün kataloğu

Ürün/SKU/fiyat/veri uydurulmaz. Gerçek katalog yalnız doğrulanmış kaynaklardan alınır; doğrulanamayan alan gerçek veri gibi sisteme eklenmez.

## 14. UX kabul kriteri

Emulator yanında yayın öncesi gerçek cihaz UX kontrolü hedeflenir: küçük ekran/yoğunluk, klavye/form, scroll/dialog, hata mesajları, loading/empty/error/offline durumları, dokunma hedefleri, filtre anlaşılabilirliği ve saha kullanım hızı.

## 15. Loglama ve hata yönetimi

Kritik başarısızlıklar sessizce yutulmaz. Secret/hassas veri loglanmadan teşhis edilebilir log/izleme ve güvenli kullanıcı hatası sağlanır.

## 16. “Hazır” tanımı

Ürün ancak profesyonel Radar UI/UX; Dashboard/CRM mimarisi; tekrar filtre temizliği; kurumsal CRM UI akışları; Room/offline; `jolfbmwxmsamzqtxassg` üzerinde RLS/izolasyon; cloud sync/offline queue/conflict/retry; doğrulanmış katalog; validate/sanitize/normalize; lint+unit+instrumentation+emulator smoke; gerekli gerçek cihaz kabulü; PR CI yeşil; main merge; main CI yeşil; güncel main Release APK; asset indirilebilirliği ve commit/artifact hash eşleşmesi tamamlandığında hazırdır.

## 17. Değişiklik yönetimi

Bu yaşayan dokümandır. Kalıcı mimari/teslim kararları buraya işlenir; kritik kararlar yalnız sohbet geçmişine bırakılmaz.

## 18. PROBLEM KAYDI — Supabase kimliği ve offline/cloud sync mimarisi

### Problem
LANU production Supabase başlangıçta doğrulanamıyordu; `jolfbmwxmsamzqtxassg` hesabında `Willy-Kilo-Takip` ve INACTIVE görünüyordu. Yanlış backend'e migration/RLS uygulama riski nedeniyle cloud E2E durduruldu. Ayrıca mobil offline queue/cache/conflict çözümünün Supabase istemcisinde hazır olup olmadığı belirsizdi.

### Çözüm / kullanıcı kararı
Kullanıcı `Willy-Kilo-Takip` projesinin LANU için kullanılacağını kesinleştirdi. Kanonik kimlik `jolfbmwxmsamzqtxassg`.

### Önceki araştırma
Önceki turda 30 ilgili teknik tartışma/kaynak ve 10+ topluluk/domain tarandı: Stack Overflow, Reddit AndroidDev, Reddit Supabase, GitHub Discussions/Issues, Answer Overflow, Kotlin Slack arşivi, DBA StackExchange, DEV Community, WeWeb Community, FlutterFlow Community, Bubble Forum, Google Groups ve resmî Android/Supabase dokümantasyonu dahil. Bu çalışma yeni 20-domain/40-kaynak standardından önce yapıldığı için yeni problemler için tek başına yeterli kanıt değildir.

### Ayıklanan sonuçlar
1. Room cihazdaki source-of-truth kalır.
2. Yerel transaction + durable outbox kullanılır.
3. WorkManager pending kayıtları kontrollü/batch işler.
4. Supabase istemcisi tek başına tam offline/cache/conflict katmanı sayılmaz.
5. Realtime tek başına reconciliation değildir; reconnect sonrası pull gerekir.
6. RLS kapatmak çözüm değildir.
7. `authenticated` tek başına izolasyon değildir; ownership/tenant predicate gerekir.
8. RLS iki gerçek kullanıcıyla negatif test edilir.
9. Nested CRM tablolarında ownership zinciri korunur.
10. UPDATE için USING+WITH CHECK, INSERT için WITH CHECK uygulanır.
11. service_role/secret APK'ya konmaz.
12. Ticari belgelerde kör last-write-wins kullanılmaz; versioned/idempotent yaklaşım tercih edilir.
13. Doğrulanmayan kayıt SYNCED sayılmaz.
14. Hedef: `Compose UI → Repository → Room → durable outbox → WorkManager → authenticated Supabase → RLS → idempotent push → reconciliation pull → conflict resolver → Room → UI`.
15. Offline: `UI → Room → LOCAL_ONLY/PENDING → outbox`; reconnect: `outbox → retry/backoff → Supabase → doğrulama → reconciliation → SYNCED`.

### Doğrulama durumu
- Backend kimliği: **ÇÖZÜLDÜ** — `jolfbmwxmsamzqtxassg`.
- Offline-first yönü: **ARAŞTIRMA İLE DESTEKLENDİ**.
- Gerçek RLS + iki kullanıcı negatif testleri: **E2E DOĞRULANACAK**.
- Offline queue→reconnect→sync→conflict: **E2E DOĞRULANACAK**.

Cloud katmanı ancak kanonik backend üzerinde şema/RLS, migration güvenliği, iki kullanıcı izolasyonu, offline/reconnect, log ve Android instrumentation/emulator smoke ile kanıtlanınca tamamlanmış sayılır.