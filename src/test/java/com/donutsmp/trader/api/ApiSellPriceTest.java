package com.donutsmp.trader.api;

import com.donutsmp.trader.api.model.TickerItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tarama tutmadiginda fiyat API'den gelir. O fiyat da lot boyutuna gore
 * olculmeli, yoksa 16'lik lot tek esyanin fiyatina asilir.
 */
class ApiSellPriceTest {

    private static final double FALLBACK = 35_000;
    private static final double FLOOR = 0;

    private static double price(TickerItem ticker, int lotSize) {
        return DonutAuctionClient.sellPriceFor(ticker, lotSize, FALLBACK, FLOOR, 1.0, 0.0);
    }

    @Test
    void asingleUndercutsTheUnitPriceByOne() {
        TickerItem ticker = new TickerItem("ladder", "ladder", 1, 10_000, 10_000);
        assertEquals(9_999, price(ticker, 1), 0.001);
    }

    /** 64'luk yigin 320.000: tanesi 5.000. 16'lik lotumuz 80.000 eder. */
    @Test
    void alotIsPricedFromTheUnitPriceNotTheListing() {
        TickerItem ticker = new TickerItem("ladder", "ladder", 64, 320_000, 5_000);
        assertEquals(79_999, price(ticker, 16), 0.001);
    }

    @Test
    void aMissingUnitPriceIsDerivedFromTheQuantity() {
        TickerItem ticker = new TickerItem("ladder", "ladder", 64, 320_000, 0);
        assertEquals(79_999, price(ticker, 16), 0.001);
    }

    @Test
    void withNoQuantityTheListingIsTreatedAsOne() {
        TickerItem ticker = new TickerItem("ladder", "ladder", 0, 10_000, 0);
        assertEquals(9_999, price(ticker, 1), 0.001);
    }

    @Test
    void noDataMeansTheFallbackPrice() {
        assertEquals(FALLBACK, price(null, 1), 0.001);
        assertEquals(FALLBACK, price(new TickerItem("ladder", "ladder", 1, 0, 0), 1), 0.001);
    }

    @Test
    void theFloorIsNeverCrossed() {
        TickerItem ticker = new TickerItem("ladder", "ladder", 1, 5_000, 5_000);
        assertEquals(9_000, DonutAuctionClient.sellPriceFor(ticker, 1, FALLBACK, 9_000, 1.0, 0.0), 0.001);
    }
}
