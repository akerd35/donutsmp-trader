package com.donutsmp.trader.license;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

/**
 * Bir lisans anahtarı: kime verildiği ve ne zaman bittiği.
 *
 * Anahtar formatı: {@code DT1.<base64 gövde>.<base64 imza>}. Gövde düz metindir
 * ki destek isterken bakılabilsin; imza gövdeyi korur.
 */
public record License(String owner, LocalDate expires, LocalDate issued) {

    public static final String PREFIX = "DT1";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /** Herkese açık lisans; oyuncu adına bağlı değildir. */
    public static final String ANY_OWNER = "*";

    public String payload() {
        return owner + "|" + expires.toEpochDay() + "|" + issued.toEpochDay();
    }

    public byte[] payloadBytes() {
        return payload().getBytes(StandardCharsets.UTF_8);
    }

    public String format(byte[] signature) {
        return PREFIX + "." + ENCODER.encodeToString(payloadBytes()) + "." + ENCODER.encodeToString(signature);
    }

    public boolean boundTo(String playerName) {
        if (ANY_OWNER.equals(owner)) return true;
        return playerName != null && owner.equalsIgnoreCase(playerName.trim());
    }

    public boolean expiredOn(LocalDate day) {
        return day.isAfter(expires);
    }

    public long daysLeft(LocalDate day) {
        return expires.toEpochDay() - day.toEpochDay();
    }

    /** @return gövde ve imza, ya da format bozuksa null */
    public static Parsed parse(String key) {
        if (key == null) return null;
        String[] parts = key.trim().split("\\.");
        if (parts.length != 3 || !PREFIX.equals(parts[0])) return null;

        try {
            byte[] payload = DECODER.decode(parts[1]);
            byte[] signature = DECODER.decode(parts[2]);
            String[] fields = new String(payload, StandardCharsets.UTF_8).split("\\|");
            if (fields.length != 3) return null;

            License license = new License(
                    fields[0],
                    LocalDate.ofEpochDay(Long.parseLong(fields[1])),
                    LocalDate.ofEpochDay(Long.parseLong(fields[2])));
            return new Parsed(license, payload, signature);
        } catch (Exception e) {
            return null;
        }
    }

    public record Parsed(License license, byte[] payload, byte[] signature) {}
}
