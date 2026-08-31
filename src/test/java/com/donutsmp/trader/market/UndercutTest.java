package com.donutsmp.trader.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UndercutTest {

    private static final double AMOUNT = 1.0;

    @Test
    void sellsOneDollarUnderTheCompetitor() {
        assertEquals(9999.0, Undercut.target(10000.0, AMOUNT, 0.0, 0.0), 0.001);
        assertEquals(9799.0, Undercut.target(9800.0, AMOUNT, 0.0, 0.0), 0.001);
        assertEquals(999999.0, Undercut.target(1000000.0, AMOUNT, 0.0, 0.0), 0.001);
        assertEquals(9.0, Undercut.target(10.0, AMOUNT, 0.0, 0.0), 0.001);
    }

    @Test
    void aBiggerFixedAmountIsHonoured() {
        assertEquals(9950.0, Undercut.target(10000.0, 50.0, 0.0, 0.0), 0.001);
    }

    @Test
    void percentWinsWhenItIsLargerThanTheFixedAmount() {
        assertEquals(9990.0, Undercut.target(10000.0, AMOUNT, 0.1, 0.0), 0.001);
        assertEquals(999.0, Undercut.target(1000.0, AMOUNT, 0.05, 0.0), 0.001, "yüzde 1$ altında kalırsa 1$ uygulanır");
    }

    @Test
    void alwaysCutsAtLeastOneDollar() {
        assertEquals(99.0, Undercut.target(100.0, 0.0, 0.0, 0.0), 0.001);
    }

    @Test
    void neverGoesBelowTheFloor() {
        assertEquals(9500.0, Undercut.target(9550.0, 100.0, 0.0, 9500.0), 0.001);
        assertEquals(9500.0, Undercut.target(9000.0, AMOUNT, 0.0, 9500.0), 0.001);
    }

    @Test
    void resultIsAWholeNumber() {
        assertEquals(9992.0, Undercut.target(10000.0, AMOUNT, 0.075, 0.0), 0.001);
    }
}
