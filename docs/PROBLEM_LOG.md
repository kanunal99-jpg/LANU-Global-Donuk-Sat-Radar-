# LANU Global Donuk Satış Radarı — Problem / Çözüm / Kanıt Günlüğü

> Bu dosya `PROJECT_CONSTITUTION.md` Bölüm 0'daki kalıcı problem kaydı ve `AUDIT_CHECKPOINT` disiplinini uygular. Kök anayasa tek bağlayıcı kurallar kaynağıdır; bu belge problem → kök neden → araştırma → çözüm → doğrulama → kanıt geçmişini tutar.

## Kanonik kimlik

- Repository: `kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-`
- Proje: **LANU Global Donuk Satış Radarı**
- Supabase: **Willy-Kilo-Takip / LANU GLOBAL**
- Project ref: `jolfbmwxmsamzqtxassg`
- Backend kullanıcı tarafından LANU için açıkça ONAYLIDIR.

---

## P-001 — Supabase backend kimliği belirsizliği

**Problem:** Android uygulamasının kullandığı `jolfbmwxmsamzqtxassg` projesi Supabase'te `Willy-Kilo-Takip` adıyla görünüyordu ve bir aşamada INACTIVE durumdaydı. Yanlış projeye migration/RLS uygulama riski doğdu.

**Kök neden:** Görünen proje adı LANU adıyla eşleşmiyordu; teknik project-ref ile işlevsel proje adı karıştırılabiliyordu.

**Çözüm:** Kullanıcı `jolfbmwxmsamzqtxassg` projesini LANU'nun kanonik backend'i olarak açıkça onayladı. Proje restore edildi ve production yönetim erişimi doğrulandı.

**Kanıt:** Kanonik kimlik kök anayasaya işlendi; production şema/RLS sorguları bu project-ref üzerinde çalıştırıldı.

**Durum:** ÇÖZÜLDÜ.

---

## P-002 — Production ticari CRM şema tipi TEXT ↔ UUID uyuşmazlığı

**Problem:** İlk commercial/contact migration denemesinde PostgreSQL `42804` tipi uyuşmazlığı oluştu.

**Kök neden:** Migration bazı ilişki kimliklerini `text` varsayarken production `lanu_crm_customers.id` ve fırsat kimlikleri `uuid` idi.

**Çözüm:** Contact/quote/order ve ilişki kolonları production UUID modeliyle hizalandı; doğrulanmamış katalog kimliği olan `product_id` bilinçli biçimde `text` bırakıldı.

**Doğrulama:** Production bilgi şeması ve constraint'ler SQL ile incelendi; commercial/contact tabloları production'da oluşturuldu.

**Durum:** ÇÖZÜLDÜ.

---

## P-003 — Ticari/contact çok-kullanıcı RLS izolasyonu kanıtı

**Problem:** Policy SQL'inin var olması gerçek kullanıcı izolasyonunun çalıştığını tek başına kanıtlamıyordu.

**Çözüm / Test:** Production transaction içinde iki geçici auth kullanıcısı oluşturuldu; `SET LOCAL ROLE authenticated` + JWT `sub` claim ile kullanıcı A/B davranışı simüle edildi. Contact/quote/quote-line/order/order-line için:

- A yalnız kendi kayıtlarını okuyabildi.
- A kendi sınırında INSERT/UPDATE/DELETE yapabildi.
- B'ye ait INSERT RLS `WITH CHECK` tarafından reddedildi.
- B'ye ait UPDATE/DELETE sıfır satır etkiledi.
- Nested quote/order line yabancı parent insertleri reddedildi.
- Kullanıcı B, A'nın kayıtlarını göremedi.

**Temizlik:** Transaction `ROLLBACK` edildi; follow-up sorgusunda geçici auth/customer/contact/quote/line/order test kayıtlarının tamamı `0` doğrulandı.

**Sınır:** Bu güçlü database-policy kanıtıdır; fiziksel Android cihaz + iki gerçek kullanıcı session E2E testi olarak raporlanmaz.

**Durum:** DATABASE RLS KANITI GEÇTİ.

---

## P-004 — Offline dirty kayıtların remote pull tarafından sessiz ezilmesi

**Problem:** Pull mantığında remote sürüm daha yüksek olduğunda yerel kayıt `PENDING_UPLOAD`, `CONFLICT`, `FAILED` veya `LOCAL_ONLY` olsa bile cloud kopyası Room verisinin üzerine yazılabiliyordu. Worker pull-before-push çalıştığı için offline kullanıcı değişikliği push conflict kontrolüne ulaşmadan kaybolabilirdi.

