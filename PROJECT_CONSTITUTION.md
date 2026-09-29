# LANU Global Donuk Satış Radarı — Proje Anayasası

> Bu belge LANU Global Donuk Satış Radarı projesinin kalıcı teknik ve teslim kurallarının tek referansıdır. Kod, PR, Issue, CI/CD, Supabase, Android ve Release kararları bu kurallara göre yürütülür. Çelişki halinde daha güvenli ve doğrulanabilir yaklaşım tercih edilir.

## 1. Ana çalışma ilkesi — @Düşün

**Ürün çıktı tam hazır hale gelene kadar çalış.**

Zorunlu döngü:

Araştır → kök nedeni belirle → düzelt → uygula → build → lint/test → instrumentation → Android emulator smoke → doğrula → PR CI → merge → main CI → Release APK → indirilebilirlik/hash doğrulaması.

CI yeşil olması tek başına ürünün hazır olduğu anlamına gelmez. Kritik işlevler ve kabul kriterleri tamamlanmadan merge/Release yapılmaz.

## 2. Katmanlı dayanıklılık

Her kritik zincirde mümkün olduğunda şu sıra uygulanır:

ANA SERVİS → ALTERNATİF → FALLBACK → HATA YÖNETİMİ → GÜVENLİ VARSAYILAN → LOG/İZLEME → SMOKE TEST.

Tek hata noktası bırakılmaması hedeflenir. Özellikle AI/LLM, harici API, veritabanı, dosya/storage, authentication, backend↔frontend, Android↔backend, deploy/production ve cloud sync zincirleri bu kurala tabidir.

Fallback yalnız teorik olarak bulunmuş sayılmaz; uygulanabilir ve test edilmiş olmalıdır.

## 3. Maliyet kuralı

Öncelik ücretsiz/açık kaynak/yerel çözümlerdir; ardından mevcut free tier kullanılır. Kullanıcı açıkça onay vermeden ücret doğurabilecek yeni servis, proje, branch veya kaynak oluşturulmaz.

## 4. Veri güvenliği ve veri kalitesi

Dışarıdan veya kullanıcıdan gelen veriler kritik kayıtlarda şu zincirden geçirilir:

Validate → sanitize → normalize → persist/sync → log.

Hatalı veri sessizce başarılı kabul edilmez. Güvenli varsayılan uygulanır ve gerektiğinde kullanıcıya anlaşılır hata gösterilir.

## 5. Android kalite kapısı

Her önemli değişiklikte uygun kapsamda:

- Build
- Android lint
- Unit test
- Instrumentation test
- Gerçek Android emulator smoke
- Kritik kullanıcı akışı smoke testleri

çalıştırılır. CI kırılırsa merge yapılmaz; önce kök neden bulunur ve düzeltilir. Çözüm belirsizse veya hata sürüyorsa en az 5 bağımsız güvenilir teknik forum/dokümantasyon kaynağıyla araştırma yapılır.

## 6. GitHub / PR / main politikası

- Aktif geliştirme PR üzerinden yürütülür.
- PR CI tamamen yeşil olmadan main'e merge edilmez.
- PR açıklamaları ve kalite kanıtları güncel HEAD ile eşleşmelidir.
- Eski/çift kaynak ağaçları ve dokümantasyon tutarsızlıkları kontrollü biçimde temizlenir.
- Aktif PR/branch ilişkisi doğrulanmadan eski branch silinmez.
- Main'e merge sonrasında main CI ayrıca tamamen yeşil doğrulanır.

## 7. Release / APK teslim kuralı

Android APK yalnız build edilmiş olmakla teslim edilmiş sayılmaz.

Yayınlanabilir sürümde:

1. Güncel main commit belirlenir.
2. Main CI tamamen yeşil doğrulanır.
3. O committen APK üretilir.
4. GitHub Releases bölümünde indirilebilir asset olarak yayınlanır.
5. Release asset'inin gerçekten indirilebildiği doğrulanır.
6. APK/artifact hash ve kaynak commit eşleşmesi doğrulanır.

Bunlar tamamlanmadan ürün “tam hazır” ilan edilmez.

## 8. Ürün bilgi mimarisi

Radar, Dashboard ve CRM görevleri birbirinden anlaşılır biçimde ayrılmalıdır.

- Radar: saha/satış fırsatı keşfi, coğrafi filtreleme ve hedefleme.
- Dashboard: KPI, satış hunisi, görevler, riskler ve yönetim görünümü.
- CRM: müşteri/lead ve tüm ticari ilişkinin operasyonel kaydı.

Tekrarlı şehir/ilçe filtreleri kaldırılır. Şehir → ilçe bağımlılığı tek, anlaşılır ve tutarlı bir filtre mimarisi üzerinden yürütülür.

## 9. Kurumsal CRM kabul kapsamı

Ürün en az aşağıdaki zincirleri kurumsal seviyede desteklemelidir:

- Müşteri / lead
- Yetkili kontaklar ve birincil kontak
- Aktivite geçmişi
- Görev / next action
- Fırsatlar
- Satış hunisi
- Ürün ve fiyat
- Teklif
- Teklif ürün satırları
- Sipariş
- Sipariş ürün satırları
- Müşteri ↔ ürün ↔ teklif ↔ sipariş ticari geçmişi
- Saha/rutin satış akışları

Bu akışların yalnız veri modeli değil gerçek kullanıcı UI/UX bağlantıları da tamamlanmalıdır.

## 10. Yerel veri ve offline-first

Room Android tarafındaki güvenilir yerel veri katmanıdır. İnternet kesintisi temel CRM kullanımını bozmayacak şekilde tasarlanır.

