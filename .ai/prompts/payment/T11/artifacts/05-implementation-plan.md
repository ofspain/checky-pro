# payment · T11 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T11 — Idempotent consumers |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |

## A design elaboration beyond the frozen brief's own text, disclosed here (not silently assumed)

The frozen brief doesn't commit `Payment`'s own state-changing writes to a specific concurrency
strategy. **This plan commits to atomic, conditional `UPDATE ... WHERE` queries (never
load-mutate-save) for every `Payment` state/field change**, mirroring `InvoiceRepository
.updateStateIfCurrentState`'s own exact T08 lesson (Kimi Phase 8: a detached-entity merge-save
creates a real lost-update race). The reason this matters *here*, specifically: `chain.tx.seen`/
`chain.tx.confirmed`/`chain.tx.finalized` are three **separate Kafka topics**; Spring Kafka
typically runs one listener-container thread per topic (or per topic-partition), so two events
for the *same* payment on two *different* topics have no cross-topic ordering guarantee relative
to each other, even though same-topic delivery for the same `watchId` is strictly ordered
(Finding 4). A `chain.tx.confirmed` and a `chain.tx.finalized` for the same payment could,
in principle, be processed concurrently by two different threads. Atomic conditional updates
(with the "is this actually greater/still in the expected state" check in the same `WHERE`
clause, not a prior `SELECT`-then-decide step) close this the same way T08's own fix did.

## Files to create

1. `services/payment/src/main/java/com/themistra/payment/payment/Payment.java`
2. `services/payment/src/main/java/com/themistra/payment/payment/PaymentRepository.java`
3. `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java`
4. `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java`
5. `services/payment/src/main/java/com/themistra/payment/consumer/ProcessedEvent.java`
6. `services/payment/src/main/java/com/themistra/payment/consumer/ProcessedEventRepository.java`
7. `services/payment/src/main/java/com/themistra/payment/consumer/dto/ChainTxSeen.java`
8. `services/payment/src/main/java/com/themistra/payment/consumer/dto/ChainTxConfirmed.java`
9. `services/payment/src/main/java/com/themistra/payment/consumer/dto/ChainTxFinalized.java`
10. `services/payment/src/main/resources/db/migration/V7__payment_app_payments_table_write_grant.sql`
11. `services/payment/src/main/resources/db/migration/V8__payment_app_processed_events_grant.sql`
12. Test files mirroring 1-9.

## Files to modify

1. `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java`
2. `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java`
3. `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`

## Public methods (signatures)

```java
// Payment.java — entity, maps every payments.payments column (even ones only a later task
// populates: amountDiscrepancy, reorgedAt, attestedAt — mirrors Invoice's own convention of
// mapping the full real schema from the start).
@Entity @Table(name = "payments")
public class Payment {
    public static Payment createSeen(Long invoiceId, String chain, String txHash, String toAddress,
            String tokenContractAddress, int confirmations, Instant now);
    // amount = BigDecimal.ZERO (Decision 2 placeholder), state = SEEN, seenAt/createdAt/updatedAt = now.

    public Long getId();
    public UUID getPaymentUuid();
    public Long getInvoiceId();
    public String getChain();
    public String getTxHash();
    public String getFromAddress();
    public String getToAddress();
    public String getTokenContractAddress();
    public BigDecimal getAmount();
    public int getConfirmations();
    public PaymentState getState();
    public String getAmountDiscrepancy();
    public boolean isAddressPoisoningFlag();
    public Instant getSeenAt();
    public Instant getConfirmedAt();
    public Instant getFinalizedAt();
    public Instant getReorgedAt();
    public Instant getAttestedAt();
    public Instant getCreatedAt();
    public Instant getUpdatedAt();
}
```

