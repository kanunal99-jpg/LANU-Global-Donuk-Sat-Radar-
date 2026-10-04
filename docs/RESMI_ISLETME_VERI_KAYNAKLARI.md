# Türkiye Resmî İşletme Veri Kaynakları — Erişim ve Entegrasyon Kararı

Son doğrulama: 2026-10-04

## Hedef

LANU'da sektör kısıtı olmadan Türkiye'deki mümkün olan en geniş işletme envanterini oluşturmak; harita/POI kaynaklarını resmî sicil ve oda verileriyle tamamlamak.

Tek bir anonim ve herkese açık "Türkiye'deki tüm işletmeleri indir" servisi doğrulanmamıştır. Bu nedenle veri hattı kaynak türüne göre ayrılır.

## 1. MERSİS

Resmî kaynaklar:
- https://mersis.ticaret.gov.tr/
- https://www.ticaret.gov.tr/ic-ticaret/ticaret-sicili/merkezi-sicil-kayit-sistemi-mersis
- https://www.ticaret.gov.tr/ic-ticaret/sikca-sorulan-sorular/ticaret-sicili

Doğrulanan durum:
- MERSİS şirket ve ticari işletmelerin tescil/değişiklik/terkin kayıtlarının merkezi sistemidir.
- Sisteme giriş yapan kullanıcılar "Sorgular > Firma Sorgu" üzerinden şirketlerin temel bilgilerine ulaşabilir.
- Bakanlık MERSİS'in çok sayıda kamu kurumu ile anlık veri paylaşımı yaptığını açıklar.
- Kamuya açık anonim toplu firma API'si veya herkese açık tam veri dump'ı doğrulanmamıştır.

LANU kararı:
- CAPTCHA/login aşılmaya veya oturum kazınmaya çalışılmaz.
- Kullanıcının/yetkili kurumun MERSİS'ten hukuka uygun şekilde aldığı CSV/XLSX/ZIP çıktıları içe alınır.
- Sicil numarası/MERSİS numarası varsa kimlik kanıtı olarak kullanılabilir.

## 2. ESBİS

Resmî kaynak:
- https://ticaret.gov.tr/esnaf-sanatkarlar/esbis/genel-bilgiler

Doğrulanan durum:
- 81 ildeki esnaf ve sanatkâr sicil işlemleri ESBİS üzerinden yürütülür.
- Sistem Türkiye geneli veya il/ilçe bazında açılan-kapanan işletme, adres/unvan değişikliği ve meslek istatistikleri gibi ayrıntılı raporlama sağlar.
- Sistem kullanıcıları Bakanlık birimleri, sicil müdürlükleri, esnaf odaları/birlikleri/federasyon/konfederasyon ve doğrudan kendi kaydı için esnaf/sanatkârlardır.
- Herkese açık anonim tüm-esnaf toplu API'si doğrulanmamıştır.

LANU kararı:
- Yetkili ESBİS çıktısı toplu içe alınır.
- Tescil/tadil/terkin durumu ACTIVE/INACTIVE/UNVERIFIED semantiğine dönüştürülür.
- Yetkisiz oturum otomasyonu yapılmaz.

## 3. TOBB / Oda-Borsa Web Servisleri / Üye Firma

Resmî teknik kaynaklar:
- https://webservistest.tobb.org.tr/
- https://webservistest.tobb.org.tr/Kilavuz2.pdf
- https://uye.tobb.org.tr/organizasyon/firma-index.jsp
- https://ub.tobb.org.tr/oda
- https://www.tobb.org.tr/OdaveBorsalarDB/Sayfalar/oda--borsa-sorgulama.php

Doğrulanan web servis yolu:
- TOBB'un Oda/Borsa Web Servis API referansı bulunmaktadır.
- Sistem WS-I Basic Profile 1.1 SOAP kullanır.
- Erişim WS-Security UsernameToken + PasswordDigest ile korunur.
- Resmî test ekranı Api, Secret ve OdaKodu bilgileri ister.
- `odayaAitUyeleriSorgula` parametresiz çağrılırsa yetkili olunan odaya ait tüm üyeleri döndürür.
- Üye liste sonucunda MERSİS no, ticaret sicil no, oda sicil no, vergi no, durum, adres, il/ilçe/mahalle ve meslek grubu bilgileri bulunabilir.
- `uyeKartiSorgula` ile tabela unvanı, web/e-posta, telefon, NACE/faaliyet, üyelik durumu gibi daha ayrıntılı alanlar dönebilir.
- Değişiklik servisleri yeni/değişen kayıtların artımlı senkronuna imkân veren kayıt türleri tanımlar.

