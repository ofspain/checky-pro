package com.themistra.payment.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The daily on-chain ledger-head anchor job's cron schedule (design.md §4c, ARCHITECTURE.md
 * §6.5). The job itself is deferred for Phase 1/MVP (spec/payment-service's own Q4 resolution,
 * task 21 skipped) — this property is declared now, per this task's own scope, but nothing reads
 * it until/unless that job is ever built.
 */
@ConfigurationProperties(prefix = "themistra.payment.ledger.anchor")
@Validated
public record LedgerAnchorProperties(
        @NotBlank String cron
) {
}
