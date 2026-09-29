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
