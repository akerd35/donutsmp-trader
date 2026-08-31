package com.donutsmp.trader.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AhPriceParserTest {

    @Test
    void readsCommaGroupedPrice() {
        assertEquals(35000.0, AhPriceParser.parsePrice("Price: $35,000"), 0.001);
    }

    @Test
    void readsDotGroupedPrice() {
        assertEquals(15999.0, AhPriceParser.parsePrice("§7Fiyat: §a$15.999"), 0.001);
        assertEquals(1234567.0, AhPriceParser.parsePrice("$1.234.567"), 0.001);
    }

    @Test
    void readsSuffixes() {
        assertEquals(15900.0, AhPriceParser.parsePrice("Buy: $15.9k"), 0.001);
        assertEquals(2500000.0, AhPriceParser.parsePrice("$2.5m"), 0.001);
        assertEquals(3000000000.0, AhPriceParser.parsePrice("$3b"), 0.001);
    }

    @Test
    void readsMixedGroupingAndDecimals() {
        assertEquals(1234567.5, AhPriceParser.parsePrice("$1,234,567.50"), 0.001);
    }

    @Test
    void stripsColourCodes() {
        assertEquals(42000.0, AhPriceParser.parsePrice("§6§lPrice: §e$42,000§r"), 0.001);
    }

    @Test
    void returnsNegativeWhenThereIsNoPrice() {
        assertTrue(AhPriceParser.parsePrice("Ladder") < 0);
        assertTrue(AhPriceParser.parsePrice("") < 0);
        assertTrue(AhPriceParser.parsePrice(null) < 0);
    }
}
