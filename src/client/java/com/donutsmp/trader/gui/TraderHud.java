package com.donutsmp.trader.gui;

import com.donutsmp.trader.DonutTraderMod;
import com.donutsmp.trader.config.TraderConfig;
import com.donutsmp.trader.market.AhListingManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public class TraderHud {

    private static final Identifier ID = Identifier.fromNamespaceAndPath(DonutTraderMod.MOD_ID, "status");

    public static void register() {
        try {
            HudElementRegistry.addLast(ID, (extractor, tickCounter) -> {
                DonutTraderMod mod = DonutTraderMod.getInstance();
                if (mod == null || !mod.getConfig().enabled) return;

                Minecraft client = Minecraft.getInstance();
                if (client.player == null || client.gui.screen() != null) return;

                int y = 6;
                for (String line : mod.getHudInfo()) {
                    extractor.text(client.font, Component.literal(line), 6, y, 0xFFFFFFFF);
                    y += 10;
                }
            });
        } catch (Throwable t) {
            DonutTraderMod.LOGGER.warn("HUD kaydedilemedi: {}", t.getMessage());
        }
    }

    public static List<String> getHudLines(TraderConfig config, AhListingManager listingManager, double currentRecommendedPrice) {
        List<String> lines = new ArrayList<>();

        String status = config.enabled ? "§a[AKTİF]" : "§c[PASİF]";
        if (config.simulationMode) {
            status += " §e(SİMÜLASYON)";
        }

        lines.add("§6§l[DonutSMP Trader] " + status);
        lines.add(String.format("§e[Slotlar]: §f%d/%d Dolu §7| §eKuyruk: §f%d",
                listingManager.getActiveListings(), listingManager.getMaxSlots(), listingManager.getQueueSize()));
        lines.add(String.format("§e[Hedef]: §f%dx %s §7| §eSatış Fiyatı: §a$%,.0f",
                config.lotSize, config.targetItem, currentRecommendedPrice));
        lines.add(String.format("§e[Kasa]: §a+$%,d §7(Satılan: §f%dx§7)",
                listingManager.getTotalEarned(), listingManager.getItemsSold()));

        return lines;
    }
}
