package com.donutsmp.trader.market;

/**
 * Rakibin altına ne kadar inileceği.
 *
 * Sabit -1$ pahalı eşyada anlamsız kalıyordu: 1.000.000'luk bir totemde 999.999
 * ile rekabet edilmez. Fark yüzdesel alınır, ama en az 1$ olur ki ucuz eşyada
 * da rakibin gerçekten altına inilsin.
 */
public final class Undercut {

    private Undercut() {}

    public static double target(double competitorPrice, double percent, double floor) {
        if (competitorPrice <= floor) return floor;

        double cut = Math.max(1.0, competitorPrice * percent / 100.0);
        double target = Math.floor(competitorPrice - cut);
        return Math.max(target, floor);
    }
}