**Araştırma:** Anayasa araştırma standardı uygulandı: 20+ bağımsız domain ve toplam 40+ ilgili teknik kaynak. Android offline-first, Supabase/Postgres RLS/sync, GitHub issue/discussion, Stack Overflow/StackExchange, Reddit, Hasura, Prisma, YugabyteDB, MongoDB/Realm, CockroachDB ve diğer production sync kaynakları incelendi.

**Ayıklanan karar:** Dirty/local state pull tarafından sessiz overwrite edilmemeli. Clean `SYNCED` kayıt remote ile refresh edilebilir; dirty kayıt push/version-conflict hattına korunarak bırakılmalıdır.

**Çözüm:** Ortak `CrmPullConflictPolicy` eklendi. `localVersion == null` ise remote insert kabul edilir; mevcut kayıt yalnız `SYNCED` durumundaysa ve remote version eski değilse refresh edilir. Core ve commercial pull aynı kurala bağlandı.

**Test:** `CommercialPullConflictPolicyTest` shared policy için missing-local, clean-sync, stale-remote, `PENDING_UPLOAD`, `CONFLICT`, `FAILED`, `LOCAL_ONLY` senaryolarını kapsar.

**Durum:** KODLANDI; exact-current-HEAD Android CI ile sürekli doğrulanacak.

---

## P-005 — Push tarafında GET-version → UPSERT TOCTOU yarış koşulu

**Problem:** Önceki push adapter'ları remote version'ı GET ile okuyup daha sonra ayrı UPSERT yapıyordu. İki cihaz aynı base version üzerinden eşzamanlı değişiklik gönderirse ikisi de pre-check'i geçip son-yazan-kazan veri kaybına yol açabilirdi.

**Kök neden:** Version kontrolü ve mutation tek PostgreSQL transaction içinde atomik değildi.

**Araştırma:** P-004 conflict araştırmasının optimistic concurrency/idempotency bölümü yeniden kullanıldı; PostgreSQL transaction, version compare-and-set, idempotent operation-id ve RLS-invoker desenleri ayıklandı.

**Çözüm:** Production'a `lanu_apply_versioned_crm_mutation` SECURITY INVOKER RPC migration'ı uygulandı. Ek olarak `lanu_crm_applied_mutations` ledger tablosu oluşturuldu.

RPC özellikleri:
- `auth.uid()` zorunlu; service-role bypass kullanılmaz.
- RLS altında çalışır.
- `operation_id` retry idempotency sağlar.
- `expected_version` ile compare-and-set yapılır.
- customer / next_action / opportunity / contact / quote / quote_line / order / order_line desteklenir.
- Sonuç: `APPLIED`, `CONFLICT`, `INVALID_VERSION`.

**Production kanıtı:** Transaction testinde:
1. İlk writer expected v1 → v2 `APPLIED`.
2. Aynı operation-id retry tekrar `APPLIED` (idempotent).
3. Farklı operation-id aynı expected v1 → v2 `CONFLICT`.
4. İlk writer verisi korunmuş kaldı.
5. Contact create expected0 için aynı idempotency/conflict zinciri geçti.
6. Kullanıcı B kullanıcı A'nın row/ledger kayıtlarını göremedi.
7. `ROLLBACK` sonrası geçici user/customer/contact/ledger kayıtları `0` kaldı.

**Android entegrasyonu:** `SupabaseAtomicMutationRemoteDataSource` versioned mutation'ları RPC'ye map eder. `HardenedSupabaseFullCrmRemoteDataSource` kanonik production sınırıdır; append-only activity push row-equivalence guard ile korunur.

**Durum:** PRODUCTION RPC KANITI GEÇTİ; Android exact-HEAD CI bekleniyor.

---

## P-006 — Supabase `auth_rls_initplan` performance uyarısı

**Problem:** Atomik mutation ledger için oluşturulan iki RLS policy doğrudan `auth.uid()` kullandığı için Performance Advisor `auth_rls_initplan` WARN verdi.

**Araştırma:** Anayasa standardına göre 20+ bağımsız domain ve 40+ ilgili kaynak incelendi. Supabase resmi Row Level Security dokümanı ve Splinter lint tanımı; PostgreSQL/PostgresPro; AWS, Azure, Google Cloud; EDB, Percona, Bytebase, Crunchy Data, PostgREST, Neon, Prisma, Hasura, CockroachDB, Stack Overflow ve bağımsız performans analizleri aynı sonucu destekledi.

**Kök neden:** Satırdan bağımsız `auth.uid()` helper çağrısı policy içinde doğrudan kullanıldığında candidate row başına tekrar değerlendirilebiliyor.

