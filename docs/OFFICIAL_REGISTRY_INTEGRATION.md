# Türkiye Resmî İşletme Sicili Entegrasyon Stratejisi

## Amaç

LANU'nun "Türkiye'deki tüm işletmeler" hedefi tek bir kaynağa bağlanmaz. Harita/POI
verisi keşif içindir; **AKTİF / PASİF / DOĞRULANMADI** gibi resmî sicil durumu ancak
yetkili resmî kayıttan kanıtlandığında atanır.

Bu belge 2026-10-04 tarihinde resmî kaynaklar incelenerek güncellenmiştir.

## Kaynak matrisi

| Kaynak | Erişim | Otomasyon | LANU kullanımı |
|---|---|---|---|
| TOBB Oda/Borsa Web Servisi | Yetkili API/Secret/OdaKodu; WS-Security UsernameToken PasswordDigest | Evet, yetkili bağlantıda | Tam üye çekimi + tarih bazlı yeni kayıt + üye değişiklikleri; JSON/CSV/XLSX fallback |
| İTO / yerel ticaret odaları | Resmî toplu bilgi talebi / oda çıktısı | Kaynağın verdiği export ölçüsünde | CSV/XLSX içe aktarma; oda sicil, unvan, durum, NACE, adres, telefon/web |
| MERSİS | Kullanıcı girişi / kurumsal veri paylaşımı | Yalnız yetkili erişim/protokol varsa | Yetkili export içe aktarımı; kamuya açık olmayan endpoint kazınmaz |
| ESBİS | e-Devlet/yetkili kullanıcı | Amaç ve yetki ile sınırlı | Yetkili export; genel kurul üye listeleri ulusal ticari envanter amacıyla kullanılmaz |
| TTSG / Ticaret Sicili Gazetesi | Firma/ilan sorgusu | Toplu ulusal envanter için birincil kaynak değil | Sicil/ilan doğrulama ve tarihsel kontrol |
| Overture Maps Places | Açık veri snapshot | Evet | Sektör bağımsız geniş işletme/POI keşfi; resmî sicil statüsü vermez |
| OpenStreetMap Overpass | ODbL | Evet | Harita/işletme keşfi ve koordinat/adres tamamlayıcı |
| Nominatim | Kullanıcı tetiklemeli arama | Sınırlı fallback | Hedefli arama; toplu POI indirme için kullanılmaz |

## TOBB Oda/Borsa yetkili servis

TOBB'un resmî "Oda/Borsa İçin Web Servis Programlama Kılavuzu ve API Referansı"
servisin SOAP/WS-I Basic Profile 1.1 kullandığını ve erişimin WS-Security
UsernameToken + PasswordDigest ile korunduğunu belirtir.

Servis isteği:
- metaData
- servisAdi
- Versiyon
- Girdiler (JSON string)

Metadata:
- transactionId
- odaBorsaNo
- terminalNo
- istekYapanKullanici
- istekZamani
- b64sha256istekOzeti

LANU için temel operasyonlar:
1. **odayaAitUyeleriSorgula** — kriter verilmezse ilgili odanın tüm üyeleri.
2. **uyeKartiSorgula** — seçili üyenin ayrıntılı kartı; adres, NACE, telefon ve diğer
   alanlar.
3. **tarihAraligindaKaydolanUyeleriSorgula** — belirli tarih aralığında kaydolan
   üyeler.
4. **uyeDegisiklikSorgula** — değişen üyeleri artımlı senkron için sorgular.

Yetkili servisten gelen işletme tarafı alanlarından LANU'nun kullanacağı çekirdek set:
- uyeOid / kaynak kayıt kimliği
- MERSİS no
- ticaret sicil / oda sicil no
- firma unvanı
- tabela unvanı (varsa)
- üyelik/sicil durumu
- firma tipi
- NACE / ana faaliyet
- il / ilçe / mahalle / açık adres
- kurumsal telefon
- web sitesi / kurumsal e-posta
- vergi numarası (yetkili kayıtta işletme alanı olarak verilmişse)

