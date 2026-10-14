package com.themistra.payment.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** {@code (chain, tx_hash)} is the real {@code UNIQUE} constraint (`uq_payment_chain_tx`) -
     * the correlation key {@code chain.tx.confirmed}/{@code chain.tx.finalized} use to find the
     * row {@code chain.tx.seen} already created. */
    Optional<Payment> findByChainAndTxHash(String chain, String txHash);

    /** T11 (mirrors {@code InvoiceRepository.updateStateIfCurrentState}'s own T08 lesson): the
     * real {@code SEEN → CONFIRMING} transition. Atomic - no entity load, no merge, no
     * lost-update window across the two separate Kafka topics ({@code chain.tx.confirmed}/
     * {@code chain.tx.finalized}) that can both target the same row. Returns 0 if the row was no
     * longer {@code SEEN} by the time this ran (e.g. {@code chain.tx.finalized} already won the
     * race) - not an error, the caller falls through to the refresh/stale paths. */
    @Modifying
    @Query("UPDATE Payment p SET p.state = :newState, p.confirmations = :confirmations, "
            + "p.confirmedAt = :now, p.updatedAt = :now "
            + "WHERE p.id = :id AND p.state = :expectedCurrentState")
    int markConfirmingIfSeen(@Param("id") Long id, @Param("expectedCurrentState") PaymentState expectedCurrentState,
                              @Param("newState") PaymentState newState,
                              @Param("confirmations") int confirmations, @Param("now") Instant now);

    /** A confirmation-count refresh for a payment already {@code CONFIRMING} - a field-only
     * update, never a state transition (Phase 3/4 Finding 3: no {@code VerificationRecord} for
     * this). The "strictly greater" check (R14's own "a lower confirmation count... ignore" rule)
     * lives in this query's own {@code WHERE} clause, not a prior read-then-decide step, closing
     * the same race window {@code markConfirmingIfSeen} closes. Returns 0 for a non-increasing
     * count (R14 stale-by-count) or a current state that is no longer {@code CONFIRMING}. */
    @Modifying
    @Query("UPDATE Payment p SET p.confirmations = :confirmations, p.updatedAt = :now "
            + "WHERE p.id = :id AND p.state = :confirmingState AND p.confirmations < :confirmations")
    int refreshConfirmationsIfGreaterAndCurrentlyConfirming(@Param("id") Long id,
                                                             @Param("confirmingState") PaymentState confirmingState,
                                                             @Param("confirmations") int confirmations,
                                                             @Param("now") Instant now);

    /** The real {@code SEEN|CONFIRMING → FINALIZED} transition (R10: finalization can legitimately
     * arrive without an intervening confirmed event). Persists the event's own real observed
     * {@code amount}/{@code fromAddress}/{@code toAddress}/{@code confirmations}/
     * {@code addressPoisoningFlag}, overwriting {@code Payment.createSeen}'s own placeholder
     * {@code amount = 0}. Atomic, same rationale as the two methods above.
     *
     * <p>Takes exactly one {@code expectedCurrentState}, not an {@code IN (SEEN, CONFIRMING)}
     * list (Phase 7 self-review: an earlier version did take a two-state list, built from a
     * {@code priorState} read moments before this query ran - but if a concurrent event changed
     * the real state from {@code SEEN} to {@code CONFIRMING} in that window, the update would
     * still match the {@code IN} list, yet the caller would record the wrong {@code fromState}
     * in the audit trail - the stale pre-read value, not the state the row was actually in when
     * this query matched it). {@link PaymentService#applyFinalized} now tries each expected state
     * in its own separate call, so whichever one succeeds is known exactly, not inferred from a
     * read that could already be stale by the time this query runs.</p> */
    @Modifying
    @Query("UPDATE Payment p SET p.state = :newState, p.amount = :amount, p.fromAddress = :fromAddress, "
            + "p.toAddress = :toAddress, p.confirmations = :confirmations, "
            + "p.addressPoisoningFlag = :addressPoisoningFlag, p.finalizedAt = :now, p.updatedAt = :now "
            + "WHERE p.id = :id AND p.state = :expectedCurrentState")
    int markFinalizedIfCurrentState(@Param("id") Long id, @Param("expectedCurrentState") PaymentState expectedCurrentState,
                                     @Param("newState") PaymentState newState,
                                     @Param("amount") BigDecimal amount, @Param("fromAddress") String fromAddress,
                                     @Param("toAddress") String toAddress, @Param("confirmations") int confirmations,
                                     @Param("addressPoisoningFlag") boolean addressPoisoningFlag,
                                     @Param("now") Instant now);
}
