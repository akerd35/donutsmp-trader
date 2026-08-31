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
    void takesTheCheapestLineWhenLoreHasSeveralNumbers() {
        List<String> lore = List.of("Price: $12,000", "Unit: $9,500");
        assertEquals(9500.0, MarketListing.competitorPrice(lore, "sinlech", NONE), 0.001);
    }

    @Test
    void loreWithoutAPriceIsIgnored() {
        assertTrue(MarketListing.competitorPrice(List.of("Click to buy"), "sinlech", NONE) < 0);
        assertTrue(MarketListing.competitorPrice(List.of(), "sinlech", NONE) < 0);
        assertTrue(MarketListing.competitorPrice(null, "sinlech", NONE) < 0);
    }

    @Test
    void anEmptySelfNameDoesNotSwallowEveryListing() {
        assertEquals(9800.0, MarketListing.competitorPrice(List.of("Price: $9,800"), "", NONE), 0.001);
        assertEquals(9800.0, MarketListing.competitorPrice(List.of("Price: $9,800"), null, NONE), 0.001);
    }
}
