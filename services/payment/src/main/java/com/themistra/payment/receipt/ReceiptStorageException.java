package com.themistra.payment.receipt;

/**
 * Thrown by {@link ReceiptStore} on any S3 {@code SdkException} — unlike {@code
 * ObservationSnapshotStore}'s own "supplementary, never throws" stance (crypto-service), this
 * task's S3 write is the load-bearing guarantee R23's "SHALL store" text exists to make durable,
 * so a failure here must be observable to the caller, never swallowed (Phase 2/4 AC4).
 */
public class ReceiptStorageException extends RuntimeException {

    public ReceiptStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
