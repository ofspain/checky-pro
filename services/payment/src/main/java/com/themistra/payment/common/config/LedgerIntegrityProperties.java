package com.themistra.payment.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The scheduled ledger integrity check's cron (threat #5 follow-ups FU-T28-2 and FU-T28-3). The
 * job runs {@code LedgerVerifier} and {@code ReceiptReconciler} and publishes the results as
 * metrics for alerting.
 */
@ConfigurationProperties(prefix = "themistra.payment.ledger.integrity-check")
@Validated
public record LedgerIntegrityProperties(
        @NotBlank String cron
) {
}