Güvenlik/veri minimizasyonu:
- LANU yalnız işletme envanteri için gerekli şirket/işletme alanlarını kullanacaktır.
- Kişi kartı, ortak, temsilci, ceza, borç ve benzeri amaç dışı hassas alanlar toplanmayacaktır.
- API/Secret uygulama kaynak koduna veya public repository'ye yazılmayacaktır.
- Yetki bilgileri yoksa web servis kullanılmayacak; yetkili JSON/CSV/XLSX/ZIP export fallback'i kullanılacaktır.

## 3. TOBB / Üye Firma / Oda Üye Sorgulama

> Not: Aşağıdaki kullanıcı arayüzü/CAPTCHA kanalları, web servis yetkisi olmayan kullanım senaryosudur.


Resmî kaynaklar:
- https://uye.tobb.org.tr/organizasyon/firma-index.jsp
- https://ub.tobb.org.tr/oda
- https://www.tobb.org.tr/OdaveBorsalarDB/Sayfalar/oda--borsa-sorgulama.php

Doğrulanan durum:
- TOBB Üye Firma ekranı kullanıcı girişi gerektirir.
- Oda Üye Sorgulama ekranlarında firma/NACE/meslek grubu filtreleri bulunur ve CAPTCHA kullanılır.
- TOBB oda/borsa dizini 81 ildeki oda ve borsaların resmî web/e-posta bilgilerini sağlar.
- Merkezi anonim toplu üye export endpoint'i doğrulanmamıştır.

LANU kararı:
- CAPTCHA bypass edilmez.
- Yetkili TOBB/Oda export'ları toplu içe alınır.
- TOBB oda/borsa dizini, hangi resmî kurumların kapsanması gerektiğini belirleyen kaynak kataloğudur.

## 4. Türkiye Ticaret Sicili Gazetesi

Resmî kaynak:
- https://www.ticaretsicil.gov.tr/

Doğrulanan durum:
- 1957'den günümüze ilan görüntüleme vardır; gazete görüntüleme kullanıcı girişi gerektirir.
- Unvan sorgulama resmî sicil müdürlüğü ve sicil numarası bilgisini gösterebilir.
- Unvan sorgusunda CAPTCHA vardır.

LANU kararı:
- Tam envanter kaynağı olarak kazınmaz.
- İhtilaflı/şüpheli kayıtların noktasal doğrulamasında yardımcı kaynak olabilir.

## 5. İTO

Resmî kaynak:
- https://bilgibankasi.ito.org.tr/
- https://bilgibankasi.ito.org.tr/tr/bilgi-bankasi/toplu-bilgi-talebi/meslek-gruplari

Doğrulanan durum:
- Firma Bilgileri Arama, NACE arama ve Toplu Bilgi Talebi kanalları vardır.
- İTO kendi Bilgi Bankasını İstanbul iş dünyası için geniş firma veri tabanı olarak tanımlar.

LANU kararı:
- İTO'nun resmî toplu bilgi talebi/çıktı kanalı birincil İstanbul sicil beslemesidir.
- Yetkili dosya CSV/XLSX/ZIP olarak LANU'ya alınır.

## 6. Yerel Ticaret / Ticaret ve Sanayi / Sanayi Odaları

Teknik gerçek:
- Odalar tek tip yayın yapmıyor.
- Bazıları kamuya açık toplu üye listesi yayımlar.
- Bazıları yalnız interaktif ve CAPTCHA'lı sorgu sağlar.
- Bazıları üye girişi/yetki ister.

