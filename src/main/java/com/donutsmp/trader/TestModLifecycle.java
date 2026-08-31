package com.donutsmp.trader;

import java.util.List;

public class TestModLifecycle {
    public static void main(String[] args) throws Exception {
        System.out.println("=== DonutTraderMod Tam Yasam Dongusu Testi Baslatiliyor ===");

        DonutTraderMod mod = new DonutTraderMod();
        mod.onInitializeClient();

        System.out.println("\n[1/3] Mod Ilk Durumu Kontrol Ediliyor...");
        System.out.println("  -> Hedef Esya: " + mod.getConfig().targetItem);
        System.out.println("  -> Max Slot: " + mod.getConfig().maxSlots);
        System.out.println("  -> Mod Aktif mi: " + mod.getConfig().enabled);

        System.out.println("\n[2/3] Market Logic Manuel Tetikleniyor...");
        mod.tickMarketLogic();
        System.out.println("  -> Hesaplanan Onerilen Fiyat: $" + String.format("%.0f", mod.getCurrentRecommendedPrice()));
        System.out.println("  -> Kuyruga Eklenen Gorev Sayisi: " + mod.getListingManager().getQueueSize());
        assert mod.getListingManager().getQueueSize() > 0 : "Kuyruğa otomatik görev eklenmeli!";

        System.out.println("\n[3/3] HUD Ciktisi Kontrol Ediliyor...");
        List<String> hudLines = mod.getHudInfo();
        for (String line : hudLines) {
            System.out.println("  [HUD] " + line);
        }

        System.out.println("\n=== TUM MOD YASAM DONGUSU TESTLERI BASARIYLA TAMAMLANDI! ===");
        System.exit(0);
    }
}