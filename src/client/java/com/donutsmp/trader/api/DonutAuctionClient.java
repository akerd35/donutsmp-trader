package com.donutsmp.trader.api;

import com.donutsmp.trader.api.model.ItemPrice;
import com.donutsmp.trader.api.model.TickerItem;
import com.donutsmp.trader.market.Undercut;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DonutAuctionClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("DonutTrader-API");
    private static final String BASE_URL = "https://api.donut.auction/v2";
    private static final Gson GSON = new Gson();

    private final HttpClient httpClient;
    private final Map<String, TickerItem> tickerCache = new ConcurrentHashMap<>();
    private final Map<String, ItemPrice> priceCache = new ConcurrentHashMap<>();
    private long lastTickerFetch = 0;
    private static final long TICKER_CACHE_TTL_MS = 4000; // 4 saniye cache

    public DonutAuctionClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    public static String normalizeItemName(String itemName) {
        if (itemName == null) return "";
        return itemName.toLowerCase().replace("minecraft:", "").trim();
    }

    public synchronized List<TickerItem> fetchTickers() {
        long now = System.currentTimeMillis();
        if (now - lastTickerFetch < TICKER_CACHE_TTL_MS && !tickerCache.isEmpty()) {
            return new ArrayList<>(tickerCache.values());
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/tickers/"))
                    .header("User-Agent", "Mozilla/5.0 DonutTrader-Fabric/1.0")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Type listType = new TypeToken<List<TickerItem>>() {}.getType();
                List<TickerItem> items = GSON.fromJson(response.body(), listType);
                if (items != null) {
                    tickerCache.clear();
                    for (TickerItem item : items) {
                        String cleanName = normalizeItemName(item.getItemName());
                        if (!cleanName.isEmpty()) {
                            tickerCache.put(cleanName, item);
                        }
                    }
                    lastTickerFetch = now;
                    return items;
                }
            } else {
                LOGGER.warn("Tickers fetch failed with status code: {}", response.statusCode());
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching tickers from donut.auction: {}", e.getMessage());
        }

        return new ArrayList<>(tickerCache.values());
    }

    public Map<String, ItemPrice> fetchPrices(List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            String idsParam = String.join(",", itemIds);
            String url = BASE_URL + "/items/prices?itemIds=" + idsParam;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 DonutTrader-Fabric/1.0")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Type listType = new TypeToken<List<ItemPrice>>() {}.getType();
                List<ItemPrice> prices = GSON.fromJson(response.body(), listType);
                Map<String, ItemPrice> result = new HashMap<>();
                if (prices != null) {
                    for (ItemPrice p : prices) {
                        if (!p.getItemId().isEmpty()) {
                            priceCache.put(p.getItemId(), p);
                            result.put(p.getItemId(), p);
                        }
                    }
                }
                return result;
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching prices from donut.auction: {}", e.getMessage());
        }

        return Collections.emptyMap();
    }

    public TickerItem getCheapestListing(String itemName) {
        String clean = normalizeItemName(itemName);
        if (clean.isEmpty()) return null;
        fetchTickers();
        return tickerCache.get(clean);
    }

    public double calculateOptimalSellPrice(String itemName, int lotSize, double defaultFallbackPrice,
                                            double minimumPriceFloor, double undercutAmount, double undercutPercent) {
        TickerItem ticker = getCheapestListing(itemName);
        if (ticker == null || ticker.getListingPrice() <= 0) {
            return Math.max(defaultFallbackPrice, minimumPriceFloor);
        }

        double competitorPrice;
        if (lotSize == 1) {
            competitorPrice = ticker.getUnitPrice() > 0 ? ticker.getUnitPrice() : ticker.getListingPrice();
        } else {
            competitorPrice = ticker.getListingPrice();
        }

        return Undercut.target(competitorPrice, undercutAmount, undercutPercent, minimumPriceFloor);
    }
}