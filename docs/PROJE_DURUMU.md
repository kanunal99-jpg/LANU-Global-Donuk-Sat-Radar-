# LANU Global Donuk Satış Radarı — Tarihsel Proje Durumu

> **Bu dosya güncel ürün durumu için kaynak değildir.** Önceki dönemlerde elle tutulan kontrol listesi zamanla kod, CI, Supabase ve Release gerçekliğinden koptuğu için ikinci bir “doğru kaynak” olarak kullanılmamalıdır.

## Güncel kaynaklar

- Bağlayıcı proje standardı: [`../PROJECT_CONSTITUTION.md`](../PROJECT_CONSTITUTION.md)
- Aktif ürün tamamlama / kabul kapsamı: GitHub Issue **#29**
- Aktif geliştirme ve exact-HEAD kanıtları: GitHub Pull Request **#30**
- Kök neden → düzeltme → doğrulama günlüğü: [`PROBLEM_LOG.md`](PROBLEM_LOG.md)
- Gerçek build/test sonucu: repository **GitHub Actions** çalışmaları
- Yayınlanmış cihaz APK'sı: yalnız başarılı `main` CI sonrasında güncellenen GitHub **Release `latest`**

## Neden bu dosya sadeleştirildi?

Eski içerikte tamamlanmış ve tamamlanmamış maddeler elle işaretleniyordu. Bu yaklaşım; kod ilerlediğinde eski `[ ]` maddelerinin kalmasına, eski CI numaralarının güncel kanıt gibi görünmesine ve Supabase/RLS/senkronizasyon gibi alanlarda yaşayan sistemle dokümantasyonun çelişmesine yol açıyordu.

Bu nedenle güncel durum artık tek bir statik Markdown kontrol listesinde tekrar tutulmaz. Bir özellik yalnız ilgili kod + test + exact commit CI kanıtı varsa tamamlanmış sayılır. Fiziksel cihaz kabulü gerektiren maddeler emulator/CI başarısıyla tamamlanmış kabul edilmez.

## Tarihçe

Bu dosyanın önceki ayrıntılı kontrol listeleri Git geçmişinde korunmaktadır. Tarihsel inceleme gerekiyorsa dosyanın commit geçmişi kullanılmalıdır; eski snapshot ürünün bugünkü durumunu temsil etmez.
