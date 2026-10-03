# LANU Global Donuk Satış Radarı

Türkiye geneli sektör bağımsız işletme keşfi, saha CRM, rota ve ziyaret planlama platformu.

## Güncel APK

[**Güncel APK'yı indir**](https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-/releases/download/latest/Lanu-Global-Donuk-Satis-Radari-latest.apk)

[Latest Release](https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-/releases/tag/latest) · Her başarılı `main` CI sonrası otomatik güncellenir.

## Kapsam
Türkiye genelinde şehir → ilçe → mahalle → işletme seviyesinde, sektör kısıtı olmadan kaynaklandırılmış işletme keşfi. Donuk gıda, toptancı, bayi, restoran ve market yalnızca alt gruplardır; ana envanter tüm işletme ailelerini kapsar.

## Tasarım hedefleri
- Harita ve liste görünümü
- Şehir/ilçe/mahalle filtreleri
- İşletme A–Z satış raporu
- Gerçek/tahmini veri ayrımı ve güven seviyesi
- Ürün eşleştirme
- Satış fırsatı skoru
- Arama, WhatsApp, navigasyon ve web aksiyonları
- Ziyaret, numune, teklif ve sipariş CRM akışı
- Offline-first saha kullanımı
- İstanbul'dan sonra yeni şehirleri veri ekleyerek açabilme
- Mobil uyumlu satış ve yönetim dashboard'u
- Huni: Potansiyel → Ziyaret → Görüşme → Teklif → Numune → Sipariş → Aktif Müşteri
- İlçe/mahalle kırılımında fırsat ve saha yoğunluğu analizi

## Kaynak gerçekliği
Uygulama doğrulanmamış işletme bilgilerini gerçekmiş gibi sunmaz. Çalışan sayısı ve satış potansiyeli gibi alanlar doğrulanmadıysa tahmin olarak etiketlenir.

İşletme keşfi katmanlıdır:
- Overture Maps Places aylık Türkiye snapshot'ı: sektör bağımsız geniş POI/işletme tabanı, il bazlı indirilebilir ve SHA-256 ile doğrulanan veri paketleri.
- OpenStreetMap Overpass: Overture snapshot'ını tamamlayan güncel harita kaynağı.
- Nominatim: yalnızca kullanıcı tetiklemeli hedefli arama fallback'i; toplu POI indirmek için kullanılmaz.
- İTO / yerel oda / TOBB / MERSİS / ESBİS: kullanıcının yetkili kanaldan temin ettiği resmî çıktılarla sicil durumu ve firma kimliği zenginleştirmesi.

Overture ve OSM verileri resmî ticaret sicili değildir. Resmî sicil kanıtı yoksa CRM durumu **DOĞRULANMADI** kalır. Overture Places verisi için kaynak/atıf bilgisi uygulamadaki işletme detayında gösterilir; veri paketi üretimi Overture'ın güncel STAC release'ini kullanır.

## Proje anayasası
Bağlayıcı kurallar: [`docs/ANAYASA.md`](docs/ANAYASA.md)

## Proje durumu ve yol haritası
Gerçekleşen işler ile henüz planlanan özelliklerin güncel listesi: [`docs/PROJE_DURUMU.md`](docs/PROJE_DURUMU.md)
