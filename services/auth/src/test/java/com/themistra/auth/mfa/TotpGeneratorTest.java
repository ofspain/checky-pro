package com.themistra.auth.mfa;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TotpGenerator} — R22, L6, AC1/AC2 of the T16 frozen brief. Plain JUnit,
 * no Spring context, no mocking ({@link MfaProperties} is a plain record).
 */
class TotpGeneratorTest {

    private static final MfaProperties PROPERTIES = new MfaProperties("Themistra", "");
    private static final Pattern SECRET_PARAM = Pattern.compile("secret=([^&]+)&");

    private final TotpGenerator generator = new TotpGenerator(PROPERTIES);

    @Test // AC1
    void generateSecretReturns20RandomBytes() {
        byte[] secret = generator.generateSecret();

        assertThat(secret).hasSize(20);
    }

    @Test // AC1 — randomness sanity check, not a statistical entropy proof
    void generateSecretProducesDifferentValuesAcrossCalls() {
        byte[] first = generator.generateSecret();
        byte[] second = generator.generateSecret();

        assertThat(first).isNotEqualTo(second);
    }

    @Test // AC2
    void buildProvisioningUriHasExpectedStructureForAKnownSecret() {
        byte[] allZeroSecret = new byte[20]; // deterministic: 160 zero bits -> 32 'A' characters

        String uri = generator.buildProvisioningUri(allZeroSecret, "user@example.com");

        assertThat(uri).isEqualTo(
                "otpauth://totp/Themistra:user@example.com"
                        + "?secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                        + "&issuer=Themistra&algorithm=SHA1&digits=6&period=30");
    }

    @Test // AC2 — L6: RFC 6238 defaults
    void buildProvisioningUriCarriesL6Parameters() {
        String uri = generator.buildProvisioningUri(generator.generateSecret(), "user@example.com");

        assertThat(uri)
                .contains("algorithm=SHA1")
                .contains("digits=6")
                .contains("period=30")
                .contains("issuer=Themistra");
    }

    @Test // AC2 — Base32 alphabet, no padding
    void buildProvisioningUriSecretIsUppercaseUnpaddedBase32() {
        String uri = generator.buildProvisioningUri(generator.generateSecret(), "user@example.com");

        String secretParam = extractSecretParam(uri);

        assertThat(secretParam).matches("[A-Z2-7]{32}");
        assertThat(secretParam).doesNotContain("=");
    }

    @Test // AC2 — round-trips through an independent (test-only) Base32 decoder, not the
          // production encoder tested against itself
    void buildProvisioningUriBase32SecretRoundTripsToOriginalBytes() {
        byte[] originalSecret = generator.generateSecret();

        String uri = generator.buildProvisioningUri(originalSecret, "user@example.com");
        byte[] decoded = referenceBase32Decode(extractSecretParam(uri));

        assertThat(decoded).isEqualTo(originalSecret);
    }

    @Test // AC2, RFC 3986 — space in issuer/label must be percent-encoded
    void buildProvisioningUriEncodesSpacesInIssuerAndLabel() {
        TotpGenerator spacedIssuerGenerator = new TotpGenerator(new MfaProperties("Acme Corp", ""));

        String uri = spacedIssuerGenerator.buildProvisioningUri(generator.generateSecret(), "john doe");

        assertThat(uri).startsWith("otpauth://totp/Acme%20Corp:john%20doe?");
        assertThat(uri).contains("&issuer=Acme%20Corp");
    }

    @Test // AC2, RFC 3986 — unreserved/pchar characters ('@', '~') must NOT be over-encoded the
          // way java.net.URLEncoder's form-encoding would (Phase 8/9 finding: use UriUtils, not
          // URLEncoder)
    void buildProvisioningUriDoesNotOverEncodeRfc3986UnreservedCharacters() {
        String uri = generator.buildProvisioningUri(generator.generateSecret(), "user+tag@example.com");

        assertThat(uri).contains(":user+tag@example.com?");
        assertThat(uri).doesNotContain("%40").doesNotContain("%2B");
    }

    @Test // AC2 — the produced string must actually be a syntactically valid URI
    void buildProvisioningUriIsSyntacticallyValid() {
        String uri = generator.buildProvisioningUri(generator.generateSecret(), "user@example.com");

        assertThat(URI.create(uri).getScheme()).isEqualTo("otpauth");
    }

    private static String extractSecretParam(String uri) {
        Matcher matcher = SECRET_PARAM.matcher(uri);
        assertThat(matcher.find()).as("uri contains a secret= query param: %s", uri).isTrue();
        return matcher.group(1);
    }

    /** Independent RFC 4648 Base32 decoder (uppercase, unpadded) — deliberately not shared code
     * with {@link TotpGenerator}'s encoder, so a bug in the production encoder can't cancel out
     * against an identical bug in the test's own decode logic. */
    private static byte[] referenceBase32Decode(String base32) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bitsInBuffer = 0;
        for (char c : base32.toCharArray()) {
            int index = alphabet.indexOf(c);
            buffer = (buffer << 5) | index;
            bitsInBuffer += 5;
            if (bitsInBuffer >= 8) {
                out.write((buffer >>> (bitsInBuffer - 8)) & 0xFF);
                bitsInBuffer -= 8;
            }
        }
        return out.toByteArray();
    }
}
