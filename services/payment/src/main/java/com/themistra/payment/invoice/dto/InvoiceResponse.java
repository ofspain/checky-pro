package com.themistra.payment.invoice.dto;

import com.themistra.payment.invoice.Invoice;
import com.themistra.payment.invoice.InvoiceState;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code POST}/{@code GET /api/v1/invoices} response body. Extended at T07 (Phase 3 findings
 * 1/2) to the full merchant-facing view — {@code amount} is a decimal {@code String}
 * (`agents.md`: "monetary values are decimal strings, never JSON numbers"), and {@code watchId}
 * is deliberately excluded (Crypto Service's own internal identifier, no merchant-facing
 * meaning). {@code invoiceUuid}/{@code payToAddress} stay present, so this remains a superset of
 * T06's own original two-field shape, not a breaking change.
 */
public record InvoiceResponse(
        UUID invoiceUuid,
        UUID merchantAccountUuid,
        String chain,
        String tokenContractAddress,
        short tokenDecimals,
        String amount,
        String payToAddress,
        String reference,
        InvoiceState state,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getInvoiceUuid(),
                invoice.getMerchantAccountUuid(),
                invoice.getChain(),
                invoice.getTokenContractAddress(),
                invoice.getTokenDecimals(),
                invoice.getAmount().toPlainString(),
                invoice.getPayToAddress(),
                invoice.getReference(),
                invoice.getState(),
                invoice.getExpiresAt(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt()
        );
    }
}
