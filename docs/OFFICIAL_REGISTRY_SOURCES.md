# Türkiye Resmî İşletme Kaynakları — Erişim ve Güven Politikası

Son araştırma: 2026-10-04

Amaç, Türkiye'deki işletme keşfini sektör kısıtı olmadan geniş tutarken **resmî sicil kanıtı** ile açık harita/POI keşif verisini birbirine karıştırmamaktır.

## Kaynak matrisi

| Kaynak | Erişim modeli | LANU kullanımı | Otomatik kazıma |
|---|---|---|---|
| MERSİS | Sisteme giriş yapan kullanıcıların `Sorgular > Firma Sorgu` ekranı; kurumlar arası entegrasyonlar ayrıca yetkilendirmeye bağlı | Kullanıcının/kurumun yetkili olarak temin ettiği CSV/XLSX/TSV/TXT veya ileride sözleşmeli entegrasyon | Hayır |
| ESBİS | Bakanlık/sicil/oda/üst kuruluş ve esnaf kullanıcıları; Türkiye/il/ilçe raporlama | Yetkili rapor/çıktı içe aktarımı | Hayır |
| TOBB Üye Firma | Kullanıcı girişi gerektirir | Yetkili çıktı içe aktarımı | Hayır |
| Yerel Oda/Borsa | Odaya göre açık rehber, CAPTCHA, giriş veya resmî toplu liste talebi olabilir | Yetkili/açık kaynak koşullarına uygun dosya içe aktarımı | Giriş/CAPTCHA atlatılmaz |
| İTO | Açık üye listeleri; ayrıca ayrı Toplu Bilgi Talebi kanalı | Açık liste alanları ve/veya resmî talep ile alınan dosya | Yalnız açık ve izinli erişim; ücretli toplu veri kullanıcı onayı olmadan alınmaz |
| TOBB Sanayi Veritabanı | Kapasite raporu olan imalat firmaları | Üretici/sanayici katmanını tamamlayıcı kaynak | Tüm işletmelerin yerine geçmez |
| Türkiye Ticaret Sicili Gazetesi | Ücretsiz üyelikle arşiv/arama; ayrıca ücretli WEB SERVİS veri aboneliği | İlan/olay ve resmî doğrulama kaynağı; ücretli servis kullanıcı onayı olmadan etkinleştirilmez | Giriş/CAPTCHA aşılmaz; yalnız yetkili çıktı içe aktarılır |
| Overture Maps Places | Açık aylık veri yayını | Türkiye geneli sektör bağımsız geniş işletme/POI keşif tabanı | Uygun veri lisansı/atıf ile batch |
| OpenStreetMap | ODbL | Overture'ı tamamlayan harita/POI keşfi | Kullanım politikasına uygun |

## 2026 TTSG ücretli WEB SERVİS yolu

Türkiye Ticaret Sicili Gazetesi resmî sayfasında 2026 için üç yıllık veri abonelik düzeyi yayımlanmıştır:

- Düzey 1: 484.932 TL/yıl
- Düzey 2: 951.792 TL/yıl
- Düzey 3: 1.427.688 TL/yıl

Düzey 3; MERSİS No, kuruluş adresi, sermaye, Vergi No/Vergi Dairesi, UAVT adres kodu, amaç-konu, ana/alt NACE ve ortak/temsilci listeleri gibi alanlar içerir. Bu yol **ücretli** olduğu için LANU'da kullanıcı onayı olmadan satın alınmaz veya etkinleştirilmez. Ayrıca Ticaret Sicili Gazetesi bir ilan/olay akışıdır; tek başına her işletmenin güncel aktif durumunu temsil eden hazır “master tablo” olarak kabul edilmez.

## Uygulama güven kuralları

