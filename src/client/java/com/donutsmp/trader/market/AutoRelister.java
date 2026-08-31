package com.donutsmp.trader.market;

import com.donutsmp.trader.api.DonutAuctionClient;
import com.donutsmp.trader.api.model.TickerItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class AutoRelister {
    private static final Logger LOGGER = LoggerFactory.getLogger("DonutTrader-Relister");

    public static class ActiveListing {
        public final int slotIndex;
        public final String itemName;
        public final int count;
        public final double listedPrice;
        public final long listedTime;
        public boolean isUndercut = false;
        public double recommendedNewPrice = 0.0;

        public ActiveListing(int slotIndex, String itemName, int count, double listedPrice) {
            this.slotIndex = slotIndex;
            this.itemName = itemName;
            this.count = count;
            this.listedPrice = listedPrice;
            this.listedTime = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return String.format("ActiveListing{slot=%d, item='%s', qty=%d, price=$%.0f, undercut=%s, newPrice=$%.0f}",
                    slotIndex, itemName, count, listedPrice, isUndercut, recommendedNewPrice);
        }
    }

    public enum RelistActionType {
        NONE,
        CANCEL_LISTING,
        RELIST_AT_NEW_PRICE
    }

    public static class RelistDecision {
        public final RelistActionType action;
        public final ActiveListing listing;
        public final double targetPrice;
        public final String reason;

        public RelistDecision(RelistActionType action, ActiveListing listing, double targetPrice, String reason) {
            this.action = action;
            this.listing = listing;
            this.targetPrice = targetPrice;
            this.reason = reason;
        }

        @Override
        public String toString() {
            return String.format("RelistDecision{action=%s, item='%s', targetPrice=$%.0f, reason='%s'}",
                    action, (listing != null ? listing.itemName : "null"), targetPrice, reason);
        }
    }

    private final DonutAuctionClient apiClient;
    private double minPriceFloor = 5000.0; // Taban fiyat koruması (Zararına satış engeli)
    private double undercutAmount = 1.0;
    private double undercutPercent = 0.0;

    public AutoRelister(DonutAuctionClient apiClient) {
        this.apiClient = apiClient;
    }

    public AutoRelister(DonutAuctionClient apiClient, double minPriceFloor, double undercutAmount, double undercutPercent) {
        this.apiClient = apiClient;
        this.minPriceFloor = minPriceFloor;
        this.undercutAmount = undercutAmount;
        this.undercutPercent = undercutPercent;
    }

    public List<RelistDecision> evaluateListings(List<ActiveListing> myActiveListings) {
        List<RelistDecision> decisions = new ArrayList<>();
        if (myActiveListings == null || myActiveListings.isEmpty()) {
            return decisions;
        }

        for (ActiveListing listing : myActiveListings) {
            TickerItem ticker = apiClient.getCheapestListing(listing.itemName);
            if (ticker == null) {
                continue;
            }

            double competitorUnitPrice = ticker.getUnitPrice() > 0 ? ticker.getUnitPrice() : ticker.getListingPrice();
            double myUnitPrice = listing.listedPrice / Math.max(1, listing.count);

            // Fiyatımız kırılmışsa rakibin altına in
            if (myUnitPrice > competitorUnitPrice) {
                double targetUnitPrice = Undercut.target(competitorUnitPrice, undercutAmount, undercutPercent, minPriceFloor);

                if (targetUnitPrice < myUnitPrice) {
                    listing.isUndercut = true;
                    listing.recommendedNewPrice = targetUnitPrice * listing.count;

                    decisions.add(new RelistDecision(
                            RelistActionType.CANCEL_LISTING,
                            listing,
                            listing.recommendedNewPrice,
                            String.format("Rakip fiyatı ($%.0f), bizim fiyatımızdan ($%.0f) ucuz. Yeni hedef: $%.0f",
                                    competitorUnitPrice, myUnitPrice, listing.recommendedNewPrice)
                    ));
                    LOGGER.info("[FIYAT KIRILDI] {} için fiyat güncellemesi gerekiyor: Eski: ${} -> Yeni: ${}",
                            listing.itemName, listing.listedPrice, listing.recommendedNewPrice);
                }
            }
        }

        return decisions;
    }

    public static int parseMyListingsScreenSlot(String[] slotItemNames, double[] slotPrices, String targetItem, double targetPrice) {
        if (slotItemNames == null || slotPrices == null) return -1;

        for (int i = 0; i < Math.min(slotItemNames.length, slotPrices.length); i++) {
            String name = slotItemNames[i];
            if (name != null && name.equalsIgnoreCase(targetItem)) {
                if (Math.abs(slotPrices[i] - targetPrice) < 1.0) {
                    return i;
                }
            }
        }
        return -1;
    }

    public double getMinPriceFloor() { return minPriceFloor; }
    public void setUndercut(double amount, double percent) { this.undercutAmount = amount; this.undercutPercent = percent; }
    public void setMinPriceFloor(double minPriceFloor) { this.minPriceFloor = minPriceFloor; }
}