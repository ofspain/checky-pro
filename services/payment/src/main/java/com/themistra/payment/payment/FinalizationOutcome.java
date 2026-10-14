package com.themistra.payment.payment;

/**
 * T13 (Phase 4 Finding 2). {@link PaymentService#applyFinalized}'s own return type - carries the
 * stable {@code invoiceId} the finalized {@link Payment} row belongs to (never the entity
 * itself, L12), alongside the computed amount discrepancy, so {@code ChainEventConsumer} can
 * transition {@code Invoice.state} to match without {@code PaymentService} ever calling
 * {@code InvoiceService} itself. Mirrors {@link ReorgOutcome}'s own identical shape (T12).
 *
 * @param amountDiscrepancy {@code null} (exact match), {@code "UNDERPAID"}, or {@code "OVERPAID"}
 *                          - the same three values the {@code amount_discrepancy} column itself
 *                          is constrained to.
 */
public record FinalizationOutcome(Long paymentId, Long invoiceId, String amountDiscrepancy) {
}
