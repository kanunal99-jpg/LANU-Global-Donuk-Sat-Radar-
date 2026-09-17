# LANU Global Donuk Satış Radarı — 2026-09-17 Araştırma Ödevi

## 1. Araştırmanın amacı

Bir sonraki geliştirme adımlarını gerçek kaynak, lisans/kullanım şartı ve ürün doğruluğu üzerinden belirlemek.

## 2. Nominatim / OpenStreetMap araştırması

### Bulgular
- Nominatim dış kullanıcılar tarafından kullanılabilir; ancak servis kapasitesi sınırlıdır ve ağır kullanım yasaktır.
- Resmî politika mutlak olarak en fazla 1 istek/saniye ister; uygulamadaki 1,1 saniyelik istemci beklemesi bu kural için güvenli taraftadır.
- Uygulama kimliğini belirten geçerli bir User-Agent/Referer gerekir. Mevcut adapter özel User-Agent gönderiyor.
- Otomatik tamamlamalı arama yerine kullanıcı tetiklemeli arama kullanılmalıdır.
- Arama sonucu eksiksiz bir bölge işletme veritabanı kabul edilmemelidir.
- Search API `extratags=1` ile kaynakta bulunan ek alanları (ör. web sitesi, açılış saatleri gibi) döndürebilir.
- `namedetails=1` alternatif isim/marka bilgilerini alabilir.

### Karar
Nominatim aktif gerçek kaynak olarak korunacak. İletişim alanları kaynakta gerçekten mevcutsa domain'e taşınacak; çalışan sayısı, satış hacmi veya benzeri kaynak dışı değerler üretilmeyecek.

## 3. OpenStreetMap harita araştırması

### Bulgular
- OSM verisi ODbL kapsamındadır ve görünür OpenStreetMap atfı gerekir.
- `tile.openstreetmap.org` kullanımında görünür atıf, uygulamayı tanımlayan User-Agent ve HTTP önbellekleme kurallarına uyulmalıdır.
- Toplu tile indirme, bölgeyi önceden doldurma veya arka planda sınırsız prefetch uygun değildir.
- OSMF, üçüncü taraf tile sağlayıcılarının veya kendi tile altyapısının kullanılmasını alternatif olarak belirtmektedir.

### Karar
Harita yalnızca kullanıcının aktif aramasından dönen gerçek sonuçları göstermelidir. Toplu şehir/ilçe tile taraması yapılmayacak. OSM atfı harita yüzeyinde görünür tutulacak.

## 4. İBB resmî veri kaynağı araştırması

İBB'nin resmî CBS altyapısında restoran, kafe, bar, internet kafe vb. sosyal noktaları içeren bir ArcGIS katmanı bulunduğu tespit edildi. Katmanda adres ve telefon gibi alanların bulunduğu belirtilmektedir.

### Karar
Bu servis doğrudan üretim kaynağı yapılmayacak. Güncellik, lisans/yeniden kullanım şartı ve üretim kullanımına uygun erişim yöntemi resmî şartlarla doğrulanmadan adapter eklenmeyecek. Mevcut OSM kaynağı güvenli varsayılan olarak korunacak.

## 5. Global Donuk Gıda ürün araştırması

Global Donuk Gıda'nın güncel kurumsal sayfaları; restoran, kafe, otel, catering/toplu yemek ve benzeri profesyonel işletmelere yönelik pişirilmiş donuk yemek çözümünü doğruluyor.

### Karar
Kurumsal sitede doğrulanabilir ürün adı/SKU/gramaj/fiyat kataloğu elde edilmeden uygulama içinde spesifik ürün listesi oluşturulmayacak. Ürün eşleştirme katmanı gerçek katalog doğrulandıktan sonra açılacak.

## 6. Offline-first / CRM araştırması

