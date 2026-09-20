package com.themistra.crypto.watch;

/**
 * Where a watch is in its life.
 *
 * <p>{@link #EXPIRED} is never written by this service: expiry is evaluated against the clock when
 * a watch is read, so there is no background job flipping rows and no second source of truth that
 * can lag. The value exists so a future archiver can mark rows it has retired.
 *
 * <p>{@link #SATISFIED} is unreachable until something can observe a payment.
 */
public enum WatchStatus {

    ACTIVE,
    EXPIRED,
    CANCELLED,
    SATISFIED
}
