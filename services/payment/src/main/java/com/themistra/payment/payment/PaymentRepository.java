package com.themistra.payment.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** {@code (chain, tx_hash)} is the real {@code UNIQUE} constraint (`uq_payment_chain_tx`) -
     * the correlation key {@code chain.tx.confirmed}/{@code chain.tx.finalized} use to find the
     * row {@code chain.tx.seen} already created. */
    Optional<Payment> findByChainAndTxHash(String chain, String txHash);

    /** Payment read (frontend Phase 1b, 2026-10-21): an invoice's payments, oldest first. */
    List<Payment> findByInvoiceIdOrderByCreatedAtAscIdAsc(Long invoiceId);

    /** T11 (Phase 8, Kimi Finding 1 - a real, confirmed omission from the frozen brief's own
     * Decision 3, not implemented until now): closes the one real residual race this task's own
     * design explicitly named but never actually guarded against in code - a second
     * {@code chain.tx.seen} for the same invoice (the disclosed T08 Phase 9 residual
     * unregister-timing race) must never create a second competing {@code Payment} row. */
    boolean existsByInvoiceIdAndStateIn(Long invoiceId, Collection<PaymentState> states);

    /** T12 (Phase 3 Kimi Finding 1): the other half of the existing-payment guard above - a
     * {@code WATCHING} row that has been reorged back to (non-null {@code reorgedAt}) must NOT
     * block a new payment the way a fresh, never-reorged {@code WATCHING} row would (there is no
     * such row in practice today - {@code WATCHING} is never persisted pre-T12 per T11's own
     * Decision 1/Finding 5 - but the condition is written generally). {@code PaymentService}
     * calls this and {@link #existsByInvoiceIdAndStateIn} separately and ORs the two booleans,
     * rather than this repository expressing the nullness distinction in one combined query. */
    boolean existsByInvoiceIdAndStateInAndReorgedAtIsNull(Long invoiceId, Collection<PaymentState> states);

    /** T11 (Phase 8, Kimi Finding 4): a plain scalar projection, not a full entity load - bypasses
     * the persistence context's own first-level cache entirely (unlike {@code findById}, which
     * would return the same already-managed, now-stale {@code Payment} instance this
     * transaction's own earlier {@code findByChainAndTxHash} call loaded, even after a
     * *different* transaction concurrently changed the real row). Used only to find the real
     * current state for an ignored-event audit record after an atomic update attempt reports 0
     * rows changed. */
    @Query("SELECT p.state FROM Payment p WHERE p.id = :id")
    Optional<PaymentState> findCurrentState(@Param("id") Long id);

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
     * read that could already be stale by the time this query runs.</p>
     *
     * <p><b>T13 (R15):</b> also sets {@code amountDiscrepancy} - {@code null} (exact match),
     * {@code "UNDERPAID"}, or {@code "OVERPAID"}, computed once by {@code PaymentService
     * #classifyDiscrepancy} before either atomic attempt, reused for both.</p> */
    @Modifying
    @Query("UPDATE Payment p SET p.state = :newState, p.amount = :amount, p.fromAddress = :fromAddress, "
            + "p.toAddress = :toAddress, p.confirmations = :confirmations, "
            + "p.addressPoisoningFlag = :addressPoisoningFlag, p.amountDiscrepancy = :amountDiscrepancy, "
            + "p.finalizedAt = :now, p.updatedAt = :now "
            + "WHERE p.id = :id AND p.state = :expectedCurrentState")
    int markFinalizedIfCurrentState(@Param("id") Long id, @Param("expectedCurrentState") PaymentState expectedCurrentState,
                                     @Param("newState") PaymentState newState,
                                     @Param("amount") BigDecimal amount, @Param("fromAddress") String fromAddress,
                                     @Param("toAddress") String toAddress, @Param("confirmations") int confirmations,
                                     @Param("addressPoisoningFlag") boolean addressPoisoningFlag,
                                     @Param("amountDiscrepancy") String amountDiscrepancy,
                                     @Param("now") Instant now);

    /** R11 (T12). A {@code chain.tx.reorged} reversal: {@code CONFIRMING → SEEN}. Atomic, same
     * rationale as {@code markConfirmingIfSeen}. No watch action accompanies this reversal - a
     * {@code SEEN} payment still correlates by {@code (chain, txHash)}, independent of any live
     * watch. {@code reorgedAt} records the real historical fact; {@code seenAt}/{@code
     * confirmedAt} are deliberately left untouched - they remain true records of when those
     * milestones were first reached, even though the payment later reverted. */
    @Modifying
    @Query("UPDATE Payment p SET p.state = :newState, p.reorgedAt = :now, p.updatedAt = :now "
            + "WHERE p.id = :id AND p.state = :expectedCurrentState")
    int markSeenIfConfirming(@Param("id") Long id, @Param("expectedCurrentState") PaymentState expectedCurrentState,
                              @Param("newState") PaymentState newState, @Param("now") Instant now);

    /** R12 (T12). A {@code chain.tx.reorged} reversal: {@code SEEN → WATCHING}. Atomic, same
     * rationale as {@code markSeenIfConfirming}. Unlike that reversal, this one does need a
     * watch action - {@code ChainEventConsumer} (never this repository, never {@code
     * PaymentService} - Phase 3 Finding 4) registers a replacement watch after this update
     * commits, since the original watch was already unregistered on first {@code SEEN}
     * (R7a/L14, T11) and there is no "reactivate" path. */
    @Modifying
    @Query("UPDATE Payment p SET p.state = :newState, p.reorgedAt = :now, p.updatedAt = :now "
            + "WHERE p.id = :id AND p.state = :expectedCurrentState")
    int markWatchingIfSeen(@Param("id") Long id, @Param("expectedCurrentState") PaymentState expectedCurrentState,
                            @Param("newState") PaymentState newState, @Param("now") Instant now);

    /** R20 (T17). A {@code BLOCKED} attest outcome: {@code FINALIZED → HELD}. Atomic, same
     * rationale as every other transition above - no entity load, no merge. Takes no
     * {@code expectedCurrentState} parameter (unlike the reorg reversals above) because this
     * task's own resolved scope (a receipt is only ever requested for an already-{@code
     * FINALIZED} payment, re-checked fresh immediately before the {@code attest()} call) means
     * {@code FINALIZED} is the only state this is ever called against. The two states are still
     * bound as {@code PaymentState} parameters, not literal strings (Phase 9, Kimi Phase 8 New
     * Finding 5) - every other method in this file binds its enum values the same way; a
     * literal here would have been the one inconsistent exception, not a match for an
     * established convention. */
    @Modifying
    @Query("UPDATE Payment p SET p.state = :newState, p.updatedAt = :now WHERE p.id = :id AND p.state = :expectedCurrentState")
    int markHeldIfFinalized(@Param("id") Long id, @Param("expectedCurrentState") PaymentState expectedCurrentState,
                             @Param("newState") PaymentState newState, @Param("now") Instant now);

    /** T19 (R24, Phase 3/4 Kimi Finding 4) - also sets {@code attestedAt}, mirroring every other
     * major transition's own dedicated timestamp column ({@code seenAt}/{@code confirmedAt}/
     * {@code finalizedAt}/{@code reorgedAt}). */
    @Modifying
    @Query("UPDATE Payment p SET p.state = :newState, p.attestedAt = :now, p.updatedAt = :now "
            + "WHERE p.id = :id AND p.state = :expectedCurrentState")
    int markAttestedIfFinalized(@Param("id") Long id, @Param("expectedCurrentState") PaymentState expectedCurrentState,
                                 @Param("newState") PaymentState newState, @Param("now") Instant now);

    /* Phase 9 Finding 8 (Kimi, Low confidence, explicit decision rather than a silent default):
     * neither reversal above touches `confirmations` or `amount`. Both values are left exactly
     * as the reorged-out chain.tx.confirmed/chain.tx.finalized event last set them - a deliberate
     * choice to treat them as historical observations (what was actually seen on-chain, at the
     * time it was seen), not as claims about current validity (which `state` and `reorgedAt`
     * already carry correctly). There is no new, more-current value to reset them to anyway:
     * `confirmations` from the original chain.tx.seen was never persisted (Payment.createSeen
     * takes it, but a reorg reversal has no "original chain.tx.seen event" in hand to re-read
     * it from), and resetting `amount` back to Payment.createSeen's own BigDecimal.ZERO
     * placeholder would misrepresent a `WATCHING` row that, pre-T12, never carried a non-zero
     * amount in the first place - there is nothing to "undo" there either. The VerificationRecord
     * written alongside each reversal is the authoritative record of the transition itself. */
}
