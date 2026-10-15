package com.themistra.payment.receipt;

import com.themistra.payment.common.crypto.AttestationResult;

import java.util.UUID;

/**
 * T17 (R17/R19/R20). The outcome of {@code ReceiptService.issueIfEligible(...)} - a sealed
 * interface (Phase 3, Kimi Finding 5) rather than a discarded {@code void}, so the signed
 * result is not lost before T18/T19 exist to persist it.
 */
public sealed interface ReceiptIssueResult {

    /** A signature was obtained. Carries the same {@code receiptUuid} the digest was computed
     * over, so a future caller (T18/T19) never needs to regenerate or guess it. */
    record Signed(UUID receiptUuid, AttestationResult.Signed signed) implements ReceiptIssueResult {
    }

    /** The attest response was {@code BLOCKED}; both the payment and invoice were moved to
     * {@code HELD}. */
    record Blocked(String reason) implements ReceiptIssueResult {
    }

    /** The payment was not {@code FINALIZED}, or its invoice was not {@code PAID}, at the time
     * of the (re-)check - no attest call was made at all. */
    record NotEligible() implements ReceiptIssueResult {
    }

    /** The attest call was refused (409) or the Crypto Service was unavailable, or the two
     * {@code HELD} updates split (Phase 3, Kimi Finding 2) - no state change occurred either
     * way. Never retried from this code path (a disclosed, out-of-scope gap - see {@code
     * ReceiptService}'s own Javadoc). */
    record Failed(String reason) implements ReceiptIssueResult {
    }
}
