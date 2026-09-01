// DonutSMP Trader lisans anahtarı üreteci.
//
//   java tools/LicenseKeyGen.java genkey
//   java tools/LicenseKeyGen.java sign <özel-anahtar> <oyuncu|*> <gün>
//
// Özel anahtar SİZDE kalır ve repoya girmez. Açık anahtar
// src/main/resources/license-pubkey.txt dosyasına yazılır ve jar'a gömülür.

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDate;
import java.util.Base64;

public class LicenseKeyGen {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            return;
        }

        switch (args[0]) {
            case "genkey" -> genkey();
            case "sign" -> {
                if (args.length < 4) {
                    usage();
                    return;
                }
                sign(args[1], args[2], Integer.parseInt(args[3]));
            }
            default -> usage();
        }
    }

    private static void usage() {
        System.out.println("""
                Kullanim:
                  java tools/LicenseKeyGen.java genkey
                  java tools/LicenseKeyGen.java sign <ozel-anahtar> <oyuncu|*> <gun>

                Ornek:
                  java tools/LicenseKeyGen.java sign $(cat private.key) Steve 30
                """);
    }

    private static void genkey() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String priv = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        String pub = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());

        Path pubFile = Path.of("src/main/resources/license-pubkey.txt");
        Files.createDirectories(pubFile.getParent());
        Files.writeString(pubFile, pub + "\n", StandardCharsets.UTF_8);

        Files.writeString(Path.of("private.key"), priv + "\n", StandardCharsets.UTF_8);

        System.out.println("Acik anahtar yazildi : " + pubFile);
        System.out.println("Ozel anahtar yazildi : private.key");
        System.out.println();
        System.out.println("private.key dosyasini KIMSEYE vermeyin ve repoya koymayin.");
        System.out.println("Kaybederseniz dagitilmis butun lisanslar dogrulanamaz hale gelir.");
    }

    private static void sign(String privateKeyBase64, String owner, int days) throws Exception {
        PrivateKey privateKey = KeyFactory.getInstance("Ed25519")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64.trim())));

        LocalDate issued = LocalDate.now();
        LocalDate expires = issued.plusDays(days);
        String payload = owner + "|" + expires.toEpochDay() + "|" + issued.toEpochDay();

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        byte[] signature = signer.sign();

        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String key = "DT1." + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + encoder.encodeToString(signature);

        System.out.println("Sahip   : " + owner);
        System.out.println("Bitis   : " + expires + " (" + days + " gun)");
        System.out.println();
        System.out.println(key);
    }
}
