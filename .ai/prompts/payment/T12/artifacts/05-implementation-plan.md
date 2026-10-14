# payment · T12 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md` (`STATUS: FROZEN`)

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/consumer/dto/ChainTxReorged.java`
  — record matching `contracts/events/chain/tx-reorged.v1.schema.json` exactly:
  `idempotencyKey, watchId, chain, txHash, tokenContractAddress, occurredAt`.
- `services/payment/src/test/java/com/themistra/payment/consumer/dto/ChainTxReorgedDeserializationTest.java`
- Test additions to `PaymentServiceTest.java`, `ChainEventConsumerTest.java`,
  `PaymentRepositoryTest.java`-equivalent (no separate Testcontainers file exists per-method;
  confirm actual test file names in Phase 6 before writing).

## Files to Modify

### `InvoiceWatchView.java`
Widen the record and `from(Invoice)` factory:
```java
public record InvoiceWatchView(Long id, UUID invoiceUuid, InvoiceState state, String payToAddress,
                                UUID watchId, String chain, String tokenContractAddress,
                                BigDecimal amount, Instant expiresAt) {
    static InvoiceWatchView from(Invoice invoice) {
        return new InvoiceWatchView(invoice.getId(), invoice.getInvoiceUuid(), invoice.getState(),
                invoice.getPayToAddress(), invoice.getWatchId(), invoice.getChain(),
                invoice.getTokenContractAddress(), invoice.getAmount(), invoice.getExpiresAt());
    }
}
```
(Confirm `Invoice` already exposes `getChain`/`getTokenContractAddress`/`getAmount`/
`getExpiresAt` getters in Phase 6 — expected yes, per T02's "every column mapped" baseline.)

### `InvoiceRepository.java`
1. Refine `findExpiredCandidatesWithoutInFlightPayment`'s `NOT EXISTS` subquery:
```sql
AND NOT EXISTS (
    SELECT 1 FROM payments.payments p
    WHERE p.invoice_id = i.id
    AND (
        p.state IN ('CREATED', 'SEEN', 'CONFIRMING')
        OR (p.state = 'WATCHING' AND p.reorged_at IS NULL)
    )
)
```
2. New method:
```java
@Modifying
@Query("UPDATE Invoice i SET i.watchId = :newWatchId, i.updatedAt = :now "
        + "WHERE i.id = :id AND i.state = 'OPEN'")
int updateWatchIdIfOpen(@Param("id") Long id, @Param("newWatchId") UUID newWatchId, @Param("now") Instant now);
```

### `InvoiceService.java`
Expose `updateWatchIdIfOpen` to `payment`/`consumer` the same way `findByWatchId` already is
(package-private repository, public service passthrough). Signature:
```java
public boolean updateWatchIdIfOpen(Long invoiceId, UUID newWatchId, Instant now)
```
Returns `true` iff a row was updated (mirrors `rows == 1` convention already used elsewhere).

### `PaymentRepository.java`
Two new atomic reversal methods, mirroring `markConfirmingIfSeen`'s exact shape:
```java
@Modifying
@Query("UPDATE Payment p SET p.state = :newState, p.reorgedAt = :now, p.updatedAt = :now "
        + "WHERE p.id = :id AND p.state = :expectedCurrentState")
int markSeenIfConfirming(@Param("id") Long id, @Param("expectedCurrentState") PaymentState expectedCurrentState,
                          @Param("newState") PaymentState newState, @Param("now") Instant now);

@Modifying
@Query("UPDATE Payment p SET p.state = :newState, p.reorgedAt = :now, p.updatedAt = :now "
        + "WHERE p.id = :id AND p.state = :expectedCurrentState")
int markWatchingIfSeen(@Param("id") Long id, @Param("expectedCurrentState") PaymentState expectedCurrentState,
                        @Param("newState") PaymentState newState, @Param("now") Instant now);
```
Also update the existing-payment guard call site (not this file — see `PaymentService` below);
`existsByInvoiceIdAndStateIn` itself is unchanged as a repository method (still a plain
Spring-Data-derived `IN` query) — the refinement is in **how `PaymentService` calls it**
(Finding 1, change 1), not the query's own shape. Decision: call it twice with two disjoint
state sets and OR the booleans, rather than writing a new custom `@Query` — simpler, no new
repository method needed:
```java
boolean activeBlock = repository.existsByInvoiceIdAndStateIn(invoice.id(),
        List.of(PaymentState.CREATED, PaymentState.SEEN, PaymentState.CONFIRMING))
        || repository.existsByInvoiceIdAndStateInAndReorgedAtIsNull(invoice.id(),
        List.of(PaymentState.WATCHING));
