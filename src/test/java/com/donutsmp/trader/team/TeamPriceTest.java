package com.donutsmp.trader.team;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamPriceTest {

    private static PeerState peer(String name, String item, int lot, long price) {
        return new PeerState(name, 1000L, true, item, lot, price, 64, 3, 4, 18);
    }

    @Test
    void aLonePlayerKeepsTheirOwnPrice() {
        assertEquals(8000.0, TeamPrice.floor(8000, "ladder", 1, List.of()), 0.001);
        assertEquals(8000.0, TeamPrice.floor(8000, "ladder", 1, null), 0.001);
    }

    @Test
    void doesNotGoUnderATeammate() {
        List<PeerState> peers = List.of(peer("Kaan", "ladder", 1, 9000));
        assertEquals(9000.0, TeamPrice.floor(8000, "ladder", 1, peers), 0.001);
    }

    @Test
    void aCheaperTeammateDoesNotDragUsDown() {
        List<PeerState> peers = List.of(peer("Kaan", "ladder", 1, 7000));
        assertEquals(9000.0, TeamPrice.floor(9000, "ladder", 1, peers), 0.001);
    }

    @Test
    void adifferentItemIsNotOurBusiness() {
        List<PeerState> peers = List.of(peer("Kaan", "water_bucket", 1, 90_000));
        assertEquals(8000.0, TeamPrice.floor(8000, "ladder", 1, peers), 0.001);
    }

    @Test
    void lotSizesAreComparedPerItem() {
        // Kaan 16'lik lotu 160.000'e satiyor: tanesi 10.000.
        List<PeerState> peers = List.of(peer("Kaan", "ladder", 16, 160_000));
        assertEquals(10_000.0, TeamPrice.floor(8000, "ladder", 1, peers), 0.001);
        assertEquals(40_000.0, TeamPrice.floor(30_000, "ladder", 4, peers), 0.001);
    }

    @Test
    void apausedTeammateIsNotSelling() {
        PeerState paused = new PeerState("Kaan", 1000, false, "ladder", 1, 9000, 64, 3, 4, 18);
        assertEquals(8000.0, TeamPrice.floor(8000, "ladder", 1, List.of(paused)), 0.001);
    }

    @Test
    void theCheapestTeammateSetsTheFloor() {
        List<PeerState> peers = List.of(
                peer("Kaan", "ladder", 1, 12_000),
                peer("Burak", "ladder", 1, 9500));
        assertEquals(9500.0, TeamPrice.floor(8000, "ladder", 1, peers), 0.001);
    }

    @Test
    void bindingSaysWhoIsHoldingThePrice() {
        List<PeerState> peers = List.of(peer("Kaan", "ladder", 1, 9000));
        assertTrue(TeamPrice.binding(8000, "ladder", 1, peers));
        assertFalse(TeamPrice.binding(9500, "ladder", 1, peers));
    }

    /**
     * Iki mod birbirinin fiyatini besleyip yukarida kilitlenmemeli.
     *
     * Yayinlanan fiyat taban UYGULANDIKTAN sonraki fiyat olursa, ikisi de
     * otekinin eski fiyatini kendi tabani yapar ve piyasa dusse bile hicbiri
     * inemez. Bu test iki tasarimi yan yana kosturup farki gosteriyor.
     */
    @Test
    void publishingTheListedPriceLocksBothModsAtTheOldPrice() {
        assertEquals(9000, settle(9000, 6999, true), "yanlis tasarim: piyasa 6999 iken 9000'de kilitli");
    }

    @Test
    void publishingThePriceBeforeTheTeamFloorFollowsTheMarketDown() {
        assertEquals(6999, settle(9000, 6999, false));
    }

    /**
     * Iki modu birkac tur kosturur.
     *
     * @param start        ikisinin de basladigi fiyat
     * @param market       ikisinin de piyasadan okudugu fiyat
     * @param publishFloor yayinlanan fiyata takim tabani dahil mi
     * @return turlar sonunda listelenen fiyat
     */
    private static long settle(long start, long market, boolean publishFloor) {
        long shownA = start, shownB = start;
        long listA = start, listB = start;

        for (int round = 0; round < 10; round++) {
            listA = (long) TeamPrice.floor(market, "ladder", 1, List.of(peer("B", "ladder", 1, shownB)));
            listB = (long) TeamPrice.floor(market, "ladder", 1, List.of(peer("A", "ladder", 1, shownA)));
            shownA = publishFloor ? listA : market;
            shownB = publishFloor ? listB : market;
        }
        assertEquals(listA, listB, "iki taraf ayni fiyatta bulusmali");
        return listA;
    }
}