```java
// PaymentRepository.java — package-private, atomic conditional updates only (no load-mutate-save
// for any state/field change).
interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByChainAndTxHash(String chain, String txHash);

    @Modifying
    int markConfirmingIfCurrentState(Long id, PaymentState expectedCurrentState, int confirmations,
            Instant now);
    // WHERE id = :id AND state = :expectedCurrentState (expectedCurrentState is always SEEN at
    // the one real call site; parameterized, not hardcoded, mirroring updateStateIfCurrentState's
    // own general shape).

    @Modifying
    int refreshConfirmationsIfGreaterAndCurrentlyConfirming(Long id, int confirmations, Instant now);
    // WHERE id = :id AND state = 'CONFIRMING' AND confirmations < :confirmations — the "strictly
    // greater" check lives in the WHERE clause itself, not a prior read-then-decide step.

    @Modifying
    int markFinalizedIfCurrentState(Long id, BigDecimal amount, String fromAddress, String toAddress,
            int confirmations, boolean addressPoisoningFlag, Instant now);
    // WHERE id = :id AND state IN ('SEEN', 'CONFIRMING').
}
```

```java
// PaymentService.java
@Service
public class PaymentService {

    public PaymentService(PaymentRepository repository, PaymentStateMachine stateMachine,
            VerificationRecordService verificationRecordService, OutboxPublisher outboxPublisher,
            Clock clock);

    @Transactional
    public Optional<Payment> applySeen(Invoice invoice, ChainTxSeen event);
    // Finding 1: if invoice.getState() != OPEN, log WARN, return Optional.empty() (no Payment, no
    // VerificationRecord — the disclosed schema-driven limitation). Otherwise: stateMachine
    // .isPermitted(WATCHING, SEEN) is asserted true by construction (not re-derived from a real
    // row — Decision 1/Finding 5), Payment.createSeen(...), repository.save(...), record a real
    // SEEN VerificationRecord (fromState = null — there is no real prior persisted row, mirrors
    // T10's own recordIgnored precedent for "no real prior state" rather than inventing a
    // WATCHING fromState nothing ever actually set), publish payment.seen. Returns the saved
    // Payment so ChainEventConsumer can call unregisterWatch with the right watchId afterward.

    @Transactional
    public void applyConfirmed(ChainTxConfirmed event);
    // findByChainAndTxHash; absent → WARN, return (Finding-1-style limitation, same reasoning).
    // Present: try markConfirmingIfCurrentState(id, SEEN, confirmations, now) first; 1 row →
    // record a real SEEN→CONFIRMING transition. 0 rows → try
    // refreshConfirmationsIfGreaterAndCurrentlyConfirming; 1 row → field-only refresh, NO
    // VerificationRecord (Finding 3). 0 rows from both → re-fetch the real current state,
    // recordIgnored (covers both "already CONFIRMING with a not-greater count" — R14's own
    // literal example — and "any other current state").

    @Transactional
    public void applyFinalized(ChainTxFinalized event);
    // findByChainAndTxHash; absent → WARN, return. Present: markFinalizedIfCurrentState(id,
    // amount, fromAddress, toAddress, confirmations, addressPoisoningFlag, now); 1 row → record a
    // real transition (fromState = the state read before the update attempt — SEEN or
    // CONFIRMING), publish payment.finalized. 0 rows → re-fetch current state, recordIgnored.
}
```

```java
// ChainEventConsumer.java
@Component
public class ChainEventConsumer {

    public ChainEventConsumer(ObjectMapper objectMapper, ProcessedEventRepository processedEventRepository,
            InvoiceService invoiceService, PaymentService paymentService, CryptoClient cryptoClient, Clock clock);

    @KafkaListener(topics = "chain.tx.seen", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onChainTxSeen(String rawJson) throws JsonProcessingException;
    // dedupe (processedEventRepository.insertIfNew(event.idempotencyKey(), now)) first; 0 rows →
    // return. invoiceService.findByWatchId(event.watchId()) — absent → WARN, return (an event for
    // a watch this service no longer recognizes at all; distinct from Finding 1's "invoice known
    // but not OPEN" case). paymentService.applySeen(invoice, event) → if present, call
    // cryptoClient.unregisterWatch(invoice.getWatchId()) LAST (Decision 4/Finding 6).

    @KafkaListener(topics = "chain.tx.confirmed", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onChainTxConfirmed(String rawJson) throws JsonProcessingException;
    // dedupe, then paymentService.applyConfirmed(event). No invoice lookup needed — correlation
    // is by (chain, txHash) via the already-created Payment row.

    @KafkaListener(topics = "chain.tx.finalized", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onChainTxFinalized(String rawJson) throws JsonProcessingException;
    // dedupe, then paymentService.applyFinalized(event).
}
```

