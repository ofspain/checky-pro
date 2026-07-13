package com.themistra.auth.token;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT signing key slots (D-011): current signs, previous stays published in JWKS through the
 * rotation overlap window. Material is injected by External Secrets — never files in the repo
 * or artifact. {@code requireConfigured} is true in-cluster so a deployment without real keys
 * fails boot instead of silently minting with an ephemeral dev key.
 */
@ConfigurationProperties(prefix = "themistra.auth.jwt")
public record SigningKeysProperties(
        KeySlot current,
        KeySlot previous,
        boolean requireConfigured
) {

    public SigningKeysProperties {
        current = current == null ? KeySlot.empty() : current;
        previous = previous == null ? KeySlot.empty() : previous;
    }

    public record KeySlot(String kid, String privateKeyPem) {

        static KeySlot empty() {
            return new KeySlot(null, null);
        }

        public boolean configured() {
            return kid != null && !kid.isBlank()
                    && privateKeyPem != null && !privateKeyPem.isBlank();
        }
    }
}
