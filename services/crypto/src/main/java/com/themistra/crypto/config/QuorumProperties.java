package com.themistra.crypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configured quorum posture (ARCHITECTURE §6.1).
 *
 * <p>Bound from {@code themistra.crypto.quorum.*}. Validation lives in
 * {@link com.themistra.crypto.quorum.QuorumPolicy} rather than here, so a misconfigured threshold
 * fails at startup instead of the first time providers disagree in production.
 */
@ConfigurationProperties(prefix = "themistra.crypto.quorum")
public class QuorumProperties {

    /** How many independent providers are configured per chain. Launch: 3. */
    private int providerCount = 3;

    /** How many must agree before a fact is treated as true. Launch: 2. Never below 2. */
    private int threshold = 2;

    public int getProviderCount() {
        return providerCount;
    }

    public void setProviderCount(int providerCount) {
        this.providerCount = providerCount;
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }
}
