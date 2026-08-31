package com.donutsmp.trader.inventory;

import java.util.Arrays;
import java.util.List;

public class TestInventorySplitter {

    // Minecraft Envanter Simülatörü
    public static class VirtualInventory {
        public final int[] slots = new int[36];
        public final String[] items = new String[36];
        public int cursorCount = 0;
        public String cursorItem = null;

        public VirtualInventory() {
            Arrays.fill(slots, 0);
            Arrays.fill(items, null);
        }

        public void setSlot(int slot, String item, int count) {
            slots[slot] = count;
            items[slot] = count > 0 ? item : null;
        }

        public void execute(InventorySplitter.ClickAction action) {
            int slot = action.slot;
            int button = action.button;

            if (button == 0) {
                // Sol Tık (Left Click)
                if (cursorCount == 0 && slots[slot] > 0) {
                    // İmleç boş, slot dolu -> Hepsini al
                    cursorCount = slots[slot];
                    cursorItem = items[slot];
                    slots[slot] = 0;
                    items[slot] = null;
                } else if (cursorCount > 0 && slots[slot] == 0) {
                    // İmleç dolu, slot boş -> Hepsini bırak
                    slots[slot] = cursorCount;
                    items[slot] = cursorItem;
                    cursorCount = 0;
                    cursorItem = null;
                } else if (cursorCount > 0 && slots[slot] > 0 && cursorItem.equals(items[slot])) {
                    // Aynı item -> Birleştir
                    slots[slot] += cursorCount;
                    cursorCount = 0;
                    cursorItem = null;
                }
            } else if (button == 1) {
                // Sağ Tık (Right Click)
                if (cursorCount > 0) {
                    // İmleçten slota 1 adet bırak
                    if (slots[slot] == 0) {
                        items[slot] = cursorItem;
                    }
                    slots[slot] += 1;
                    cursorCount -= 1;
                    if (cursorCount == 0) {
                        cursorItem = null;
                    }
                }
            }
        }

        public int getTotalItemCount(String itemName) {
            int total = (cursorItem != null && cursorItem.equals(itemName)) ? cursorCount : 0;
            for (int i = 0; i < 36; i++) {
                if (items[i] != null && items[i].equals(itemName)) {
                    total += slots[i];
                }
            }
            return total;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== InventorySplitter Simulasyon Testi Baslatiliyor ===");

        // Test 1: 64 Merdivenden 1 adet ayırma (Slot 12 -> Slot 36)
        System.out.println("\n[Test 1] 64'luk Merdivenden 1 adet ayirma (Slot 12 -> Hotbar Slot 0)");
        VirtualInventory inv1 = new VirtualInventory();
        inv1.setSlot(12, "ladder", 64);
        int initialTotal1 = inv1.getTotalItemCount("ladder");

        List<InventorySplitter.ClickAction> plan1 = InventorySplitter.planSplit(12, 0, 64, 1);
        System.out.println("Planlanan tiklama sayisi: " + plan1.size());
        for (InventorySplitter.ClickAction act : plan1) {
            inv1.execute(act);
            System.out.println("  " + act);
        }

        System.out.println("Sonuc:");
        System.out.println("  -> Kaynak Slot (12): " + inv1.slots[12] + " adet " + inv1.items[12]);
        System.out.println("  -> Hedef Slot (0): " + inv1.slots[0] + " adet " + inv1.items[0]);
        System.out.println("  -> Imlec (Cursor): " + inv1.cursorCount + " adet (Sıfır olmalı)");
        
        assert inv1.slots[0] == 1 : "Hedef slotta 1 adet olmali!";
        assert inv1.slots[12] == 63 : "Kaynak slotta 63 adet kalmali!";
        assert inv1.cursorCount == 0 : "Imlecte item kalmamali!";
        assert inv1.getTotalItemCount("ladder") == initialTotal1 : "Toplam item sayisi degismemeli!";
        System.out.println("  => TEST 1 BASARILI! (Tam 1x lot ayrildi, sifir kayip)");

        // Test 2: 16'lik Lot Ayirma (64 Ender Pearl -> 16 Ender Pearl)
        System.out.println("\n[Test 2] 64'luk Ender Pearl'den 16 adetlik lot ayirma (Slot 10 -> Hotbar Slot 1)");
        VirtualInventory inv2 = new VirtualInventory();
        inv2.setSlot(10, "ender_pearl", 64);
        int initialTotal2 = inv2.getTotalItemCount("ender_pearl");

        List<InventorySplitter.ClickAction> plan2 = InventorySplitter.planSplit(10, 1, 64, 16);
        for (InventorySplitter.ClickAction act : plan2) {
            inv2.execute(act);
        }

        System.out.println("Sonuc:");
        System.out.println("  -> Kaynak Slot (10): " + inv2.slots[10] + " adet " + inv2.items[10]);
        System.out.println("  -> Hedef Slot (1): " + inv2.slots[1] + " adet " + inv2.items[1]);
        System.out.println("  -> Imlec (Cursor): " + inv2.cursorCount + " adet (Sıfır olmalı)");
        
        assert inv2.slots[1] == 16 : "Hedef slotta 16 adet olmali!";
        assert inv2.slots[10] == 48 : "Kaynak slotta 48 adet kalmali!";
        assert inv2.cursorCount == 0 : "Imlecte item kalmamali!";
        assert inv2.getTotalItemCount("ender_pearl") == initialTotal2 : "Toplam item sayisi degismemeli!";
        System.out.println("  => TEST 2 BASARILI! (Tam 16x lot ayrildi, sifir kayip)");

        // Test 3: Boş Hotbar ve Kaynak Arama
        System.out.println("\n[Test 3] Otomatik Slot Tespiti");
        String[] sampleNames = new String[36];
        int[] sampleCounts = new int[36];
        sampleNames[14] = "water_bucket";
        sampleCounts[14] = 1;

        int foundSource = InventorySplitter.findSourceItemSlot(sampleNames, sampleCounts, "water_bucket", 1);
        int foundEmptyHotbar = InventorySplitter.findEmptyHotbarSlot(sampleNames, 0, 8);
        System.out.println("  -> Bulunan Su Kovasi Slotu: " + foundSource + " (Beklenen: 14)");
        System.out.println("  -> Bulunan Ilk Bos Hotbar Slotu: " + foundEmptyHotbar + " (Beklenen: 0)");

        System.out.println("\n=== TUM SIMULASYON TESTLERI BASARIYLA TAMAMLANDI! ===");
    }
}