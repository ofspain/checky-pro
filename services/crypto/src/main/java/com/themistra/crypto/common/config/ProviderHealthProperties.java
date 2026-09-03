package com.themistra.crypto.common.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How many consecutive disagreements (while a provider is otherwise healthy) trip a
 * {@code chain.provider.degraded} transition (R5's "repeatedly disagreeing"). No numeric value is
 * given anywhere in the spec for this threshold - {@code 3} is this task's own justified, reviewable
 * default, not derived from a cited requirement.
 */
@ConfigurationProperties(prefix = "themistra.crypto.provider-health")
@Validated
public record ProviderHealthProperties(@Positive int disagreementThreshold) {
}
