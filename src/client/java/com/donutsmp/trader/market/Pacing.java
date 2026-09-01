package com.donutsmp.trader.market;

/**
 * Modun ne zaman çalışıp ne zaman durduğu.
 *
 * İki sebeple duruyor: aralıksız çalışmak sunucuya sürekli yük bindiriyor ve
 * oyuncuya oyunu geri vermiyor; bir de üst üste başarısız olan bir işlemi aynı
 * hızda tekrar denemek hem boşuna hem de sorunu büyütüyor.
 */
public final class Pacing {

    private Pacing() {}

    /**
     * @param cycleStart çalışma döngüsünün başladığı an
     * @param workMs     bu kadar çalış; 0 ise dinlenme yok
     * @param restMs     sonra bu kadar dur
     */
    public static boolean resting(long now, long cycleStart, long workMs, long restMs) {
        if (workMs <= 0 || restMs <= 0) return false;

        long elapsed = now - cycleStart;
        if (elapsed < 0) return false;

        return elapsed % (workMs + restMs) >= workMs;
    }

    /** Dinlenmenin bitmesine kalan süre; çalışma zamanıysa 0. */
    public static long restRemainingMs(long now, long cycleStart, long workMs, long restMs) {
        if (!resting(now, cycleStart, workMs, restMs)) return 0;

        long phase = (now - cycleStart) % (workMs + restMs);
        return workMs + restMs - phase;
    }

    /** Üst üste hata: her seferinde iki katı bekle, tavanı aşma. */
    public static long backoffMs(int consecutiveFailures, long baseMs, long maxMs) {
        if (consecutiveFailures <= 0) return 0;

        long delay = baseMs;
        for (int i = 1; i < consecutiveFailures && delay < maxMs; i++) {
            delay *= 2;
        }
        return Math.min(delay, maxMs);
    }
}
