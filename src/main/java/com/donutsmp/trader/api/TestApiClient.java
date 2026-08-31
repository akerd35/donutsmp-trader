package com.donutsmp.trader.api;

import com.donutsmp.trader.api.model.ItemPrice;
import com.donutsmp.trader.api.model.TickerItem;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TestApiClient {
    public static void main(String[] args) {
        System.out.println("=== DonutAuctionClient Testi Baslatiliyor ===");
        DonutAuctionClient client = new DonutAuctionClient();

        System.out.println("[1/3] Canli Ticker verileri cekiliyor (/v2/tickers/)...");
        List<TickerItem> tickers = client.fetchTickers();
        System.out.println("Toplam cekilen ticker adedi: " + tickers.size());
        
        for (int i = 0; i < Math.min(5, tickers.size()); i++) {
            TickerItem item = tickers.get(i);
            System.out.println("  -> " + item);
        }

        System.out.println("\n[2/3] Fiyat ve Likidite sorgulanıyor (/v2/items/prices)...");
        if (!tickers.isEmpty()) {
            String firstId = tickers.get(0).getItemId();
            Map<String, ItemPrice> prices = client.fetchPrices(Arrays.asList(firstId));
            for (Map.Entry<String, ItemPrice> entry : prices.entrySet()) {
                System.out.println("  -> ID: " + entry.getKey() + " => " + entry.getValue());
            }
        }

        System.out.println("\n[3/3] Akilli Fiyatlandirma (Auto-Undercut) Testi...");
        double sellPriceLadder = client.calculateOptimalSellPrice("ladder", 1, 35000.0, 10000.0);
        System.out.println("  -> Merdiven (Ladder 1x) Onerilen Satis Fiyati: $" + String.format("%.0f", sellPriceLadder));

        double sellPriceTotem = client.calculateOptimalSellPrice("totem_of_undying", 1, 95000.0, 50000.0);
        System.out.println("  -> Totem (Totem 1x) Onerilen Satis Fiyati: $" + String.format("%.0f", sellPriceTotem));

        System.out.println("\n=== Test Basariyla Tamamlandi! ===");
    }
}