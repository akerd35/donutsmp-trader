package com.donutsmp.trader.license;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.util.Base64;

/**
 * Lisans anahtarını doğrular.
 *
 * İmza Ed25519; özel anahtar sizde kalır, moda yalnızca açık anahtar gömülür.
 * Açık anahtar yoksa mod lisanssız çalışır — kendi derlemeleriniz kilitlenmesin
 * diye böyle; yayınlanacak sürümde anahtarı gömmek gerekir.
 */
public final class LicenseVerifier {

    public enum Status {
        OK,
        NO_LICENCE_REQUIRED,
        MISSING,
        MALFORMED,
        BAD_SIGNATURE,
        WRONG_OWNER,
        EXPIRED
    }

    public record Result(Status status, License license, String message) {
        public boolean allowed() {
            return status == Status.OK || status == Status.NO_LICENCE_REQUIRED;
        }
    }

    private static final String RESOURCE = "/license-pubkey.txt";

    private LicenseVerifier() {}

    /** Jar'a gömülü açık anahtar; yoksa null. */
    public static String embeddedPublicKey() {
        try (InputStream in = LicenseVerifier.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return null;
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            return text.isEmpty() || text.startsWith("#") ? null : text;
        } catch (Exception e) {
            return null;
        }
    }

    public static Result verify(String key, String publicKeyBase64, String playerName, LocalDate today) {
        if (publicKeyBase64 == null || publicKeyBase64.isBlank()) {
            return new Result(Status.NO_LICENCE_REQUIRED, null, "Lisans kontrolü kapalı (açık anahtar gömülü değil)");
        }

        if (key == null || key.isBlank()) {
            return new Result(Status.MISSING, null, "Lisans anahtarı girilmemiş");
        }

        License.Parsed parsed = License.parse(key);
        if (parsed == null) {
            return new Result(Status.MALFORMED, null, "Lisans anahtarı okunamadı");
        }

        if (!signatureValid(publicKeyBase64, parsed)) {
            return new Result(Status.BAD_SIGNATURE, parsed.license(), "Lisans imzası geçersiz");
        }

        License license = parsed.license();
        if (!license.boundTo(playerName)) {
            return new Result(Status.WRONG_OWNER, license,
                    "Bu lisans " + license.owner() + " adına, siz " + playerName);
        }

        if (license.expiredOn(today)) {
            return new Result(Status.EXPIRED, license, "Lisans " + license.expires() + " tarihinde doldu");
        }

        return new Result(Status.OK, license,
                license.owner() + " — " + license.daysLeft(today) + " gün kaldı");
    }

    private static boolean signatureValid(String publicKeyBase64, License.Parsed parsed) {
        try {
            byte[] encoded = Base64.getDecoder().decode(publicKeyBase64.trim());
            PublicKey publicKey = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(encoded));

            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(parsed.payload());
            return verifier.verify(parsed.signature());
        } catch (Exception e) {
            return false;
        }
    }
}
