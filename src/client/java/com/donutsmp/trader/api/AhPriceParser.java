package com.donutsmp.trader.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AhPriceParser {
    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "(?i)(?:price|cost|buy|fiyat|satın al|ucret|ücret)[^0-9$]*\\$?([0-9][0-9.,]*)\\s*([kmb])?"
    );

    private static final Pattern DIRECT_PRICE_PATTERN = Pattern.compile(
            "(?i)\\$\\s*([0-9][0-9.,]*)\\s*([kmb])?"
    );

    private static final Pattern GROUPED = Pattern.compile("^[0-9]{1,3}([.,][0-9]{3})+$");

    public static double parsePrice(String text) {
        if (text == null || text.trim().isEmpty()) return -1;
        String clean = text.replaceAll("(?i)§[0-9a-fk-or]", "").trim();

        Matcher m1 = PRICE_PATTERN.matcher(clean);
        if (m1.find()) {
            return extractValue(m1.group(1), m1.group(2));
        }

        Matcher m2 = DIRECT_PRICE_PATTERN.matcher(clean);
        if (m2.find()) {
            return extractValue(m2.group(1), m2.group(2));
        }

        return -1;
    }

    /**
     * "15.999" ve "15,999" ikisi de on beş bin dokuz yüz doksan dokuzdur.
     * Ayırıcıyı ondalık noktası sanmak fiyatı bin kat yanlış okutur.
     */
    private static double extractValue(String numStr, String suffix) {
        if (numStr == null) return -1;
        String sanitized = numStr.trim();
        if (GROUPED.matcher(sanitized).matches()) {
            sanitized = sanitized.replace(".", "").replace(",", "");
        } else {
            sanitized = sanitized.replace(",", ".");
            int last = sanitized.lastIndexOf('.');
            if (last >= 0) {
                sanitized = sanitized.substring(0, last).replace(".", "") + sanitized.substring(last);
            }
        }

        try {
            double val = Double.parseDouble(sanitized);
            if (suffix != null) {
                switch (suffix.toLowerCase()) {
                    case "k" -> val *= 1_000;
                    case "m" -> val *= 1_000_000;
                    case "b" -> val *= 1_000_000_000L;
                    default -> { }
                }
            }
            return val;
        } catch (Exception e) {
            return -1;
        }
    }
}
