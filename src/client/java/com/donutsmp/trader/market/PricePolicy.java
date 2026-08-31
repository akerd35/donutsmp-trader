package com.donutsmp.trader.market;

/**
 * Fiyatı ne zaman değiştirmeli?
 *
 * Menü fiyatları kısaltarak gösteriyor: 11.999 ekranda "11k" yazıyor. Okuduğumuz
 * değer gerçeğinden 999'a kadar düşük olabilir, dolayısıyla küçük farklara
 * tepki vermek fiyatı kendi kendine aşağı çeker — 11.999 gören mod 10.999'a,
 * onu "10k" gören mod 9.999'a iner. Bu yüzden iki fren var: rakip bizden
 * belirgin biçimde ucuz olmalı, ve tek tük ucuz ilan hareket ettirmemeli.
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
     * @param competitorsBelow   bizden ucuz olan ilan sayısı
     * @param minGap             rakip bizden en az bu kadar ucuz olmadan inilmez
     * @param minBelow           bu sayıdan az ucuz ilan varsa beklenir
     */
    public static Decision decide(double current, double cheapestCompetitor, int competitorsBelow,
                                  double floor, double undercutAmount, double undercutPercent,
                                  double minStep, double minGap, int minBelow) {
        if (cheapestCompetitor <= 0) {
            return new Decision(current, Action.KEEP, "rakip yok, fiyat korunuyor");
        }

        double candidate = Undercut.target(cheapestCompetitor, undercutAmount, undercutPercent, floor);

        if (current <= 0) {
            return new Decision(candidate, Action.UNDERCUT, "ilk fiyat");
        }

        // Rakip bizden pahalı: fiyatı yükseltmek serbest, buradaki gürültü
        // riski yok — yanlış okuma bizi zarara değil, satılmamaya götürür.
        if (cheapestCompetitor >= current) {
            if (candidate - current >= Math.max(1.0, minStep)) {
                return new Decision(candidate, Action.RAISE, "piyasa yükseldi");
            }
            return new Decision(current, Action.KEEP, "fark önemsiz");
        }

        if (competitorsBelow < Math.max(1, minBelow)) {
            return new Decision(current, Action.KEEP,
                    "sadece " + competitorsBelow + " ucuz ilan var, satılmaları bekleniyor");
        }

        if (current - cheapestCompetitor < Math.max(1.0, minGap)) {
            return new Decision(current, Action.KEEP,
                    "rakip yeterince ucuz değil (fark " + Math.round(current - cheapestCompetitor) + ")");
        }

        if (candidate <= floor && current <= floor) {
            return new Decision(current, Action.KEEP, "taban fiyattayız");
        }

        return new Decision(candidate, Action.UNDERCUT, "rakip belirgin şekilde altımızda");
    }
}
