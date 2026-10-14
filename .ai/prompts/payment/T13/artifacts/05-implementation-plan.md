# payment · T13 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md` (`STATUS: FROZEN`)

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/payment/FinalizationOutcome.java`
  — `public record FinalizationOutcome(Long paymentId, Long invoiceId, String amountDiscrepancy)`.
- Test additions to `PaymentServiceTest.java`, `ChainEventConsumerTest.java`, `InvoiceServiceTest.java`
  (confirm this file's exact name/location in Phase 6 — `expireInvoice`'s own existing tests are
  the precedent to mirror).

## Files to Modify

### `PaymentRepository.java`

```java
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
```
One new parameter, inserted into the existing `SET` clause and the existing parameter list — the
method's own existing callers (`tryFinalizeFrom`) are already being changed in this same task
(see below), so there is no orphaned call site.

### `PaymentService.java`

1. Two new named constants (Phase 4 Finding 3's own resolution):
```java
private static final String UNDERPAID = "UNDERPAID";
private static final String OVERPAID = "OVERPAID";
```
2. New read-only method (Phase 4 Finding 2):
```java
/** T13 (Phase 4 Finding 2). A plain, read-only passthrough - never the entity, mirrors {@link
 * ReorgOutcome}'s own shape - so ChainEventConsumer can look up the invoice by this stable
 * foreign key before calling applyFinalized, never by the event's own watchId. */
@Transactional(readOnly = true)
public Optional<Long> findInvoiceIdByChainAndTxHash(String chain, String txHash) {
    Objects.requireNonNull(chain, "chain must not be null");
    Objects.requireNonNull(txHash, "txHash must not be null");
    return repository.findByChainAndTxHash(chain, txHash).map(Payment::getInvoiceId);
}
```
3. `applyFinalized`'s new signature:
```java
@Transactional
public Optional<FinalizationOutcome> applyFinalized(InvoiceWatchView invoice, ChainTxFinalized event) {
    Objects.requireNonNull(invoice, "invoice must not be null");
    Objects.requireNonNull(event, "event must not be null");
    Optional<Payment> maybePayment = repository.findByChainAndTxHash(event.chain(), event.txHash());
    if (maybePayment.isEmpty()) {
        log.warn("Ignoring chain.tx.finalized for chain={} txHash={}: no known payment", event.chain(), event.txHash());
        return Optional.empty();
    }
    Payment payment = maybePayment.get();
    Instant now = clock.instant();
    String amountDiscrepancy = classifyDiscrepancy(event.amount(), invoice.amount());

    PaymentState fromState;
    if (tryFinalizeFrom(payment.getId(), PaymentState.CONFIRMING, event, amountDiscrepancy, now)) {
        fromState = PaymentState.CONFIRMING;
    } else if (tryFinalizeFrom(payment.getId(), PaymentState.SEEN, event, amountDiscrepancy, now)) {
        fromState = PaymentState.SEEN;
    } else {
        PaymentState currentState = repository.findCurrentState(payment.getId()).orElse(payment.getState());
        verificationRecordService.recordIgnored(payment.getId(), currentState, event.idempotencyKey(),
                Map.of("reason", "stale-finalized-event"));
        return Optional.empty();
    }

    verificationRecordService.record(payment.getId(), fromState, PaymentState.FINALIZED,
            event.idempotencyKey(), event.confirmations(), null);

    outboxPublisher.publish("payment-finalized", payment.getPaymentUuid().toString(), "payment.finalized",
            new PaymentFinalizedPayload(payment.getPaymentUuid(), event.chain(), event.txHash(),
                    event.fromAddress(), event.toAddress(), event.tokenContractAddress(),
                    event.amount().toPlainString(), event.confirmations(), event.addressPoisoningFlag(), now));

    return Optional.of(new FinalizationOutcome(payment.getId(), invoice.id(), amountDiscrepancy));
}

