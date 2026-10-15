# LANU-Global-Donuk-Sat-Radar- — KANITLI TEKNİK DENETİM

**Tarih:** 15.10.2026  
**Kapsam:** GitHub dosya ağacı, seçili kaynak kodu, CI logları, Releases. Gerçek cihaz ve dış servislerin tam E2E testi yapılmadı.  
**Dosya sayısı (denetim anında):** 188  
**CI gözlemi:** 08.10.2026 Android CI success; latest Release APK, SHA-256 ve build-info var; 81 adet il veri dosyası Release'de listeleniyor.  
**Anayasa:** [PROJE_ANAYASASI_VE_DUZELTILECEKLER.md](https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-/blob/main/PROJE_ANAYASASI_VE_DUZELTILECEKLER.md)

## Kanıtlarla Düzeltilmesi Gerekenler

### 1. 81 il dosyası, 81 ilde tüm işletmeler demek değildir — P1 (VERİ KAPSAMI DOĞRULAMA GEREK)
- **Kanıt:** [docs/ANAYASA.md#L6-L12](https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-/blob/main/docs/ANAYASA.md#L6-L12); [CI koşusu](https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-/actions/runs/37780528953)
- **Gözlem:** Anayasa da idari katalog bütünlüğünün işletme arama kapsamı anlamına gelmediğini söylüyor.
- **Somut örnek / etkisi:** İstanbul bir ilçesinde '0 sonuç' dönmesi işletme yokluğunu kanıtlamaz; kapsama etiketi gerekir.
- **Düzeltme kabul testi:** 81 il, ilçe/mahalle ve işletme sayılarının kaynak/güncellik ve veri kalitesi ölçümleri; eksikleri 'doğrulanmadı' göster.
- **Durum:** ARAŞTIRILDI, DÜZELTME HENÜZ BAŞLAMADI.

### 2. Sicil ve CRM kişisel verileri doğrulama gerektiriyor — P1 (GÜVENLİK / GİZLİLİK TESTİ)
- **Kanıt:** [app/src/main/java/com/lanu/globaldonuksatisradari/data/OfficialRegistryEnrichment.kt#L13-L19](https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-/blob/main/app/src/main/java/com/lanu/globaldonuksatisradari/data/OfficialRegistryEnrichment.kt#L13-L19); [CI koşusu](https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-/actions/runs/37780528953)
- **Gözlem:** ITO, TOBB, MERSIS gibi kaynak tanımları bulunuyor; resmi kaynağın bir şekilde referanslanması otomatik sınırsız çekme yetkisi vermez.
- **Somut örnek / etkisi:** TC no/vergi no, kişi adı, şirket adı dışa aktarma işlemi izin/amaç ve maskeleme kontrolünden geçmeli.
- **Düzeltme kabul testi:** Kayıt kaynağı ve kullanım izni, açık yetki, KVKK, veri minimizasyonu, export erişim günlüğü, maskeleme ve yanlış eşleşme testleri.
- **Durum:** ARAŞTIRILDI, DÜZELTME HENÜZ BAŞLAMADI.

### 3. Overture veri bütünlüğü SHA doğrulama ve cache var — P2 (DOĞRULANMIŞ İYİ UYGULAMA)
- **Kanıt:** [app/src/main/java/com/lanu/globaldonuksatisradari/data/OvertureBusinessSourceAdapter.kt#L315-L334](https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-/blob/main/app/src/main/java/com/lanu/globaldonuksatisradari/data/OvertureBusinessSourceAdapter.kt#L315-L334); [CI koşusu](https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-/actions/runs/37780528953)
- **Gözlem:** Mevcut kod önbellekteki dosyayı SHA-256 ile kontrol ediyor, manifest güncellenemeyince cache deniyor.
- **Somut örnek / etkisi:** Kısmi indirmenin sahte geçerli veri sayılması önlenmeli.
- **Düzeltme kabul testi:** Checksum başarısız, eksik şehir, eski manifest, ağ yok, yanlış format ve atomik yazma testleri.
- **Durum:** ARAŞTIRILDI, DÜZELTME HENÜZ BAŞLAMADI.

### 4. Harita-Excel-saha rota gerçek uçtan uca test gerektiriyor — P2 (İŞLEVSEL KABUL TESTİ)
- **Kanıt:** [README.md#L14-L24](https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-/blob/main/README.md#L14-L24); [CI koşusu](https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-/actions/runs/37780528953)
- **Gözlem:** Liste, harita, planlama ve rapor hedefleri var; Release APK bulunması tüm akışların sorunsuz olduğunu göstermez.
- **Somut örnek / etkisi:** Filtre ile 81 il, doğru koordinat, Excel kolonları ve ziyaret sıklığı bir saha müşteri setinde karşılaştırılır.
- **Düzeltme kabul testi:** İl/ilçe/mahalle filtre+üretim taraması, Excel sütun sözleşmesi, aynı işletme tekilleştirme, açık/pasif/doğrulanmamış akışı.
- **Durum:** ARAŞTIRILDI, DÜZELTME HENÜZ BAŞLAMADI.

## Zorunlu 4 Faz
1. Araştır/Envanter: kapsam, kanıt ve etki; bu rapor başlangıç seviyesinde seçili dosya/CI incelemesidir.
2. Kök neden ve düzeltme: P0/P1 önce; en küçük geri alınabilir kod değişimi; gizli veriyi koru; ücret için onay al.
3. Build + test: lint, unit/integrasyon/UI/E2E, offline/kesinti/fallback, gerekirse gerçek cihaz.
4. Doğrula ve yayınla: tüm testler yeşil; Android APK Releases altında SHA-256 ile doğrudan indirilebilir; smoke ve bağlantı doğrulaması.

**Bulgu kapatma:** Kod satırı + kök neden + düzeltme commit'i + test + CI + sürüm/ürün smoke kanıtı olmadan hiçbir madde bitmiş sayılmaz.