```java
// ProcessedEvent.java — mirrors notification-service's own ProcessedEvent exactly, minus the
// event_type column this service's own table doesn't have.
@Entity @Table(name = "processed_events")
public class ProcessedEvent {
    public String getEventKey();
    public Instant getProcessedAt();
}
```

```java
// ProcessedEventRepository.java — mirrors notification-service's own exactly, one fewer bind
// parameter (no event_type column here).
interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    int insertIfNew(String eventKey, Instant processedAt);
    // INSERT INTO payments.processed_events (event_key, processed_at) VALUES (:eventKey,
    // :processedAt) ON CONFLICT (event_key) DO NOTHING
}
```

```java
// DTOs — Java records, field names matching the real schemas exactly (camelCase).
public record ChainTxSeen(String idempotencyKey, UUID watchId, UUID invoiceUuid, String chain,
        String txHash, String tokenContractAddress, int confirmations, Instant occurredAt) {}

public record ChainTxConfirmed(String idempotencyKey, UUID watchId, UUID invoiceUuid, String chain,
        String txHash, String tokenContractAddress, int confirmations, Instant occurredAt) {}

public record ChainTxFinalized(String idempotencyKey, UUID watchId, UUID invoiceUuid, String chain,
        String txHash, String fromAddress, String toAddress, String tokenContractAddress,
        BigDecimal amount, int confirmations, boolean addressPoisoningFlag, Instant occurredAt) {}
```

```java
// InvoiceRepository.java — one new method, package-private, called only by InvoiceService.
Optional<Invoice> findByWatchId(UUID watchId);
```

```java
// InvoiceService.java — one new public method (Finding 2's own cross-module API).
public Optional<Invoice> findByWatchId(UUID watchId);
```

## Private methods

- `ChainEventConsumer`: none beyond the 3 listener methods — each is small enough (dedupe, one
  lookup, one delegate call, one conditional `unregisterWatch`) that a private helper would be
  over-structuring.
- `PaymentService`: a single private `recordIgnoredForUnknownOrNonOpenInvoice`-style helper is
  tempting but each of the 3 call sites' own "nothing to attach a record to" case has a different
  enough shape (no invoice at all vs. invoice not `OPEN` vs. no payment row found) that inlining
  each is clearer than a shared helper with 3 branches.

## Entities used

`Payment` (new), `ProcessedEvent` (new), `Invoice` (read-only, via `InvoiceService`).

## Repositories used

`PaymentRepository` (new), `ProcessedEventRepository` (new), `InvoiceRepository` (extended,
accessed only through `InvoiceService`).

## Services used

`PaymentStateMachine` (T09), `VerificationRecordService` (T10), `CryptoClient` (T08),
`OutboxPublisher` (T04), `InvoiceService` (extended).

## Migrations

```sql
-- V7__payment_app_payments_table_write_grant.sql
-- T11: payment_app needs to create and repeatedly update Payment rows (chain.tx.seen creates;
-- chain.tx.confirmed/chain.tx.finalized update). T08's own V5 granted SELECT-only for the expiry
-- sweep's own read-only in-flight check; this widens it to the full read-write set this task's
-- own ChainEventConsumer needs. No DELETE - a Payment row is never deleted once created.
GRANT INSERT, UPDATE ON payments.payments TO payment_app;
```

