package com.donutsmp.trader.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdaterTest {

    @Test
    void comparesVersionsNumericallyNotAlphabetically() {
        assertTrue(Updater.isNewer("1.0.1", "1.0.0"));
        assertTrue(Updater.isNewer("1.10.0", "1.9.0"), "1.10 > 1.9 olmalı");
        assertFalse(Updater.isNewer("1.0.0", "1.0.0"));
        assertFalse(Updater.isNewer("1.0.0", "1.0.1"));
    }

    @Test
    void unparseableVersionsFallBackToInequality() {
        assertFalse(Updater.isNewer(null, "1.0.0"));
        assertFalse(Updater.isNewer("1.0.0", null));
    }

    @Test
    void readsTheHashOutOfSha256sumOutput() {
        String hash = "a".repeat(64);
        assertEquals(hash, Updater.firstToken(hash + "  donutsmp-trader-1.0.1.jar\n"));
        assertEquals(hash, Updater.firstToken(hash));
    }

    @Test
    void rejectsAnythingThatIsNotASha256() {
        assertNull(Updater.firstToken("not-a-hash  file.jar"));
        assertNull(Updater.firstToken("a".repeat(63)));
        assertNull(Updater.firstToken(""));
        assertNull(Updater.firstToken(null));
    }
}
