package com.donutsmp.trader.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PricePolicyTest {

    private static final double FLOOR = 5000;
    private static final double AMOUNT = 1;
    private static final double PERCENT = 0;
    private static final double STEP = 10;
    private static final double GAP = 2000;
    private static final int MIN_BELOW = 3;

    private PricePolicy.Decision decide(double current, double competitor, int below) {
        return PricePolicy.decide(current, competitor, below, FLOOR, AMOUNT, PERCENT, STEP, GAP, MIN_BELOW);
    }

    @Test
    void keepsThePriceWhenThereIsNoCompetitor() {
        PricePolicy.Decision d = decide(9999, 0, 0);
        assertEquals(PricePolicy.Action.KEEP, d.action());
        assertFalse(d.changed());
    }

    @Test
    void ignoresTheRoundingArtefactThatCausedTheSpiral() {
        // 11.999'a koyduk; menü "11k" gösteriyor, biz 11.000 okuyoruz.
        PricePolicy.Decision d = decide(11999, 11000, 5);
        assertEquals(PricePolicy.Action.KEEP, d.action(),
                "999'luk görüntü farkı fiyatı aşağı çekmemeli");
        assertEquals(11999.0, d.price(), 0.001);
    }

    @Test
    void oneOrTwoCheapListingsDoNotMoveUs() {
        assertEquals(PricePolicy.Action.KEEP, decide(11999, 8000, 1).action());
        assertEquals(PricePolicy.Action.KEEP, decide(11999, 8000, 2).action());
    }

    @Test
    void actsWhenEnoughListingsAreClearlyCheaper() {
        PricePolicy.Decision d = decide(11999, 8000, 3);
        assertEquals(PricePolicy.Action.UNDERCUT, d.action());
        assertEquals(7999.0, d.price(), 0.001);
    }

    @Test
    void aTwoThousandGapIsTheThreshold() {
        assertEquals(PricePolicy.Action.KEEP, decide(10000, 8001, 5).action(), "1999 fark yetmez");
        assertEquals(PricePolicy.Action.UNDERCUT, decide(10000, 8000, 5).action(), "2000 fark yeter");
    }

    @Test
    void stillRaisesWhenTheMarketMovesUp() {
        PricePolicy.Decision d = decide(9000, 20000, 0);
        assertEquals(PricePolicy.Action.RAISE, d.action());
        assertEquals(19999.0, d.price(), 0.001);
    }

    @Test
    void neverGoesUnderTheFloor() {
        assertEquals(FLOOR, decide(9999, 4000, 5).price(), 0.001);
        assertEquals(PricePolicy.Action.KEEP, decide(FLOOR, 4000, 5).action());
    }

    @Test
    void doesNotWalkItselfDownAcrossRepeatedScans() {
        double price = 11999;
        for (int i = 0; i < 30; i++) {
            // Her turda menü bizim fiyatımızı kısaltarak gösteriyor.
            double asDisplayed = Math.floor(price / 1000) * 1000;
            price = decide(price, asDisplayed, 5).price();
        }
        assertEquals(11999.0, price, 0.001, "otuz tarama sonra fiyat aynı kalmalı");
    }

    @Test
    void takesTheFirstPriceWhenWeHaveNone() {
        PricePolicy.Decision d = decide(0, 9800, 4);
        assertEquals(PricePolicy.Action.UNDERCUT, d.action());
        assertEquals(9799.0, d.price(), 0.001);
    }
}
