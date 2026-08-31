package com.donutsmp.trader.market;

import com.donutsmp.trader.api.AhPriceParser;

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

    /**
     * @return rakibin fiyatı, ilan bizimse ya da fiyat okunamıyorsa -1
     */
    public static double competitorPrice(List<String> loreLines, String selfName, Set<Long> ownPrices) {
        if (loreLines == null || loreLines.isEmpty()) return -1;

        String self = selfName == null ? "" : selfName.trim().toLowerCase();
        double cheapest = -1;

        for (String line : loreLines) {
            if (line == null) continue;
            if (!self.isEmpty() && line.toLowerCase().contains(self)) return -1;

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
