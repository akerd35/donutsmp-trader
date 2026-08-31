# DonutSMP Fabric Mod (Minecraft 26.2) Otonom Al-Sat & Split Trader Plani

Bu plan, Minecraft 26.2 Fabric Loader uzerinde DonutSMP icin canli API destekli, 18-slot limit yonetimli, tekli/lot boluculu (split-seller) ve auto-undercut / relist yetenekli tam otonom client modunun adim adim insa edilmesini saglar.

---

## Faz 1: Temel Mimari & Canli API Istemcisi
- [x] **Adim 1.1: Fabric Mod Proje Iskeletinin Kurulmasi ve Ilk Derleme**
  - **Bitti Sayilma Sarti:** abric.mod.json, uild.ps1, kaynak kod yapisi ve Java 21 derleme zincirinin hazirlanmasi; build komutunun basariyla calisip mod jar dosyasini uretmesi.
- [x] **Adim 1.2: DonutAuction Canli API Istemcisi & Model Katmani**
  - **Bitti Sayilma Sarti:** DonutAuctionClient.java, TickerItem.java, ItemPrice.java siniflarinin yazilmasi; birim testinin (TestApiClient.java) derlenip calistirilarak canli /v2/tickers/ ve /v2/items/prices verilerini basariyla konsola yazdirmasi.

---

## Faz 2: Envanter Yonetimi & Otomatik Lot Bolme (Split Engine)
- [x] **Adim 2.1: Envanter Lot Bolucu ve Hazirlayici (InventorySplitter)**
  - **Bitti Sayilma Sarti:** 64'luk stack'leri 1x (veya belirlenen lot boyutuna) donusturen, hotbar slotuna yerlestiren ve simulasyon testinde envanter butunlugunu dogrulayan Java sinifinin ve testinin basariyla calismasi.

---

## Faz 3: AH Satis, 18 Slot Takibi & Auto-Undercut/Relist Motoru
- [x] **Adim 3.1: AH Listeleme ve 18 Slot Kuyruk Yoneticisi (AhListingManager)**
  - **Bitti Sayilma Sarti:** 18 slot limitini hafizada ve chat dinleyicisinde (item sold bildirimleri) takip eden, /ah sell <fiyat> ve yesil cam onayini yoneten modÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¼lÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¼n yazilmasi ve test edilmesi.
- [x] **Adim 3.2: Fiyat Kirilma Dedektoru ve Otomatik Yeniden Listeleme (AutoRelister)**
  - **Bitti Sayilma Sarti:** Piyasadaki en ucuz tekli ilani izleyen, fiyati kirilan eski ilani /ah listings uzerinden iptal edip yeni fiyattan (-1$) pazara koyan mantigin kodlanmasi.

---

## Faz 4: Oyun Ici Kontroller, HUD, Config & Modrinth Kurulumu
- [x] **Adim 4.1: Keybind, HUD & Ayar Sistemi (TraderConfig, TraderHud)**
  - **Bitti Sayilma Sarti:** Modu acip/kapatan tus atamasi, 18 slot durumunu ve kar durumunu gosteren HUD render katmaninin kodlanmasi.
- [x] **Adim 4.2: Insan Benzeri Gecikmeler, Nihai Derleme ve Mod Kurulumu**
  - **Bitti Sayilma Sarti:** TÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚ÂÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â±klama gecikmelerinin (jitter) eklenmesi, ./build.ps1 ile modun derlenip ModrinthApp\profiles\26.2 Fabric\mods klasorune kopyalanmasi ve kurulumun dogrulanmasi.