package com.donutsmp.trader.team;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fiyat kırılmayacak oyuncular.
 *
 * Rakip taraması bir ilanın kime ait olduğunu lore'daki isimden anlıyor. Kendi
 * ilanımızı rakip saymak fiyatı dibe çekiyordu; iki arkadaş aynı eşyayı
 * satıyorsa birbirini rakip saymak da aynı şeyi yapar, sadece iki kat hızlı.
 */
public final class Team {

    /** Minecraft adları 3-16 karakter. Daha kısa bir ad lore'da rastgele eşleşir. */
    private static final int MIN_NAME = 3;
    private static final int MAX_NAME = 16;
    private static final int MAX_MEMBERS = 16;

    private Team() {}

    public static boolean validName(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        if (trimmed.length() < MIN_NAME || trimmed.length() > MAX_NAME) return false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') return false;
        }
        return true;
    }

    /**
     * @return listeye girdiyse true; zaten varsa ya da ad geçersizse false
     *
     * "Var mı" ile "ekle" iki ayrı adım, ve listeye iki iş parçacığı yazıyor:
     * araya kilit girmezse aynı ad iki kez eklenebilir.
     */
    public static boolean add(List<String> members, String name) {
        if (members == null || !validName(name)) return false;
        String trimmed = name.trim();
        synchronized (members) {
            if (members.size() >= MAX_MEMBERS) return false;
            for (String existing : members) {
                if (trimmed.equalsIgnoreCase(existing)) return false;
            }
            members.add(trimmed);
        }
        return true;
    }

    public static boolean remove(List<String> members, String name) {
        if (members == null || name == null) return false;
        return members.removeIf(existing -> existing.equalsIgnoreCase(name.trim()));
    }

    public static boolean contains(Collection<String> members, String name) {
        if (members == null || name == null) return false;
        for (String existing : members) {
            if (name.trim().equalsIgnoreCase(existing)) return true;
        }
        return false;
    }

    /**
     * Rakip sayılmayacak adların küçük harfli hâli: kendimiz ve takım.
     *
     * Geçersiz adlar burada elenir — lore araması "contains" olduğu için iki
     * harflik bir ad piyasadaki her ilanı bizim gibi gösterirdi.
     */
    public static Set<String> ourNames(Collection<String> members, String self) {
        Set<String> out = new LinkedHashSet<>();
        if (validName(self)) out.add(self.trim().toLowerCase(Locale.ROOT));
        if (members != null) {
            for (String member : members) {
                if (validName(member)) out.add(member.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }
}
