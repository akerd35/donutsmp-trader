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
        assertEquals(18, manager.getActiveListings());
        return manager;
    }

    @Test
    void countsListingsUpToTheSlotLimit() {
        AhListingManager manager = new AhListingManager(2);

        manager.onListingSent();
        manager.onListingVerified();
        assertEquals(1, manager.getActiveListings());

        manager.onListingSent();
        manager.onListingVerified();
        assertEquals(2, manager.getActiveListings());
        assertTrue(manager.isLimitReached(), "sayac gostergede dolu");

        // Ama sayac artik kapi degil: durdurmak sunucunun isi.
        assertTrue(manager.canListMore(), "sayac dolu diye listeleme durmaz");
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
        assertFalse(manager.isLimitReached());

        manager.syncActiveListings(18);
        assertEquals(18, manager.getActiveListings());
        assertTrue(manager.isLimitReached(), "gosterge dolu");
        assertTrue(manager.canListMore(), "yine de denenir; durduran sunucudur");
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
     * Sayac dolu dese bile listeleme durmamali.
     *
     * Olculdu: sayac 18/18'de takiliyken gercekte dokuz slot bostu ve mod
     * 5,5 dakika hic ilan acmadi. Kac slot oldugunu yalnizca sunucu bilir,
     * o yuzden sayac artik kapi degil.
     */
    @Test
    void afullCounterDoesNotStopListing() {
        AhListingManager manager = full();
        assertEquals(18, manager.getActiveListings());
        assertTrue(manager.canListMore(), "sayac dolu diye durmamali");
    }

    /** Gercekten dolu oldugunu yalnizca sunucu soyleyebilir. */
    @Test
    void onlyTheServerCanStopUs() {
        AhListingManager manager = new AhListingManager(18);
        long now = System.currentTimeMillis();
        assertTrue(manager.canListMore(now));

        manager.onChatMessage("You have too many listed items!");
        assertFalse(manager.canListMore(now + 1_000), "reddin ardindan beklenir");
        assertTrue(manager.canListMore(now + AhListingManager.limitBackoffMs() + 1_000),
                "sure dolunca yine denenir");
    }

    /** Ilan girdiyse sunucu bizi sinirlamiyor: bekleme kalkar. */
    @Test
    void averifiedListingClearsTheServerLock() {
        AhListingManager manager = new AhListingManager(18);
        manager.onChatMessage("You have too many listed items!");
        assertFalse(manager.canListMore());

        manager.onListingSent();
        manager.onListingVerified();
        assertTrue(manager.canListMore());
    }

    /** Bir satis da beklemeyi kaldirir. */
    @Test
    void asaleReopensListingImmediately() {
        AhListingManager manager = new AhListingManager(18);
        manager.onChatMessage("You have too many listed items!");
        assertFalse(manager.canListMore());

        manager.onChatMessage("Your Ladder sold for $9,000!");
        assertTrue(manager.canListMore(), "satis sonrasi hemen listeleyebilmeli");
    }

    /**
     * Sunucu "dolu" deyince ilan da girmemis olur ve dogrulama basarisiz olur.
     * O yol beklemeyi silmemeli, yoksa reddin ustune bir saniye sonra yeniden
     * sorulur.
     */
    @Test
    void afailedVerificationDoesNotCancelTheServersBackoff() {
        AhListingManager manager = new AhListingManager(18);
        manager.onListingSent();
        manager.onChatMessage("You have too many listed items!");
        assertFalse(manager.canListMore());

        manager.onListingRejected();   // esya elde kaldi
        assertFalse(manager.canListMore(), "sunucunun sozu gecerli kalmali");
    }

    /** Gosterge, tahmini sayac ile sunucunun cevabini ayirmali. */
    @Test
    void theDisplayTellsAGuessApartFromTheServersAnswer() {
        AhListingManager manager = full();
        assertTrue(manager.isLimitReached(), "sayac dolu goruyor");
        assertFalse(manager.isServerLimited(), "ama sunucu bir sey demedi");
        assertEquals(0, manager.limitSecondsLeft());

        manager.onChatMessage("You have too many listed items!");
        assertTrue(manager.isServerLimited());
        assertTrue(manager.limitSecondsLeft() > 0 && manager.limitSecondsLeft() <= 15);
    }

    /** Savas hâlâ durdurur; o sunucunun baska bir kurali. */
    @Test
    void combatStillStopsEverything() {
        AhListingManager manager = new AhListingManager(18);
        manager.onChatMessage("You cannot do this while in combat!");
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
