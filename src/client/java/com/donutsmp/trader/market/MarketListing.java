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
        if (ownPrices != null && ownPrices.contains((long) cheapest)) return -1;

        return cheapest;
    }
}
