package com.donutsmp.trader.gui;

import com.donutsmp.trader.DonutTraderMod;
import com.donutsmp.trader.inventory.InventoryActionHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Açılan bir konteyner menüsünün yapısını dosyaya yazar.
 *
 * /ah menülerine karşı otomasyon yazmak için slot numaralarının, eşya
 * kimliklerinin ve lore metinlerinin gerçek hâli gerekir; ekran görüntüsünden
 * okumak tahmin olur.
 */
public final class ScreenDump {

    private ScreenDump() {}

    public static Path file() {
        return FabricLoader.getInstance().getGameDir().resolve("donutsmp-trader-dump.txt");
    }

    public static void capture(Component title, AbstractContainerMenu menu) {
        if (menu == null) return;

        List<String> lines = new ArrayList<>();
        int containerSlots = Math.max(0, menu.slots.size() - 36);
        lines.add("=== EKRAN: " + (title == null ? "(başlıksız)" : title.getString()) + " ===");
        lines.add("menu=" + menu.getClass().getSimpleName() + " toplamSlot=" + menu.slots.size()
                + " konteynerSlot=" + containerSlots);

        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) continue;

            StringBuilder line = new StringBuilder();
            line.append("  [").append(i).append("] ")
                    .append(InventoryActionHelper.idOf(stack)).append(" x").append(stack.getCount())
                    .append(" | ad: ").append(stack.getHoverName().getString());

            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                for (Component loreLine : lore.lines()) {
                    line.append("\n        lore: ").append(loreLine.getString());
                }
            }
            lines.add(line.toString());
        }
        lines.add("");

        try {
            Path path = file();
            Files.writeString(path, String.join("\n", lines) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            DonutTraderMod.LOGGER.info("[Dump] Ekran kaydedildi: {}", path);
        } catch (Exception e) {
            DonutTraderMod.LOGGER.warn("[Dump] Yazilamadi: {}", e.getMessage());
        }
    }
}
