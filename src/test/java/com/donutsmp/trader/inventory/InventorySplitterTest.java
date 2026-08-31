package com.donutsmp.trader.inventory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventorySplitterTest {

    /** Minecraft'in slot tıklama davranışının küçük bir kopyası. */
    private static final class VirtualInventory {
        final int[] counts = new int[45];
        final String[] items = new String[45];
        int cursorCount = 0;
        String cursorItem = null;

        void set(int slot, String item, int count) {
            counts[slot] = count;
            items[slot] = count > 0 ? item : null;
        }

        void execute(InventorySplitter.ClickAction action) {
            int slot = action.slot;
            if (action.button == 0) {
                if (cursorCount == 0 && counts[slot] > 0) {
                    cursorCount = counts[slot];
                    cursorItem = items[slot];
                    set(slot, null, 0);
                } else if (cursorCount > 0 && counts[slot] == 0) {
                    set(slot, cursorItem, cursorCount);
                    cursorCount = 0;
                    cursorItem = null;
                } else if (cursorCount > 0 && cursorItem.equals(items[slot])) {
                    counts[slot] += cursorCount;
                    cursorCount = 0;
                    cursorItem = null;
                }
            } else if (action.button == 1 && cursorCount > 0) {
                if (counts[slot] == 0) items[slot] = cursorItem;
                counts[slot] += 1;
                cursorCount -= 1;
                if (cursorCount == 0) cursorItem = null;
            }
        }

        int total(String item) {
            int sum = cursorItem != null && cursorItem.equals(item) ? cursorCount : 0;
            for (int i = 0; i < counts.length; i++) {
                if (item.equals(items[i])) sum += counts[i];
            }
            return sum;
        }
    }

    private VirtualInventory run(int sourceCount, int lot) {
        VirtualInventory inv = new VirtualInventory();
        inv.set(12, "ladder", sourceCount);
        for (InventorySplitter.ClickAction action : InventorySplitter.planSplit(12, 36, sourceCount, lot)) {
            inv.execute(action);
        }
        return inv;
    }

    @Test
    void splitsOneOffAStackWithoutLosingAnything() {
        VirtualInventory inv = run(64, 1);
        assertEquals(1, inv.counts[36]);
        assertEquals(63, inv.counts[12]);
        assertEquals(64, inv.total("ladder"));
        assertEquals(0, inv.cursorCount, "imleçte eşya kalmamalı");
    }

    @Test
    void splitsALargerLot() {
        VirtualInventory inv = run(64, 16);
        assertEquals(16, inv.counts[36]);
        assertEquals(48, inv.counts[12]);
        assertEquals(64, inv.total("ladder"));
        assertEquals(0, inv.cursorCount);
    }

    @Test
    void movesTheWholeStackWhenItAlreadyMatches() {
        VirtualInventory inv = run(1, 1);
        assertEquals(1, inv.counts[36]);
        assertEquals(0, inv.counts[12]);
        assertEquals(1, inv.total("ladder"));
        assertEquals(0, inv.cursorCount);
    }

    @Test
    void refusesToPlanWhenTheStackIsTooSmall() {
        List<InventorySplitter.ClickAction> actions = InventorySplitter.planSplit(12, 36, 3, 8);
        assertTrue(actions.isEmpty());
    }

    @Test
    void findsTheSourceSlotAndAFreeHotbarSlot() {
        String[] names = new String[45];
        int[] counts = new int[45];
        names[12] = "ladder";
        counts[12] = 64;
        assertEquals(12, InventorySplitter.findSourceItemSlot(names, counts, "ladder", 1));
        assertEquals(-1, InventorySplitter.findSourceItemSlot(names, counts, "ladder", 65));
        assertEquals(0, InventorySplitter.findEmptyHotbarSlot(names, 0, 8));
    }
}
