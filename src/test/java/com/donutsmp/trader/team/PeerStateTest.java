package com.donutsmp.trader.team;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeerStateTest {

    private static PeerState of(String name, String item, int lot, long price) {
        return new PeerState(name, 1000L, true, item, lot, price, 64, 3, 4, 18);
    }

    @Test
    void freshnessFollowsTheClock() {
        PeerState peer = of("Kaan", "ladder", 1, 9000);
        assertTrue(peer.fresh(1000 + 5_000, 90_000));
        assertFalse(peer.fresh(1000 + 120_000, 90_000));
    }

    @Test
    void neverPublishedIsNeverFresh() {
        PeerState peer = new PeerState("Kaan", 0, true, "ladder", 1, 9000, 0, 0, 0, 18);
        assertFalse(peer.fresh(1000, 90_000));
        assertFalse(peer.usable());
    }

    @Test
    void unitPriceDividesByTheLot() {
        assertEquals(1000.0, of("Kaan", "ladder", 16, 16_000).unitPrice(), 0.001);
        assertEquals(9000.0, of("Kaan", "ladder", 1, 9000).unitPrice(), 0.001);
    }

    @Test
    void sellsOnlyWhenEnabledAndTheItemMatches() {
        assertTrue(of("Kaan", "ladder", 1, 9000).sells("LADDER"));
        assertFalse(of("Kaan", "ladder", 1, 9000).sells("water_bucket"));
        assertFalse(new PeerState("Kaan", 1000, false, "ladder", 1, 9000, 0, 0, 0, 18).sells("ladder"));
    }

    @Test
    void strippsFormattingCodesFromRemoteText() {
        PeerState hostile = new PeerState("§cKaan§r", 1000, true, "§lladder", 1, 9000, 0, 0, 0, 18);
        PeerState clean = hostile.sanitised();
        assertEquals("cKaanr", clean.name());
        assertFalse(clean.item().contains("§"));
    }

    @Test
    void clampsNumbersFromAnotherMachine() {
        PeerState absurd = new PeerState("Kaan", 1000, true, "ladder",
                9999, -5, -20, 99, 5000, -3);
        PeerState clean = absurd.sanitised();
        assertEquals(64, clean.lotSize());
        assertEquals(0, clean.price());
        assertEquals(0, clean.itemsLeft());
        assertEquals(9, clean.freeHotbarSlots());
        assertEquals(999, clean.activeListings());
        assertEquals(0, clean.maxSlots());
    }

    @Test
    void longRemoteTextIsCut() {
        PeerState clean = new PeerState("x".repeat(500), 1000, true, "y".repeat(500),
                1, 1, 0, 0, 0, 18).sanitised();
        assertTrue(clean.name().length() <= 32);
        assertTrue(clean.item().length() <= 32);
    }
}
