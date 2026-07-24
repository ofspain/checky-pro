package com.themistra.auth.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * NIST 800-63B password policy config (L2): length bounds plus the Have I Been Pwned
 * breach-check endpoint and its fail-open timeout. Validated at startup — a missing or blank
 * value fails boot instead of silently defaulting (the reference project's silent-default bug
 * class, gap-analysis §3).
 */
@ConfigurationProperties(prefix = "themistra.auth.password")
@Validated
public record PasswordPolicyProperties(

        @Min(1) int minLength,
        @Min(1) int maxLength,
        @NotNull @Valid BreachCheck breachCheck
) {

    public record BreachCheck(
            boolean enabled,
            @NotBlank String urlPrefix,
            @Positive long timeoutMs
    ) {
    }
}
