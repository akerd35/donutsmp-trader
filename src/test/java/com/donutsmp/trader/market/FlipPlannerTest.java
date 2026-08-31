package com.donutsmp.trader.market;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlipPlannerTest {

    private static final List<FlipPlanner.Offer> MARKET = List.of(
            new FlipPlanner.Offer(3, 12000),
            new FlipPlanner.Offer(1, 8000),
            new FlipPlanner.Offer(2, 9500),
            new FlipPlanner.Offer(4, 4000)
    );

    @Test
    void buysOnlyWhatIsUnderTheLimit() {
        List<FlipPlanner.Buy> picks = FlipPlanner.plan(MARKET, 10000, 20000, 1_000_000, 0);
        assertEquals(3, picks.size());
        assertTrue(picks.stream().noneMatch(b -> b.price() > 10000));
    }

    @Test
    void takesTheCheapestFirst() {
        List<FlipPlanner.Buy> picks = FlipPlanner.plan(MARKET, 10000, 20000, 1_000_000, 0);
        assertEquals(4, picks.get(0).slot());
        assertEquals(4000.0, picks.get(0).price(), 0.001);
    }

    @Test
    void stopsAtTheBudget() {
        List<FlipPlanner.Buy> picks = FlipPlanner.plan(MARKET, 10000, 20000, 12000, 0);
        assertEquals(12000.0, FlipPlanner.totalCost(picks), 0.001);
        assertEquals(2, picks.size(), "4000 + 8000 sığar, 9500 sığmaz");
    }

    @Test
    void refusesTradesThatDoNotClearTheMargin() {
        // En ucuzu 4000; 9600'e satmak 5600 kâr, 6000 marjı geçmiyor.
        assertTrue(FlipPlanner.plan(MARKET, 10000, 9600, 1_000_000, 6000).isEmpty(),
                "satış fiyatı alıştan yeterince yüksek değilse alma");

        // Marj 5000 iken yalnızca 4000'lik ilan geçer.
        List<FlipPlanner.Buy> picks = FlipPlanner.plan(MARKET, 10000, 9600, 1_000_000, 5000);
        assertEquals(1, picks.size());
        assertEquals(4000.0, picks.get(0).price(), 0.001);
    }

    @Test
    void reportsCostAndProfit() {
        List<FlipPlanner.Buy> picks = FlipPlanner.plan(MARKET, 10000, 20000, 1_000_000, 0);
        assertEquals(21500.0, FlipPlanner.totalCost(picks), 0.001);
        assertEquals(38500.0, FlipPlanner.expectedProfit(picks), 0.001);
    }

    @Test
    void buyingComesBeforeListing() {
        assertTrue(FlipPlanner.shouldBuyFirst(FlipPlanner.plan(MARKET, 10000, 20000, 1_000_000, 0)));
        assertFalse(FlipPlanner.shouldBuyFirst(FlipPlanner.plan(MARKET, 1000, 20000, 1_000_000, 0)));
        assertFalse(FlipPlanner.shouldBuyFirst(List.of()));
    }

    @Test
    void refusesToActOnNonsenseSettings() {
        assertTrue(FlipPlanner.plan(MARKET, 0, 20000, 1000, 0).isEmpty());
        assertTrue(FlipPlanner.plan(MARKET, 10000, 0, 1000, 0).isEmpty());
        assertTrue(FlipPlanner.plan(MARKET, 10000, 20000, 0, 0).isEmpty());
        assertTrue(FlipPlanner.plan(null, 10000, 20000, 1000, 0).isEmpty());
    }
}
