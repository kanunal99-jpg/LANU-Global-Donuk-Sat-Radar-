# Global Donuk Gıda — Ürün Kataloğu Araştırması (2026-09-20)

## Sonuç
Resmî Global Donuk Gıda web sitesinde şirketin HORECA kullanım senaryosu, pişmiş donuk yemek modeli, standart porsiyon, hızlı dondurma ve -18°C soğuk zincir yaklaşımı doğrulanabiliyor. Ancak kamuya açık sayfalarda SKU/ürün kodu, net gramaj, koli içi adet, fiyat ve tam ürün kataloğu veri tablosu yayınlanmış bir katalog bulunamadı.

Bu nedenle uygulama hiçbir SKU, gramaj, fiyat veya stok değerini tahmin ederek üretim verisi olarak kullanmayacaktır.

## Doğrulanmış resmî kaynaklar
- https://globaldonukgida.com/hakkimizda/
- https://globaldonukgida.com/nasil-calisir/
- https://globaldonukgida.com/neden-global-donuk-gida/
- https://globaldonukgida.com/is-ortakligi/

## 2026-09-21 yeniden doğrulama

Resmî site yeniden tarandı. Kamuya açık ve arama motorlarınca indekslenmiş sayfalarda şirketin ürün modeli ve ürün grubu hakkında doğrulanabilir açıklamalar var; ancak tek tek SKU/ürün adı + ürün fotoğrafı + gramaj/koli/alerjen tablosu şeklinde güvenilir bir ürün listesi bulunamadı. Bu nedenle başka üreticilerin ürünleri Global Donuk Gıda ürünüymüş gibi eklenmeyecek ve fotoğrafı da başka kaynaktan kopyalanmayacaktır.

Doğrulanabilen içerik:
- Pişmiş donuk yemek modeli.
- Standart porsiyonlama.
- Hızlı dondurma.
- -18°C soğuk zincir.
- Türk ve dünya mutfağından farklı lezzetlerden oluşan ürün yelpazesi.

Uygulamadaki katalog veri modeli artık ayrıca:
- detaylı açıklama,
- ürün fotoğrafı HTTPS URL'si,
- resmî kaynak HTTPS URL'si,
- kaynak doğrulama zamanı
alanlarını destekliyor. Fotoğraf URL'si varsa uygulama içinde güvenli ağ görüntüleme ve yüklenemezse fallback davranışı kullanılacak.

## Uygulama kuralı
Ürün kataloğu için yetkili şirket kaynağından CSV/XLSX/PDF veya API sağlandığında:
1. SKU/ürün kodu
2. ürün adı
3. kategori
4. gramaj/porsiyon
5. koli içi adet
6. raf ömrü
7. saklama koşulu
8. alerjen bilgisi
9. satış fiyatı ve fiyat tarihi (varsa)
10. kaynak URL/doküman ve doğrulama zamanı
alanları gerçek kaynakla içeri alınacaktır.

Fiyat ve stok zaman bağımlıdır; kaynağın tarihi tutulmadan güncel gerçek gibi gösterilemez.

## Güvenlik
Ürün kataloğu kullanıcı APK'sına sabitlenmiş gizli bir veri olarak değil, doğrulanmış kaynak/DB katmanından alınacaktır. Kaynak bulunmadığında AI ürün önerisi gerçek SKU varmış gibi üretmeyecektir.
