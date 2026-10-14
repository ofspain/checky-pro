package com.themistra.payment.invoice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * {@code POST /api/v1/invoices} request body (R1). {@code merchantAccountUuid} is deliberately
 * NOT a field here — the caller's identity comes from the validated JWT's own {@code sub} claim,
 * never from the request body (Phase 3 finding 6). {@code tokenDecimals} ({@code @Min}/{@code
 * @Max}) and {@code reference} ({@code @Size}) validation is new this task — T05 deferred both
 * since no DTO existed yet (Phase 3 finding 5).
 */
public record CreateInvoiceRequest(
        @NotBlank @Pattern(regexp = "ETHEREUM|TRON") String chain,
        @NotBlank String tokenContractAddress,
        @Min(0) @Max(255) short tokenDecimals,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String payToAddress,
        @Size(max = 140) String reference
) {
}
