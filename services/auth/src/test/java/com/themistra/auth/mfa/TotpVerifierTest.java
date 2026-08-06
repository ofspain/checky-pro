package com.themistra.auth.mfa;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TotpVerifier} — L6 (RFC 6238: HMAC-SHA1, 6 digits, 30s step, current step
 * plus one adjacent step in each direction). Plain JUnit, no Spring context.
 *
 * <p>{@link #referenceGenerateCode} is a separate, independently-written HOTP/TOTP implementation
 * (not sharing code with {@link TotpVerifier}'s own private {@code generateCode}/{@code
 * hmacSha1}), used for the boundary/tolerance-window tests — the same "don't test the production
 * code against itself" discipline {@code TotpGeneratorTest} uses for Base32. The core algorithm
 * itself is additionally verified against RFC 6238 Appendix B's published SHA1 test vectors
 * (external ground truth, not derived from either implementation).</p>
 */
class TotpVerifierTest {

    /** RFC 6238 Appendix B's standard 20-byte ASCII test secret. */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private final TotpVerifier verifier = new TotpVerifier();

    @Test // L6 — RFC 6238 Appendix B known-answer test, truncated from the RFC's 8-digit example
          // to L6's 6 digits (an 8-digit truncation's low 6 digits equal a 6-digit truncation's
          // result, since both take the same binary code mod a power of ten)
    void verifyAcceptsKnownRfc6238TestVectors() {
        assertThat(verifier.verify(RFC_SECRET, "287082", Instant.ofEpochSecond(59))).isTrue();
        assertThat(verifier.verify(RFC_SECRET, "081804", Instant.ofEpochSecond(1111111109))).isTrue();
        assertThat(verifier.verify(RFC_SECRET, "050471", Instant.ofEpochSecond(1111111111))).isTrue();
        assertThat(verifier.verify(RFC_SECRET, "005924", Instant.ofEpochSecond(1234567890))).isTrue();
        assertThat(verifier.verify(RFC_SECRET, "279037", Instant.ofEpochSecond(2000000000))).isTrue();
    }

    @Test // a known-good code for one moment must not verify at an unrelated moment
    void verifyRejectsKnownVectorAtWrongTime() {
        assertThat(verifier.verify(RFC_SECRET, "287082", Instant.ofEpochSecond(1111111109))).isFalse();
    }

    @Test // L6 — 90s tolerance window: current step
    void verifyAcceptsCodeForCurrentStep() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        assertThat(verifier.verify(RFC_SECRET, referenceGenerateCode(RFC_SECRET, stepFor(now)), now)).isTrue();
    }

    @Test // L6 — 90s tolerance window: one step before (clock-skew allowance)
    void verifyAcceptsCodeForOneStepBefore() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        assertThat(verifier.verify(RFC_SECRET, referenceGenerateCode(RFC_SECRET, stepFor(now) - 1), now)).isTrue();
    }

    @Test // L6 — 90s tolerance window: one step after (clock-skew allowance)
    void verifyAcceptsCodeForOneStepAfter() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        assertThat(verifier.verify(RFC_SECRET, referenceGenerateCode(RFC_SECRET, stepFor(now) + 1), now)).isTrue();
    }

    @Test // L6 boundary — two steps before must be rejected, not just "eventually tolerated"
    void verifyRejectsCodeTwoStepsBefore() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        assertThat(verifier.verify(RFC_SECRET, referenceGenerateCode(RFC_SECRET, stepFor(now) - 2), now)).isFalse();
    }

    @Test // L6 boundary — two steps after must be rejected
    void verifyRejectsCodeTwoStepsAfter() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        assertThat(verifier.verify(RFC_SECRET, referenceGenerateCode(RFC_SECRET, stepFor(now) + 2), now)).isFalse();
    }

    @Test
    void verifyRejectsWrongCode() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        String correct = referenceGenerateCode(RFC_SECRET, stepFor(now));
        String wrong = correct.equals("000000") ? "000001" : "000000";

        assertThat(verifier.verify(RFC_SECRET, wrong, now)).isFalse();
    }

    @Test // a code correctly generated for a different secret must not verify against this one
    void verifyRejectsCodeGeneratedWithADifferentSecret() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        byte[] otherSecret = "09876543210987654321".getBytes(StandardCharsets.US_ASCII);

        assertThat(verifier.verify(RFC_SECRET, referenceGenerateCode(otherSecret, stepFor(now)), now)).isFalse();
    }

    @Test // codes are always exactly 6 digits, zero-padded — proves the comparison isn't broken by
          // digit-dropping for a raw binary code small enough to need leading zeros
    void verifyHandlesCodesRequiringLeadingZeroPadding() {
        for (long step = 0; step < 5000; step++) {
            String code = referenceGenerateCode(RFC_SECRET, step);
            if (code.startsWith("0")) {
                assertThat(verifier.verify(RFC_SECRET, code, Instant.ofEpochSecond(step * 30))).isTrue();
                return;
            }
        }
        throw new AssertionError("no zero-padded code found in search window — widen it");
    }

    private static long stepFor(Instant instant) {
        return Math.floorDiv(instant.getEpochSecond(), 30);
    }

    /** Independent RFC 4226/6238 HOTP/TOTP implementation — deliberately separate code from
     * {@link TotpVerifier}'s own private methods, so a bug there can't cancel out against an
     * identical bug here. */
    private static String referenceGenerateCode(byte[] secret, long timeCounter) {
        byte[] counterBytes = new byte[8];
        long counter = timeCounter;
        for (int i = 7; i >= 0; i--) {
            counterBytes[i] = (byte) (counter & 0xFF);
            counter >>= 8;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(counterBytes);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
