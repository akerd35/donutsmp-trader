package com.donutsmp.trader.api.model;

import com.google.gson.annotations.SerializedName;

public class TickerItem {
    @SerializedName("itemId")
    private String itemId;

    @SerializedName("itemName")
    private String itemName;

    @SerializedName("displayName")
    private String displayName;

    @SerializedName("quantity")
    private int quantity;

    @SerializedName("listingPrice")
    private double listingPrice;

    @SerializedName("unitPrice")
    private double unitPrice;

    @SerializedName("observedAt")
    private String observedAt;

    @SerializedName("isStale")
    private boolean isStale;

    public TickerItem() {
    }

    public TickerItem(String itemId, String itemName, int quantity, double listingPrice, double unitPrice) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.listingPrice = listingPrice;
        this.unitPrice = unitPrice;
    }

    public String getItemId() {
        return itemId != null ? itemId : "";
    }

    public String getItemName() {
        return itemName != null ? itemName : "";
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getListingPrice() {
        return listingPrice;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public String getObservedAt() {
        return observedAt != null ? observedAt : "";
    }

    public boolean isStale() {
        return isStale;
    }

    @Override
    public String toString() {
        return String.format("TickerItem{name='%s', qty=%d, price=%.0f, unitPrice=%.1f}",
                itemName, quantity, listingPrice, unitPrice);
    }
}