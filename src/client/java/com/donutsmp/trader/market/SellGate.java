package com.donutsmp.trader.market;

/**
 * Şu an satabilir miyiz, satamıyorsak neden?
 *
 * Bu kararlar tek bir 140 satırlık tick metodunun içinde iç içe if'lerdi:
 * test edilemiyordu ve "mod neden satmıyor" sorusunun cevabı yalnızca kodu
 * okuyarak bulunabiliyordu. Buradaki hiçbir şey Minecraft bilmiyor, dolayısıyla
 * hem tick hem de {@code /trader why} aynı cevabı veriyor.
 *
 * Sıra davranışın kendisidir: savaş bekleme süresinden önce gelir, çünkü
 * savaştayken beklemenin bitmesi bir şeyi değiştirmez.
 */
public final class SellGate {

    private SellGate() {}

    public enum Reason {
        GO("satışa hazır"),
        DISABLED("mod pasif"),
        RESTING("mola"),
        BACKOFF("üst üste başarısız listeleme sonrası bekleme"),
        COMBAT("savaş modu"),
        VERIFYING("önceki ilan doğrulanıyor"),
        WAITING_MARKET("piyasa cevabı bekleniyor"),
        COOLDOWN("komut arası bekleme"),
        SLOTS_FULL("sunucu ilan sınırı dolu dedi"),
        IN_AIR("havadasınız"),
        BAD_ITEM("hedef eşya geçersiz"),
        NO_HOTBAR("hotbar'da boş slot yok"),
        NO_ITEMS("envanterde satacak eşya kalmadı"),
        NO_PRICE("fiyat hesaplanamadı");

        private final String text;

        Reason(String text) { this.text = text; }

        public String text() { return text; }
    }

    public enum Action {
        /** /ah sell gönder. */
        LIST,
        /** Önce yığından lot ayır. */
        SPLIT,
        /** Bir şey yapma; sebep Verdict.reason(). */
        WAIT
    }

    public record Verdict(Action action, Reason reason) {
        public boolean acting() { return action != Action.WAIT; }
    }

    /**
     * Piyasa sorgusu dahil her şeyi durduran sebepler.
     *
     * Ayrı duruyorlar çünkü mola ya da savaş sırasında sunucuya arama komutu da
     * gitmemeli; bunlar sorgudan ÖNCE bakılır.
     */
    public static Reason blocking(boolean enabled, boolean resting, boolean backingOff, boolean inCombat) {
        if (!enabled) return Reason.DISABLED;
        if (resting) return Reason.RESTING;
        if (backingOff) return Reason.BACKOFF;
        if (inCombat) return Reason.COMBAT;
        return null;
    }

    /**
     * Satışı durduran ama piyasa sorgusunu durdurmayan sebepler.
     *
     * @param handReady elimizde tam olarak bir lot var mı
     */
    public record Facts(
            boolean waitingForVerify,
            boolean waitingForMarket,
            boolean coolingDown,
            boolean slotsFree,
            boolean onGround,
            boolean itemExists,
            boolean handReady,
            boolean hotbarFree,
            boolean haveItems,
            long price
    ) {}

    public static Verdict next(Facts f) {
        if (f.waitingForVerify()) return wait(Reason.VERIFYING);
        if (f.waitingForMarket()) return wait(Reason.WAITING_MARKET);
        if (f.coolingDown()) return wait(Reason.COOLDOWN);
        if (!f.slotsFree()) return wait(Reason.SLOTS_FULL);
        if (!f.onGround()) return wait(Reason.IN_AIR);
        if (!f.itemExists()) return wait(Reason.BAD_ITEM);

        if (!f.handReady()) {
            // Lot kurmak icin once bos bir hotbar slotu, sonra yigin gerek.
            if (!f.hotbarFree()) return wait(Reason.NO_HOTBAR);
            if (!f.haveItems()) return wait(Reason.NO_ITEMS);
            return new Verdict(Action.SPLIT, Reason.GO);
        }

        if (f.price() <= 0) return wait(Reason.NO_PRICE);
        return new Verdict(Action.LIST, Reason.GO);
    }

    private static Verdict wait(Reason reason) {
        return new Verdict(Action.WAIT, reason);
    }
}