private static String classifyDiscrepancy(BigDecimal observedAmount, BigDecimal invoiceAmount) {
    int comparison = observedAmount.compareTo(invoiceAmount);
    if (comparison < 0) {
        return UNDERPAID;
    }
    if (comparison > 0) {
        return OVERPAID;
    }
    return null;
}
```
4. `tryFinalizeFrom` gains the new parameter, passed straight through:
```java
private boolean tryFinalizeFrom(Long paymentId, PaymentState expectedCurrentState, ChainTxFinalized event,
                                 String amountDiscrepancy, Instant now) {
    if (!stateMachine.isPermitted(expectedCurrentState, PaymentState.FINALIZED)) {
        throw new IllegalStateException(...); // unchanged
    }
    return repository.markFinalizedIfCurrentState(paymentId, expectedCurrentState, PaymentState.FINALIZED,
            event.amount(), event.fromAddress(), event.toAddress(), event.confirmations(),
            event.addressPoisoningFlag(), amountDiscrepancy, now) > 0;
}
```

### `InvoiceService.java`

```java
/** T13 (Phase 4 Finding 1, widened from a single-state guard after Kimi's Phase 3 review):
 * tries OPEN then EXPIRED, mirroring PaymentService.tryFinalizeFrom's own two-attempt shape -
 * closes a real path through T08's own already-disclosed sweep race (a payment that legitimately
 * finalizes after its own invoice was wrongly swept to EXPIRED in that race window must still be
 * able to leave EXPIRED, per design.md O2's own "attest anyway" policy). Phase 4 Finding 5: null
 * is rejected outright, not silently a no-op. */
@Transactional
public boolean markOutcomeIfOpenOrExpired(Long invoiceId, InvoiceState outcome, Instant now) {
    Objects.requireNonNull(invoiceId, "invoiceId must not be null");
    Objects.requireNonNull(outcome, "outcome must not be null");
    Objects.requireNonNull(now, "now must not be null");
    if (outcome != InvoiceState.PAID && outcome != InvoiceState.UNDERPAID && outcome != InvoiceState.OVERPAID) {
        throw new IllegalArgumentException("outcome must be PAID, UNDERPAID, or OVERPAID, got: " + outcome);
    }
    if (repository.updateStateIfCurrentState(invoiceId, InvoiceState.OPEN, outcome, now) > 0) {
        return true;
    }
    return repository.updateStateIfCurrentState(invoiceId, InvoiceState.EXPIRED, outcome, now) > 0;
}
```
No new repository method — `updateStateIfCurrentState` (T08) is already fully generic and is
called twice, exactly mirroring `tryFinalizeFrom`'s own two-attempt pattern.

### `ChainEventConsumer.java`

```java
@KafkaListener(topics = "chain.tx.finalized", groupId = "${spring.kafka.consumer.group-id}")
@Transactional
public void onChainTxFinalized(String rawJson) {
    ChainTxFinalized event = deserialize(rawJson, ChainTxFinalized.class, "chain.tx.finalized");
    if (event == null || !dedupe(event.idempotencyKey())) {
        return;
    }

    Optional<Long> maybeInvoiceId = paymentService.findInvoiceIdByChainAndTxHash(event.chain(), event.txHash());
    if (maybeInvoiceId.isEmpty()) {
        log.warn("Ignoring chain.tx.finalized for chain={} txHash={}: no known payment", event.chain(), event.txHash());
        return;
    }
    Optional<InvoiceWatchView> maybeInvoice = invoiceService.findByIdAsWatchView(maybeInvoiceId.get());
    if (maybeInvoice.isEmpty()) {
        log.error("Ignoring chain.tx.finalized for invoiceId={}: payment references a non-existent invoice "
                + "(data integrity issue, not a routine race)", maybeInvoiceId.get());
        return;
    }

    Optional<FinalizationOutcome> outcome = paymentService.applyFinalized(maybeInvoice.get(), event);
    if (outcome.isEmpty()) {
        return;
    }
    InvoiceState invoiceOutcome = toInvoiceState(outcome.get().amountDiscrepancy());
    boolean updated = invoiceService.markOutcomeIfOpenOrExpired(outcome.get().invoiceId(), invoiceOutcome, clock.instant());
    if (!updated) {
        log.warn("Invoice {} was not OPEN or EXPIRED when its payment finalized (state already moved on); "
                + "leaving its own state as-is", outcome.get().invoiceId());
    }
}

