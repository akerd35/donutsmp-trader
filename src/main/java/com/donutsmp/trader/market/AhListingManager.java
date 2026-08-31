package com.donutsmp.trader.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AhListingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("DonutTrader-ListingManager");

    // DonutSMP SatÄ±ÅŸ MesajÄ± KalÄ±plarÄ±
    private static final Pattern SALE_PATTERN = Pattern.compile(
            "(?i)(?:bought|sold|purchased|satÄ±ldÄ±|satÄ±n aldÄ±).*?\\$?([0-9]{1,3}(?:[.,][0-9]{3})*|[0-9]+(?:\\s*[km])?)",
            Pattern.CASE_INSENSITIVE
    );

    // Ä°lan Ä°ptal / Geri Ã‡ekme MesajÄ± KalÄ±plarÄ±
    private static final Pattern CANCEL_PATTERN = Pattern.compile(
            "(?i)(?:cancelled|canceled|removed|collected|reclaimed|expired|iptal|Ã§ekildi)",
            Pattern.CASE_INSENSITIVE
    );

    public static class ListingTask {
        public final String itemName;
        public final int lotSize;
        public final double price;
        public final int hotbarSlot;

        public ListingTask(String itemName, int lotSize, double price, int hotbarSlot) {
            this.itemName = (itemName != null) ? itemName.toLowerCase().replace("minecraft:", "").trim() : "";
            this.lotSize = Math.max(1, lotSize);
            this.price = Math.max(0.0, price);
            this.hotbarSlot = hotbarSlot;
        }

        @Override
        public String toString() {
            return String.format("ListingTask{item='%s', size=%d, price=$%.0f, slot=%d}",
                    itemName, lotSize, price, hotbarSlot);
        }
    }

    public enum State {
        IDLE,
        PREPARING_ITEM,
        COMMAND_SENT,
        WAITING_CONFIRMATION,
        CONFIRMED
    }

    private int maxSlots = 18;
    private int activeListings = 0;
    private boolean isLimitReached = false;
    private long combatUntil = 0; // SavaÅŸ sÃ¼resi korumasÄ±
    private long totalEarned = 0;
    private int itemsSold = 0;
    private State currentState = State.IDLE;
    private final Queue<ListingTask> queue = new ArrayDeque<>();
    private ListingTask currentTask = null;

    public AhListingManager() {
        this(18);
    }

    public AhListingManager(int maxSlots) {
        this.maxSlots = Math.max(1, maxSlots);
    }

    public static String stripColorCodes(String input) {
        if (input == null) return "";
        return input.replaceAll("(?i)Â§[0-9a-fk-or]", "").trim();
    }

    public synchronized boolean canListMore() {
        if (System.currentTimeMillis() < combatUntil) {
            return false; // SavaÅŸta iken komut gÃ¶nderilemez
        }
        return !isLimitReached && activeListings < maxSlots;
    }

    public synchronized boolean hasAvailableSlot() {
        return canListMore();
    }

    public synchronized int getAvailableSlotCount() {
        return Math.max(0, maxSlots - activeListings);
    }

    public synchronized void onListingAttempt() {
        this.activeListings = Math.min(maxSlots, this.activeListings + 1);
        if (this.activeListings >= maxSlots) {
            this.isLimitReached = true;
        }
    }

    public synchronized void offerTask(ListingTask task) {
        if (task != null) {
            queue.offer(task);
        }
    }

    public synchronized ListingTask pollNextTask() {
        if (!hasAvailableSlot() || queue.isEmpty()) {
            return null;
        }
        currentTask = queue.poll();
        currentState = State.PREPARING_ITEM;
        return currentTask;
    }

    public synchronized void markCommandSent() {
        this.currentState = State.WAITING_CONFIRMATION;
    }

    public synchronized void onListingConfirmed() {
        onListingAttempt();
        this.currentState = State.IDLE;
        currentTask = null;
    }

    public synchronized boolean isInCombat() {
        return System.currentTimeMillis() < combatUntil;
    }

    public synchronized boolean onChatMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) return false;

        String cleanMessage = stripColorCodes(rawMessage);
        String lower = cleanMessage.toLowerCase();

        // 1. SavaÅŸ / Combat Tespiti ("You cannot do this in combat")
        if (lower.contains("cannot do this in combat") || lower.contains("in combat") || lower.contains("savaÅŸta")) {
            this.combatUntil = System.currentTimeMillis() + 20000; // 20 saniye savaÅŸ molasÄ±
            LOGGER.warn("[SAVAS KORUMASI] SavaÅŸta olunduÄŸu tespit edildi. 20 saniye iÅŸlem durduruldu.");
            return true;
        }

        // 2. DonutSMP Limit Dolu UyarÄ±sÄ±
        if (lower.contains("too many listed items") || lower.contains("too many")
                || lower.contains("maximum") || lower.contains("limit reached") || lower.contains("cannot list")) {
            this.activeListings = this.maxSlots;
            this.isLimitReached = true;
            this.currentState = State.IDLE;
            LOGGER.warn("[LIMIT DOLU] Sunucu ilan sÄ±nÄ±rÄ±na ulaÅŸÄ±ldÄ± (18/18). Yeni satÄ±ÅŸ bekleniyor.");
            return true;
        }

        // 3. SatÄ±ÅŸ MesajÄ± (Slot boÅŸalÄ±r, para eklenir, limit kalkar)
        if (lower.contains("bought your") || lower.contains("bought") || lower.contains("sold")
                || lower.contains("purchased") || lower.contains("satÄ±ldÄ±")) {
            Matcher saleMatcher = SALE_PATTERN.matcher(cleanMessage);
            if (saleMatcher.find()) {
                String priceStr = saleMatcher.group(1).replace(",", "").replace(".", "").trim();
                try {
                    long multiplier = 1;
                    if (priceStr.toLowerCase().endsWith("k")) {
                        multiplier = 1000;
                        priceStr = priceStr.substring(0, priceStr.length() - 1).trim();
                    } else if (priceStr.toLowerCase().endsWith("m")) {
                        multiplier = 1000000;
                        priceStr = priceStr.substring(0, priceStr.length() - 1).trim();
                    }
                    long price = Long.parseLong(priceStr) * multiplier;
                    this.totalEarned += price;
                } catch (NumberFormatException ignored) {
                }
            }

            this.itemsSold++;
            if (this.activeListings > 0) {
                this.activeListings--;
            }
            this.isLimitReached = false;
            this.currentState = State.IDLE;
            LOGGER.info("[SATIS BILDIRIMI] EÅŸya satÄ±ldÄ±! SatÄ±lan: {}x, Kasa: +${}, Aktif Slot: {}/{}",
                    itemsSold, totalEarned, activeListings, maxSlots);
            return true;
        }

        // 4. Ä°ptal / Geri Ã‡ekme MesajÄ± (Slot boÅŸalÄ±r, limit kalkar)
        Matcher cancelMatcher = CANCEL_PATTERN.matcher(cleanMessage);
        if (cancelMatcher.find()) {
            if (this.activeListings > 0) {
                this.activeListings--;
            }
            this.isLimitReached = false;
            this.currentState = State.IDLE;
            LOGGER.info("[ILAN IPTAL] Ä°lan geri Ã§ekildi! Aktif Slot: {}/{}", activeListings, maxSlots);
            return true;
        }

        return false;
    }

    public static int findConfirmButtonSlot(String[] itemNames, String[] displayNames) {
        if (itemNames == null) return -1;
        for (int i = 0; i < itemNames.length; i++) {
            String name = (itemNames[i] != null) ? itemNames[i].toLowerCase() : "";
            String disp = (displayNames != null && i < displayNames.length && displayNames[i] != null)
                    ? stripColorCodes(displayNames[i]).toLowerCase() : "";
            if (name.contains("lime_stained_glass") || name.contains("green_stained_glass")
                    || disp.contains("confirm") || disp.contains("onayla") || disp.contains("sat")) {
                return i;
            }
        }
        return -1;
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
    public int getQueueSize() { return queue.size(); }
    public State getCurrentState() { return currentState; }
    public void setCurrentState(State state) { this.currentState = state; }
    public void resetAll() {
        this.activeListings = 0;
        this.isLimitReached = false;
        this.combatUntil = 0;
        this.currentState = State.IDLE;
        this.queue.clear();
        this.currentTask = null;
    }
}