```
This needs one new derived-query method, `existsByInvoiceIdAndStateInAndReorgedAtIsNull`
(Spring Data can derive this directly from the property names — no `@Query` needed).

### `PaymentService.java`
1. Refine `applySeen`'s guard per above (two-call OR, not a rewritten `NON_TERMINAL_STATES`
   query).
2. New method:
```java
/** R11/R12. A chain.tx.reorged event walks CONFIRMING back to SEEN or SEEN back to WATCHING;
 * any other current state is stale (R14). Phase 3 Finding 6: the event's own chain/
 * tokenContractAddress must match the already-loaded Payment's own stored values, or the event
 * is treated as stale regardless of state. Returns the state the payment landed in (empty =
 * stale/ignored) so ChainEventConsumer — never this method — can decide whether to call
 * CryptoClient/InvoiceService (Phase 3 Finding 4: no outbound side effect may originate here). */
@Transactional
public Optional<PaymentState> applyReorged(ChainTxReorged event) {
    Objects.requireNonNull(event, "event must not be null");
    Optional<Payment> maybePayment = repository.findByChainAndTxHash(event.chain(), event.txHash());
    if (maybePayment.isEmpty()) {
        log.warn("Ignoring chain.tx.reorged for chain={} txHash={}: no known payment", event.chain(), event.txHash());
        return Optional.empty();
    }
    Payment payment = maybePayment.get();

    if (!payment.getChain().equals(event.chain())
            || !payment.getTokenContractAddress().equals(event.tokenContractAddress())) {
        log.error("Ignoring chain.tx.reorged for paymentId={}: chain/tokenContractAddress mismatch", payment.getId());
        return Optional.empty();
    }

    Instant now = clock.instant();

    if (!stateMachine.isPermitted(PaymentState.CONFIRMING, PaymentState.SEEN)
            || !stateMachine.isPermitted(PaymentState.SEEN, PaymentState.WATCHING)) {
        throw new IllegalStateException("A reorg reversal edge is not permitted - PaymentStateMachine's own edge set changed unexpectedly");
    }

    int toSeen = repository.markSeenIfConfirming(payment.getId(), PaymentState.CONFIRMING, PaymentState.SEEN, now);
    if (toSeen > 0) {
        verificationRecordService.record(payment.getId(), PaymentState.CONFIRMING, PaymentState.SEEN,
                event.idempotencyKey(), null, null);
        return Optional.of(PaymentState.SEEN);
    }

    int toWatching = repository.markWatchingIfSeen(payment.getId(), PaymentState.SEEN, PaymentState.WATCHING, now);
    if (toWatching > 0) {
        verificationRecordService.record(payment.getId(), PaymentState.SEEN, PaymentState.WATCHING,
                event.idempotencyKey(), null, null);
        return Optional.of(PaymentState.WATCHING);
    }

    PaymentState currentState = repository.findCurrentState(payment.getId()).orElse(payment.getState());
    verificationRecordService.recordIgnored(payment.getId(), currentState, event.idempotencyKey(),
            Map.of("reason", "stale-reorg-event"));
    return Optional.empty();
}
```
No `outboxPublisher` call (mirrors `applyConfirmed`'s own precedent — an intermediate,
non-terminal transition; the frozen brief names no outbound event for R11/R12).

### `ChainEventConsumer.java`
New listener, 4th topic, mirroring `onChainTxSeen`'s own shape but with the register call
**outside** the transaction (opposite placement from `unregisterWatch`, per Decision 2/Finding 4):
```java
@KafkaListener(topics = "chain.tx.reorged", groupId = "${spring.kafka.consumer.group-id}")
public void onChainTxReorged(String rawJson) {
    ChainTxReorged event = deserialize(rawJson, ChainTxReorged.class, "chain.tx.reorged");
    if (event == null) {
        return;
    }
    Optional<InvoiceWatchView> maybeInvoice = invoiceService.findByWatchId(event.watchId());
    Optional<PaymentState> newState = applyReorgedTransactionally(event);
    if (newState.isPresent() && newState.get() == PaymentState.WATCHING && maybeInvoice.isPresent()) {
        registerReplacementWatch(maybeInvoice.get());
    }
}

@Transactional
private Optional<PaymentState> applyReorgedTransactionally(ChainTxReorged event) {
    if (!dedupe(event.idempotencyKey())) {
        return Optional.empty();
    }
    return paymentService.applyReorged(event);
}

