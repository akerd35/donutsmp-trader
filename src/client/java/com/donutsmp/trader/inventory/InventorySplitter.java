package com.donutsmp.trader.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class InventorySplitter {
    private static final Logger LOGGER = LoggerFactory.getLogger("DonutTrader-Splitter");

    public static class ClickAction {
        public final int slot;
        public final int button; // 0 = Left, 1 = Right
        public final int clickType; // 0 = PICKUP, 1 = QUICK_MOVE
        public final String description;

        public ClickAction(int slot, int button, int clickType, String description) {
            this.slot = slot;
            this.button = button;
            this.clickType = clickType;
            this.description = description;
        }

        @Override
        public String toString() {
            return String.format("ClickAction{slot=%d, btn=%s, desc='%s'}",
                    slot, (button == 0 ? "LEFT" : "RIGHT"), description);
        }
    }

    public static List<ClickAction> planSplit(int sourceSlot, int targetEmptySlot, int sourceCount, int desiredLotSize) {
        List<ClickAction> actions = new ArrayList<>();

        if (sourceCount <= 0 || desiredLotSize <= 0 || sourceCount < desiredLotSize) {
            LOGGER.warn("Invalid split parameters: sourceCount={}, desiredLotSize={}", sourceCount, desiredLotSize);
            return actions;
        }

        // Eger zaten tam olarak istenen boyuttaysa, direkt tasiyabiliriz
        if (sourceCount == desiredLotSize) {
            actions.add(new ClickAction(sourceSlot, 0, 0, "Tam yigini sol tikla al"));
            actions.add(new ClickAction(targetEmptySlot, 0, 0, "Hedef slota sol tikla birak"));
            return actions;
        }

        // 1. Adim: Kaynak slottaki butun yigini imlece al (Sol Tik)
        actions.add(new ClickAction(sourceSlot, 0, 0, "Kaynak slottaki yigini al (Cursor = " + sourceCount + ")"));

        // 2. Adim: Hedef slota tam olarak desiredLotSize adet birak (Her Sag Tik = 1 adet)
        for (int i = 1; i <= desiredLotSize; i++) {
            actions.add(new ClickAction(targetEmptySlot, 1, 0, "Hedef slota 1 adet birak (Hedef = " + i + ")"));
        }

        // 3. Adim: Kalan (sourceCount - desiredLotSize) adet itemi kaynak slota geri koy (Sol Tik)
        int remaining = sourceCount - desiredLotSize;
        actions.add(new ClickAction(sourceSlot, 0, 0, "Kalan " + remaining + " itemi kaynak slota iade et"));

        return actions;
    }

    public static int findSourceItemSlot(String[] slotItemNames, int[] slotCounts, String targetItemName, int minCount) {
        if (slotItemNames == null || slotCounts == null) return -1;

        for (int i = 0; i < slotItemNames.length; i++) {
            String item = slotItemNames[i];
            if (item != null && item.equalsIgnoreCase(targetItemName) && slotCounts[i] >= minCount) {
                return i;
            }
        }
        return -1;
    }

    public static int findEmptyHotbarSlot(String[] slotItemNames, int hotbarStart, int hotbarEnd) {
        if (slotItemNames == null) return -1;

        for (int i = hotbarStart; i <= hotbarEnd; i++) {
            if (i >= 0 && i < slotItemNames.length && (slotItemNames[i] == null || slotItemNames[i].isEmpty())) {
                return i;
            }
        }
        return -1;
    }
}