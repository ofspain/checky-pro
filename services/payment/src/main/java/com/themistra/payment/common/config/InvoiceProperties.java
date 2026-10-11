package com.themistra.payment.common.config;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Invoice defaults (design.md §4c, Q1/Q3 resolved 2026-10-11): a 60-minute validity window, and
 * an amount-tolerance value fixed at 0 — exact match only, no tolerance band (design.md O1, R15).
 * Both fields have a sensible, already-decided default; neither is forced-required.
 */
@ConfigurationProperties(prefix = "themistra.payment.invoice")
@Validated
public record InvoiceProperties(
        @Positive int defaultTtlMinutes,
        @PositiveOrZero long amountToleranceBaseUnits
) {
}
