package com.donutsmp.trader.api.model;

import com.google.gson.annotations.SerializedName;

public class ItemPrice {

    public static class ItemInfo {
        @SerializedName("id")
        public String id;

        @SerializedName("itemName")
        public String itemName;
    }

    public static class PriceDetails {
        @SerializedName("value")
        public double value;

        @SerializedName("volume24Hours")
        public double volume24Hours;

        @SerializedName("saleCount24Hours")
        public long saleCount24Hours;
    }

    @SerializedName("item")
    private ItemInfo item;

    @SerializedName("price")
    private PriceDetails price;

    public ItemPrice() {
    }

    public String getItemId() {
        return (item != null && item.id != null) ? item.id : "";
    }

    public String getItemName() {
        return (item != null && item.itemName != null) ? item.itemName : "";
    }

    public double getMarketValue() {
        return price != null ? price.value : 0.0;
    }

    public double getVolume24Hours() {
        return price != null ? price.volume24Hours : 0.0;
    }

    public long getSaleCount24Hours() {
        return price != null ? price.saleCount24Hours : 0;
    }

    @Override
    public String toString() {
        return String.format("ItemPrice{name='%s', val=%.0f, 24hSales=%d}",
                getItemName(), getMarketValue(), getSaleCount24Hours());
    }
}