private void registerReplacementWatch(InvoiceWatchView invoice) {
    try {
        RegisterWatchResponse response = cryptoClient.registerWatch(invoice.invoiceUuid(), invoice.chain(),
                invoice.payToAddress(), invoice.tokenContractAddress(), invoice.amount(), invoice.expiresAt());
        invoiceService.updateWatchIdIfOpen(invoice.id(), response.watchId(), clock.instant());
    } catch (CryptoServiceUnavailableException e) {
        log.error("Failed to register a replacement watch for invoiceUuid={}: invoice will rely on its own "
                + "natural expiry (Phase 4 Finding 3 resolution)", invoice.invoiceUuid(), e);
    }
}
```
**Open implementation note for Phase 6**: `dedupe(...)` must run inside the same transaction as
`applyReorged` (L5) but the register call must run outside it (Decision 2/Finding 4) — the
split above (`onChainTxReorged` non-transactional, `applyReorgedTransactionally` `@Transactional`)
mirrors how a private `@Transactional` method behaves under Spring's self-invocation proxy rules;
**confirm in Phase 6 whether this class already has a `Clock`/needs one injected** (used above
for `updateWatchIdIfOpen`'s own `now` — `PaymentService` already has one; `ChainEventConsumer`
may not. Check before writing.)

## Public Methods (signatures)

- `PaymentService.applyReorged(ChainTxReorged event): Optional<PaymentState>`
- `InvoiceService.updateWatchIdIfOpen(Long invoiceId, UUID newWatchId, Instant now): boolean`
- `ChainEventConsumer.onChainTxReorged(String rawJson): void`

## Private/Package Methods

- `PaymentRepository.markSeenIfConfirming`, `markWatchingIfSeen`,
  `existsByInvoiceIdAndStateInAndReorgedAtIsNull` (package-private interface methods).
- `InvoiceRepository.updateWatchIdIfOpen` (package-private).
- `ChainEventConsumer.applyReorgedTransactionally`, `registerReplacementWatch` (private).

## Entities Used

`Payment` (no new fields — `reorgedAt` already mapped since T11's "every column mapped"
baseline), `Invoice` (no new fields — `watchId` already mapped, just gains its first mutator).

## Repositories Used

`PaymentRepository` (extended), `InvoiceRepository` (extended, package-private, accessed only
via `InvoiceService`).

## Services Used

`PaymentService` (extended), `InvoiceService` (extended), `VerificationRecordService`
(unchanged — already documented as covering R9-R12), `PaymentStateMachine` (unchanged, both
edges pre-existed), `CryptoClient` (its `registerWatch` method, first caller since T06),
`OutboxPublisher` (NOT called by this task — confirmed above).

## Unit/Integration Tests Required

1. `shouldWalkBackwardConfirmingToSeenOnReorg` (named, AC1) — `PaymentServiceTest`.
2. `shouldWalkBackwardSeenToWatchingOnReorgBeforeConfirm` (named, AC2) — `PaymentServiceTest`,
   asserting the returned `Optional<PaymentState>` is `WATCHING`.
3. Guard test (AC3) — a `PaymentServiceTest` or `PaymentServiceIntegrationTest` case: `applySeen`
   succeeds for a different `txHash` on an invoice whose only existing payment is `WATCHING`
   with `reorgedAt` non-null.
4. Sweep test (AC4) — extends the existing T08 sweep test file: an invoice whose only payment is
   `WATCHING` with `reorgedAt` non-null and `expires_at` in the past IS returned by
   `findExpiredCandidatesWithoutInFlightPayment` (likely Testcontainers-gated like T08's own
   equivalent test — confirm file in Phase 6).
5. Register-failure test (AC5) — `ChainEventConsumerTest`: `cryptoClient.registerWatch` throwing
   `CryptoServiceUnavailableException` does not propagate and does not prevent dedupe/commit
   (verify via a spy/mock that the DB-side call already happened).
6. Layering test (AC6) — `PaymentServiceTest`: `verifyNoInteractions` on mocked `CryptoClient`/
   `InvoiceService` collaborators across `applyReorged` (confirm `PaymentService` does not even
   hold references to them before writing this test — expected no, per Finding 4).
7. Mismatch test (AC7) — `PaymentServiceTest`: a `chain.tx.reorged` event with a different
   `tokenContractAddress` than the loaded `Payment`'s own value is dropped, `recordIgnored`
   called (or no record call at all — confirm exact behavior against AC7's wording in Phase 6:
   "dropped, not reversed" doesn't specify whether an ignored-audit-record is still written;
   lean toward yes, for consistency with every other stale case, unless Phase 6 finds a schema
   reason not to).
8. Stale/unknown-payment test (AC8) — `PaymentServiceTest`/`ChainEventConsumerTest`, mirrors T11.
9. Dedupe test (AC10) — `ChainEventConsumerTest`, mirrors the other 3 identical tests.
10. `ChainTxReorgedDeserializationTest` — mirrors the 3 existing DTO tests, including the
    established unknown-property-rejection test.
11. `ArchitectureTest` re-run — no new canary expected.

## Execution Order

1. No migration needed (`reorged_at`, `watch_id` columns already exist — confirm in Phase 6, do
   not write a new `V9` unless Phase 6 finds a real gap).
2. `ChainTxReorged` DTO + its deserialization test.
3. `InvoiceWatchView` widening + `InvoiceRepository.updateWatchIdIfOpen` +
   `findExpiredCandidatesWithoutInFlightPayment` refinement + `InvoiceService` passthrough.
4. `PaymentRepository`: `markSeenIfConfirming`, `markWatchingIfSeen`,
   `existsByInvoiceIdAndStateInAndReorgedAtIsNull`.
5. `PaymentService.applyReorged` + guard refinement in `applySeen`.
6. `ChainEventConsumer.onChainTxReorged` + helpers.
7. Tests (all 11 items above), then `ArchitectureTest` re-run, then full suite.

Every file above traces to the frozen brief's own Files to Create/Modify sections — no file
beyond that list is touched.
