package com.donutsmp.trader.market;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ekran taramasi: hangi ilan rakip sayilir?
 *
 * Yigin boyutu bu isin tamami. Ayni esyanin 1'lik ve 64'luk ilani ayni mal
 * degildir ve fiyatlari kiyaslanamaz.
 */
class BoardScanTest {

    private static final Set<Long> NO_OWN = Set.of();
    private static final List<String> US = List.of("sinlech");

    private static MarketListing.Entry listing(String item, int count, String seller, String price) {
        return new MarketListing.Entry(item, count,
                List.of("§7Seller: §f" + seller, "§7Price: §a$" + price));
    }

    @Test
    void aSameSizeListingIsACompetitor() {
        MarketListing.Board board = MarketListing.scan(
                List.of(listing("ladder", 1, "Notch", "9,800")), "ladder", 1, US, NO_OWN);
        assertEquals(9800.0, board.cheapest(), 0.001);
        assertEquals(1, board.prices().size());
    }

    /**
     * Asil hata buydu: 64 merdiveni 5.000'e basan biri, tek merdivenimizin
     * rakibi degil. Onun altina inmek 10.000'lik esyayi 4.999'a vermekti.
     */
    @Test
    void aBulkListingIsNotACompetitorForASingle() {
        MarketListing.Board board = MarketListing.scan(
                List.of(listing("ladder", 64, "Notch", "5,000")), "ladder", 1, US, NO_OWN);
        assertTrue(board.empty(), "64'luk yigin tek esyanin rakibi degil");
        assertEquals(1, board.skippedSize());
        assertEquals(-1, board.cheapest(), 0.001);
    }

    /** Tersi daha pahaliya patlar: 16'lik lotu tek esyanin altina asmak. */
    @Test
    void aSingleListingIsNotACompetitorForALot() {
        MarketListing.Board board = MarketListing.scan(
                List.of(listing("ladder", 1, "Notch", "10,000")), "ladder", 16, US, NO_OWN);
        assertTrue(board.empty());
        assertEquals(1, board.skippedSize());
    }

    @Test
    void onlyTheSameItemCounts() {
        MarketListing.Board board = MarketListing.scan(
                List.of(listing("water_bucket", 1, "Notch", "500")), "ladder", 1, US, NO_OWN);
        assertTrue(board.empty());
        assertEquals(0, board.skippedSize(), "baska esya boyut yuzunden atlanmis sayilmaz");
    }

    @Test
    void ourOwnAndOurTeamAreCountedSeparately() {
        MarketListing.Board board = MarketListing.scan(List.of(
                listing("ladder", 1, "sinlech", "9,999"),
                listing("ladder", 1, "Kaan", "9,500"),
                listing("ladder", 1, "Notch", "9,800")),
                "ladder", 1, List.of("sinlech", "kaan"), NO_OWN);

        assertEquals(9800.0, board.cheapest(), 0.001);
        assertEquals(2, board.skippedOwn());
        assertEquals(0, board.skippedSize());
    }

    @Test
    void theCheapestComparableWins() {
        MarketListing.Board board = MarketListing.scan(List.of(
                listing("ladder", 1, "A", "12,000"),
                listing("ladder", 64, "B", "1,000"),
                listing("ladder", 1, "C", "9,500")),
                "ladder", 1, US, NO_OWN);

        assertEquals(9500.0, board.cheapest(), 0.001);
        assertEquals(1, board.skippedSize());
    }

    @Test
    void countsHowManyUndercutUs() {
        MarketListing.Board board = MarketListing.scan(List.of(
                listing("ladder", 1, "A", "9,000"),
                listing("ladder", 1, "B", "9,500"),
                listing("ladder", 1, "C", "11,000")),
                "ladder", 1, US, NO_OWN);

        assertEquals(2, board.below(10_000));
        assertEquals(0, board.below(0), "fiyatimiz yoksa kimse altimizda degil");
        assertEquals(3, board.below(99_999));
    }

    @Test
    void anEmptyBoardIsNotAnError() {
        MarketListing.Board board = MarketListing.scan(List.of(), "ladder", 1, US, NO_OWN);
        assertTrue(board.empty());
        assertEquals(-1, board.cheapest(), 0.001);
        assertTrue(MarketListing.scan(null, "ladder", 1, US, NO_OWN).empty());
    }

    @Test
    void aListingWithoutAReadablePriceIsSkipped() {
        MarketListing.Board board = MarketListing.scan(
                List.of(new MarketListing.Entry("ladder", 1, List.of("Click to buy"))),
                "ladder", 1, US, NO_OWN);
        assertTrue(board.empty());
    }
}
