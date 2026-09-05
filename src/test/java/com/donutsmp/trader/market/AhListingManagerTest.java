package com.donutsmp.trader.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AhListingManagerTest {

    /** 18 slotu da dolu bir yonetici. */
    private AhListingManager full() {
        AhListingManager manager = new AhListingManager(18);
        while (manager.canListMore()) {
            manager.onListingSent();
            manager.onListingVerified();
        }
        return manager;
    }

    @Test
    void countsListingsUpToTheSlotLimit() {
        AhListingManager manager = new AhListingManager(2);
        assertTrue(manager.canListMore());

        manager.onListingSent();
        manager.onListingVerified();
        assertTrue(manager.canListMore());

        manager.onListingSent();
        manager.onListingVerified();
        assertFalse(manager.canListMore(), "limit dolunca yeni ilan yok");
        assertEquals(2, manager.getActiveListings());
    }

    @Test
    void otherPlayersSalesAreIgnored() {
        AhListingManager manager = full();
        assertFalse(manager.onChatMessage("[Auction] Notch bought Ladder from Steve for $35,000!"));
        assertFalse(manager.onChatMessage("Steve sold 64 diamonds to Alex for $1,000,000"));
        assertEquals(0, manager.getItemsSold());
        assertEquals(18, manager.getActiveListings());
    }

    @Test
    void serverLimitMessagePinsTheCounter() {
        AhListingManager manager = new AhListingManager(18);
        manager.onListingAttempt();
        assertTrue(manager.onChatMessage("§cYou have too many listed items!"));
        assertTrue(manager.isLimitReached());
        assertEquals(18, manager.getActiveListings());
        assertFalse(manager.canListMore());
    }

    @Test
    void ordinaryChatDoesNotMoveTheCounter() {
        AhListingManager manager = full();
        assertFalse(manager.onChatMessage("<Steve> my base got removed lol"));
        assertFalse(manager.onChatMessage("Event expired"));
        assertEquals(18, manager.getActiveListings());
    }

    @Test
    void ownCancelledListingFreesASlot() {
        AhListingManager manager = full();
        assertTrue(manager.onChatMessage("You collected your listing: Ladder"));
        assertEquals(17, manager.getActiveListings());
        assertEquals(0, manager.getItemsSold());
    }

    @Test
    void combatTagBlocksListingForTwentySeconds() {
        AhListingManager manager = new AhListingManager(18);
        assertTrue(manager.onChatMessage("§cYou cannot do this in combat!"));
        assertTrue(manager.isInCombat());
        assertFalse(manager.canListMore());
    }

    @Test
    void combatWordInOrdinaryChatIsNotACombatTag() {
        AhListingManager manager = new AhListingManager(18);
        assertFalse(manager.onChatMessage("<Steve> combat update when"));
        assertFalse(manager.isInCombat());
    }

    @Test
    void rejectedListingDoesNotConsumeASlot() {
        AhListingManager manager = new AhListingManager(18);
        manager.onListingSent();
        assertEquals(1, manager.getActiveListings());

        assertTrue(manager.onChatMessage("§cYou cannot do that while in the air!"));
        assertEquals(0, manager.getActiveListings(), "reddedilen ilan slot harcamamalı");
    }

    @Test
    void combatRejectionAlsoReturnsTheSlot() {
        AhListingManager manager = new AhListingManager(18);
        manager.onListingSent();
        assertTrue(manager.onChatMessage("§cYou cannot do this in combat!"));
        assertEquals(0, manager.getActiveListings());
        assertTrue(manager.isInCombat());
    }

    @Test
    void failureWithNothingPendingChangesNothing() {
        AhListingManager manager = full();
        assertFalse(manager.onChatMessage("§cYou cannot fly here!"));
        assertEquals(18, manager.getActiveListings());
    }

    @Test
    void listingsScreenIsTheAuthorityOnSlotCount() {
        AhListingManager manager = new AhListingManager(18);
        manager.onListingSent();
        manager.onListingSent();

        // Ekranda elle konmuş ilanlarla birlikte 7 tane görünüyor
        manager.syncActiveListings(7);
        assertEquals(7, manager.getActiveListings());
        assertTrue(manager.canListMore());

        manager.syncActiveListings(18);
        assertFalse(manager.canListMore());
    }

    @Test
    void resetClearsEverything() {
        AhListingManager manager = full();
        manager.resetAll();
        assertEquals(0, manager.getActiveListings());
        assertFalse(manager.isLimitReached());
        assertTrue(manager.canListMore());
    }
}
