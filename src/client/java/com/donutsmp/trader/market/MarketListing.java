package com.donutsmp.trader.market;

import com.donutsmp.trader.api.AhPriceParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * /ah listesindeki bir ilan bizim mi, rakibin mi?
 *
 * Kendi ilanımız da listede görünür. Onu rakip sayarsak her taramada kendi
 * fiyatımızın bir altına ineriz; altı saniyede bir tarayan bir mod bunu dakikalar
 * içinde tabana kadar götürür.
 */
public final class MarketListing {

    private MarketListing() {}

    /** Menüdeki bir slot: eşya, kaç tane, ve lore satırları. */
    public record Entry(String itemId, int count, List<String> lore) {}

    /**
     * Taramadan çıkan rakip fiyatları ve neyin neden atlandığı.
     *
     * @param skippedOwn  bizim ya da takımın ilanı
     * @param skippedSize aynı eşya ama farklı yığın boyutu
     */
    public record Board(List<Double> prices, int skippedOwn, int skippedSize) {

        public boolean empty() { return prices.isEmpty(); }

        /** En ucuz rakip; hiç yoksa -1. */
        public double cheapest() {
            double best = -1;
            for (double price : prices) {
                if (best < 0 || price < best) best = price;
            }
            return best;
        }

        /** Verilen fiyattan ucuz kaç ilan var. */
        public int below(double price) {
            if (price <= 0) return 0;
            int count = 0;
            for (double other : prices) {
                if (other < price) count++;
            }
            return count;
        }
    }

    /**
     * Menüdeki ilanlardan kıyaslanabilir olanları süz.
     *
     * Yığın boyutu eşleşmesi şart. 64'lük bir yığının toplam fiyatını tek bir
     * eşyanın fiyatı sanmak, fiyatı 64 kat yanlış okumaktır: 64 merdiveni
     * 5.000'e basan birinin altına inip TEK merdivenimizi 4.999'a asarsak
     * 10.000'lik eşyayı yarı fiyatına vermiş oluruz. Bunun tersi de olur ve
     * daha pahalıya patlar: elimizdeki 16'lık lotu, tek eşyalık bir ilanın
     * altına asmak on altı eşyayı bir tanenin parasına satmaktır.
     *
     * Aynı boyutta ilan yoksa kıyas edilecek rakip yok demektir; fiyat
     * hareket etmez.
     */
    public static Board scan(List<Entry> entries, String targetItem, int lotSize,
                             Collection<String> ourNames, Set<Long> ownPrices) {
        List<Double> prices = new ArrayList<>();
        int skippedOwn = 0;
        int skippedSize = 0;

        if (entries == null || targetItem == null) return new Board(prices, 0, 0);

        for (Entry entry : entries) {
            if (entry == null || !targetItem.equals(entry.itemId())) continue;
            if (entry.count() != lotSize) {
                skippedSize++;
                continue;
            }
            double price = competitorPrice(entry.lore(), ourNames, ownPrices);
            if (price < 0) {
                skippedOwn++;
                continue;
            }
            prices.add(price);
        }
        return new Board(prices, skippedOwn, skippedSize);
    }

    /**
     * @return rakibin fiyatı, ilan bizimse ya da fiyat okunamıyorsa -1
     */
    public static double competitorPrice(List<String> loreLines, String selfName, Set<Long> ownPrices) {
        return competitorPrice(loreLines, selfName == null ? List.of() : List.of(selfName), ownPrices);
    }

    /**
     * @param ourNames kendi adımız ve fiyat kırmayacağımız takım arkadaşları
     * @return rakibin fiyatı, ilan bizden birine aitse ya da fiyat okunamıyorsa -1
     */
    public static double competitorPrice(List<String> loreLines, Collection<String> ourNames, Set<Long> ownPrices) {
        if (loreLines == null || loreLines.isEmpty()) return -1;

        double cheapest = -1;

        for (String line : loreLines) {
            if (line == null) continue;
            if (isOurs(line, ourNames)) return -1;

            double parsed = AhPriceParser.parsePrice(line);
            if (parsed > 0 && (cheapest < 0 || parsed < cheapest)) {
                cheapest = parsed;
            }
        }

        if (cheapest < 0) return -1;

        // İsim lore'da yazmıyorsa son çare: bu fiyatı biz astıysak bizimdir.
        if (isOurPrice(cheapest, ownPrices)) return -1;

        return cheapest;
    }

    /**
     * Satırda bizden birinin adı geçiyor mu?
     *
     * Kısa adlar aranmaz: iki harflik bir ad piyasadaki her ilanda geçer ve
     * mod hiç rakip göremez hâle gelir.
     */
    static boolean isOurs(String line, Collection<String> ourNames) {
        if (ourNames == null || ourNames.isEmpty()) return false;
        String lower = line.toLowerCase();
        for (String name : ourNames) {
            if (name == null) continue;
            String trimmed = name.trim().toLowerCase();
            if (trimmed.length() >= 3 && lower.contains(trimmed)) return true;
        }
        return false;
    }

    /**
     * Menü 11.999'u "11k" diye gösteriyor; tam eşleşme aramak kendi ilanımızı
     * rakip sanmamıza yeter. Yuvarlanmış bir değer, o binliğe düşen kendi
     * fiyatlarımızdan birini gizliyor olabilir.
     */
    static boolean isOurPrice(double price, Set<Long> ownPrices) {
        if (ownPrices == null || ownPrices.isEmpty()) return false;

        long exact = (long) price;
        if (ownPrices.contains(exact)) return true;

        for (long step : new long[] {1_000L, 1_000_000L}) {
            if (exact % step != 0) continue;
            for (long own : ownPrices) {
                if (own >= exact && own < exact + step) return true;
            }
        }
        return false;
    }
}