**TCKN, ortak listesi, temsilci kişisel bilgileri ve başka kişi kayıtları ulusal
işletme envanterine otomatik alınmaz.**

### Güvenlik kararı

TOBB API/Secret **Android APK içine konmaz** ve public GitHub reposuna yazılmaz.
Yetkili servis bağlantısı kurulacaksa secret'lar sunucu/CI secret store içinde tutulur.
Android yalnız normalize edilmiş, izinli işletme snapshot'ını veya kullanıcının resmî
export dosyasını tüketir.

Canlı servis entegrasyonu yapılırken WSDL/endpoint ve servis versiyonu TOBB tarafından
yetkilendirilen hesaptan alınmalı; bunlar tahmin edilmez/hardcode edilmez.

## MERSİS

Ticaret Bakanlığı, MERSİS'in şirket ve ticari işletmelerin merkezi kayıt sistemi
olduğunu ve kamu kurumlarıyla anlık veri paylaşımı yaptığını belirtmektedir. Sisteme
giriş yapan kullanıcılar "Sorgular > Firma Sorgu" ile temel şirket bilgilerine
ulaşabilir.

LANU kuralı:
- anonim/captcha kazıma yok,
- yetkili kurumsal veri paylaşım servisi/protokolü varsa sunucu tarafında bağlanır,
- aksi halde kullanıcının resmî export'u CSV/XLSX/JSON olarak içe alınır.

## ESBİS

ESBİS Türkiye genelindeki esnaf ve sanatkâr sicil işlemlerinin merkezi sistemidir.
Bakanlığın üye listesi ekranındaki bilgiler belirli kanuni amaçla sınırlıdır ve
KVKK'ya aykırı başka amaçla kullanılamaz.

LANU kuralı:
- genel kurul üye listeleri ticari lead havuzu olarak toplanmaz,
- yalnız LANU kullanımı için açıkça yetkilendirilmiş resmî export/veri paylaşımı
  kullanılır,
- kişisel üye alanları otomatik envantere alınmaz.

## İTO ve yerel odalar

İTO ve bazı odalar toplu firma bilgisi talebi/export hizmeti sunmaktadır. Bu dosyalar
LANU'nun ortak importer'ından geçirilir. Dosyada il yoksa kullanıcı tarafından seçilen
oda/şehir bağlamı kullanılır.

## Ortak normalize zinciri

Her resmî veri girişi:

1. validate
2. sanitize
3. normalize
4. source provenance ekle
5. sicil/MERSİS kimliği doğrula
6. şehir partition'ına yaz
7. mükerrerleri kimlik + unvan + konum + telefon üzerinden birleştir
8. AKTİF/PASİF/DOĞRULANMADI semantiğini uygula
9. CRM sahibi (owner) izolasyonunu koru
10. log + test

Pasif/terkin kaydı mevcut güncel telefon/adres/web bilgisinin üzerine yazmaz.

## Eksikliği dürüst işaretleme

"Türkiye'deki tüm işletmeler" bir hedef kapsamdır; hiçbir kaynak tek başına hukuken ve
teknik olarak %100 kapsama garantisi vermez. LANU bu nedenle kayıtların kaynağını ve
doğrulama durumunu saklar. Resmî kanıt yoksa durum **DOĞRULANMADI** olarak kalır.

## Resmî referanslar

- TOBB Oda/Borsa test uygulaması: https://webservistest.tobb.org.tr/
- TOBB Oda/Borsa Web Servis Kılavuzu: https://webservistest.tobb.org.tr/Kilavuz2.pdf
- MERSİS: https://www.ticaret.gov.tr/ic-ticaret/ticaret-sicili/merkezi-sicil-kayit-sistemi-mersis
- MERSİS SSS: https://ticaret.gov.tr/ic-ticaret/sikca-sorulan-sorular/ticaret-sicili
- ESBİS genel bilgiler: https://ticaret.gov.tr/esnaf-sanatkarlar/esbis/genel-bilgiler
- Esnaf ve Sanatkâr Portalı: https://esnaf.ticaret.gov.tr/
