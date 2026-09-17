# LANU Global Donuk Satış Radarı — Veri Kaynakları

## Amaç

İşletme verisinin uygulamaya alınmasında kaynak kapsamı, erişim yöntemi, kullanım şartı, veri kalitesi ve son doğrulama zamanı zorunludur.

## Aktif gerçek kaynak

### OpenStreetMap Nominatim
- Kaynak kimliği: `osm-nominatim`
- Yayıncı: OpenStreetMap Foundation
- Kaynak: https://nominatim.openstreetmap.org/
- Lisans/veri: ODbL; uygulamada görünür OpenStreetMap atıfı bulunmalıdır.
- Erişim: Kullanıcı tarafından başlatılan tekil arama.
- Uygulama davranışı: Otomatik tamamlama, periyodik tarama ve bir alanın bütün POI'lerini sistematik olarak indirme yapılmaz.
- Hız sınırı: Uygulama tarafında en az 1,1 saniye aralık uygulanır ve özel User-Agent gönderilir.
- Kapsam: Arama sonucu dönen OSM kayıtları; **İstanbul'daki tüm işletmelerin eksiksiz veri tabanı değildir**.
- Alanlar: ad, şehir, ilçe, mahalle (varsa), koordinat (varsa), kategori (varsa), kaynak adresi (varsa).
- Durum: **BAĞLANDI — GERÇEK KULLANICI TETİKLEMELİ ARAMA AKTİF**

## Bekleyen resmî kaynaklar

### İstanbul Ticaret Odası Bilgi Bankası
- Kaynak kimliği: `ito-bilgi-bankasi`
- Yayıncı: İstanbul Ticaret Odası
- Kaynak: https://bilgibankasi.ito.org.tr/tr/bilgi-bankasi/firma-bilgileri
- Resmî sitede firma araması; Ticaret Sicil/Oda Sicil, ticaret unvanı, NACE kodu ve meslek grubu üzerinden sunuluyor.
- "Toplu Bilgi Talebi" özelliği mevcut; fakat otomatik toplu aktarım için kapsam, teslim biçimi, ücret/koşullar ve yeniden kullanım şartları ayrıca doğrulanmalıdır.
- Durum: **ÜRETİM TOPLU ENTEGRASYONU BEKLİYOR — ERİŞİM/KULLANIM ŞARTI DOĞRULANMALI**

### Türkiye Ticaret Sicili Gazetesi
- Kaynak kimliği: `ticaret-sicili-gazetesi`
- Yayıncı: Türkiye Odalar ve Borsalar Birliği / Türkiye Ticaret Sicili Gazetesi
- Kaynak: https://www.ticaretsicil.gov.tr/
- Unvan sorgulama ve ilan görüntüleme hizmetleri vardır; üyelik/giriş ve bazı sorgularda doğrulama kontrolleri bulunur.
- 2026 veri aboneliği web servisleri ücretlidir; uygulamaya ücretli abonelik eklenmeden önce kullanıcı/onay gereklidir.
- Durum: **BAĞLANMADI — ÜCRETLİ VERİ ABONELİĞİ ONAYI/EKİP ERİŞİMİ BEKLİYOR**

## Veri kabul kapısı

Bir kaynak aşağıdaki koşullar sağlanmadan doğrulanmış üretim kaynağı olarak bağlanamaz:

1. Resmî kaynak adresi doğrulanır.
2. Veri kapsamı belirlenir.
3. Erişim yöntemi ve otomasyon şartları doğrulanır.
4. Lisans/kullanım/yeniden kullanım şartları doğrulanır.
5. Uygulamanın kullanım amacıyla uyum doğrulanır.
6. Alan listesi ve veri kalitesi doğrulanır.
7. Son doğrulama zamanı kaydedilir.
8. Gerekli izin/erişim sağlanır.
9. Adapter testleri geçer.
10. CI başarılı olmadan üretim entegrasyonu tamamlanmış sayılmaz.

## Veri sınıfları

- `VERIFIED_OFFICIAL`: doğrulanmış resmî kaynaktan gelen veri.
- `VERIFIED_EXTERNAL`: şartları doğrulanmış harici kaynaktan gelen veri.
- `ESTIMATED`: açıkça tahmin olarak işaretlenen veri.
- `USER_ENTERED`: kullanıcı tarafından girilen veri.
- `UNVERIFIED`: doğrulanmamış veri.

Tazelik ayrı tutulur: `FRESH` / `STALE`. Tazelik, kaynağın kökenini değiştirmez.

## Yasaklar

- Captcha/auth/erişim kontrollerini aşarak kaynağı otomatikleştirmek.
- Kaynakta bulunmayan işletme, telefon, çalışan sayısı, satış potansiyeli veya benzeri bilgileri gerçekmiş gibi üretmek.
- Bir kaynağın kapsamını "İstanbul'daki tüm işletmeler" şeklinde genellemek.
- Kullanım şartları doğrulanmadan toplu scraping/indirme yapmak.
