package com.donutsmp.trader.team;

/**
 * Bir oyuncunun arkadaşlarına gösterdiği anlık durum.
 *
 * Karşıdan gelen JSON başka bir makinede yazıldı: sayıları sınırla, metni
 * temizle. Renk kodu içeren bir ad HUD'ı ve sohbeti boyayabilir.
 */
public record PeerState(
        String name,
        long updatedAt,
        boolean enabled,
        String item,
        int lotSize,
        long price,
        int itemsLeft,
        int freeHotbarSlots,
        int activeListings,
        int maxSlots
) {

    private static final int MAX_TEXT = 32;

    public boolean fresh(long now, long staleMs) {
        return updatedAt > 0 && now - updatedAt < staleMs;
    }

    public long ageSeconds(long now) {
        return Math.max(0, (now - updatedAt) / 1000);
    }

    /** Lot başına değil, tane başına fiyat: lot boyutları farklı olabilir. */
    public double unitPrice() {
        return lotSize > 0 ? price / (double) lotSize : price;
    }

    public boolean sells(String targetItem) {
        return enabled && item != null && !item.isBlank()
                && item.equalsIgnoreCase(targetItem == null ? "" : targetItem.trim());
    }

    /** Okunan her alan makul aralığa çekilmiş bir kopya. */
    public PeerState sanitised() {
        return new PeerState(
                clean(name),
                Math.max(0, updatedAt),
                enabled,
                clean(item),
                clamp(lotSize, 1, 64),
                Math.max(0, Math.min(price, 1_000_000_000_000L)),
                clamp(itemsLeft, 0, 99_999),
                clamp(freeHotbarSlots, 0, 9),
                clamp(activeListings, 0, 999),
                clamp(maxSlots, 0, 999));
    }

    public boolean usable() {
        return name != null && !name.isBlank() && updatedAt > 0;
    }

    private static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    static String clean(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length() && out.length() < MAX_TEXT; i++) {
            char c = raw.charAt(i);
            if (c == '§' || c == '&' || Character.isISOControl(c)) continue;
            out.append(c);
        }
        return out.toString().trim();
    }
}
