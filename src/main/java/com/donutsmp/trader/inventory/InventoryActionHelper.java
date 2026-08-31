package com.donutsmp.trader.inventory;

import com.donutsmp.trader.api.DonutAuctionClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public class InventoryActionHelper {

    /**
     * Envanterde hedef eşyayı barındıran slotu bulur (InventoryMenu ID'si: 9..44).
     */
    public static int findTargetSlot(Player player, String targetItemName) {
        if (player == null || targetItemName == null) return -1;
        String cleanTarget = DonutAuctionClient.normalizeItemName(targetItemName);

        // 1. Önce Ana Envanteri (9..35) ve Hotbar'ı (36..44) tara
        for (int i = 9; i <= 44; i++) {
            ItemStack stack = player.inventoryMenu.getSlot(i).getItem();
            if (!stack.isEmpty()) {
                String name = DonutAuctionClient.normalizeItemName(stack.getItem().toString());
                if (name.contains(cleanTarget)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Hotbar'daki (36..44) ilk boş slotu bulur.
     * @return Hotbar index (0..8) veya boş yoksa -1.
     */
    public static int findEmptyHotbarIndex(Player player) {
        if (player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Kaynak slottaki yığından tam olarak `lotSize` kadar eşyayı hedef hotbar slotuna ayırır.
     * 3 Adımlı Deterministik Tıklama:
     * 1. Sol tık ile yığını al (Cursor = N)
     * 2. Sağ tık ile hedef hotbar slotuna lotSize kadar bırak (Cursor = N - lotSize)
     * 3. Sol tık ile kalanı kaynak slota geri bırak (Cursor = 0)
     */
    public static boolean splitToHotbar(Minecraft client, int sourceContainerSlot, int targetHotbarIndex, int lotSize) {
        if (client.player == null || client.gameMode == null) return false;

        int targetContainerSlot = 36 + targetHotbarIndex; // Hotbar 0..8 -> Container Slot 36..44

        // 1. Kaynak slottaki yığını imlece al (Sol tık)
        client.gameMode.handleContainerInput(0, sourceContainerSlot, 0, ContainerInput.PICKUP, client.player);

        // 2. Hedef hotbar slotuna lotSize kadar sağ tıkla (her sağ tık 1 adet bırakır)
        for (int i = 0; i < lotSize; i++) {
            client.gameMode.handleContainerInput(0, targetContainerSlot, 1, ContainerInput.PICKUP, client.player);
        }

        // 3. İmleçte kalan eşyaları kaynak slota iade et (Sol tık)
        client.gameMode.handleContainerInput(0, sourceContainerSlot, 0, ContainerInput.PICKUP, client.player);

        return true;
    }
}