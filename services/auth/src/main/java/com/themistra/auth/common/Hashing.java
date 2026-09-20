package com.themistra.auth.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 hex digest — generic cross-cutting plumbing (moved here from the token module, D-021,
 * once the audit module needed the same primitive for hashing user-agent strings).
 */
public final class Hashing {

    private Hashing() {
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Constant-time comparison of two hex-encoded digests, via {@link MessageDigest#isEqual}
     * rather than {@link String#equals} (which short-circuits on the first mismatched character
     * and is not safe for comparing secret-derived values). Both arguments are expected to be
     * hex strings produced by {@link #sha256}; each is decoded to bytes before comparison.
     */
    public static boolean constantTimeEquals(String hexA, String hexB) {
        byte[] a = HexFormat.of().parseHex(hexA);
        byte[] b = HexFormat.of().parseHex(hexB);
        return MessageDigest.isEqual(a, b);
    }
}
