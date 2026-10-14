package com.themistra.payment.invoice;

/**
 * An {@code InvoiceService.create(...)} call that fails R3/R4 validation. Public (Phase 3
 * finding 1) — unlike {@code crypto-service}'s own package-private {@code
 * InvalidWatchRequestException}, this service's future {@code InvoiceController} (task 6) will
 * live in a different package and needs to catch it. The message is always a caller-facing
 * validation reason, never internal/stack-trace detail (agents.md).
 */
public class InvalidInvoiceRequestException extends RuntimeException {

    public InvalidInvoiceRequestException(String message) {
        super(message);
    }
}
