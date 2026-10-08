# Efsane Kokoreç · Fiyat ve Satış Takibi

Java 21 ve JavaFX ile hazırlanmış masaüstü satış uygulaması. Satışlar bilgisayardaki SQLite dosyasında tutulur. Yerel ağ sunucusu açıkken aynı Wi-Fi ağına bağlı telefonlardan sipariş girilebilir; veriler buluta gönderilmez.

## Proje yapısı

Bu proje iki ayrı Maven modülünden oluşur:

- `backend/`: İş kuralları, modeller, rapor servisleri ve SQLite kalıcılık katmanı. JavaFX bağımlılığı yoktur.
- `frontend/`: JavaFX uygulaması ve ekranlar. Backend’i servis arayüzünden kullanır; SQL içermez.
- `frontend/src/main/java/app/frontend/server/`: Telefon sipariş sayfasını ve bilgisayardaki SQLite veritabanına yazan yerel ağ API'sini sunar.
- `frontend/src/main/resources/mobile/`: Telefonlar için tarayıcı tabanlı sipariş ekranı.
- Kök `pom.xml`: Modüllerin ortak derleme ayarları ve sıralaması.

## Özellikler

- Günlük ciro, tahmini brüt kâr, satılan miktar ve işlem sayısı özeti
- Hızlı satış ekranında masaüstü ve telefonda ürünler önce bekleyen siparişe eklenir; adetler ve satırlar kaydetmeden düzenlenir, ödeme yöntemi seçilip sipariş tek seferde kaydedilir
- Ödeme yöntemi her satış kaydıyla SQLite veritabanına yazılır ve satış detaylarında gösterilir; eski satışlar `Belirtilmedi` olarak kalır
- Günlük ve son 7 günlük görünüm
- Başlangıç/bitiş tarihi ve ödeme yöntemine göre rapor; Nakit, Kart ve IBAN cirosu/işlem özeti; ürün/porsiyon bazında miktar, ciro ve kâr
- Satış detayları ve son 12 ayın ciro/kâr grafiği
- Ürün, porsiyon, liste fiyatı ve birim maliyet yönetimi
- Ürün listesindeki çöp kutusu ile menüden ürün kaldırma; geçmiş satışlar korunur
- Satış kaydında isteğe bağlı fiyat ve not
- Satış anındaki ürün adı, porsiyon, fiyat ve maliyet kopyalanır. Sonraki menü/maliyet değişikliği geçmiş satış raporlarını değiştirmez.
- Yeni satışlarda tarih ve saat saklanır, satış tablolarında ayrı gösterilir. Saat bilgisi olmayan eski kayıtlar `—` olarak görünür.
- Yerel ağ sunucusu: masaüstü ve telefon ortak SQLite veritabanını kullanır. Telefonda ürünler düzenlenebilir bekleyen siparişe eklenir; `Siparişi kaydet` tüm satırları tek veritabanı işlemiyle kaydeder. Kaydedilen çok ürünlü sipariş raporlarda tek işlem sayılır.

## Menü başlangıç verileri

Fotoğraftan okunabilen fiyatlar başlangıçta eklenir: kuzu kokoreç çeyrek 200, yarım 300, 3 çeyrek 450, tam 600; uykuluk-badem 350; kasap sucuk yarım 200, 3 çeyrek 300, tam 400; gram satırı 600. Yaprak ciğer fiyatları görselde okunmadığı için sıfır fiyatla eklenir. Ürünler ekranından gerçek fiyatları ve maliyetleri satışa başlamadan önce düzenleyin; kâr hesabı için maliyet bilgisi girilmelidir. Tüm tutarlar TL kabul edilir.

## Derleme

Makinede JDK 21+ ve Maven 3.9+ kurulu olmalı. Proje kökünde:

```sh
mvn clean package
```

Derleme sonrasında uygulama JAR'ını açmak için:

```sh
java -jar frontend/target/efsane-kokorec-frontend-1.0.0.jar
```

Paket çıktısı `frontend/target/efsane-kokorec-frontend-1.0.0.jar` dosyasıdır. Backend, JavaFX ve SQLite bağımlılıkları uygulama JAR'ına alınır.

## Windows EXE üretimi