### Bulgular
- Android'in güncel offline-first mimari rehberi, ağ kullanan her repository için yerel ve ağ veri kaynağı ayrımını öneriyor.
- Yerel veri kaynağı uygulamanın okuma tarafındaki canonical/source-of-truth katmanı olmalı; UI doğrudan ağ kaynağına bağlanmamalı.
- Kritik saha yazmaları için lazy write yaklaşımı uygundur: önce yerel kaydet, sonra senkronizasyon kuyruğuna al.
- Kalıcı yazma kuyruğu ve yeniden deneme için Room + WorkManager yaklaşımı uygundur.
- Çakışmalar için sürüm/timestamp metadata'sı tutulmalı; son yazan kazanır gibi bir strateji ancak iş kuralı olarak açıkça seçilirse uygulanmalı.
- Güncel AndroidX kararlı Room sürümü 2.8.5'tir (09.09.2026). Room 3.0.3 de mevcuttur; bu proje için Android-only stabil çizgide Room 2.8.5 değerlendirmeye alınacaktır.

### Supabase/backend araştırması
- Supabase Auth kullanıcı kimliğini JWT üzerinden sağlar ve RLS ile satır bazlı yetkilendirmeye bağlanabilir.
- Exposed/public şemadaki tablolar için RLS açık olmalıdır; grants ve RLS ayrı katmanlardır.
- Kullanıcı sahipliği politikalarında `TO authenticated` ile birlikte `(select auth.uid()) = user_id` gibi sahiplik koşulu kullanılmalı.
- UPDATE politikalarında hem `USING` hem `WITH CHECK` düşünülmeli; aksi halde kullanıcı satır sahipliğini değiştirebilir.
- `service_role`/secret anahtarları Android istemcisine konulmamalıdır.
- RLS politikaları için allow/deny testleri yazılmalı.

### Uygulama kararı
CRM veri modeli; müşteri, aktivite, aşama geçişi ve senkronizasyon işlemi olarak ayrıştırıldı. Bu turda henüz kalıcı Room/Supabase backend işaretlenmedi; model ve geçiş kuralları önce test edilebilir saf domain katmanı olarak eklendi.

## 7. Uygulama ödevleri — bu tur

1. `extratags` parser için doğrudan birim test ekle.
2. CRM aşama geçişlerini merkezi domain kurallarıyla tanımla ve test et.
3. Kalıcı offline veri katmanında local-first repository tasarla.
4. Senkronizasyon kuyruğunu kalıcılaştır ve retry/conflict metadata'sını taşı.
5. Backend/Auth/RLS şemasını doğrulanabilir migration + RLS testleriyle bağla.
6. Gerçek cihazda canlı Nominatim + harita + navigasyon smoke testini yap.
7. İBB ve İTO/Ticaret Sicili için yetkili kullanım şartlarını doğrula.
8. Gerçek Global Donuk SKU kataloğunu doğrulanmış kaynakla bağla.

## 8. Kaynaklar

- Nominatim Usage Policy: https://operations.osmfoundation.org/policies/nominatim/
- OSM Tile Usage Policy: https://operations.osmfoundation.org/policies/tiles/
- Nominatim Search API: https://nominatim.org/release-docs/develop/api/Search/
- Android Offline-first: https://developer.android.com/topic/architecture/data-layer/offline-first
- AndroidX Room releases: https://developer.android.com/jetpack/androidx/releases/room
- Supabase RLS: https://supabase.com/docs/guides/database/postgres/row-level-security
- Supabase Auth: https://supabase.com/docs/guides/auth

## 9. Açık ödevler

- İBB veri servisi için yeniden kullanım/lisans/erişim doğrulaması.
- İstanbul ilçe/mahalle bazında kapsamlı ve güncel HORECA veri kümesinin yetkili kaynaktan edinilmesi.
- Gerçek Global Donuk ürün kataloğunun doğrulanmış SKU/ürün verisi olarak temin edilmesi.
- Saha CRM, kalıcı backend ve offline-first senkronizasyon.
- Gerçek cihazda canlı Nominatim + harita smoke testi.
