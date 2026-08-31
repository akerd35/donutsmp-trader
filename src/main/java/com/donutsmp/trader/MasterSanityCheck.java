package com.donutsmp.trader;

import com.donutsmp.trader.api.TestApiClient;
import com.donutsmp.trader.inventory.TestInventorySplitter;
import com.donutsmp.trader.market.TestAutoRelister;
import com.donutsmp.trader.market.TestListingManager;

public class MasterSanityCheck {
    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("      DONUT TRADER MASTER NIHAI KONTROL & BUTUNLESIK TEST");
        System.out.println("===============================================================");

        long start = System.currentTimeMillis();

        try {
            System.out.println("\n>>> [1/6] API Istemcisi & Model Katmani Test Ediliyor...");
            TestApiClient.main(new String[0]);

            System.out.println("\n>>> [2/6] Envanter Lot Bolucu Simulasyonu Test Ediliyor...");
            TestInventorySplitter.main(new String[0]);

            System.out.println("\n>>> [3/6] AH 18-Slot Kuyruk & Chat Parser Test Ediliyor...");
            TestListingManager.main(new String[0]);

            System.out.println("\n>>> [4/6] Auto-Undercut & Taban Fiyat Motoru Test Ediliyor...");
            TestAutoRelister.main(new String[0]);

            System.out.println("\n>>> [5/6] /trader Komutlari ve Tab Tamamlama Test Ediliyor...");
            TestCommands.main(new String[0]);

            System.out.println("\n>>> [6/6] Kapsamli Stres ve Hata Senaryolari Test Ediliyor...");
            TestComprehensiveSuite.main(new String[0]);

            long elapsed = System.currentTimeMillis() - start;
            System.out.println("\n===============================================================");
            System.out.println("   SONUC: TUM MODUL VE TESTLER %100 HATASIZ TAMAMLANDI! (" + elapsed + " ms)");
            System.out.println("===============================================================");
        } catch (Throwable t) {
            System.err.println("TEST BASARISIZ OLDU: " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        }
    }
}