Cloud doğrulanamıyorsa güvenli varsayılan LOCAL_ONLY/PENDING benzeri açık bir sync durumudur. Doğrulanmamış kayıt sahte biçimde SYNCED işaretlenmez.

Offline queue, retry/backoff, reconnect sonrası sync, idempotency ve conflict çözümü test edilmeden cloud dayanıklılığı tamamlanmış sayılmaz.

## 11. Supabase — KANONİK BACKEND KİMLİĞİ

**Bu bölüm kritik ve kalıcıdır.**

LANU Global Donuk Satış Radarı için kullanılacak Supabase projesi:

- Supabase mevcut proje adı: **Willy-Kilo-Takip**
- LANU içindeki anlamı / hedef adı: **LANU GLOBAL / LANU Global Radar Supabase**
- Project ref / ID: **jolfbmwxmsamzqtxassg**
- Bölge: **eu-central-1**

### Kesin kural

**Willy-Kilo-Takip projesi durdurulamaz/silinemez ve başka bir proje sanılarak terk edilemez. Bu Supabase projesi LANU Global Donuk Satış Radarı'nın kanonik backend projesidir.**

Gelecekte Supabase Dashboard görünen adı `LANU GLOBAL` olarak değiştirilse bile kanonik teknik kimlik `jolfbmwxmsamzqtxassg` project ref'idir. İsim değişikliği project ref'i değiştirmez.

Yeni bir LANU Supabase projesi, kullanıcı açıkça talep etmedikçe oluşturulmaz. `jolfbmwxmsamzqtxassg` yanlışlıkla “başka uygulamanın backend'i” olarak reddedilmez.

Proje INACTIVE/paused görülürse bunun LANU backend olmadığı sonucu çıkarılmaz; önce bu anayasa esas alınır ve proje güvenli biçimde restore/aktif etme süreci değerlendirilir.

## 12. Supabase güvenlik ve çok kullanıcı izolasyonu

Cloud hazır sayılmadan önce gerçek backend üzerinde doğrulanmalıdır:

- Auth kullanıcı kimliği
- Her kullanıcı/tenant için veri izolasyonu
- RLS'nin tüm exposed tablolar için etkinliği
- SELECT/INSERT/UPDATE/DELETE politikalarının gerçek erişim modeliyle uyumu
- UPDATE için gerekli USING + WITH CHECK koşulları
- Başka kullanıcının kayıtlarının okunamadığının/yazılamadığının negatif testleri
- Data API erişim/grant ayarları
- service_role/secret anahtarların Android istemciye gömülmemesi
- Cloud sync
- Offline queue → reconnect → sync
- Conflict çözümü
- Retry/backoff ve idempotency
- Log/izleme

RLS yalnız “authenticated” rolüyle sınırlandırılmış sayılmaz; gerçek ownership/tenant predicate kullanılmalıdır.

## 13. Global Donuk ürün kataloğu

Ürün/SKU/fiyat/veri uydurulmaz.

Gerçek Global Donuk ürün kataloğu yalnız doğrulanmış kaynaklardan alınır. Kaynakta bulunmayan SKU, fiyat, açıklama veya ürün özelliği gerçek veri gibi sisteme eklenmez. Kaynak doğrulanamıyorsa alan bilinmiyor/eksik bırakılır veya açıkça yerel taslak olarak işaretlenir.

## 14. UX kabul kriteri

Emulator testine ek olarak yayın öncesi gerçek cihaz UX kontrolü hedeflenir. Özellikle:

- küçük ekran ve farklı yoğunluklar
- klavye/form davranışı
- scroll ve modal/dialog kullanımı
- hata mesajları
- loading/empty/error/offline durumları
- dokunma hedefleri
- filtrelerin anlaşılabilirliği
- saha kullanım hızı

kontrol edilir.

## 15. Loglama ve hata yönetimi

Kritik zincirlerde başarısızlıklar sessizce yutulmaz. Hassas veri ve secret loglanmadan teşhis edilebilir log/izleme sağlanır. Kullanıcıya güvenli ve anlaşılır hata durumu gösterilir.

## 16. “Hazır” tanımı

LANU Global Donuk Satış Radarı yalnız aşağıdakilerin tamamı sağlandığında hazırdır:

- Profesyonel Radar UI/UX
- Dashboard ve CRM bilgi mimarisi
- Tekrarlı filtrelerin kaldırılması
- Kurumsal CRM akışlarının gerçek UI üzerinden çalışması
- Room/offline kullanım
- Kanonik Supabase `jolfbmwxmsamzqtxassg` üzerinde güvenli RLS ve kullanıcı izolasyonu
- Cloud sync/offline queue/conflict/retry dayanıklılığı
- Doğrulanmış Global Donuk kataloğu
- Validate/sanitize/normalize ve hata yönetimi
- Lint + unit + instrumentation + emulator smoke
- Gerekli gerçek cihaz UX kabulü
- PR CI tamamen yeşil
- Main merge
- Main CI tamamen yeşil
- Güncel main commit için GitHub Release APK
- Release asset indirilebilirlik doğrulaması
- Commit/artifact hash eşleşmesi

Herhangi bir kritik madde eksikse ürün “tam hazır” değildir.

## 17. Değişiklik yönetimi

Bu anayasa proje boyunca yaşayan dokümandır. Mimari veya teslim standardında kalıcı bir karar alındığında bu dosya güncellenmelidir. Özellikle backend kimliği, güvenlik modeli, release politikası ve kabul kriterlerindeki değişiklikler sözlü varsayıma bırakılmamalıdır.
