package com.donutsmp.trader.market;

import com.donutsmp.trader.api.DonutAuctionClient;

import java.util.ArrayList;
import java.util.List;

public class TestAutoRelister {
    public static void main(String[] args) {
        System.out.println("=== AutoRelister Birim Testi Baslatiliyor ===");

        DonutAuctionClient client = new DonutAuctionClient();
        AutoRelister relister = new AutoRelister(client, 10000.0, 100.0); // 10k taban fiyat, 100$ min fark

        // Mock Aktif İlanlarımız
        List<AutoRelister.ActiveListing> myListings = new ArrayList<>();
        myListings.add(new AutoRelister.ActiveListing(0, "ladder", 1, 49999.0));
        myListings.add(new AutoRelister.ActiveListing(1, "totem_of_undying", 1, 150000.0));
        myListings.add(new AutoRelister.ActiveListing(2, "water_bucket", 1, 30000.0));

        System.out.println("\n[Test 1] Aktif İlanlar Değerlendiriliyor...");
        List<AutoRelister.RelistDecision> decisions = relister.evaluateListings(myListings);

        System.out.println("Alınan Relist Kararı Sayısı: " + decisions.size());
        for (AutoRelister.RelistDecision decision : decisions) {
            System.out.println("  -> " + decision);
            System.out.println("     Gerekçe: " + decision.reason);
        }

        assert !decisions.isEmpty() : "Piyasa fiyatı daha düşük olan eşyalar için relist kararı çıkmalı!";
        System.out.println("  => TEST 1 BASARILI! (Undercut tespiti ve hedef fiyat hesaplandı)");

        // Test 2: Taban Fiyat Koruması
        System.out.println("\n[Test 2] Taban Fiyat (Price Floor) Koruması");
        double floorPrice = relister.getMinPriceFloor();
        System.out.println("  -> Ayarlı Taban Fiyat: $" + floorPrice);
        for (AutoRelister.RelistDecision d : decisions) {
            assert d.targetPrice >= floorPrice : "Hedef fiyat asla taban fiyatın altına inmemeli!";
        }
        System.out.println("  => TEST 2 BASARILI! (Zararına satış engellendi)");

        // Test 3: /ah listings Ekranında Hedef Slot Tespiti
        System.out.println("\n[Test 3] /ah listings Menüsünde İptal Edilecek Slotu Bulma");
        String[] screenItems = new String[54];
        double[] screenPrices = new double[54];

        screenItems[10] = "ladder";
        screenPrices[10] = 49999.0;
        screenItems[11] = "totem_of_undying";
        screenPrices[11] = 150000.0;

        int foundSlot = AutoRelister.parseMyListingsScreenSlot(screenItems, screenPrices, "ladder", 49999.0);
        System.out.println("  -> Bulunan İptal Slotu: " + foundSlot + " (Beklenen: 10)");
        assert foundSlot == 10 : "Slot 10 doğru tespit edilmeli!";

        System.out.println("\n=== TUM AUTORELISTER TESTLERI BASARIYLA TAMAMLANDI! ===");
    }
}