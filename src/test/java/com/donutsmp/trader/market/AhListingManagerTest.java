package com.donutsmp.trader.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AhListingManagerTest {

    /** 18 slotu da dolu bir yonetici. */
    private AhListingManager full() {
        AhListingManager manager = new AhListingManager(18);
        for (int i = 0; i < 18; i++) {
            manager.onListingSent();
            manager.onListingVerified();
        }
        assertFalse(manager.canListMore(), "on sekiz ilandan sonra sayac dolu olmali");
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

    /**
     * Sayac kaymissa duzeltme gorunur olmali.
     *
     * Olculdu: sayac 18/18'de takiliyken gercek 8 idi ve mod 5,5 dakika hic
     * ilan acmadi. Duzeltme sessizce gectigi icin de kimse fark etmedi.
     */
    @Test
    void syncReportsThePreviousCounterSoDriftIsVisible() {
        AhListingManager manager = full();
        assertEquals(18, manager.getActiveListings());

        int before = manager.syncActiveListings(8);
        assertEquals(18, before, "duzeltilen deger geri donmeli");
        assertEquals(8, manager.getActiveListings());
        assertTrue(manager.canListMore(), "duzeltmeden sonra yeniden listeleyebilmeli");
    }

    @Test
    void syncReportsNoDriftWhenTheCounterWasRight() {
        AhListingManager manager = new AhListingManager(18);
        manager.onListingSent();
        manager.onListingVerified();
        assertEquals(1, manager.syncActiveListings(1));
    }

    /** Sunucu "too many items" dedikten sonra da duzeltme calismali. */
    @Test
    void syncClearsAStuckLimitFlag() {
        AhListingManager manager = new AhListingManager(18);
        manager.onChatMessage("You have too many listed items!");
        assertFalse(manager.canListMore());

        manager.syncActiveListings(3);
        assertTrue(manager.canListMore(), "gercek 3/18 ise kilit kalkmali");
        assertEquals(3, manager.getActiveListings());
    }

    // ---------- sayac kaymasindan cikis ----------

    /**
     * Sayac dolu derken bile arada bir denenmeli.
     *
     * Olculdu: sayac 18/18'de takiliyken gercek 8 idi ve mod 5,5 dakika hic
     * ilan acmadi. Karar sayacin degil sunucunun.
     */
    @Test
    void afullCounterStillGetsARetryAfterTheWindow() {
        AhListingManager manager = full();
        long now = System.currentTimeMillis();

        assertFalse(manager.canListMore(now), "hemen degil");
        assertFalse(manager.canListMore(now + 14_000), "penceresi dolmadan degil");
        assertTrue(manager.canListMore(now + AhListingManager.retryWhenFullMs() + 100),
                "on bes saniye sonra yine denenmeli");
    }

    /** Deneme tuttuysa sayac yanilmis: hemen devam edebilmeli. */
    @Test
    void asuccessfulRetryUnblocksTheCounter() {
        AhListingManager manager = full();
        assertEquals(18, manager.getActiveListings());

        manager.onListingSent();        // dolu sanirken deneme
        manager.onListingVerified();    // tuttu

        assertEquals(17, manager.getActiveListings(), "sayac gercegin ustundeymis");
        assertTrue(manager.canListMore(), "beklemeden devam etmeli");
    }

    /**
     * Normal listeleme sayaci azaltmamali.
     *
     * Kosulsuz azaltma sayacin sinira hic oturmamasina yol aciyordu; testte
     * sonsuz donguye girdi, oyunda ise durmadan ilan acmaya calisirdi.
     */
    @Test
    void anOrdinaryListingDoesNotWindTheCounterBack() {
        AhListingManager manager = new AhListingManager(3);
        for (int i = 0; i < 3; i++) {
            manager.onListingSent();
            manager.onListingVerified();
        }
        assertEquals(3, manager.getActiveListings());
        assertFalse(manager.canListMore());
    }

    /** Sunucu "dolu" derse pencere reddin oldugu andan itibaren yeniden baslar. */
    @Test
    void theServersRefusalRestartsTheWindow() {
        AhListingManager manager = full();
        long window = AhListingManager.retryWhenFullMs();
        long now = System.currentTimeMillis();
        assertTrue(manager.canListMore(now + window + 100), "pencere dolunca denenebilirdi");

        manager.onChatMessage("You have too many listed items!");

        assertFalse(manager.canListMore(now + 1_000), "reddin hemen ardindan denenmez");
        assertTrue(manager.canListMore(now + window + 5_000), "yeni pencere de dolunca yine denenir");
    }

    /** Bir satis yer acar: beklemeye gerek yok. */
    @Test
    void asaleReopensListingImmediately() {
        AhListingManager manager = full();
        manager.onChatMessage("Your Ladder sold for $9,000!");
        assertTrue(manager.canListMore(), "satis sonrasi hemen listeleyebilmeli");
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
