package com.donutsmp.trader.market;

/**
 * Fiyatı ne zaman değiştirmeli?
 *
 * Her taramada körlemesine "en ucuzun bir altı" demek iki şeyi kaybettiriyor:
 * rakip yokken fiyatı boşuna aşağı çeker, bir kuruşluk farklar için de ilanı
 * durmadan yeniler. Karar burada toplandı ki ne zaman kırdığı denetlenebilsin.
 */
public final class PricePolicy {

    private PricePolicy() {}

    public enum Action {
        KEEP,
        UNDERCUT,
        RAISE
    }

    public record Decision(double price, Action action, String reason) {
        public boolean changed() {
            return action != Action.KEEP;
        }
    }

    /**
     * @param current            şu anki hedef fiyatımız
     * @param cheapestCompetitor bizim olmayan en ucuz ilan; yoksa 0 ya da eksi
     * @param minStep            bu kadarlık farka değmez, fiyat sabit kalır
     */
    public static Decision decide(double current, double cheapestCompetitor, double floor,
                                  double undercutAmount, double undercutPercent, double minStep) {
        if (cheapestCompetitor <= 0) {
            return new Decision(current, Action.KEEP, "rakip yok, fiyat korunuyor");
        }

        if (cheapestCompetitor <= floor) {
            double atFloor = Math.max(floor, 0);
            if (Math.abs(atFloor - current) < 0.5) {
                return new Decision(current, Action.KEEP, "rakip taban fiyatın altında, tabandayız");
            }
            return new Decision(atFloor, Action.UNDERCUT, "rakip taban fiyatın altında, tabana iniliyor");
        }

        double candidate = Undercut.target(cheapestCompetitor, undercutAmount, undercutPercent, floor);

        if (current <= 0) {
            return new Decision(candidate, Action.UNDERCUT, "ilk fiyat");
        }

        double delta = candidate - current;
        if (Math.abs(delta) < Math.max(1.0, minStep)) {
            return new Decision(current, Action.KEEP, "fark önemsiz");
        }

        if (delta > 0) {
            return new Decision(candidate, Action.RAISE, "piyasa yükseldi, fiyat artırılıyor");
        }

        return new Decision(candidate, Action.UNDERCUT, "rakip altımıza girdi");
    }
}
