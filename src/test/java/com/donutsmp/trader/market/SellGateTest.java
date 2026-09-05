package com.donutsmp.trader.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellGateTest {

    /** Her sey yolunda: elde bir lot var, fiyat belli. */
    private static SellGate.Facts ready() {
        return new SellGate.Facts(false, false, false, true, true, true, true, true, true, 9000);
    }

    private static SellGate.Facts with(SellGate.Facts f, java.util.function.Function<SellGate.Facts, SellGate.Facts> edit) {
        return edit.apply(f);
    }

    // ---------- her seyi durduranlar ----------

    @Test
    void nothingBlocksAWorkingMod() {
        assertNull(SellGate.blocking(true, false, false, false));
    }

    @Test
    void aPausedModIsTheFirstAnswer() {
        assertEquals(SellGate.Reason.DISABLED, SellGate.blocking(false, true, true, true));
    }

    /**
     * Sira davranistir: savasta beklemenin bitmesini beklemek anlamsiz, ama
     * mola savastan once gelir cunku mola sirasinda hicbir sey yapilmaz.
     */
    @Test
    void restBeatsBackoffBeatsCombat() {
        assertEquals(SellGate.Reason.RESTING, SellGate.blocking(true, true, true, true));
        assertEquals(SellGate.Reason.BACKOFF, SellGate.blocking(true, false, true, true));
        assertEquals(SellGate.Reason.COMBAT, SellGate.blocking(true, false, false, true));
    }

    // ---------- satisi durduranlar ----------

    @Test
    void aReadyPlayerLists() {
        SellGate.Verdict verdict = SellGate.next(ready());
        assertEquals(SellGate.Action.LIST, verdict.action());
        assertEquals(SellGate.Reason.GO, verdict.reason());
        assertTrue(verdict.acting());
    }

    @Test
    void theOrderOfRefusalsIsFixed() {
        SellGate.Facts f = ready();
        assertEquals(SellGate.Reason.VERIFYING, SellGate.next(with(f, x -> new SellGate.Facts(
                true, true, true, false, false, false, true, true, true, 0))).reason());
        assertEquals(SellGate.Reason.WAITING_MARKET, SellGate.next(new SellGate.Facts(
                false, true, true, false, false, false, true, true, true, 0)).reason());
        assertEquals(SellGate.Reason.COOLDOWN, SellGate.next(new SellGate.Facts(
                false, false, true, false, false, false, true, true, true, 0)).reason());
        assertEquals(SellGate.Reason.SLOTS_FULL, SellGate.next(new SellGate.Facts(
                false, false, false, false, false, false, true, true, true, 0)).reason());
        assertEquals(SellGate.Reason.IN_AIR, SellGate.next(new SellGate.Facts(
                false, false, false, true, false, false, true, true, true, 0)).reason());
        assertEquals(SellGate.Reason.BAD_ITEM, SellGate.next(new SellGate.Facts(
                false, false, false, true, true, false, true, true, true, 0)).reason());
    }

    @Test
    void anEmptyHandMeansBuildingALotFirst() {
        SellGate.Verdict verdict = SellGate.next(new SellGate.Facts(
                false, false, false, true, true, true, false, true, true, 9000));
        assertEquals(SellGate.Action.SPLIT, verdict.action());
    }

    @Test
    void aLotCannotBeBuiltWithoutRoomOrStock() {
        assertEquals(SellGate.Reason.NO_HOTBAR, SellGate.next(new SellGate.Facts(
                false, false, false, true, true, true, false, false, true, 9000)).reason());
        assertEquals(SellGate.Reason.NO_ITEMS, SellGate.next(new SellGate.Facts(
                false, false, false, true, true, true, false, true, false, 9000)).reason());
    }

    /**
     * Elimizde lot varsa hotbar'in dolu olmasi onemli degil.
     *
     * Aksi hâlde son lotu elinde tutan oyuncu onu hiç satamazdı: hotbar dolu
     * diye reddedilir, ama bölecek bir şey de kalmamıştır.
     */
    @Test
    void aFullHotbarDoesNotBlockAnAlreadyHeldLot() {
        SellGate.Verdict verdict = SellGate.next(new SellGate.Facts(
                false, false, false, true, true, true, true, false, false, 9000));
        assertEquals(SellGate.Action.LIST, verdict.action());
    }

    @Test
    void nothingIsListedWithoutAPrice() {
        assertEquals(SellGate.Reason.NO_PRICE, SellGate.next(new SellGate.Facts(
                false, false, false, true, true, true, true, true, true, 0)).reason());
        assertEquals(SellGate.Action.WAIT, SellGate.next(new SellGate.Facts(
                false, false, false, true, true, true, true, true, true, -5)).action());
    }

    @Test
    void everyReasonCanBeShownToThePlayer() {
        for (SellGate.Reason reason : SellGate.Reason.values()) {
            assertTrue(reason.text() != null && !reason.text().isBlank(), reason + " metinsiz");
        }
    }
}