Windows üzerinde JDK 21+ (jpackage dahil) ve Maven kurup önce `mvn clean package` çalıştırın. Ardından proje klasöründeki `build-exe.bat` dosyasını çalıştırın. Betik her çalıştırmada yeni bir `dist-build-*` klasörü üretir; böylece önceki taşınabilir uygulama klasörünüzdeki veritabanını silmez. İçindeki `EfsaneKokorec/` klasörü taşınabilir uygulamadır. Bu klasörün tamamını, `data/efsane-kokorec.sqlite` dosyasıyla birlikte başka konuma kopyalayabilirsiniz.

Bu bir Windows EXE olduğu için EXE paketi Windows'ta üretilmelidir. jpackage, uygulamayla uyumlu çalışma zamanını paketler.

## macOS uygulaması üretimi

Mac üzerinde JDK 21+ ve Maven 3.9+ kurduktan sonra `build-mac.command` dosyasına çift tıklayın. Betik projeyi derler, `dist-mac-build-<tarih>/EfsaneKokorec.app` uygulama paketini oluşturur ve açar. `.app` paketi Mac'te Finder'dan çift tıklanarak başlatılabilir; Java çalışma zamanı pakete eklenir. Mac uygulama paketi Mac'te üretilmelidir.

## Veritabanı ve taşıma

Windows taşınabilir paketinde SQLite dosyası EXE'nin yanındaki `data/efsane-kokorec.sqlite` konumuna yazılır. Mac'te veritabanı `.app` paketinin yanındaki `data/efsane-kokorec.sqlite` dosyasıdır; uygulamayı taşırken `.app` ile `data/` klasörünü birlikte taşıyın. Uygulama açıldığında dosya yoksa otomatik oluşturulur ve örnek menü eklenir. Yedek almak için uygulamayı kapatıp veritabanı dosyasını kopyalayın. Uygulama yazma izni olmayan bir konumdaysa macOS kullanıcı verileri klasörüne kaydeder. Farklı bir klasör kullanmak için `efsane.data.dir` Java sistem özelliğini ayarlayın.

## Telefonla aynı yerel ağda sipariş

1. Masaüstü uygulamasını işletme bilgisayarında açık bırakın. Uygulama açılırken telefon sipariş sunucusu `8765` portunda başlar.
2. Bilgisayar ekranının sol altındaki `Telefon` adresini ve 6 haneli PIN'i bulun. PIN `data/phone-access-code.txt` dosyasında saklanır ve uygulama yeniden açıldığında değişmez.
3. Telefonu bilgisayarla aynı Wi-Fi ağına bağlayıp tarayıcıda ekrandaki `http://...:8765` adresini açın. PIN'i girip bağlanın.
4. Ödeme yöntemini seçin ve ürün kartında `Sepete` düğmesine dokunun. Her dokunuş bekleyen siparişe 1 porsiyon ekler. Ekrandaki sipariş listesinde adetleri artırıp azaltabilir veya ürün satırını silebilirsiniz. `Siparişi kaydet` seçilene kadar satışlar veritabanına yazılmaz; kaydetme tüm ürünleri tek seferde ekler. Ödeme yöntemi siparişin tamamına uygulanır.

Ürün kartındaki çöp kutusu, daha önce kaydedilmiş bugünkü satıştan 1 porsiyonu onay sormadan siler. Bekleyen sipariş satırındaki çöp kutusu ise yalnızca kaydedilmemiş ürünü listeden çıkarır.

İnternet bağlantısı şart değildir; ancak bilgisayar açık, uygulama çalışır ve yerel Wi-Fi/router ayakta olmalıdır. Telefon mobil veride veya başka Wi-Fi'deyken bu adres çalışmaz. İşletme bilgisayarındaki güvenlik duvarı ilk çalıştırmada Java uygulamasından gelen yerel ağ bağlantısına izin istemesi halinde izin verin. PIN'i yalnızca işletme cihazlarıyla paylaşın; bu ilk sürüm telefon siparişi ekleme ve ürün listesini görme yetkisi verir. Telefon ekranı PIN'i tarayıcıda hatırlar; tarayıcı verisini temizlerseniz PIN'i tekrar girin.
