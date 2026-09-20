package com.themistra.auth.apikey;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain JUnit, no Spring context — pure logic, mirrors {@code TotpVerifierTest}'s precedent for
 * crypto-primitive unit tests. */
class ApiKeyHasherTest {

    private final ApiKeyHasher hasher = new ApiKeyHasher();
    private static final String KEY = "ck_live_abcdefgh12345678.zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz";

    @Test
    void matchesReturnsTrueForTheCorrectKey() {
        String hash = hasher.hash(KEY);
        assertThat(hasher.matches(KEY, hash)).isTrue();
    }

    @Test
    void matchesReturnsFalseForAnIncorrectKey() {
        String hash = hasher.hash(KEY);
        String differentKey = "ck_live_abcdefgh12345678.yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy";

        assertThat(hasher.matches(differentKey, hash)).isFalse();
    }

    /** Not a real timing measurement (flaky in CI) — a correctness proxy proving the comparison
     * doesn't special-case where within the hash a mismatch occurs, whichever position it's at. */
    @Test
    void matchesRejectsMismatchesRegardlessOfPosition() {
        String hash = hasher.hash(KEY);

        assertThat(hasher.matches(KEY, flipFirstHexChar(hash))).isFalse();
        assertThat(hasher.matches(KEY, flipLastHexChar(hash))).isFalse();
    }

    private static String flipFirstHexChar(String hex) {
        return (hex.charAt(0) == '0' ? '1' : '0') + hex.substring(1);
    }

    private static String flipLastHexChar(String hex) {
        return hex.substring(0, hex.length() - 1) + (hex.charAt(hex.length() - 1) == '0' ? '1' : '0');
    }
}
