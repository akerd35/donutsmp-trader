package com.donutsmp.trader.market;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketListingTest {

    private static final Set<Long> NONE = Set.of();

    @Test
    void readsACompetitorsPrice() {
        List<String> lore = List.of("§7Seller: §fNotch", "§7Price: §a$9,800");
        assertEquals(9800.0, MarketListing.competitorPrice(lore, "sinlech", NONE), 0.001);
    }

    @Test
    void ourOwnListingIsNotACompetitor() {
        List<String> lore = List.of("§7Seller: §fsinlech", "§7Price: §a$9,999");
        assertTrue(MarketListing.competitorPrice(lore, "sinlech", NONE) < 0,
                "kendi ilanımızı rakip sayarsak fiyat spiral yapar");
    }

    @Test
    void sellerNameMatchIsCaseInsensitive() {
        List<String> lore = List.of("Seller: SinLech", "Price: $9,999");
        assertTrue(MarketListing.competitorPrice(lore, "sinlech", NONE) < 0);
    }

    @Test
    void ourOwnPriceIsSkippedWhenTheNameIsNotInTheLore() {
        List<String> lore = List.of("§7Price: §a$9,999");
        assertTrue(MarketListing.competitorPrice(lore, "sinlech", Set.of(9999L)) < 0);
        assertEquals(9800.0, MarketListing.competitorPrice(List.of("Price: $9,800"), "sinlech", Set.of(9999L)), 0.001);
    }

    @Test
    void recognisesOurOwnListingThroughTheMenusRounding() {
        // 11.999'a astık; menü "11k" gösteriyor.
        List<String> lore = List.of("§7Price: §a$11k");
        assertTrue(MarketListing.competitorPrice(lore, "sinlech", Set.of(11999L)) < 0,
                "kısaltılmış fiyat kendi ilanımızı gizlememeli");
    }

    @Test
    void aRoundedPriceFromSomeoneElseIsStillACompetitor() {
        List<String> lore = List.of("§7Price: §a$8k");
        assertEquals(8000.0, MarketListing.competitorPrice(lore, "sinlech", Set.of(11999L)), 0.001);
    }

    @Test
    void takesTheCheapestLineWhenLoreHasSeveralNumbers() {
        List<String> lore = List.of("Price: $12,000", "Unit: $9,500");
        assertEquals(9500.0, MarketListing.competitorPrice(lore, "sinlech", NONE), 0.001);
    }

    @Test
    void loreWithoutAPriceIsIgnored() {
        assertTrue(MarketListing.competitorPrice(List.of("Click to buy"), "sinlech", NONE) < 0);
        assertTrue(MarketListing.competitorPrice(List.of(), "sinlech", NONE) < 0);
        assertTrue(MarketListing.competitorPrice((List<String>) null, "sinlech", NONE) < 0);
    }

    @Test
    void anEmptySelfNameDoesNotSwallowEveryListing() {
        assertEquals(9800.0, MarketListing.competitorPrice(List.of("Price: $9,800"), "", NONE), 0.001);
        assertEquals(9800.0, MarketListing.competitorPrice(List.of("Price: $9,800"), (String) null, NONE), 0.001);
    }

    @Test
    void ateammatesListingIsNotACompetitorEither() {
        List<String> lore = List.of("§7Seller: §fKaan", "§7Price: §a$9,000");
        assertTrue(MarketListing.competitorPrice(lore, List.of("sinlech", "kaan"), NONE) < 0,
                "arkadasin fiyatini kirmak ikimizi de tabana goturur");
    }

    @Test
    void aStrangerIsStillACompetitorWithATeam() {
        List<String> lore = List.of("§7Seller: §fNotch", "§7Price: §a$9,800");
        assertEquals(9800.0, MarketListing.competitorPrice(lore, List.of("sinlech", "kaan"), NONE), 0.001);
    }

    @Test
    void aShortTeamNameCannotSwallowTheWholeMarket() {
        List<String> lore = List.of("§7Seller: §fNotch", "§7Price: §a$9,800");
        assertEquals(9800.0, MarketListing.competitorPrice(lore, List.of("ab", ""), NONE), 0.001);
    }
}
