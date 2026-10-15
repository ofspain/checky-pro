package com.themistra.payment.receipt;

/**
 * Thrown by {@link ReceiptStore} when a receipt could not be durably stored — either an S3
 * {@code SdkException} from a {@code putObject} call, or an {@code IOException} while rendering
 * the PDF (Phase 7, self-review: unified under one type, rather than a separate {@code
 * UncheckedIOException} for the rendering step, so a caller has exactly one exception to catch).
 * Unlike {@code ObservationSnapshotStore}'s own "supplementary, never throws" stance
 * (crypto-service), this task's S3 write is the load-bearing guarantee R23's "SHALL store" text
 * exists to make durable, so any failure here must be observable to the caller, never swallowed
 * (Phase 2/4 AC4).
 */
public class ReceiptStorageException extends RuntimeException {

    public ReceiptStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
