package com.donutsmp.trader.market;

import com.donutsmp.trader.api.AhPriceParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

public class AhListingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("DonutTrader-ListingManager");

    /**
     * Sohbet bildirimleri yalnızca BİZİM ilanımızı ilgilendiriyorsa sayaca işler.
     * DonutSMP satışları herkese duyurur; "sold" geçen her satırı saymak
     * aktif slot sayacını başkalarının işlemleriyle kaydırır.
     */
    private static final Pattern MINE_PATTERN = Pattern.compile(
            "(?i)\\b(your|you)\\b|senin|sizin|ilanınız|eşyanız"
    );

    private static final Pattern SALE_KEYWORDS = Pattern.compile(
            "(?i)\\b(?:bought|sold|purchased)\\b|satıldı"
    );

    private static final Pattern CANCEL_PATTERN = Pattern.compile(
            "(?i)\\b(?:cancelled|canceled|removed|collected|reclaimed|expired)\\b|iptal|geri çekildi"
    );

    /**
     * /ah sell reddedildiğinde slot harcanmamıştır. Gönderimden hemen sonraki
     * kısa pencerede geniş eşleşmek güvenlidir: yanlış pozitifin bedeli bir
     * fazla deneme, sunucu da onu "too many" ile geri çevirir.
     */
    private static final Pattern FAILURE_PATTERN = Pattern.compile(
            "(?i)cannot|can't|can not|unable|must be|not allowed|invalid|failed|in the air|havada|yapamazsın|geçersiz"
    );

    private static final Pattern COMBAT_PATTERN = Pattern.compile(
            "(?i)(?:cannot|can't|can not|unable to).{0,40}combat|(?:while|are) in combat|savaşta"
    );

    private static final Pattern LIMIT_PATTERN = Pattern.compile(
            "(?i)too many (?:listed |active )?items|listing limit|reached .{0,20}limit|maximum .{0,20}listings"
    );

    private static final long PENDING_WINDOW_MS = 3000;

    /**
     * Sayaç dolu derken ne kadar sonra yine denenir.
     *
     * Sayaç sohbet bildirimlerinden yürüyor ve sunucu her satışı bildirmiyor;
     * kaçan bildirim sayacı gerçeğin üstünde bırakıyor ve mod 18/18 sanıp
     * duruyordu — ölçüldü, bir kere 5,5 dakika. Karar sayacın değil sunucunun:
     * dolu görünsek bile arada bir denenir, sunucu reddederse süre yeniden
     * başlar. Bedeli on beş saniyede bir reddedilen komut.
     */
    private static final long RETRY_WHEN_FULL_MS = 15_000;

    private int maxSlots = 18;
    private int activeListings = 0;
    private long pendingSince = 0;
    private boolean isLimitReached = false;
    /** Sunucu "ilan sınırın doldu" dedi; bu ana kadar tekrar denenmez. */
    private long limitUntil = 0;
    private long lastAttemptAt = 0;
    /** Bu deneme "sayaç dolu diyor ama yine de dene" denemesi miydi? */
    private boolean probing = false;
    private long combatUntil = 0; // Savaş süresi koruması
    private long totalEarned = 0;
    private int itemsSold = 0;

    public AhListingManager() {
        this(18);
    }

    public AhListingManager(int maxSlots) {
        this.maxSlots = Math.max(1, maxSlots);
    }

    public static String stripColorCodes(String input) {
        if (input == null) return "";
        return input.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
    }

    public synchronized boolean canListMore() {
        return canListMore(System.currentTimeMillis());
    }

    /** Saat disaridan verilebilsin ki on bes saniyelik pencere test edilebilsin. */
    synchronized boolean canListMore(long now) {
        if (now < combatUntil) return false;      // Savaşta iken komut gönderilemez
        if (now < limitUntil) return false;       // Sunucu az önce reddetti
        if (activeListings < maxSlots) return true;
        return now - lastAttemptAt >= RETRY_WHEN_FULL_MS;
    }

    static long retryWhenFullMs() { return RETRY_WHEN_FULL_MS; }

    public synchronized void onListingAttempt() {
        this.activeListings = Math.min(maxSlots, this.activeListings + 1);
        if (this.activeListings >= maxSlots) {
            this.isLimitReached = true;
        }
    }

    /** Slotu şimdilik ayırır; sunucu reddederse geri alınır. */
    public synchronized void onListingSent() {
        this.probing = activeListings >= maxSlots;
        onListingAttempt();
        this.lastAttemptAt = System.currentTimeMillis();
        this.pendingSince = System.currentTimeMillis();
    }

    private boolean pending() {
        return pendingSince > 0 && System.currentTimeMillis() - pendingSince < PENDING_WINDOW_MS;
    }

    /** Doğrulama başarısız oldu: ilan hiç girmemiş, slotu geri ver. */
    public synchronized void onListingRejected() {
        pendingSince = 0;
        if (activeListings > 0) activeListings--;
        this.isLimitReached = false;
        this.limitUntil = 0;
    }

    /**
     * İlan gerçekten girdi.
     *
     * Sayaç dolu derken girdiyse sayaç yanılmış, yer varmış: bir eksiğe çekilir
     * ki bir sonraki deneme on beş saniye beklemesin. Böylece gerçek sınıra
     * kadar hızla dolar, orada sunucu "too many" der ve sayaç yerine oturur.
     */
    public synchronized void onListingVerified() {
        pendingSince = 0;
        limitUntil = 0;
        // Yalnızca DENEME tuttuysa düzelt. Koşulsuz azaltmak sayacın dolu
        // kalmasını imkânsız kılar: normal listeleme de her seferinde bir
        // eksiltir ve sayaç sınıra hiç oturmaz.
        if (probing && activeListings >= maxSlots) {
            activeListings = Math.max(0, maxSlots - 1);
            isLimitReached = false;
        }
        probing = false;
    }

    /** Reddedilen ilanı sayaçtan düşer. Pencere geçtiyse ilan gerçekten girmiştir. */
    private boolean releasePending() {
        if (!pending()) return false;
        pendingSince = 0;
        if (activeListings > 0) activeListings--;
        this.isLimitReached = false;
        return true;
    }

    /**
     * /ah listings ekranından okunan gerçek ilan sayısı.
     *
     * @return sayacın önceki değeri; farklıysa sayaç kaymış demektir
     */
    public synchronized int syncActiveListings(int actual) {
        int before = this.activeListings;
        this.pendingSince = 0;
        this.limitUntil = 0;
        setActiveListings(actual);
        return before;
    }

    public synchronized boolean isInCombat() {
        return System.currentTimeMillis() < combatUntil;
    }

    public synchronized boolean onChatMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) return false;

        String cleanMessage = stripColorCodes(rawMessage);

        if (COMBAT_PATTERN.matcher(cleanMessage).find()) {
            this.combatUntil = System.currentTimeMillis() + 20000;
            releasePending();
            LOGGER.warn("[SAVAS KORUMASI] Savaşta olunduğu tespit edildi. 20 saniye işlem durduruldu.");
            return true;
        }

        if (LIMIT_PATTERN.matcher(cleanMessage).find()) {
            this.activeListings = this.maxSlots;
            this.isLimitReached = true;
            this.limitUntil = System.currentTimeMillis() + RETRY_WHEN_FULL_MS;
            this.pendingSince = 0;
            LOGGER.warn("[LIMIT DOLU] Sunucu ilan sınırına ulaşıldı ({}/{}). Yeni satış bekleniyor.", activeListings, maxSlots);
            return true;
        }

        if (FAILURE_PATTERN.matcher(cleanMessage).find() && releasePending()) {
            LOGGER.info("[ILAN REDDEDILDI] Sunucu satisi kabul etmedi, slot geri alindi: {}/{}", activeListings, maxSlots);
            return true;
        }

        boolean mine = MINE_PATTERN.matcher(cleanMessage).find();
        if (!mine) return false;

        if (SALE_KEYWORDS.matcher(cleanMessage).find()) {
            double price = AhPriceParser.parsePrice(cleanMessage);
            if (price > 0) {
                this.totalEarned += (long) price;
            }

            this.itemsSold++;
            releaseSlot();
            LOGGER.info("[SATIS BILDIRIMI] Eşya satıldı! Satılan: {}x, Kasa: +${}, Aktif Slot: {}/{}",
                    itemsSold, totalEarned, activeListings, maxSlots);
            return true;
        }

        if (CANCEL_PATTERN.matcher(cleanMessage).find()) {
            releaseSlot();
            LOGGER.info("[ILAN IPTAL] İlan geri çekildi! Aktif Slot: {}/{}", activeListings, maxSlots);
            return true;
        }

        return false;
    }

    /** Bir ilan satıldı ya da geri çekildi: yer açıldı, beklemeye gerek yok. */
    private void releaseSlot() {
        if (this.activeListings > 0) {
            this.activeListings--;
        }
        this.isLimitReached = false;
        this.limitUntil = 0;
    }

    // Getters & Setters
    public int getMaxSlots() { return maxSlots; }
    public void setMaxSlots(int maxSlots) { this.maxSlots = Math.max(1, maxSlots); }
    public int getActiveListings() { return activeListings; }
    public void setActiveListings(int activeListings) {
        this.activeListings = Math.min(maxSlots, Math.max(0, activeListings));
        this.isLimitReached = (this.activeListings >= this.maxSlots);
    }
    public boolean isLimitReached() { return isLimitReached; }
    public void setLimitReached(boolean limitReached) { this.isLimitReached = limitReached; }
    public long getTotalEarned() { return totalEarned; }
    public int getItemsSold() { return itemsSold; }
    public void resetAll() {
        this.activeListings = 0;
        this.pendingSince = 0;
        this.isLimitReached = false;
        this.limitUntil = 0;
        this.lastAttemptAt = 0;
        this.probing = false;
        this.combatUntil = 0;
    }
}