private static InvoiceState toInvoiceState(String amountDiscrepancy) {
    if (amountDiscrepancy == null) {
        return InvoiceState.PAID;
    }
    return switch (amountDiscrepancy) {
        case "UNDERPAID" -> InvoiceState.UNDERPAID;
        case "OVERPAID" -> InvoiceState.OVERPAID;
        default -> throw new IllegalStateException("Unexpected amountDiscrepancy value: " + amountDiscrepancy);
    };
}
```
**Note**: this listener's own `@Transactional` placement is unchanged from today (directly on
the method) — Decision 3 (Phase 2) confirmed no `TransactionTemplate` split is needed, since
`markOutcomeIfOpenOrExpired` is a plain local atomic update with no outbound call, unlike T12's
`registerWatch`.

## Public Methods (signatures)

- `PaymentService.findInvoiceIdByChainAndTxHash(String chain, String txHash): Optional<Long>`
- `PaymentService.applyFinalized(InvoiceWatchView invoice, ChainTxFinalized event): Optional<FinalizationOutcome>`
- `InvoiceService.markOutcomeIfOpenOrExpired(Long invoiceId, InvoiceState outcome, Instant now): boolean`
- `ChainEventConsumer.onChainTxFinalized(String rawJson): void` (signature unchanged, body changes)

## Private Methods

- `PaymentService.classifyDiscrepancy(BigDecimal observedAmount, BigDecimal invoiceAmount): String` (static).
- `PaymentService.tryFinalizeFrom(...)` (existing, new parameter).
- `ChainEventConsumer.toInvoiceState(String amountDiscrepancy): InvoiceState` (static).

## Entities Used

`Payment` (no new field — `amountDiscrepancy` already mapped since T11), `Invoice` (no new
field — `state` already mapped, gains a new value range of call sites, not a new column).

## Repositories Used

`PaymentRepository` (extended: one new `@Param`), `InvoiceRepository` (unchanged — its own
already-generic `updateStateIfCurrentState` is reused as-is, zero new query).

## Services Used

`PaymentService` (extended), `InvoiceService` (extended), `VerificationRecordService`
(unchanged — the `FINALIZED` `VerificationRecord` already exists; the discrepancy is a
`Payment`-row field, not part of the verification record's own shape), `PaymentStateMachine`
(unchanged, no new edge), `OutboxPublisher` (unchanged call site, same payload shape — Phase 4
Finding 6 is a forward note for task 24, not a T13 change).

## Unit/Integration Tests Required

1. `shouldFlagUnderpaymentAndOverpaymentAgainstInvoiceAmount` (named, AC1) — likely 3 test
   methods (under/exact/over) in `PaymentServiceTest`, each asserting
   `markFinalizedIfCurrentState`'s own `amountDiscrepancy` argument.
2. Two `InvoiceServiceTest` cases for `markOutcomeIfOpenOrExpired` — one `OPEN → PAID` success,
   one `EXPIRED → UNDERPAID` success (AC2/Finding 1's own widening), mirroring `expireInvoice`'s
   own existing test shape.
3. A non-eligible-invoice-state test (e.g. `HELD`) returning `false` with no exception.
4. A null-outcome test asserting `NullPointerException` (AC8).
5. An invalid-enum-value test (e.g. passing `InvoiceState.EXPIRED` as the *outcome* itself)
   asserting `IllegalArgumentException` — confirms the defensive guard Phase 2/4 both specified.
6. `shouldSurfaceAddressPoisoningFlagFromObservation` (named, AC3) — likely a thin confirming
   test over the already-existing T11 behavior, in `PaymentServiceTest` and/or
   `ChainEventConsumerTest`.
7. A stale/unknown-current-state `applyFinalized` test confirming no discrepancy/outcome is
   computed or returned (AC4) — extends the existing `shouldRecordIgnoredWhenFinalizedEventIsStale`.
8. An unknown-payment consumer test for `onChainTxFinalized` (AC7's own "no known payment" path,
   now surfaced one level up via `findInvoiceIdByChainAndTxHash`).
9. A consumer-level test proving `invoiceService.markOutcomeIfOpenOrExpired` is called with the
   right `InvoiceState` for each of the three outcomes (`ArgumentCaptor`, mirrors T12's own
   `ArgumentCaptor` precedent for `ChainEventConsumer`).
10. `ArchitectureTest` re-run — no new canary expected.

## Execution Order

1. No migration needed — all columns/enum values already exist (confirm in Phase 6, do not
   write a new `V9` unless Phase 6 finds a real gap).
2. `FinalizationOutcome` record.
3. `PaymentRepository.markFinalizedIfCurrentState`'s new `SET` clause/parameter.
4. `PaymentService`: constants, `classifyDiscrepancy`, `findInvoiceIdByChainAndTxHash`,
   `applyFinalized`/`tryFinalizeFrom` new signatures.
5. `InvoiceService.markOutcomeIfOpenOrExpired`.
6. `ChainEventConsumer.onChainTxFinalized` + `toInvoiceState` helper.
7. Tests (all 10 items above), then `ArchitectureTest` re-run, then full suite.

Every file above traces to the frozen brief's own Files to Create/Modify sections — no file
beyond that list is touched.
