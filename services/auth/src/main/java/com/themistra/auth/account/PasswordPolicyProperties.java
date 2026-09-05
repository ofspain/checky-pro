package com.themistra.auth.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
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
 * class, gap-analysis §3). {@code minLength}/{@code maxLength} are bounded to L2's locked 12/128
 * range so a deployment cannot configure its way out of the policy (Phase 8/9 review).
 */
@ConfigurationProperties(prefix = "themistra.auth.password")
@Validated
public record PasswordPolicyProperties(

        @Min(12) @Max(128) int minLength,
        @Min(12) @Max(128) int maxLength,
        @NotNull @Valid BreachCheck breachCheck
) {

    @AssertTrue(message = "minLength must be less than or equal to maxLength")
    public boolean isLengthRangeValid() {
        return minLength <= maxLength;
    }

    public record BreachCheck(
            boolean enabled,
            @NotBlank String urlPrefix,
            @Positive @Max(Integer.MAX_VALUE) long timeoutMs
    ) {
    }
}
