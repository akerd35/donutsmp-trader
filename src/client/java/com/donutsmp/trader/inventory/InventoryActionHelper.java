package com.donutsmp.trader.inventory;

import com.donutsmp.trader.api.DonutAuctionClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public class InventoryActionHelper {

    /** Envanter menüsünde sırt çantası önce, hotbar sonra gelir. */
    public static final int BACKPACK_MENU_START = 9;
    public static final int HOTBAR_MENU_START = 36;
    public static final int HOTBAR_MENU_END = 44;

    public static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return DonutAuctionClient.normalizeItemName(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    /**
     * Kural "ladder" diyorsa, isimlendirilmiş ya da büyülü bir merdiven o kural
     * değildir; sunucu lore'u her eşyada olduğu için ona bakılmaz.
     */
    public static boolean isPlain(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.isDamaged() || stack.isEnchanted()) return false;
        if (stack.get(DataComponents.CUSTOM_NAME) != null) return false;
        return stack.get(DataComponents.CONTAINER) == null;
    }

    /**
     * Hedef eşyanın en dolu yığınını menü slotu olarak döndürür.
     *
     * @param minCount en az bu kadar adet barındırmalı; aksi halde bölme
     *                 tamamlanamaz ve mod aynı yığını sonsuza dek dener
     * @param excludeMenuSlot lotun kurulacağı hedef slot
     */
    public static int findTargetSlot(Player player, String targetItemName, int minCount, int excludeMenuSlot) {
        if (player == null || targetItemName == null) return -1;
        String want = DonutAuctionClient.normalizeItemName(targetItemName);

        int best = -1;
        int most = 0;
        for (int i = BACKPACK_MENU_START; i <= HOTBAR_MENU_END; i++) {
            if (i == excludeMenuSlot) continue;
            ItemStack stack = player.inventoryMenu.getSlot(i).getItem();
            if (stack.isEmpty() || !isPlain(stack) || !idOf(stack).equals(want)) continue;
            if (stack.getCount() < minCount) continue;
            if (stack.getCount() > most) {
                most = stack.getCount();
                best = i;
            }
        }
        return best;
    }

    /** Hotbar'daki (envanter indeksi 0..8) ilk boş slot, yoksa -1. */
    public static int findEmptyHotbarIndex(Player player) {
        if (player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    /**
     * Kaynak yığından tam olarak lotSize adedi hedef hotbar slotuna ayırır:
     * sol tıkla al, hedefe lotSize kez sağ tıkla, kalanı sol tıkla iade et.
     */
    public static boolean splitToHotbar(Minecraft client, int sourceMenuSlot, int targetHotbarIndex, int lotSize) {
        if (client.player == null || client.gameMode == null) return false;
        if (!client.player.containerMenu.getCarried().isEmpty()) return false;

        int targetMenuSlot = HOTBAR_MENU_START + targetHotbarIndex;

        client.gameMode.handleContainerInput(0, sourceMenuSlot, 0, ContainerInput.PICKUP, client.player);

        for (int i = 0; i < lotSize; i++) {
            client.gameMode.handleContainerInput(0, targetMenuSlot, 1, ContainerInput.PICKUP, client.player);
        }

        client.gameMode.handleContainerInput(0, sourceMenuSlot, 0, ContainerInput.PICKUP, client.player);

        return true;
    }
}
