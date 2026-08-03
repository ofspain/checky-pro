package com.themistra.auth.mfa;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MFA config (L14, ADR-0003): the TOTP provisioning issuer label and the KMS CMK ARN used for
 * seed envelope encryption. {@code seedKekArn} is deliberately not {@code @NotBlank} — a blank
 * value is legal only in the {@code local} profile (fixed local-dev key, no KMS call); any other
 * profile refuses to boot on a blank value — see {@link MfaSeedEncryption}'s constructor guard.
 */
@ConfigurationProperties(prefix = "themistra.auth.mfa")
@Validated
public record MfaProperties(

        @NotBlank String issuerName,
        String seedKekArn
) {
}
