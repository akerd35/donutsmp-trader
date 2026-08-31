package com.donutsmp.trader.market;

public class TestListingManager {
    public static void main(String[] args) {
        System.out.println("=== AhListingManager Birim Testi Baslatiliyor ===");

        AhListingManager manager = new AhListingManager(18);

        // Test 1: Başlangıç Durumu
        System.out.println("\n[Test 1] Başlangıç Durumu ve Slot Kontrolü");
        System.out.println("  -> Maksimum Slot: " + manager.getMaxSlots());
        System.out.println("  -> Aktif Slot: " + manager.getActiveListings());
        System.out.println("  -> Boş Slot: " + manager.getAvailableSlotCount());
        assert manager.hasAvailableSlot() : "Başlangıçta boş slot olmalı!";

        // Test 2: 18 Adet Görev Ekleme ve Doldurma
        System.out.println("\n[Test 2] 18 Adet İlan Listeleme ve Doldurma");
        for (int i = 1; i <= 20; i++) {
            manager.offerTask(new AhListingManager.ListingTask("ladder", 1, 35000.0, 0));
        }

        int filled = 0;
        while (manager.hasAvailableSlot()) {
            AhListingManager.ListingTask task = manager.pollNextTask();
            if (task != null) {
                manager.markCommandSent();
                manager.onListingConfirmed();
                filled++;
            }
        }

        System.out.println("  -> Doldurulan İlan Sayısı: " + filled + " (Beklenen: 18)");
        System.out.println("  -> Kalan Kuyruk Sayısı: " + manager.getQueueSize() + " (Beklenen: 2)");
        System.out.println("  -> Aktif Slot: " + manager.getActiveListings() + "/18");
        assert manager.getActiveListings() == 18 : "Aktif slot 18 olmalı!";
        assert !manager.hasAvailableSlot() : "Dolu iken hasAvailableSlot false olmalı!";
        assert manager.pollNextTask() == null : "Limit doluyken null dönmeli!";
        System.out.println("  => TEST 2 BASARILI! (18/18 limitinde durduruldu)");

        // Test 3: Chat Satış Bildirimi Yakalama
        System.out.println("\n[Test 3] Chat Satış Bildirimi Yakalama");
        String sampleChat1 = "[Auction] Your item Ladder was sold to StevePvP for $35,000!";
        boolean caught1 = manager.onChatMessage(sampleChat1);
        System.out.println("  -> Chat Mesajı 1 Yakalandı mı: " + caught1);
        System.out.println("  -> Satılan Eşya Sayısı: " + manager.getItemsSold() + " (Beklenen: 1)");
        System.out.println("  -> Toplam Kazanılan: $" + manager.getTotalEarned() + " (Beklenen: 35000)");
        System.out.println("  -> Yeni Aktif Slot: " + manager.getActiveListings() + "/18 (Beklenen: 17)");
        assert caught1 : "Satış mesajı yakalanmalı!";
        assert manager.getActiveListings() == 17 : "Slot 17'ye düşmeli!";
        assert manager.hasAvailableSlot() : "Bir slot açılmış olmalı!";

        // Kuyruktaki bekleyen görevden 1 tanesini hemen çekme
        AhListingManager.ListingTask newTask = manager.pollNextTask();
        assert newTask != null : "Boşalan slota yeni görev atanmalı!";
        manager.markCommandSent();
        manager.onListingConfirmed();
        System.out.println("  -> Boşalan slota yeni görev başarıyla listelendi! Aktif: " + manager.getActiveListings() + "/18");

        // Test 4: Onay Butonu Tespiti
        System.out.println("\n[Test 4] Yeşil Cam Onay Butonu Tespiti");
        String[] mockItems = new String[27];
        String[] mockDisplays = new String[27];
        mockItems[11] = "red_stained_glass_pane"; // İptal
        mockDisplays[11] = "Cancel";
        mockItems[15] = "lime_stained_glass_pane"; // Onayla
        mockDisplays[15] = "Confirm Listing";

        int confirmSlot = AhListingManager.findConfirmButtonSlot(mockItems, mockDisplays);
        System.out.println("  -> Bulunan Onay Slotu İndeksi: " + confirmSlot + " (Beklenen: 15)");
        assert confirmSlot == 15 : "Onay slotu 15 olmalı!";

        System.out.println("\n=== TUM LISTING MANAGER TESTLERI BASARIYLA TAMAMLANDI! ===");
    }
}