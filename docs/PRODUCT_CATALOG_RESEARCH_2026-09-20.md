# Global Donuk Gıda — Ürün Kataloğu Araştırması (2026-09-20)

## Sonuç
Resmî Global Donuk Gıda web sitesinde şirketin HORECA kullanım senaryosu, pişmiş donuk yemek modeli, standart porsiyon, hızlı dondurma ve -18°C soğuk zincir yaklaşımı doğrulanabiliyor. Ancak kamuya açık sayfalarda SKU/ürün kodu, net gramaj, koli içi adet, fiyat ve tam ürün kataloğu veri tablosu yayınlanmış bir katalog bulunamadı.

Bu nedenle uygulama hiçbir SKU, gramaj, fiyat veya stok değerini tahmin ederek üretim verisi olarak kullanmayacaktır.

## Doğrulanmış resmî kaynaklar
- https://globaldonukgida.com/hakkimizda/
- https://globaldonukgida.com/nasil-calisir/
- https://globaldonukgida.com/neden-global-donuk-gida/
- https://globaldonukgida.com/is-ortakligi/

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