**Çözüm:** Ledger SELECT/INSERT policy koşulu erişim anlamı değişmeden `owner_user_id = (select auth.uid())` biçimine taşındı. Forward migration production'a uygulandı.

**Doğrulama:** Supabase Security Advisor temiz. Performance Advisor tekrar çalıştırıldığında `auth_rls_initplan` uyarısı tamamen kalktı; kalan bulgular yalnız INFO seviyesinde unused-index notlarıdır. Yeni/az trafikli indexler yalnız kullanım sayacı sıfır diye silinmedi.

**Durum:** ÇÖZÜLDÜ / ADVISOR TEMİZ.

---

## P-007 — Duplicate Supabase migration version prefix

**Problem:** Repo migration ağacında atomic sync ve commercial domain-constraints dosyaları aynı `20260929215500` version önekini taşıyordu.

**Risk:** Supabase CLI/migration history sıralamasında aynı migration version iki farklı dosyaya karşılık gelebilir.

**Çözüm:** Domain-constraints migration production history'deki gerçek version `20260929183829` adına taşındı; eski duplicate-version dosyası silindi.

**Durum:** DUPLICATE VERSION ÇÖZÜLDÜ.

---

## P-008 — İki bağımsız “anayasa” dosyası

**Problem:** Kök `PROJECT_CONSTITUTION.md` yanında `docs/ANAYASA.md` de bağımsız anayasa gibi görünüyordu.

**Çözüm:** `docs/ANAYASA.md` başlığı ve giriş metni “ESKİ ANAYASA / TARİHSEL EK” olarak değiştirildi. Tek bağlayıcı kaynak açıkça root `PROJECT_CONSTITUTION.md` olarak sabitlendi; tarihsel yararlı ilkeler kaybolmadı.

**Durum:** ÇÖZÜLDÜ.

---

## P-009 — Global Donuk gerçek ürün katalog kaynağı

**Problem:** Public web kaynaklarında doğrulanmış tam SKU/fiyat/gramaj tablosu bulunmuyor.

**Kural:** Uydurma SKU, fiyat, stok, gramaj veya ürün özelliği production katalog verisi olarak yazılamaz.

**Mevcut güvenli davranış:** `ProductCatalogRepository` boş başlar; kullanıcı tarafından eklenen kayıt doğrulanmış kaynak gösterilmedikçe doğrulanmış resmî katalog verisi sayılmaz. Public kaynak yalnız işletmenin donuk hazır yemek / hızlı dondurma / -18°C modelini doğrular; doğrulanmamış alanlar gerçek katalog gibi seed edilmez.

**Gerekli dış kaynak:** Yetkili Global Donuk CSV/XLSX/PDF/API veya doğrudan doğrulanabilir katalog kaynağı geldiğinde source + verification timestamp ile import edilir.

**Durum:** SAHTE VERİ YOK; YETKİLİ TAM KATALOG DIŞ KAYNAK BEKLİYOR.

---

## P-010 — Ürün provenance alanının herhangi bir HTTPS alan adını “doğrulanmış resmî kaynak” sayabilmesi

**Problem:** `sourceVerifiedAtEpochMs` dolu bir ürün kaydı için yalnız HTTPS kontrolü yapılıyordu; `example.com` gibi üçüncü taraf bir URL teorik olarak doğrulanmış Global Donuk kaynağı gibi saklanabilirdi. Eski SharedPreferences kayıtları da bu metadata'yı taşıyabiliyordu.

**Kök neden:** Web URL güvenliği ile iş kaynağı provenance doğrulaması aynı kavram gibi ele alınmıştı.

**Çözüm:** Doğrulanmış ürün kaynağı yalnız `globaldonukgida.com` veya gerçek alt alan adlarında kabul edilir. Benzer/aldatıcı `globaldonukgida.com.example.org` alan adları reddedilir. Eski persisted kayıtta resmî alan adı doğrulanamıyorsa URL korunur fakat `sourceVerifiedAtEpochMs` düşürülür; kayıt doğrulanmış sayılmaz.

**Test:** Unit testler resmî domain/subdomain kabulünü ve spoof-domain reddini; Android instrumentation testi legacy third-party verified metadata'nın downgrade edilmesini ve resmî Global Donuk kaynağının doğrulamasının korunmasını kapsar.

**Durum:** KODLANDI; exact-current-HEAD Android CI/instrumentation ile doğrulanacak.

---

## P-011 — Çok kullanıcılı offline sync kuyruğunda 500 kayıtlık starvation sınırı

