package com.themistra.auth.mfa;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MFA config (L14, ADR-0003): the TOTP provisioning issuer label, the KMS CMK ARN used for seed
 * envelope encryption, and whether that ARN is required. {@code seedKekArn} is deliberately not
 * {@code @NotBlank} — a blank value is legal in local development (fixed local-dev key, no KMS
 * call). {@code seedKekRequired} gates that fallback exactly like {@code SigningKeysProperties
 * #requireConfigured} gates the ephemeral JWT dev key: defaults to {@code false} here and in
 * every profile unless explicitly overridden, flipped to {@code true} only by the deployed
 * (non-local) environment's config — see {@link MfaSeedEncryption}'s constructor guard.
 */
@ConfigurationProperties(prefix = "themistra.auth.mfa")
@Validated
public record MfaProperties(

        @NotBlank String issuerName,
        String seedKekArn,
        boolean seedKekRequired
) {
}
