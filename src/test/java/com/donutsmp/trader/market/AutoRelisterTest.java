package com.donutsmp.trader.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoRelisterTest {

    @Test
    void findsOurOwnListingOnTheListingsScreen() {
        String[] items = new String[54];
        double[] prices = new double[54];
        items[10] = "ladder";
        prices[10] = 49999.0;
        items[11] = "totem_of_undying";
        prices[11] = 150000.0;

        assertEquals(10, AutoRelister.parseMyListingsScreenSlot(items, prices, "ladder", 49999.0));
        assertEquals(-1, AutoRelister.parseMyListingsScreenSlot(items, prices, "ladder", 12345.0));
    }
}
