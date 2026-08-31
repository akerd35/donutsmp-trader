package com.donutsmp.trader.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AhScreensTest {

    @Test
    void recognisesTheServersOwnListingsTitle() {
        assertTrue(AhScreens.isMyListings("Auction -> Your Items"));
        assertTrue(AhScreens.isMyListings("Your Listings"));
        assertTrue(AhScreens.isMyListings("Auction -> My Listings"));
    }

    @Test
    void ourOwnListingsAreNeverTreatedAsTheMarket() {
        assertFalse(AhScreens.isMarket("Auction -> Your Items"),
                "kendi ilanlarımızı taramak fiyatımızı kendi kendine kırar");
    }

    @Test
    void recognisesTheBrowseScreen() {
        assertTrue(AhScreens.isMarket("Auction"));
        assertTrue(AhScreens.isMarket("Auction -> Ladder"));
        assertFalse(AhScreens.isMyListings("Auction"));
    }

    @Test
    void aChestCalledYourItemsIsNotTheListingsScreen() {
        assertFalse(AhScreens.isMyListings("Your Items"));
    }

    @Test
    void unrelatedScreensAreNeitherOfThem() {
        for (String title : new String[] {"Chest", "Large Chest", "Shulker Box", "Crafting", "", null}) {
            assertFalse(AhScreens.isMarket(title), "beklenmeyen ekran piyasa sayılmamalı: " + title);
            assertFalse(AhScreens.isMyListings(title));
        }
    }
}
