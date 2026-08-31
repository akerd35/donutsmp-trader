package com.donutsmp.trader.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AhPriceParser {
    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "(?i)(?:price|cost|buy|fiyat|satın al|ucret|ücret)[^0-9$]*\\$?([0-9.,]+)\\s*(k|m|b)?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DIRECT_PRICE_PATTERN = Pattern.compile(
            "\\$([0-9]{1,3}(?:[.,][0-9]{3})*|[0-9]+(?:\\.[0-9]+)?)\\s*(k|m|b)?",
            Pattern.CASE_INSENSITIVE
    );

    public static double parsePrice(String text) {
        if (text == null || text.trim().isEmpty()) return -1;
        String clean = text.replaceAll("(?i)§[0-9a-fk-or]", "").trim();

        // 1. "Price: $15,999" veya "Buy: $15.9k" kalıbı
        Matcher m1 = PRICE_PATTERN.matcher(clean);
        if (m1.find()) {
            return extractValue(m1.group(1), m1.group(2));
        }

        // 2. Doğrudan "$15,999" veya "$15.9k" kalıbı
        Matcher m2 = DIRECT_PRICE_PATTERN.matcher(clean);
        if (m2.find()) {
            return extractValue(m2.group(1), m2.group(2));
        }

        return -1;
    }

    private static double extractValue(String numStr, String suffix) {
        try {
            String sanitized = numStr.replace(",", "");
            double val = Double.parseDouble(sanitized);
            if (suffix != null) {
                String s = suffix.toLowerCase();
                if (s.equals("k")) val *= 1_000;
                else if (s.equals("m")) val *= 1_000_000;
                else if (s.equals("b")) val *= 1_000_000_000;
            }
            return val;
        } catch (Exception e) {
            return -1;
        }
    }
}