```sql
-- V8__payment_app_processed_events_grant.sql
-- T11: mirrors notification-service's own identical, already-proven
-- V4__notification_app_processed_events_grant.sql exactly. Append-only ledger: INSERT + SELECT
-- only, no UPDATE/DELETE - a key is never revised or deleted once recorded.
GRANT INSERT, SELECT ON payments.processed_events TO payment_app;
```

## Unit/integration tests required

All plain JUnit + Mockito unless noted (no Kafka broker needed — listener methods are called
directly, mirroring `AuthEventConsumer`'s own unit-test precedent; T14 is where a real
Testcontainers+Kafka end-to-end proof belongs):

1. **`PaymentServiceTest`**: `shouldTransitionToSeenOnChainTxSeen`,
   `shouldEmitPaymentSeenOnFirstSeen` (verify `OutboxPublisher.publish` args),
   `shouldTransitionToConfirmingOnChainTxConfirmed`, a refresh test (AC4a, no
   `VerificationRecord`), a stale-by-count test (recordIgnored, AC4b),
   `shouldTransitionToFinalizedOnChainTxFinalized` (from both `SEEN` and `CONFIRMING`), a
   Finding-1 test (`applySeen` against a non-`OPEN` invoice creates nothing), a
   "payment not found" test each for `applyConfirmed`/`applyFinalized`.
2. **`ChainEventConsumerTest`**: `shouldDedupeDuplicateChainEventsByEventKey` (named — dedupe
   returns 0, nothing else is called), `shouldUnregisterWatchOnFirstSeenAndIgnoreSecondMatch`
   (named — verifies `unregisterWatch` called with the right `watchId`, called *after*
   `paymentService.applySeen`), a test proving `unregisterWatch` is *not* called when
   `applySeen` returns empty (Finding 1's own consumer-side consequence), an "unknown watchId"
   test (no invoice found at all).
3. **`PaymentTest`** (or folded into `PaymentServiceTest`): a reflection/source-text guard proving
   no code path ever persists `state = CREATED`/`WATCHING` (AC9, Finding 5).
4. **DTO deserialization tests** (3, Finding 7/AC12): one inline JSON literal per DTO, matching
   the real schema's own required+optional fields.
5. **`ArchitectureTest`** (modified): `FEATURE_MODULES` gains `"consumer"`; existing canaries
   re-run; a new source-text or dependency check that `consumer` never references
   `InvoiceRepository` (Finding 2/AC10) — simplest as a source-text test over
   `ChainEventConsumer.java`/`PaymentService.java` asserting neither imports
   `com.themistra.payment.invoice.InvoiceRepository`.

## Execution order

1. Migrations `V7`/`V8` (nothing else can be tested end-to-end without the real grants, though
   unit tests with mocked repositories don't strictly need them yet).
2. `InvoiceRepository.findByWatchId` + `InvoiceService.findByWatchId` (small, independent,
   unblocks everything else's own test fixtures).
3. `Payment.java`.
4. `PaymentRepository.java`.
5. `PaymentService.java` (depends on 3, 4, plus T09/T10/T04 already-built classes).
6. `ProcessedEvent.java` + `ProcessedEventRepository.java` (independent of 3-5).
7. The 3 DTOs (independent, needed by both `PaymentService`'s own method signatures and
   `ChainEventConsumer`).
8. `ChainEventConsumer.java` (depends on everything above).
9. `ArchitectureTest.java` modification.
10. Full `mvn -pl services/payment -am test` run (excluding the known-broken/unrunnable
    Testcontainers classes) to confirm the non-Docker suite is green and grew by exactly this
    task's own new tests.

Every file above traces to the frozen brief's own "Files to Create"/"Files to Modify" sections;
nothing added beyond them except the two atomic-update repository methods and the migrations'
own exact grant scope, both direct, necessary consequences of the frozen brief's own text, not
scope creep.
