package com.donutsmp.trader.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricePolicyTest {

    private static final double FLOOR = 5000;
    private static final double AMOUNT = 1;
    private static final double PERCENT = 0;
    private static final double STEP = 10;

    private PricePolicy.Decision decide(double current, double competitor) {
        return PricePolicy.decide(current, competitor, FLOOR, AMOUNT, PERCENT, STEP);
    }

    @Test
    void keepsThePriceWhenThereIsNoCompetitor() {
        PricePolicy.Decision d = decide(9999, 0);
        assertEquals(PricePolicy.Action.KEEP, d.action());
        assertEquals(9999.0, d.price(), 0.001);
        assertFalse(d.changed(), "rakip yokken fiyat düşürmek bedava para vermektir");
    }

    @Test
    void undercutsOnlyWhenSomeoneIsBelowUs() {
        PricePolicy.Decision d = decide(9999, 9000);
        assertEquals(PricePolicy.Action.UNDERCUT, d.action());
        assertEquals(8999.0, d.price(), 0.001);
    }

    @Test
    void raisesWhenTheMarketMovesUp() {
        PricePolicy.Decision d = decide(9000, 20000);
        assertEquals(PricePolicy.Action.RAISE, d.action());
        assertEquals(19999.0, d.price(), 0.001);
    }

    @Test
    void ignoresChangesSmallerThanTheStep() {
        PricePolicy.Decision d = decide(9999, 10005);
        assertEquals(PricePolicy.Action.KEEP, d.action());
        assertEquals(9999.0, d.price(), 0.001, "5 dolarlık oynama için ilan yenilenmez");
    }

    @Test
    void neverGoesUnderTheFloor() {
        PricePolicy.Decision d = decide(9999, 4000);
        assertEquals(FLOOR, d.price(), 0.001);

        PricePolicy.Decision atFloor = decide(FLOOR, 4000);
        assertEquals(PricePolicy.Action.KEEP, atFloor.action());
    }

    @Test
    void takesTheFirstPriceWhenWeHaveNone() {
        PricePolicy.Decision d = decide(0, 9800);
        assertEquals(PricePolicy.Action.UNDERCUT, d.action());
        assertEquals(9799.0, d.price(), 0.001);
    }

    @Test
    void doesNotRatchetDownAcrossRepeatedScans() {
        double price = 9999;
        for (int i = 0; i < 20; i++) {
            PricePolicy.Decision d = decide(price, 0);
            price = d.price();
        }
        assertEquals(9999.0, price, 0.001, "yirmi tarama sonra fiyat aynı kalmalı");
    }

    @Test
    void settlesInsteadOfOscillating() {
        double price = decide(9999, 9800).price();
        assertEquals(9799.0, price, 0.001);
        PricePolicy.Decision again = decide(price, 9800);
        assertEquals(PricePolicy.Action.KEEP, again.action(), "aynı rakip fiyatı ikinci kez kırılmamalı");
        assertTrue(again.price() == price);
    }
}
