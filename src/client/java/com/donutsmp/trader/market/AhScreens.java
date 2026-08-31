package com.donutsmp.trader.market;

import java.util.regex.Pattern;

/**
 * Hangi ekran ne?
 *
 * Kendi ilanlarımızın ekranını piyasa sanmak en pahalı hatadır: mod kendi
 * fiyatının altını keser, her açılışta biraz daha ucuzlar. Bu yüzden tanınmayan
 * bir ekran piyasa sayılmaz — yalnızca başlığı açıkça auction olan taranır.
 */
public final class AhScreens {

    private static final Pattern AUCTION = Pattern.compile("(?i)auction|müzayede|pazar");

    private static final Pattern MINE = Pattern.compile("(?i)\\byour\\b|\\bmy\\b|senin");

    /** "Your Items" adli bir sandik ilan ekrani degildir; bu kelimeler ayirt eder. */
    private static final Pattern LISTINGS = Pattern.compile("(?i)listing|ilan");

    private AhScreens() {}

    /** "Auction -> Your Items", "Your Listings" ... */
    public static boolean isMyListings(String title) {
        if (title == null) return false;
        if (!MINE.matcher(title).find()) return false;
        return AUCTION.matcher(title).find() || LISTINGS.matcher(title).find();
    }

    /** Rakiplerin ilanlarının listelendiği ekran. */
    public static boolean isMarket(String title) {
        if (title == null) return false;
        return AUCTION.matcher(title).find() && !isMyListings(title);
    }
}
