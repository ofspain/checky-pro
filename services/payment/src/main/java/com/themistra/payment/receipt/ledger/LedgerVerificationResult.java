package com.themistra.payment.receipt.ledger;

/**
 * T20 (R22). The outcome of {@link LedgerVerifier#verify()}. A sealed interface rather than a
 * boolean plus nullable fields, mirroring {@code AttestationResult}/{@code ReceiptIssueResult}'s
 * own established convention throughout this service.
 */
public sealed interface LedgerVerificationResult {

    /** The whole chain, head to tail, checks out - including the empty-ledger and
     * genesis-only-ledger cases. */
    record Intact() implements LedgerVerificationResult {
    }

    /** The first entry (head-to-tail order) that failed verification, and which check failed -
     * one of the three exact reason strings {@link LedgerVerifier} uses. */
    record Broken(Long seq, String reason) implements LedgerVerificationResult {
    }
}
