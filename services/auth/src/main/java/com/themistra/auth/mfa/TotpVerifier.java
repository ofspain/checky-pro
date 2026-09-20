package com.themistra.auth.mfa;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * Stateless RFC 6238 TOTP verification (L6: HMAC-SHA1, 6 digits, 30s step). No AWS SDK import —
 * mirrors {@link TotpGenerator}'s posture (ADR-0003, D-025 confines AWS SDK use to
 * {@link MfaSeedEncryption} alone). Never logs the secret or the submitted code.
 */
@Component
public class TotpVerifier {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final int ADJACENT_STEPS = 1;
    private static final int MODULUS = 1_000_000;
    private static final String HMAC_ALGORITHM = "HmacSHA1";

    /**
     * Verifies {@code submittedCode} against the current 30s time step and one adjacent step in
     * each direction (90s total tolerance, L6) — a standard clock-skew allowance.
     */
    public boolean verify(byte[] secret, String submittedCode, Instant now) {
        return verifyAndReturnStep(secret, submittedCode, now).isPresent();
    }

    /**
     * Same matching logic as {@link #verify}, but returns the matched 30s time step instead of a
     * boolean — task 20's login-time replay defense needs the step to compare against an
     * enrollment's last-accepted one. Evaluates every candidate step before returning, exactly
     * like {@link #verify}, so which step (if any) matched is not observable via timing.
     */
    public OptionalLong verifyAndReturnStep(byte[] secret, String submittedCode, Instant now) {
        long currentStep = Math.floorDiv(now.getEpochSecond(), TIME_STEP_SECONDS);
        long matchedStep = -1;
        for (long step = currentStep - ADJACENT_STEPS; step <= currentStep + ADJACENT_STEPS; step++) {
            if (constantTimeEquals(generateCode(secret, step), submittedCode)) {
                matchedStep = step;
            }
        }
        return matchedStep == -1 ? OptionalLong.empty() : OptionalLong.of(matchedStep);
    }

    /** The instant at which {@code step} began — the inverse of the step derivation above, so
     * callers can persist a step as a comparable {@link Instant} without knowing the step length. */
    public Instant stepStart(long step) {
        return Instant.ofEpochSecond(step * TIME_STEP_SECONDS);
    }

    private static String generateCode(byte[] secret, long timeCounter) {
        byte[] counterBytes = ByteBuffer.allocate(8).putLong(timeCounter).array();
        byte[] hash = hmacSha1(secret, counterBytes);

        int offset = hash[hash.length - 1] & 0x0F;
        int binaryCode = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);

        return String.format("%0" + CODE_DIGITS + "d", binaryCode % MODULUS);
    }

    private static byte[] hmacSha1(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 computation failed", e);
        }
    }

    /** Avoids a timing oracle on the comparison, cheap insurance even though TOTP codes are
     * short-lived and rate-limiting (not timing safety) is the primary defense against guessing. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
