package com.themistra.payment.payment;

/**
 * T12 (Phase 8 Kimi Finding 1). {@link PaymentService#applyReorged}'s own return type - carries
 * the stable {@code invoiceId} the reversed {@code Payment} row belongs to, alongside the state
 * it landed in. Introduced specifically so {@code ChainEventConsumer} can look up the invoice
 * for a replacement-watch registration by this stable foreign key, never by the reorg event's
 * own {@code watchId}: that field is a frozen snapshot of whichever watch was active when the
 * transaction was first observed, and {@code Invoice.watchId} can have moved on by the time a
 * cross-partition-delayed reorg event for an earlier transaction on the same invoice is finally
 * processed (different transactions' events partition by their own distinct original
 * {@code watchId}, so Kafka gives no ordering guarantee between them) - a lookup keyed on the
 * event's own {@code watchId} would then silently fail to find the invoice at all.
 */
public record ReorgOutcome(Long paymentId, Long invoiceId, PaymentState newState) {
}
