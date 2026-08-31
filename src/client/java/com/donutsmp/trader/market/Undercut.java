package com.donutsmp.trader.market;

/**
 * Rakibin altına ne kadar inileceği.
 *
 * Varsayılan sabit 1$: rakip 10.000'e satıyorsa biz 9.999'a. Yüzde isteyen
 * ayrıca verebilir; ikisinden büyük olan uygulanır. Kesme her hâlükârda en az
 * 1$'dır, yoksa ucuz eşyada rakiple aynı fiyata düşülür.
 */
public final class Undercut {

    private Undercut() {}

    public static double target(double competitorPrice, double amount, double percent, double floor) {
        if (competitorPrice <= floor) return floor;

        double cut = Math.max(1.0, Math.max(amount, competitorPrice * percent / 100.0));
        double target = Math.floor(competitorPrice - cut);
        return Math.max(target, floor);
    }
}
