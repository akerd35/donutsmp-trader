package com.donutsmp.trader.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacingTest {

    private static final long WORK = 300_000; // 5 dk
    private static final long REST = 60_000;  // 1 dk

    @Test
    void worksThenRests() {
        assertFalse(Pacing.resting(0, 0, WORK, REST));
        assertFalse(Pacing.resting(299_000, 0, WORK, REST));
        assertTrue(Pacing.resting(300_000, 0, WORK, REST));
        assertTrue(Pacing.resting(359_000, 0, WORK, REST));
    }

    @Test
    void theCycleRepeats() {
        assertFalse(Pacing.resting(360_000, 0, WORK, REST), "ikinci çalışma turu");
        assertTrue(Pacing.resting(660_000, 0, WORK, REST), "ikinci dinlenme");
    }

    @Test
    void zeroDisablesResting() {
        assertFalse(Pacing.resting(999_999, 0, 0, REST));
        assertFalse(Pacing.resting(999_999, 0, WORK, 0));
    }

    @Test
    void reportsHowLongTheRestHasLeft() {
        assertEquals(0, Pacing.restRemainingMs(1000, 0, WORK, REST));
        assertEquals(60_000, Pacing.restRemainingMs(300_000, 0, WORK, REST));
        assertEquals(1_000, Pacing.restRemainingMs(359_000, 0, WORK, REST));
    }

    @Test
    void backoffDoublesAndThenStops() {
        assertEquals(0, Pacing.backoffMs(0, 2000, 60_000));
        assertEquals(2000, Pacing.backoffMs(1, 2000, 60_000));
        assertEquals(4000, Pacing.backoffMs(2, 2000, 60_000));
        assertEquals(8000, Pacing.backoffMs(3, 2000, 60_000));
        assertEquals(60_000, Pacing.backoffMs(20, 2000, 60_000), "tavanı aşmaz");
    }
}
