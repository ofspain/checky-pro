package com.themistra.crypto.quorum;

/**
 * How many independent providers must agree before a fact is treated as true.
 *
 * <p>Launch policy is 2-of-3 (ARCHITECTURE §6.1). Expressed as a value rather than a constant so
 * that a chain served by more providers, or a stricter policy for high-value verifications, does
 * not require touching the reader.
 *
 * @param providerCount how many independent providers are configured for the chain
 * @param threshold     how many must agree
 */
public record QuorumPolicy(int providerCount, int threshold) {

    public QuorumPolicy {
        if (providerCount < 1) {
            throw new IllegalArgumentException("providerCount must be at least 1");
        }
        if (threshold < 2) {
            throw new IllegalArgumentException(
                    "threshold must be at least 2 — a single provider is never authoritative (§6.1)");
        }
        if (threshold > providerCount) {
            throw new IllegalArgumentException(
                    "threshold " + threshold + " cannot exceed providerCount " + providerCount);
        }
    }

    /** The launch policy: three commercially independent providers, two must agree. */
    public static QuorumPolicy twoOfThree() {
        return new QuorumPolicy(3, 2);
    }
}
