# payment · T17 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md` (FROZEN)

No code in this artifact — planning only.

## Files to create

1. `services/payment/src/main/java/com/themistra/payment/payment/PaymentReceiptView.java`
2. `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceReceiptView.java`
3. `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptIssueResult.java`
4. `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptService.java`

## Files to modify

1. `services/payment/src/main/java/com/themistra/payment/payment/PaymentRepository.java`
2. `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java`
3. `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java`
4. `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java`
5. `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java`

## Public methods (signatures)

**`PaymentReceiptView`** (record, `payment` package):
```java
public record PaymentReceiptView(Long id, UUID paymentUuid, Long invoiceId, String chain, String txHash,
        String tokenContractAddress, BigDecimal amount, String fromAddress, Instant finalizedAt,
        PaymentState state) {}
```

**`InvoiceReceiptView`** (record, `invoice` package):
```java
public record InvoiceReceiptView(Long id, UUID invoiceUuid, UUID merchantAccountUuid, short tokenDecimals,
        String payToAddress, InvoiceState state) {}
```
Both mirror `InvoiceWatchView`'s own static `from(Invoice invoice)` factory shape (package-private
factory, constructed from the real entity inside its own module, never passed across).

**`ReceiptIssueResult`** (sealed interface, `receipt` package):
```java
public sealed interface ReceiptIssueResult {
    record Signed(UUID receiptUuid, AttestationResult.Signed signed) implements ReceiptIssueResult {}
    record Blocked(String reason) implements ReceiptIssueResult {}
    record NotEligible() implements ReceiptIssueResult {}
    record Failed(String reason) implements ReceiptIssueResult {}
}
```

