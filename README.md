# 🍩 DonutSMP Autonomous Split-Trader Client Mod

[![Minecraft Version](https://img.shields.io/badge/Minecraft-26.2-brightgreen.svg)](https://minecraft.net/)
[![Fabric Loader](https://img.shields.io/badge/Fabric-0.19.3-blue.svg)](https://fabricmc.net/)
[![Java Version](https://img.shields.io/badge/Java-25-orange.svg)](https://www.azul.com/downloads/?version=java-25-ea&os=windows)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)

DonutSMP (`donutsmp.net`) sunucusu için geliştirilmiş, canlı piyasa API'si (`donut.auction`) destekli, 18-slot limit optimizasyonlu, tekli/lot bölücülü (**Split-Selling**) ve fiyat kırılma korumalı (**Auto-Undercut / Relist**) tam otonom Fabric istemci (client) modudur.

---

## 🎯 Projenin Amacı ve Ticaret Stratejisi

DonutSMP pazarında oyuncular genellikle acil ihtiyaç duydukları eşyaları (Merdiven, Su Kovası, Totem, End Kristali vb.) 64'lük toplu paketler yerine **1 adet (tekli)** olarak satın alırlar.

* **Toplu Hammadde Maliyeti:** 64 adet Merdiven veya Log craft maliyeti: `~$2.000 - $5.000`
* **Tekli Satış Değeri:** Pazarda tekli merdiven birim fiyatı: `~$10.000 - $35.000`
* **Kâr Marjı:** 64 adetlik bir yığından tek tek satıldığında `~$640.000 - $2.200.000` kazanç elde edilir!

Bu mod, 64'lük yığınları insan hatası ve eşya kaybı olmadan **1x (tekli)** parçalara böler, DonutSMP pazarında **18 slot dolana kadar** arka arkaya satışa koyar, bir eşyanız satıldığı anda chatten algılayıp 1 saniyede yerine yenisini koyar ve rakipleriniz fiyat kırdığında otomatik olarak 1$ altına günceller.

---

## ✨ Temel Özellikler

### 1. ⚡ Sıfır Kayıplı Deterministik Lot Bölücü (`InventorySplitter`)
* 64'lük eşyaları 3 adımlı sanal tıklama algoritmasıyla (Sol tık al -> Sağ tıkla 1 adet bırak -> Sol tık kalanı iade et) böler.
* Eşyaların yere düşmesini veya imleçte takılı kalmasını %100 engeller.
* 64'lük yığının tamamının yanlışlıkla ucuza satılmasını imkansız kılar.

### 2. 📡 Canlı Piyasa API'si & Canlı In-Game Lore Tarayıcısı
* **Web API (`donut.auction/v2/`):** 45 ana ürünün (Totem, Su Kovası, Shulker vb.) anlık en ucuz fiyatlarını ve likiditelerini çeker.
* **In-Game AH Tarayıcısı:** `/ah` menüsü açıldığında rakiplerin fiyatlarını lore üzerinden tarar ve otomatik olarak rakibin **1$ altına** fiyat belirler *(sabit tutar ya da yüzde olarak ayarlanabilir)*.
* **Taban Fiyat Koruması (`minPriceFloor`):** Belirlediğiniz taban fiyatın altına asla inmez (zararına satış engeli).

### 3. 🤖 Tam Otonom & Sunucu Tabanlı 18-Slot Yönetimi
* Sizin hiçbir menü açmanıza gerek yoktur.
* 18 slot dolana kadar arka arkaya listeler.
* DonutSMP'den gelen *"You have too many listed items"* mesajını ilk milisaniyede yakalayıp kilitlenir.
* Bir eşyanız satıldığında veya siz bir ilanı çektiğinizde sohbetteki bildirimi anında yakalar, slotu boşa çıkarır ve çantanızdaki sıradaki eşyayı satışa koyar.

### 4. 🛡️ Güvenlik & Anti-Spam Korumaları
* **Savaş Modu Koruması (Combat Tag Guard):** Savaşta olduğunuzda (*"You cannot do this in combat"*) otomatik olarak **20 saniye** duraklar.
* **İnsan Benzeri Gecikme (1.4s Cooldown):** Sunucu antispam sistemlerine takılmamak için komutlar arasına güvenli bekleme koyar.
* **Chat / ESC Duraklatma:** Sohbette yazı yazarken veya ESC menüsündeyken mod otomatik olarak durur.
* **Başlangıç Güvenliği:** Oyuna her girildiğinde güvenlik gereği daima `[PASİF]` başlar.

---

## 🎮 Oyun İçi Komutlar & Kısayollar

| Komut | Tab Desteği | Açıklama |
| :--- | :--- | :--- |
| **`/trader fullauto <eşya>`** | `[TAB]` | **Tek komut:** hedefi ayarlar, piyasayı kendisi okur, satmaya başlar |
| **`K`** veya **`Ğ`** | - | Modu anında Açar / Kapatır *(Minecraft Keybinds menüsünden değiştirilebilir)* |
| **`/trader on`** / **`/trader off`** | `[TAB]` | Modu başlatır / duraklatır |
| **`/trader item <eşya>`** | `[TAB]` | Hedef eşyayı değiştirir *(Örn: `ladder`, `water_bucket`, `totem_of_undying`)* |
| **`/trader price <fiyat>`** | Sayı | Satış fiyatını belirler *(Örn: `/trader price 25000`)* |
| **`/trader lot <adet>`** | `1..64` | Kaçar kaçar satılacağını ayarlar *(Varsayılan: 1x)* |
| **`/trader slots <sayı>`** | `1..54` | Maksimum slot limitinizi ayarlar *(Varsayılan: 18)* |
| **`/trader active <sayı>`** | Sayı | Aktif ilan sayısını eşitler *(Örn: `/trader active 0`)* |
| **`/trader reset`** | `[TAB]` | İlan sayacını sıfırlar |
| **`/trader floor <fiyat>`** | Sayı | Taban fiyat koruması koyar *(Zararına satış engeli)* |
| **`/trader undercut on\|off`** | `[TAB]` | Piyasayı takip et *(varsayılan)* ya da `/trader price` fiyatını sabitle |
| **`/trader undercut <dolar>`** | Sayı | Rakipten kaç dolar ucuz *(Varsayılan: 1 — rakip 10.000 ise 9.999)* |
| **`/trader undercut percent <yüzde>`** | Sayı | Sabit yerine yüzdesel fark *(büyük olan uygulanır)* |
| **`/trader sim on\|off`** | `[TAB]` | Simülasyon: eşyayı ayırır ama `/ah sell` göndermez |
| **`/trader dump on\|off`** | `[TAB]` | Açılan menülerin yapısını dosyaya yazar *(geliştirme için)* |
| **`/trader update`** | `[TAB]` | GitHub'daki son sürümü indirir *(yeniden başlatınca uygulanır)* |
| **`/trader reload`** | `[TAB]` | Ayar dosyasını diskten yeniden okur |
| **`/trader status`** | `[TAB]` | Anlık durumu, aktif slotları ve toplam kasayı gösterir |
| **`/trader help`** | `[TAB]` | Oyun içi detaylı kullanım rehberini açar |

---

## 🏗️ Mimari ve Proje Yapısı

```text
com.donutsmp.trader/
├── DonutTraderMod.java          # Fabric ClientModInitializer & ana yaşam döngüsü
├── api/
│   ├── DonutAuctionClient.java  # HTTP Client (donut.auction v2 tickers & prices)
│   ├── AhPriceParser.java       # In-game lore & tooltip fiyat ayrıştırıcı
│   └── model/
│       ├── TickerItem.java      # Canlı pazar DTO
│       └── ItemPrice.java       # Likidite & 24s satış hacmi DTO
├── inventory/
│   ├── InventorySplitter.java   # 3 adımlı sanal lot bölme motoru
│   └── InventoryActionHelper.java # Minecraft container slot taşıma yardımcısı
├── market/
│   ├── AhListingManager.java    # 18-slot limit & chat bildirim yakalayıcı
│   └── AutoRelister.java        # Fiyat kırılma & yeniden listeleme analizcisi
├── gui/
│   ├── TraderCommands.java      # Brigadier komutları ve akıllı Tab tamamlayıcı
│   └── TraderHud.java           # Canlı HUD arayüzü
└── config/
    └── TraderConfig.java        # JSON tabanlı kalıcı ayar sistemi
```

---

## 📦 Derleme ve Kurulum

### Gereksinimler
* **Minecraft:** 26.2 (Fabric Loader 0.19.3, Fabric API 0.158.0+26.2)
* **Java:** 25+ *(Gradle/Loom gerekli JDK'yi kendisi indirir)*

26.2 obfuscate edilmeden dağıtıldığı için Yarn mapping'i yoktur; proje Mojang
isimlerine karşı, `mappings` bağımlılığı olmadan derlenir.

### Derleme
```bash
./gradlew build      # Windows: .\build.ps1
```
Çıktı: `build/libs/donutsmp-trader-1.0.0.jar`

### Kurulum
Jar'ı Minecraft **kapalıyken** profilinizin `mods` klasörüne kopyalayın.
macOS'ta:
```bash
./install.sh "Fabric 26.2"
```
Çalışan bir oyunun altında jar'ı değiştirmeyin: JVM zip'i açık tutar ve oyun
`ZipException` ile çöker.

### Hızlı başlangıç
```
/trader floor 5000
/trader fullauto ladder
```
Gerisi otomatiktir: mod piyasayı kendisi sorar (`/ah search <eşya>`), en ucuz
rakibin 1$ altına fiyat belirler, 64'lük yığından lot ayırır ve slot dolana
kadar listeler. Diğer komutların hepsi ince ayar içindir.

Mod piyasayı **6 saniyede bir** kendisi sorup fiyatı yeniden hesaplar
(`scanIntervalSeconds`, 3–600 sn). Her tarama menüyü kısa süreliğine açıp
kapattığı için sık aralık oyun oynamayı zorlaştırır ve sunucunun komut hız
sınırına yaklaştırır; rahatsız ederse büyütün. Okunan fiyat, aralığın üç katı kadar geçerli sayılır;
o süre içinde tarama tutmazsa API fiyatına düşer ama satış durmaz.

Sunucunuzda arama komutu farklıysa `config/donutsmp_trader.json` içindeki
`marketCommand` alanını değiştirin (varsayılan `ah search %s`).

### Güncelleme
Oyun içinde `/trader update`: GitHub Release'inden son jar'ı indirir, sha256'sını
release'teki değerle karşılaştırır ve `<oyun klasörü>/donutsmp-trader-update/`
içinde bekletir. Değişim oyun **kapanırken** yapılır — çalışan bir jar'ın
üzerine yazılamaz. Eski jar silinemezse (Windows dosyayı kilitleyebilir)
güncelleme uygulanmaz ve oyun eski sürümle sorunsuz açılmaya devam eder; jar'ı
elle kopyalamanız yeterlidir.

Yeni sürüm yayınlamak için etiket atmanız yeterli; `release.yml` derleyip jar'ı
ve sha256'sını release'e ekler:
```bash
git tag v1.0.1 && git push origin v1.0.1
```

### Testler
```bash
./gradlew test       # 48 test: fiyat ayrıştırıcı, slot sayacı, lot bölücü
```

---

## ⚠️ Bilinen Sınırlar

| Konu | Durum |
| :--- | :--- |
| **Var olan ilanların yeniden fiyatlanması** | Piyasa yükselince yeni ilanlar doğru fiyattan gider, ama **zaten asılı olan ilanlar eski fiyatta kalır**. `AutoRelister` bunu tespit edip logluyor; `/ah listings` menüsünden iptal edip yeniden koyma akışı yazılmadı. |
| **Sohbet bildirimi eşleşmesi** | Slot sayacı yalnızca "your/you" geçen bildirimleri kendi ilanı sayar. DonutSMP metni farklıysa sayaç düşmez; `/ah listings` menüsünü açmak sayacı gerçekle eşitler. |
| **`/ah listings` başlık eşleşmesi** | Ekran, başlığında `your listings` / `my listings` / `ilanlar` geçtiğinde tanınır. Sunucudaki başlık farklıysa senkron çalışmaz — `/trader active <sayı>` ile elle eşitleyin. |
| **Kendi ilanımızın ayırt edilmesi** | Tarama, lore'unda kullanıcı adınız geçen ilanları atlar; ad yazmıyorsa kendi astığımız fiyatlar (son 64 tanesi) atlanır. Sunucu satıcı adını lore'a hiç yazmıyorsa ve fiyatınıza eşit gerçek bir rakip varsa o rakip görülmez — fiyat düşmez, güvenli taraf. |
| **Onay ekranı** | `findConfirmButtonSlot` yazıldı ama akışa bağlı değil; `/ah sell` sonrası onay penceresini elle kapatmanız gerekebilir. |

## 👥 Geliştiriciler & Katkıda Bulunanlar
* **Burak Amasya** ([@BurakAmasyaa](https://github.com/BurakAmasyaa))
* **Kaan (akerd35)** ([@akerd35](https://github.com/akerd35))

---

## 📜 Lisans
Bu proje [MIT](LICENSE) lisansı altında korunmaktadır.