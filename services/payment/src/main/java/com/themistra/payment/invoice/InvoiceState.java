package com.themistra.payment.invoice;

/** Matches {@code chk_invoice_state} in {@code V1__payments_baseline.sql} exactly (immutable, T02). */
public enum InvoiceState {
    OPEN,
    PAID,
    UNDERPAID,
    OVERPAID,
    EXPIRED,
    HELD
}
