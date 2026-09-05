package com.themistra.crypto.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The attestation key's KMS identifier (design.md L11) — never the key material itself, which
 * never leaves KMS. Exactly one identifying field: {@code keyId} may be a key id or an alias/ARN;
 * KMS resolves region/account from it, so a separate region field would be redundant and could
 * disagree with the id it's paired with. This is config only — {@code kms:Sign} is reachable
 * solely from the {@code attest} module (design.md task 20), never from this class.
 */
@ConfigurationProperties(prefix = "themistra.crypto.kms")
@Validated
public record KmsProperties(
        @NotBlank String keyId
) {
}