**`ReceiptService`** (new `@Service`, `receipt` package):
```java
public ReceiptService(PaymentService paymentService, InvoiceService invoiceService,
                       CryptoClient cryptoClient, PlatformTransactionManager transactionManager, Clock clock)

public ReceiptIssueResult issueIfEligible(Long paymentId, String triggerEventKey)
```
(`transactionManager` wrapped into its own `TransactionTemplate` field in the constructor,
mirroring `ChainEventConsumer`'s own identical established pattern.)

**`PaymentRepository`** (new method, mirrors every existing `@Modifying` method's own shape):
```java
@Modifying
@Query("UPDATE Payment p SET p.state = :newState, p.updatedAt = :now WHERE p.id = :id AND p.state = 'FINALIZED'")
int updateStateIfFinalized(@Param("id") Long id, @Param("newState") PaymentState newState, @Param("now") Instant now);

@Query("SELECT new com.themistra.payment.payment.PaymentReceiptView(p.id, p.paymentUuid, p.invoiceId, "
       + "p.chain, p.txHash, p.tokenContractAddress, p.amount, p.fromAddress, p.finalizedAt, p.state) "
       + "FROM Payment p WHERE p.id = :id")
Optional<PaymentReceiptView> findReceiptView(@Param("id") Long id);
```

**`PaymentService`** (two new methods):
```java
public Optional<PaymentReceiptView> findReceiptPrimitives(Long paymentId)

public boolean markHeldIfFinalized(Long paymentId, String blockedReason, String triggerEventKey, Instant now)
```
`markHeldIfFinalized` mirrors the existing `isPermitted` pre-check style (line ~288's own
`stateMachine.isPermitted(expectedCurrentState, PaymentState.FINALIZED)` precedent):
asserts `stateMachine.isPermitted(PaymentState.FINALIZED, PaymentState.HELD)` (true by
construction, documentation/safety-net only), calls
`repository.updateStateIfFinalized(paymentId, PaymentState.HELD, now)`, and on success (`> 0`)
calls `verificationRecordService.record(paymentId, PaymentState.FINALIZED, PaymentState.HELD,
triggerEventKey, null, Map.of("blockedReason", blockedReason))` — mirrors
`VerificationRecordService.record`'s own real signature exactly (confirmed against source:
`(Long paymentId, PaymentState fromState, PaymentState toState, String triggerEventKey, Integer
confirmations, Map<String,Object> detail)` — `confirmations` is `null` here, not applicable to a
`HELD` transition).

**`InvoiceRepository`** (new method, mirrors `updateWatchIdIfOpen`'s own shape):
```java
@Modifying
@Query("UPDATE Invoice i SET i.state = 'HELD', i.updatedAt = :now WHERE i.id = :id AND i.state = 'PAID'")
int markHeldIfPaid(@Param("id") Long id, @Param("now") Instant now);

@Query("SELECT new com.themistra.payment.invoice.InvoiceReceiptView(i.id, i.invoiceUuid, i.merchantAccountUuid, "
       + "i.tokenDecimals, i.payToAddress, i.state) FROM Invoice i WHERE i.id = :id")
Optional<InvoiceReceiptView> findReceiptView(@Param("id") Long id);
```

**`InvoiceService`** (two new methods):
```java
public Optional<InvoiceReceiptView> findReceiptPrimitives(Long invoiceId)

public boolean markHeldIfPaid(Long invoiceId, Instant now)
```
(Named per Kimi Finding 9, delegating to the repository's own named query rather than the
generic `updateStateIfCurrentState` — the receipt-blocked business meaning stays visible at the
call site, not just in the repository's own query text.)

**`ChainEventConsumer`** (constructor gains `ReceiptService receiptService`; `onChainTxFinalized`
restructured per the frozen brief's own illustrative sketch — exact final shape below):
```java
@KafkaListener(topics = "chain.tx.finalized", groupId = "${spring.kafka.consumer.group-id}")
public void onChainTxFinalized(String rawJson) {
    ChainTxFinalized event = deserialize(rawJson, ChainTxFinalized.class, "chain.tx.finalized");
    if (event == null) {
        return;
    }

    Optional<FinalizationOutcome> outcome = transactionTemplate.execute(status -> {
        if (!dedupe(event.idempotencyKey())) {
            return Optional.<FinalizationOutcome>empty();
        }
        Optional<InvoiceWatchView> invoice = resolveInvoiceForFinalization(event);
        if (invoice.isEmpty()) {
            return Optional.<FinalizationOutcome>empty();
        }
        Optional<FinalizationOutcome> result = paymentService.applyFinalized(invoice.get(), event);
        result.ifPresent(o -> invoiceService.markOutcomeIfOpenOrExpired(
                o.invoiceId(), toInvoiceState(o.amountDiscrepancy()), clock.instant()));
        return result;
    });

    outcome.filter(o -> o.amountDiscrepancy() == null)
            .ifPresent(o -> receiptService.issueIfEligible(o.paymentId(), event.idempotencyKey()));
}
```
No `@Transactional` annotation on the method itself (removed — the `TransactionTemplate` block
is now the only transactional boundary, mirroring `onChainTxReorged`'s own exact precedent).

## Private methods

- `ReceiptService`: a private `handleBlocked(Long paymentId, Long invoiceId, String reason,
  String triggerEventKey)` → `ReceiptIssueResult` — the all-or-nothing `TransactionTemplate`
  block from the frozen brief's own step 5 (both `HELD` updates, split-result
  `IllegalStateException` + catch + `Failed`).
- `ReceiptService`: a private `buildPayload(PaymentReceiptView payment, InvoiceReceiptView
  invoice)` → `ReceiptPayload` — maps the two views' own fields into T16's existing record
  (fresh `UUID.randomUUID()` for `receiptUuid`).

## Entities used

None directly — `Payment`/`Invoice` stay entirely inside their own modules; `ReceiptService`
only ever sees `PaymentReceiptView`/`InvoiceReceiptView`/`ReceiptPayload`/`AttestationResult`.

## Repositories used

`PaymentRepository` (via `PaymentService`, never directly), `InvoiceRepository` (via
`InvoiceService`, never directly) — mirrors every other cross-module access pattern in this
codebase.

## Services used

`PaymentService`, `InvoiceService`, `CryptoClient`, `VerificationRecordService` (transitively,
via `PaymentService.markHeldIfFinalized`, not a new direct dependency for `ReceiptService`
itself).

## Unit/integration tests required

All unit, mocked collaborators, no Spring context (mirrors `PaymentServiceTest`/
`InvoiceServiceTest`/`ChainEventConsumerTest`'s own established shape):

**`ReceiptServiceTest`:**
1. `shouldIssueReceiptOnlyFromFinalizedState` (named) — `FINALIZED`+`PAID` → `attest` called,
   `Signed` returned.
2. `shouldNeverIssueReceiptBeforeFinalized` (named) — payment not `FINALIZED` → `NotEligible`,
   `attest` never called.
3. `shouldReturnNotEligibleWhenInvoiceIsUnderpaidOrOverpaid` — invoice `UNDERPAID`/`OVERPAID` →
   `NotEligible`, `attest` never called (the resolved R17/R18 scope decision).
4. `shouldRequestKmsSignatureViaCryptoAttestEndpoint` (named) — verifies the digest/call shape
   (correct `receiptDigestSha256`/`chain`/`txHash` passed to `CryptoClient.attest`).
5. `shouldNotIssueReceiptWhenAttestReturnsBlocked` (named) — both `markHeldIfFinalized`/
   `markHeldIfPaid` called, `Blocked` returned, no event-related call made.
6. `shouldReturnFailedWithoutStateChangeWhenAttestIsRefused` — `AttestationRefusedException` →
   `Failed`, neither `HELD` method called (Finding 6).
7. `shouldReturnFailedWithoutStateChangeWhenCryptoServiceIsUnavailable` —
   `CryptoServiceUnavailableException` → `Failed`, neither `HELD` method called.
8. `shouldThrowAndRollBackWhenOnlyPaymentHeldUpdateSucceeds` — `markHeldIfFinalized` returns
   `true`, `markHeldIfPaid` returns `false` → the split-result path, caught, `Failed` returned
   (Finding 2).
9. `shouldThrowAndRollBackWhenOnlyInvoiceHeldUpdateSucceeds` — the mirror case.
10. `shouldReturnBlockedWithoutErrorWhenBothHeldUpdatesAreAlreadyNoOps` — both return `false`
    (already resolved elsewhere) → logged `WARN`, still returns `Blocked` (not `Failed` — the
    attest response itself was genuinely `BLOCKED`; the no-op here is a separate, already-handled
    race, not a new failure).

**`PaymentServiceTest`/`InvoiceServiceTest`:** new tests for `findReceiptPrimitives`/
`markHeldIfFinalized`/`markHeldIfPaid`, mirroring the existing test shapes for analogous methods
(e.g. `markOutcomeIfOpenOrExpired`'s own test pattern).

**`ChainEventConsumerTest`:** updated/new tests confirming `onChainTxFinalized`'s own
pre-existing dedupe/outcome-update behavior is unchanged after the restructuring, and that
`receiptService.issueIfEligible` is called exactly once, only when `amountDiscrepancy == null`,
and only after the first transaction's own work completes (not before).

## Execution order

1. `PaymentReceiptView`/`InvoiceReceiptView` — no dependencies.
2. `PaymentRepository`/`InvoiceRepository` new methods — depend on 1.
3. `PaymentService.findReceiptPrimitives`/`markHeldIfFinalized`;
   `InvoiceService.findReceiptPrimitives`/`markHeldIfPaid` — depend on 2.
4. `ReceiptIssueResult` — no dependency on 1–3, can be done any time.
5. `ReceiptService` — depends on 3 and 4, plus the already-existing `CryptoClient`/
   `ReceiptDigest`/`ReceiptPayload` (T15/T16).
6. `ChainEventConsumer`'s restructuring — depends on 5 existing (needs `ReceiptService` injected).
7. All test files (deferred to Phase 10, per the standard rule — this task is not test-only).

## Traceability check

Every file above traces directly to the frozen brief's own "Files to Create"/"Files to Modify"
lists (Phase 4). No file is added that the brief does not authorize.
