package com.themistra.payment.payment;

/** Matches {@code chk_payment_state} in {@code V1__payments_baseline.sql} exactly (immutable, T02). */
public enum PaymentState {
    CREATED, WATCHING, SEEN, CONFIRMING, FINALIZED, ATTESTED, HELD
}
