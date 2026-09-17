# LANU Global Donuk Satış Radarı — 2026-09-17 Araştırma Ödevi

## 1. Araştırmanın amacı

Bir sonraki geliştirme adımlarını gerçek kaynak, lisans/kullanım şartı ve ürün doğruluğu üzerinden belirlemek.

## 2. Nominatim / OpenStreetMap araştırması

### Bulgular
- Nominatim dış kullanıcılar tarafından kullanılabilir; ancak servis kapasitesi sınırlıdır ve ağır kullanım yasaktır.
- Resmî politika mutlak olarak en fazla 1 istek/saniye ister; uygulamadaki 1,1 saniyelik istemci beklemesi bu kural için güvenli taraftadır.
- Uygulama kimliğini belirten geçerli bir User-Agent/Referer gerekir. Mevcut adapter özel User-Agent gönderiyor.
- Otomatik tamamlama yerine kullanıcı tetiklemeli arama kullanılmalıdır.
- Arama sonucu eksiksiz bir bölge işletme veritabanı kabul edilmemelidir.
- Search API `extratags=1` ile kaynakta bulunan ek alanları (ör. web sitesi, açılış saatleri gibi) döndürebilir.
- `namedetails=1` alternatif isim/marka bilgilerini alabilir.

### Karar
Nominatim aktif gerçek kaynak olarak korunacak. İlk uygulama iyileştirmesi, kaynakta gerçekten bulunan iletişim/işletme alanlarını `extratags` üzerinden taşıyabilmek olacaktır. Çalışan sayısı, satış hacmi veya benzeri kaynak dışı değerler üretilmeyecektir.

## 3. OpenStreetMap harita araştırması

### Bulgular
- OSM verisi ODbL kapsamındadır ve görünür OpenStreetMap atfı gerekir.
- `tile.openstreetmap.org` kullanımında görünür atıf, uygulamayı tanımlayan User-Agent ve HTTP önbellekleme kurallarına uyulmalıdır.
- Toplu tile indirme, bölgeyi önceden doldurma veya arka planda sınırsız prefetch uygun değildir.
- OSMF, üçüncü taraf tile sağlayıcılarının veya kendi tile altyapısının kullanılmasını alternatif olarak belirtmektedir.

### Karar
Bu iterasyonda harita, kullanıcı tarafından elde edilen gerçek sonuçları gösterecek şekilde sınırlı ve görünür bir keşif yüzeyi olarak ele alınacaktır. Toplu şehir/ilçe tile taraması yapılmayacaktır. OSM atfı harita yüzeyinde görünür tutulacaktır.

## 4. İBB resmî veri kaynağı araştırması

İBB'nin resmî CBS altyapısında restoran, kafe, bar, internet kafe vb. sosyal noktaları içeren bir ArcGIS katmanı bulunduğu tespit edildi. Katmanda adres ve telefon gibi alanların bulunduğu belirtilmektedir.

### Karar
Bu servis doğrudan üretim kaynağı yapılmayacak. Servisin güncelliği, lisansı/yeniden kullanım şartı ve üretim kullanımına uygun erişim yöntemi ayrıca resmî şartlarla doğrulanmadan adapter eklenmeyecek. Mevcut OSM kaynağı güvenli varsayılan olarak korunacak.

## 5. Global Donuk Gıda ürün araştırması

Global Donuk Gıda'nın güncel kurumsal sayfaları; restoran, kafe, otel, catering/toplu yemek ve benzeri profesyonel işletmelere yönelik pişirilmiş donuk yemek çözümünü doğruluyor. Porsiyonlama, hızlı dondurma, -18°C soğuk zincir ve operasyonel kolaylık anlatılıyor.

### Karar
Kurumsal sitede doğrulanabilir ürün adı/SKU/gramaj/fiyat kataloğu elde edilmeden uygulama içinde spesifik ürün listesi oluşturulmayacak. Ürün eşleştirme katmanı veri kaynağı bulunduğunda açılacak.

## 6. Bu araştırmadan çıkan uygulama ödevi

1. Nominatim sorgusunda `extratags=1` desteği ekle.
2. `VerifiedBusiness` modeline yalnızca kaynakta bulunursa gösterilecek telefon, web sitesi ve çalışma saatleri alanlarını ekle.
3. İşletme detay raporunda bu alanları "kaynakta mevcut" / "kaynakta yok" mantığıyla göster.
4. Harita görünümünü gerçek arama sonuçlarına bağla ve görünür OSM atfını koru.
5. Haritayı toplu veri tarama aracına dönüştürme.
6. OSM ve ürün veri kurallarını proje durumuna kanıtlı şekilde işle.

## 7. Açık ödevler

- İBB veri servisi için yeniden kullanım/lisans/erişim doğrulaması.
- İstanbul ilçe/mahalle bazında kapsamlı ve güncel HORECA veri kümesinin yetkili kaynaktan edinilmesi.
- Gerçek Global Donuk ürün kataloğunun doğrulanmış SKU/ürün verisi olarak temin edilmesi.
- Saha CRM, kalıcı backend ve offline-first senkronizasyon.
- Gerçek cihazda canlı Nominatim + harita smoke testi.