**Problem:** `CrmSyncEngine`, aktif kullanıcı için work seçmeden önce global pending queue'dan ilk 500 kaydı alıyor ve ownership resolver ile sonradan filtreliyordu. Başka bir kullanıcıya ait 500+ eski pending işlem, aktif kullanıcının kendi kuyruğunu sürekli scan limitinin arkasında bırakabiliyordu.

**Kök neden:** Ownership filtresi SQL `LIMIT` işleminden sonra uygulama katmanında yapılıyordu.

**Çözüm:** `SyncOperationDao.pendingForOwner` eklendi. Customer/activity/next_action/opportunity/contact/quote/quote_line/order/order_line sahipliği customer lineage üzerinden SQLite içinde filtrelenir; `LIMIT` owner filtresinden sonra uygulanır. `RoomCrmSyncOwnershipResolver` ikinci bağımsız savunma katmanı olarak korunur.

**Test:** Room instrumentation testi, owner-B'ye ait 501 eski pending işlem önünde owner-A'nın daha yeni tek işlemini kuyruğa koyar; owner-A engine'in yine kendi işlemini seçip sync etmesi ve B'nin 501 kaydını tüketmemesi doğrulanır.

**Durum:** KODLANDI; exact-current-HEAD Android CI/instrumentation ile doğrulanacak.

---

## P-012 — Aynı cihazda `businessSourceId` dedupe'ın kullanıcılar arasında veri karıştırması

**Problem:** `addBusinessAsCustomer`, mevcut müşteriyi yalnız `businessSourceId` ile arıyordu. Aynı cihazda kullanıcı A aynı OSM işletmesini daha önce CRM'e aldıysa kullanıcı B ekleme yaptığında A'nın yerel customer kaydı döndürülebiliyordu.

**Kök neden:** İşletme kaynak dedupe'ı tenant/owner kapsamına alınmamıştı; cloud RLS doğru olsa da yerel Room dedupe sınırı kullanıcı kimliğini hesaba katmıyordu.

**Çözüm:** `CrmCustomerDao.findByBusinessSourceIdForOwner` eklendi. Eşleşme `businessSourceId + ownerUserId` üzerinden yapılır; `NULL` owner için de yalnız aynı local-only kapsam eşleşir. `LocalCrmRepository.addBusinessAsCustomer` bu owner-scoped sorguya geçirildi.

**Test:** Yeni Room instrumentation testi aynı verified business kaynağını önce owner-A, sonra owner-B, sonra tekrar owner-A için ekler. A ve B'nin customer ID'leri farklı; owner-A tekrarı aynı ID; toplam iki müşteri ve iki cloud queue create işlemi beklenir.

**Durum:** KODLANDI; exact-current-HEAD Android CI/instrumentation ile doğrulanacak.

---

## P-013 — Android CI yalnız seçili smoke sınıfını çalıştırıyordu

**Problem:** Emulator CI yalnız `MainActivitySmokeTest` sınıfını hedefleyebildiği için Room/commercial/product-persistence gibi diğer instrumentation testlerinin repository'de bulunması onların kabul kapısında gerçekten koştuğunu kanıtlamıyordu.

**Çözüm:** Android workflow emulator üzerinde `connectedDebugAndroidTest` ile tüm androidTest paketini çalıştıracak şekilde değiştirildi. Başarısızlıkta ADB/JUnit/emulator log tanılaması korunur; ardından artifact hazırlama ancak tam instrumentation başarıyla geçerse çalışır.

**Durum:** KODLANDI; exact-current-HEAD Android CI sonucu bekleniyor.

---

## AUDIT_CHECKPOINT

`app/src/main/java/com/lanu/globaldonuksatisradari/crm` cloud-sync ve yerel tenant zinciri incelendi:

`Room mutation → owner-scoped durable sync queue → CrmSyncWorker → HardenedSupabaseFullCrmRemoteDataSource → dirty-safe pull / atomic versioned push → RLS → idempotent mutation ledger → CrmSyncEngine state transition`.

Son doğrulanan/kodlanan noktalar:
- dirty pull preservation
- deterministic queue / parent-child ordering
- owner-before-limit queue selection; cross-user starvation koruması
- owner-scoped business-source dedupe
- atomik version conflict
- retry idempotency
- append-only activity equivalence fallback
- production two-user RLS isolation
- RLS advisor performance fix
- Global Donuk verified-source domain provenance
- full Android instrumentation workflow gate

**Sonraki audit:** exact-current-HEAD Android CI → full instrumentation sonuçları → UI pending-sync metadata owner kapsamı → fiziksel cihaz UX kabul blokajı → main/release zinciri.
