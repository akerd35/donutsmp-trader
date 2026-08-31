package com.donutsmp.trader.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UndercutTest {

    @Test
    void takesATenthOfAPercentOffTheCompetitor() {
        assertEquals(9990.0, Undercut.target(10000.0, 0.1, 0.0), 0.001);
        assertEquals(999000.0, Undercut.target(1000000.0, 0.1, 0.0), 0.001);
        assertEquals(9790.0, Undercut.target(9800.0, 0.1, 0.0), 0.001);
    }

    @Test
    void alwaysGoesAtLeastOneBelowOnCheapItems() {
        assertEquals(499.0, Undercut.target(500.0, 0.1, 0.0), 0.001);
        assertEquals(9.0, Undercut.target(10.0, 0.1, 0.0), 0.001);
        assertEquals(99.0, Undercut.target(100.0, 0.0, 0.0), 0.001, "yüzde 0 olsa bile rakibin altına inmeli");
    }

    @Test
    void neverGoesBelowTheFloor() {
        assertEquals(9500.0, Undercut.target(9550.0, 5.0, 9500.0), 0.001);
        assertEquals(9500.0, Undercut.target(9000.0, 0.1, 9500.0), 0.001, "rakip tabanın altındaysa tabanda kal");
    }

    @Test
    void resultIsAWholeNumber() {
        assertEquals(9993.0, Undercut.target(10000.0, 0.07, 0.0), 0.001);
    }
}