Doğrulanmış açık örnekler:
- Diyarbakır Ticaret ve Sanayi Odası:
  https://dtso.org.tr/raporlar/uye-listeleri
  https://dtso.org.tr/up_files/pdf/liste-web.pdf
  Resmî sayfa üye listesinin belirli aralıklarla güncellendiğini belirtir.
- İnegöl Ticaret ve Sanayi Odası:
  https://www.itso.org.tr/uyeler
  Meslek gruplarına göre indirilebilir üye listeleri yayımlar.
- Antalya Ticaret ve Sanayi Odası:
  https://www.atso.org.tr/tr/company
  Tekil üye sorgu ekranında doğrulama kodu bulunur; anonim toplu kazıma kaynağı değildir.

LANU kararı:
1. Açıkça indirilebilir resmî üye dosyaları ayrı "public official directory" hattından otomatik alınır.
2. Login/yetki gerektiren oda dosyaları kullanıcı tarafından indirildikten sonra toplu içe alınır.
3. CAPTCHA'lı sorgular otomatik kazınmaz.
4. Her kaydın kaynak URL'si, kurum adı, dosya tarihi ve import zamanı korunur.
5. Kamuya açık üye listesinde sicil numarası yoksa kayıt işletme keşfine eklenebilir fakat "sicil kimliği doğrulandı" sayılmaz.

## 7. TOBB Sanayi Bilgi Sistemi / Kapasite Raporu

Resmî kaynaklar:
- https://sanayi.org.tr/
- https://www.tobb.org.tr/SanayiMudurlugu/Sayfalar/AnaSayfa.php

Doğrulanan durum:
- Kapasite raporları üretici firmaların iletişim, üretim kapasitesi, makine, hammadde, sermaye ve istihdam verilerini içerir.
- TOBB bunu sanayi veri tabanının kaynağı olarak tanımlar.
- Güncel site JavaScript uygulamasıdır; herkese açık güncel bulk API/export sözleşmesi doğrulanmamıştır.
- Eski TOBB eğitim dokümanlarında kayıt sonrası detaylı sorgu anlatılır; bu tek başına güncel bulk erişim kanıtı sayılmaz.

LANU kararı:
- İmalatçı kapsamını güçlendiren önemli kaynak olarak tutulur.
- Güncel izin/export yöntemi netleşmeden otomatik kazıma yapılmaz.
- Yetkili kapasite/TOBB çıktıları mevcut TOBB import kanalından alınır.

## Uygulama mimarisi

### Katman A — Açık resmî toplu dosya
Resmî kurumun kamuya açıkça yayımladığı CSV/XLSX/PDF dosya -> kaynak URL doğrulama -> dosya hash -> normalize -> duplicate kontrol -> şehir/ilçe partition -> Radar.

### Katman B — Yetkili export
MERSİS / ESBİS / TOBB / İTO / yerel oda dosyası -> kullanıcı çoklu dosya seçimi veya ZIP -> boyut/entry/format doğrulama -> sanitize -> normalize -> sicil kimliği kontrolü -> CRM enrichment.

### Katman C — Noktasal doğrulama
CAPTCHA/login ile tekil sorgu -> kullanıcı tarafından gerektiğinde kontrol. Otomatik scraping yok.

### Katman D — Harita/POI
Overture + OSM -> işletme keşfi. Resmî sicil sayılmaz. Resmî veri ile eşleşirse ACTIVE/INACTIVE/registry evidence eklenir.

## Kabul kriterleri

- Gıda/HORECA filtresi yok.
- Yetkili export'ta 1 veya yüzlerce dosya aynı işlemde alınabilir.
- ZIP içindeki desteklenen dosyalar güvenli sınırlar içinde açılabilir.
- Aynı kaynak/sicil numarası tekrar geldiğinde güncel kayıt kazanır.
- Sicil numarası olmayan açık oda listesi işletme keşfine girebilir; fakat resmî kimlik doğrulaması gibi gösterilmez.
- Kaynakta olmayan TC/Vergi No, telefon, web veya koordinat uydurulmaz.
- ACTIVE/INACTIVE ancak kaynak bunu destekliyorsa atanır; aksi halde UNVERIFIED.
- Her kritik veri zincirinde kaynak, hata ve fallback kaydı tutulur.
