package com.themistra.payment.export;

/**
 * An export request that fails validation: a missing/malformed {@code sub} claim, a missing
 * {@code from}/{@code to}, {@code from > to}, or an unrecognized {@code format} value. The
 * message is always a caller-facing validation reason, never internal/stack-trace detail
 * (agents.md) - mirrors {@code InvalidInvoiceRequestException}'s exact shape.
 */
public class InvalidExportRequestException extends RuntimeException {

    public InvalidExportRequestException(String message) {
        super(message);
    }
}
