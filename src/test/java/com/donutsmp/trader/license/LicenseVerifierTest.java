package com.donutsmp.trader.license;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.LocalDate;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseVerifierTest {

    private static KeyPair keys;
    private static String publicKey;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);

    @BeforeAll
    static void generateKeys() throws Exception {
        keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
    }

    private String issue(String owner, LocalDate expires) throws Exception {
        License license = new License(owner, expires, TODAY.minusDays(1));
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate());
        signer.update(license.payloadBytes());
        return license.format(signer.sign());
    }

    @Test
    void acceptsAValidKey() throws Exception {
        LicenseVerifier.Result r = LicenseVerifier.verify(
                issue("Steve", TODAY.plusDays(30)), publicKey, "Steve", TODAY);
        assertEquals(LicenseVerifier.Status.OK, r.status());
        assertTrue(r.allowed());
        assertEquals(30, r.license().daysLeft(TODAY));
    }

    @Test
    void ownerMatchIgnoresCase() throws Exception {
        assertEquals(LicenseVerifier.Status.OK, LicenseVerifier.verify(
                issue("Steve", TODAY.plusDays(1)), publicKey, "steve", TODAY).status());
    }

    @Test
    void rejectsSomeoneElsesKey() throws Exception {
        LicenseVerifier.Result r = LicenseVerifier.verify(
                issue("Steve", TODAY.plusDays(30)), publicKey, "Notch", TODAY);
        assertEquals(LicenseVerifier.Status.WRONG_OWNER, r.status());
        assertFalse(r.allowed());
    }

    @Test
    void rejectsAnExpiredKey() throws Exception {
        LicenseVerifier.Result r = LicenseVerifier.verify(
                issue("Steve", TODAY.minusDays(1)), publicKey, "Steve", TODAY);
        assertEquals(LicenseVerifier.Status.EXPIRED, r.status());
    }

    @Test
    void acceptsOnTheLastDay() throws Exception {
        assertEquals(LicenseVerifier.Status.OK, LicenseVerifier.verify(
                issue("Steve", TODAY), publicKey, "Steve", TODAY).status());
    }

    @Test
    void aWildcardKeyWorksForAnyone() throws Exception {
        assertEquals(LicenseVerifier.Status.OK, LicenseVerifier.verify(
                issue("*", TODAY.plusDays(5)), publicKey, "Anyone", TODAY).status());
    }

    @Test
    void rejectsATamperedPayload() throws Exception {
        String key = issue("Steve", TODAY.plusDays(1));
        String[] parts = key.split("\\.");
        String forged = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("Steve", "Notch");
        String tampered = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(forged.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        assertEquals(LicenseVerifier.Status.BAD_SIGNATURE,
                LicenseVerifier.verify(tampered, publicKey, "Notch", TODAY).status());
    }

    @Test
    void rejectsAKeySignedBySomeoneElse() throws Exception {
        KeyPair other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String otherPublic = Base64.getEncoder().encodeToString(other.getPublic().getEncoded());
        assertEquals(LicenseVerifier.Status.BAD_SIGNATURE, LicenseVerifier.verify(
                issue("Steve", TODAY.plusDays(1)), otherPublic, "Steve", TODAY).status());
    }

    @Test
    void reportsMissingAndMalformedKeys() {
        assertEquals(LicenseVerifier.Status.MISSING,
                LicenseVerifier.verify("", publicKey, "Steve", TODAY).status());
        assertEquals(LicenseVerifier.Status.MALFORMED,
                LicenseVerifier.verify("çöp", publicKey, "Steve", TODAY).status());
        assertEquals(LicenseVerifier.Status.MALFORMED,
                LicenseVerifier.verify("DT1.abc", publicKey, "Steve", TODAY).status());
    }

    @Test
    void runsUnlicensedWhenNoPublicKeyIsEmbedded() {
        LicenseVerifier.Result r = LicenseVerifier.verify(null, null, "Steve", TODAY);
        assertEquals(LicenseVerifier.Status.NO_LICENCE_REQUIRED, r.status());
        assertTrue(r.allowed(), "kendi derlemeleriniz kilitlenmemeli");
    }
}
