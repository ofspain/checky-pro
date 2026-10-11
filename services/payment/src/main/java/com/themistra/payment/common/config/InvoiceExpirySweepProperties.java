package com.themistra.payment.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The invoice expiry sweep job's cron schedule (design.md §4c). Read by
 * {@code InvoiceExpirySweepJob}, task 8 — this property is declared now, per this task's own
 * scope, ahead of that job's own implementation.
 */
@ConfigurationProperties(prefix = "themistra.payment.invoice.expiry-sweep")
@Validated
public record InvoiceExpirySweepProperties(
        @NotBlank String cron
) {
}
