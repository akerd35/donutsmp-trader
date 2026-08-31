package com.donutsmp.trader.market;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ucuz ilanları toplayıp daha pahalıya asma kararı.
 *
 * Karar burada, tıklama dışarıda: hangi slota tıklanacağını seçen mantık
 * gerçek para harcatıyor, bu yüzden oyun sınıflarından bağımsız ve test
 * edilebilir olmak zorunda.
 */
public final class FlipPlanner {

    private FlipPlanner() {}

    public record Offer(int slot, double price) {}

    public record Buy(int slot, double price, double resellAt) {}

    /**
     * @param buyBelow  bu fiyata kadar olan ilanlar alınır
     * @param sellAt    alınanların asılacağı fiyat
     * @param budget    bu turda harcanabilecek toplam
     * @param minMargin alış ile satış arasında aranan en az fark
     */
    public static List<Buy> plan(List<Offer> offers, double buyBelow, double sellAt, double budget, double minMargin) {
        List<Buy> picks = new ArrayList<>();
        if (offers == null || offers.isEmpty()) return picks;
        if (buyBelow <= 0 || sellAt <= 0 || budget <= 0) return picks;

        List<Offer> sorted = new ArrayList<>(offers);
        sorted.sort(Comparator.comparingDouble(Offer::price));

        double spent = 0;
        for (Offer offer : sorted) {
            if (offer.price() <= 0 || offer.price() > buyBelow) continue;
            if (sellAt - offer.price() < minMargin) continue;
            if (spent + offer.price() > budget) continue;

            spent += offer.price();
            picks.add(new Buy(offer.slot(), offer.price(), sellAt));
        }
        return picks;
    }

    /** Alınacak bir şey varken listeleme beklemeli: ucuzu kapmak önceliklidir. */
    public static boolean shouldBuyFirst(List<Buy> picks) {
        return picks != null && !picks.isEmpty();
    }

    public static double totalCost(List<Buy> picks) {
        double total = 0;
        for (Buy buy : picks) total += buy.price();
        return total;
    }

    public static double expectedProfit(List<Buy> picks) {
        double profit = 0;
        for (Buy buy : picks) profit += buy.resellAt() - buy.price();
        return profit;
    }
}
