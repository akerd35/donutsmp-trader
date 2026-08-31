package com.donutsmp.trader.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AhListingManagerTest {

    private AhListingManager full() {
        AhListingManager manager = new AhListingManager(18);
        for (int i = 0; i < 20; i++) {
            manager.offerTask(new AhListingManager.ListingTask("ladder", 1, 35000.0, 0));
        }
        while (manager.hasAvailableSlot()) {
            AhListingManager.ListingTask task = manager.pollNextTask();
            if (task == null) break;
            manager.markCommandSent();
            manager.onListingConfirmed();
        }
        return manager;
    }

    @Test
    void stopsAtTheSlotLimit() {
        AhListingManager manager = full();
        assertEquals(18, manager.getActiveListings());
        assertEquals(2, manager.getQueueSize());
        assertFalse(manager.hasAvailableSlot());
        assertNull(manager.pollNextTask());
    }

    @Test
    void ownSaleFreesASlotAndBanksThePrice() {
        AhListingManager manager = full();
        assertTrue(manager.onChatMessage("[Auction] Your item Ladder was sold to StevePvP for $35,000!"));
        assertEquals(1, manager.getItemsSold());
        assertEquals(35000L, manager.getTotalEarned());
        assertEquals(17, manager.getActiveListings());
        assertTrue(manager.hasAvailableSlot());
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
    void findsTheGreenConfirmButton() {
        String[] items = new String[27];
        String[] displays = new String[27];
        items[11] = "red_stained_glass_pane";
        displays[11] = "Cancel";
        items[15] = "lime_stained_glass_pane";
        displays[15] = "Confirm Listing";
        assertEquals(15, AhListingManager.findConfirmButtonSlot(items, displays));
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
        assertEquals(0, manager.getQueueSize());
        assertTrue(manager.canListMore());
    }
}
