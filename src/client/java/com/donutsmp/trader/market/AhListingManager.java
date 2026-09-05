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

    private int maxSlots = 18;
    private int activeListings = 0;
    private long pendingSince = 0;
    private boolean isLimitReached = false;
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
        if (System.currentTimeMillis() < combatUntil) {
            return false; // Savaşta iken komut gönderilemez
        }
        return !isLimitReached && activeListings < maxSlots;
    }

    public synchronized void onListingAttempt() {
        this.activeListings = Math.min(maxSlots, this.activeListings + 1);
        if (this.activeListings >= maxSlots) {
            this.isLimitReached = true;
        }
    }

    /** Slotu şimdilik ayırır; sunucu reddederse geri alınır. */
    public synchronized void onListingSent() {
        onListingAttempt();
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
    }

    public synchronized void onListingVerified() {
        pendingSince = 0;
    }

    /** Reddedilen ilanı sayaçtan düşer. Pencere geçtiyse ilan gerçekten girmiştir. */
    private boolean releasePending() {
        if (!pending()) return false;
        pendingSince = 0;
        if (activeListings > 0) activeListings--;
        this.isLimitReached = false;
        return true;
    }

    /** /ah listings ekranından okunan gerçek ilan sayısı. */
    public synchronized void syncActiveListings(int actual) {
        this.pendingSince = 0;
        setActiveListings(actual);
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

    private void releaseSlot() {
        if (this.activeListings > 0) {
            this.activeListings--;
        }
        this.isLimitReached = false;
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
        this.combatUntil = 0;
    }
}