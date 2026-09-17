# LANU Global Donuk Satış Radarı — Veri Kaynakları

## Amaç

Bu belge, işletme verisinin uygulamaya alınmasından önce kaynak kapsamı, erişim yöntemi ve kullanım şartlarının doğrulanmasını zorunlu kılar.

## Aday resmî kaynaklar

### İstanbul Ticaret Odası Bilgi Bankası
- Kaynak kimliği: `ito-bilgi-bankasi`
- Yayıncı: İstanbul Ticaret Odası
- Kaynak: https://bilgibankasi.ito.org.tr/tr/bilgi-bankasi/firma-bilgileri
- Uygulama kapsamı: İTO'nun kendi veri/kapsam sınırları ile sınırlıdır.
- Erişim yöntemi: Önce resmî erişim ve kullanım şartları doğrulanacaktır.
- Toplu veri: "Toplu Bilgi Talebi" özelliğinin alanları, ücret/koşulları, teslim biçimi ve yeniden kullanım şartları doğrulanmadan otomatik toplu aktarım yapılmayacaktır.
- Durum: **KULLANIMA AÇILMADI — ŞARTLAR/ERİŞİM DOĞRULAMASI BEKLİYOR**

### Türkiye Ticaret Sicili Gazetesi
- Kaynak kimliği: `ticaret-sicili-gazetesi`
- Yayıncı: Türkiye Odalar ve Borsalar Birliği / Türkiye Ticaret Sicili Gazetesi
- Kaynak: https://www.ticaretsicil.gov.tr/
- Uygulama kapsamı: Sicil tescil/ilan ve ilgili resmî hizmetlerin kapsamı ile sınırlıdır.
- Erişim yöntemi: Kimlik doğrulama, captcha, görüntüleme ve kullanım koşulları dikkate alınacaktır.
- Durum: **KULLANIMA AÇILMADI — ŞARTLAR/ERİŞİM DOĞRULAMASI BEKLİYOR**

## Veri kabul kapısı

Bir kaynak aşağıdaki koşullar sağlanmadan `BusinessRepository` içine gerçek işletme kaynağı olarak bağlanamaz:

1. Kaynağın resmî adresi doğrulanır.
2. Veri kapsamı yazılı olarak belirlenir.
3. Erişim yöntemi ve otomasyon şartları doğrulanır.
4. Lisans/kullanım/yeniden kullanım şartları doğrulanır.
5. Uygulamanın kullanım amacıyla uyum doğrulanır.
6. Alan listesi ve veri kalitesi doğrulanır.
7. Son doğrulama zamanı kaydedilir.
8. Gerekli izin/erişim sağlanır.
9. Test verisiyle adapter doğrulanır.
10. CI başarılı olmadan üretim entegrasyonu tamamlanmış sayılmaz.

## Veri sınıfları

- `VERIFIED_OFFICIAL`: doğrulanmış resmî kaynaktan gelen veri.
- `VERIFIED_EXTERNAL`: kullanım şartları doğrulanmış harici kaynaktan gelen veri.
- `ESTIMATED`: açıkça tahmin olarak işaretlenen veri.
- `USER_ENTERED`: kullanıcı tarafından girilen veri.
- `STALE`: doğrulama zamanı geçerli kabul eşiğini aşmış veri.
- `UNVERIFIED`: doğrulanmamış veri; kritik işletme gerçeği olarak gösterilemez.

## Yasaklar

- Kaynak erişimini captcha/auth/erişim kontrollerini aşarak otomatikleştirmek.
- Kaynakta bulunmayan işletme, telefon, çalışan sayısı, satış potansiyeli veya benzeri bilgileri üretmek.
- Bir kaynağın kapsamını "İstanbul'daki tüm işletmeler" şeklinde genellemek.
- Kaynak şartları doğrulanmadan toplu scraping/indirme mimarisi kurmak.