1. Overture/OSM kaydı resmî sicil kanıtı değildir; `registryStatus` otomatik olarak ACTIVE yapılmaz.
2. Resmî kimlik için sicil/kayıt numarası aranır.
3. VKN/TCKN yalnız yetkili resmî dosyada açıkça varsa alınır; tahmin edilmez.
4. Aynı kaynak + aynı il için birden çok dosya birleştirilir; yeni dosya eski dosyanın tamamını sessizce silmez.
5. Aynı sicil numarasının daha yeni kaydı eski kaydı günceller.
6. Konumu bilinmeyen resmî kayıt keyfî şehre/ilçeye dağıtılmaz.
7. Pasif/terkin kaydı mevcut güncel telefon/adres/web bilgisinin üzerine yazmaz.
8. TTSG ilanı bir **olay kanıtıdır**; tek ilan şirketin bugünkü FAAL/PASİF durumunu kanıtlamaz ve tarihsel adres güncel operasyon adresinin üzerine yazılmaz.
9. "Tasfiyeye giriş" veya "tasfiye halinde" tek başına pasif/kapanmış sayılmaz; ancak tasfiye sonu/sicilden terkin gibi açık kapanış kanıtları pasiflik göstergesi olabilir.
10. Şube terkini yalnız ilgili şubenin kapanışıdır; merkez şirketin kapanışı olarak yorumlanmaz.
11. Giriş, CAPTCHA veya kurumsal yetki gerektiren sistemler otomatik kazınmaz.
12. Büyük dosya sınırı aşılırsa kayıt kesilmez; içe aktarım açık hata verir ve dosyanın parçalara bölünmesi istenir.
13. Resmî verinin kaynağı, sicil/MERSİS kimliği, ilan olayı ve güncel durum alanı CRM/Excel çıktısında birbirinden ayrı izlenebilir kalır.


## 2026-10-05 TTSG ücretsiz üyelik saha testi

Kullanıcının ücretsiz TTSG üyeliğiyle, ücretli işlem başlatmadan yapılan kontrollü testte aşağıdaki sonuçlar gözlenmiştir:

- 4 işletmenin sonuç tablosunda **ticari unvan, sicil müdürlüğü, sicil no, yayın tarihi, ilan türü, gazete sayı/sayfa** görüldü.
- Açılabilen 3 ilanın PDF görüntüsünde **adres, MERSİS no, tescil tarihi ve belge/karar ayrıntıları** doğrulanabildi.
- PDF içerikleri seçilebilir metin değildi; OCR taslağının görsel insan kontrolü olmadan güvenli olduğu doğrulanmadı.
- 4. işletmede (İstanbul / 247338-0, BİG MEDYA TEKNOLOJİ AŞ) unvan/adres değişikliği ilan PDF'si açılırken CAPTCHA görüldü. Bu noktadan sonra yeni sorgu/indirme durduruldu ve CAPTCHA denenmedi.
- Bu gözlem **"her dört PDF'de CAPTCHA"** veya kesin günlük/saatlik kota anlamına gelmez.
- Ücretsiz sonuç tablosunda Excel/CSV toplu dışa aktarma veya ücretsiz API doğrulanmadı.
- Aynı sicil altında çok sayıda ilan bulunabildiği için **ilan sayısı işletme sayısı değildir**; TTSG geçmişi şirket başına tek satıra indirgenmemelidir.
- ODTÜ Teknokent örneğinde şube terkini görüldü; bu, merkez ASELSAN şirketinin kapanışı olarak yorumlanmadı.
- İncelenen dört işletmenin de **bugünkü şirket durumu** yalnız bu ilan örnekleriyle doğrulanmadı.

### LANU'da veri modeli kararı

TTSG içe aktarımı şu alanları mümkün olduğunca ayrı tutar:

- sicil/kayıt no
- MERSİS no
- sicil müdürlüğü
- sicil/ilan olayı
- yayın tarihi
- tescil tarihi
- gazete sayı/sayfa
- kaynak referansı
- tarihsel ilandaki adres
- güncel durum (yalnız açıkça doğrulanmışsa)

TTSG olay kayıtları Overture/OSM veya güncel CRM adresinin üzerine otomatik yazılmaz ve resmî üyelik hesabı kesintisiz scraping kaynağı olarak kullanılmaz.

## Resmî referanslar

- MERSİS: https://ticaret.gov.tr/ic-ticaret/sikca-sorulan-sorular/ticaret-sicili
- MERSİS giriş: https://mersis.ticaret.gov.tr/
- ESBİS: https://ticaret.gov.tr/esnaf-sanatkarlar/esbis/genel-bilgiler
- TOBB Üye Firma: https://uye.tobb.org.tr/organizasyon/firma-index.jsp
- TOBB Oda/Borsa sorgulama: https://www.tobb.org.tr/OdaveBorsalarDB/Sayfalar/oda--borsa-sorgulama.php
- TOBB Sanayi Veritabanı: https://sanayi.tobb.org.tr/
- TTSG veri aboneliği: https://www.ticaretsicil.gov.tr/view/hizlierisim/goster.php?Guid=4fb204d4-8b72-11e9-a292-54e058904e0d
- İTO Bilgi Bankası: https://bilgibankasi.ito.org.tr/
- İTO Toplu Bilgi Talebi: https://bilgibankasi.ito.org.tr/tr/bilgi-bankasi/toplu-bilgi-talebi/meslek-gruplari
