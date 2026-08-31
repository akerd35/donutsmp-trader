package com.donutsmp.trader.gui;

import com.donutsmp.trader.config.TraderConfig;
import com.donutsmp.trader.market.AhListingManager;

import java.util.ArrayList;
import java.util.List;

public class TraderHud {

    public static List<String> getHudLines(TraderConfig config, AhListingManager listingManager, double currentRecommendedPrice) {
        List<String> lines = new ArrayList<>();

        String status = config.enabled ? "§a[AKTİF]" : "§c[PASİF]";
        if (config.simulationMode) {
            status += " §e(SİMÜLASYON)";
        }

        lines.add("§6§l[DonutSMP Trader] " + status + " §7(Tuş: 'K')");
        lines.add(String.format("§e[Slotlar]: §f%d/%d Dolu §7| §eKuyruk: §f%d",
                listingManager.getActiveListings(), listingManager.getMaxSlots(), listingManager.getQueueSize()));
        lines.add(String.format("§e[Hedef]: §f%dx %s §7| §eSatış Fiyatı: §a$%.0f",
                config.lotSize, config.targetItem, currentRecommendedPrice));
        lines.add(String.format("§e[Kasa]: §a+$%,d §7(Satılan: §f%dx§7)",
                listingManager.getTotalEarned(), listingManager.getItemsSold()));

        return lines;
    }
}