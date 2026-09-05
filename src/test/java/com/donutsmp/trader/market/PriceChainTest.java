package com.donutsmp.trader.market;

import com.donutsmp.trader.team.PeerState;
import com.donutsmp.trader.team.TeamPrice;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Menudeki tahtadan gonderilen fiyata kadar butun zincir.
 *
 * Parcalar tek tek test ediliyor ama asil soru bileske: "ah sell" ile giden
 * sayi, elimizdeki lotun tamami icin dogru mu? Zincirdeki her deger BIR
 * ILANIN TOPLAMI olmali; biri tane fiyatina kaysa hata sessizce gecer.
 */
class PriceChainTest {

    private static final Set<Long> NO_OWN = Set.of();
    private static final List<String> ME = List.of("sinlech");

    private static final double FLOOR = 5_000;
    private static final double UNDERCUT = 1.0;
    private static final double PERCENT = 0.0;
    private static final double MIN_STEP = 10;
    private static final double MIN_GAP = 2_000;
    private static final int MIN_BELOW = 3;

    private static MarketListing.Entry listing(int count, String seller, String price) {
        return new MarketListing.Entry("ladder", count,
                List.of("§7Seller: §f" + seller, "§7Price: §a$" + price));
    }

    /** Tahtadan fiyata: tarama, politika, takim tabani. */
    private static double chain(List<MarketListing.Entry> board, int lotSize,
                                double current, List<PeerState> peers, List<String> ourNames) {
        MarketListing.Board scan = MarketListing.scan(board, "ladder", lotSize, ourNames, NO_OWN);
        if (scan.empty()) return Math.max(current, FLOOR);

        PricePolicy.Decision decision = PricePolicy.decide(current, scan.cheapest(),
                scan.below(current), FLOOR, UNDERCUT, PERCENT, MIN_STEP, MIN_GAP, MIN_BELOW);
        double price = Math.max(decision.price(), FLOOR);
        return TeamPrice.floor(price, "ladder", lotSize, peers);
    }

    @Test
    void threeCheapRivalsPullThePriceDown() {
        List<MarketListing.Entry> board = List.of(
                listing(1, "A", "9,000"),
                listing(1, "B", "9,100"),
                listing(1, "C", "9,200"),
                listing(1, "sinlech", "12,000"));

        assertEquals(8_999, chain(board, 1, 12_000, List.of(), ME), 0.001);
    }

    @Test
    void oneCheapRivalIsNotEnoughToMove() {
        List<MarketListing.Entry> board = List.of(
                listing(1, "A", "9,000"),
                listing(1, "B", "12,500"),
                listing(1, "C", "13,000"));

        assertEquals(12_000, chain(board, 1, 12_000, List.of(), ME), 0.001,
                "tek ucuz ilan icin fiyat kirilmaz");
    }

    /**
     * Asil korunan hata: tahtadaki tek ucuz ilan 64'luk bir yigin.
     *
     * Boyut kontrolu olmasa fiyat 4.999'a inerdi ve tek merdivenimiz 64'lugun
     * yarisina giderdi.
     */
    @Test
    void aBulkDumpDoesNotDragASinglesPriceDown() {
        // Uc tane toplu ilan: "en az 3 ucuz rakip" kurali burada korumaz,
        // koruyan tek sey yigin boyutu kontrolu.
        List<MarketListing.Entry> board = List.of(
                listing(64, "Dumper", "5,000"),
                listing(64, "Dumper2", "5,100"),
                listing(32, "Dumper3", "4,000"),
                listing(1, "A", "12,500"),
                listing(1, "B", "13,000"),
                listing(1, "C", "13,500"));

        double price = chain(board, 1, 12_000, List.of(), ME);
        assertTrue(price >= 12_000,
                "toplu yiginlar tek esyanin fiyatini indirmemeli, indi: " + price);
    }

    /** Lotumuz 16 iken yalnizca 16'lik ilanlar rakip. */
    @Test
    void alotIsPricedAgainstLotsOfTheSameSize() {
        List<MarketListing.Entry> board = List.of(
                listing(1, "A", "9,000"),
                listing(16, "B", "140,000"),
                listing(16, "C", "141,000"),
                listing(16, "D", "142,000"));

        assertEquals(139_999, chain(board, 16, 160_000, List.of(), ME), 0.001);
    }

    @Test
    void noComparableListingLeavesThePriceAlone() {
        List<MarketListing.Entry> board = List.of(listing(64, "A", "5,000"), listing(32, "B", "4,000"));
        assertEquals(12_000, chain(board, 1, 12_000, List.of(), ME), 0.001);
    }

    @Test
    void theFloorWinsOverTheMarket() {
        List<MarketListing.Entry> board = List.of(
                listing(1, "A", "4,000"),
                listing(1, "B", "4,100"),
                listing(1, "C", "4,200"));

        assertEquals(FLOOR, chain(board, 1, 12_000, List.of(), ME), 0.001);
    }

    /**
     * Takim arkadasi rakip degil.
     *
     * Kaan 9.000'e satiyor ama o bizden. Onu atlayinca en ucuz GERCEK rakip
     * 12.500, yani piyasa ustumuzde: fiyat dusmez, yukselir.
     */
    @Test
    void aTeammateNeitherDragsUsDownNorCountsAsARival() {
        List<MarketListing.Entry> board = List.of(
                listing(1, "Kaan", "9,000"),
                listing(1, "A", "12,500"),
                listing(1, "B", "13,000"),
                listing(1, "C", "13,500"));

        List<PeerState> peers = List.of(
                new PeerState("Kaan", System.currentTimeMillis(), true, "ladder", 1, 9_000, 64, 3, 4, 18));

        double price = chain(board, 1, 12_000, peers, List.of("sinlech", "kaan"));
        assertEquals(12_499, price, 0.001, "arkadasin 9.000'i fiyati asagi cekmemeli");
        assertTrue(price > 9_000, "arkadasin altina inilmedi");
    }

    @Test
    void everyPriceInTheChainIsAWholeListingNotAUnit() {
        List<MarketListing.Entry> board = List.of(
                listing(16, "A", "80,000"),
                listing(16, "B", "81,000"),
                listing(16, "C", "82,000"));

        double price = chain(board, 16, 100_000, List.of(), ME);
        assertEquals(79_999, price, 0.001);
        assertTrue(price > 16 * FLOOR / 16, "fiyat lotun tamami icin");
    }
}
