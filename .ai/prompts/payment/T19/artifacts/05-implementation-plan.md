# payment · T19 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md` (FROZEN)

No code in this artifact — planning only.

## Files to create

1. `services/payment/src/main/java/com/themistra/payment/receipt/Receipt.java`
2. `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptRepository.java`
3. `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerEntry.java`
4. `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerEntryRepository.java`
5. `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerService.java`
6. `services/payment/src/main/java/com/themistra/payment/receipt/event/ReceiptIssuedPayload.java`
7. `services/payment/src/main/resources/db/migration/V9__payment_app_receipts_grant.sql`

## Files to modify

1. `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptService.java`
2. `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStore.java` (Finding
   12 — `toCanonicalMap` widened to `public static`, no other change)
3. `services/payment/src/main/java/com/themistra/payment/payment/PaymentRepository.java`
4. `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java`

## Public types/methods (signatures)

**`Receipt`** (entity, `receipt` package, mirrors `VerificationRecord`'s own shape exactly —
protected no-arg constructor, static `create` factory, getters only, explicit `Clock`-derived
timestamp):
```java
@Entity
@Table(name = "receipts")
public class Receipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "receipt_uuid", nullable = false, unique = true) private UUID receiptUuid;
    @Column(name = "payment_id", nullable = false, unique = true) private Long paymentId;
    @Column(name = "digest_sha256", nullable = false, length = 64) private String digestSha256;
    @Column(name = "signature", nullable = false) private String signature;
    @Column(name = "kms_key_id", nullable = false, length = 256) private String kmsKeyId;
    @Column(name = "s3_bucket", nullable = false, length = 128) private String s3Bucket;
    @Column(name = "s3_key", nullable = false, length = 256) private String s3Key;
    @Column(name = "signed_at", nullable = false) private Instant signedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static Receipt create(UUID receiptUuid, Long paymentId, String digestSha256,
            String signature, String kmsKeyId, String s3Bucket, String s3Key,
            Instant signedAt, Instant createdAt) { ... }
    // getters only, no setters (mirrors VerificationRecord)
}
```
`paymentId` is a scalar `Long`, never `@ManyToOne` to `Payment` (Finding 10, L12).

**`ReceiptRepository`** (package-private, mirrors every other repository's own visibility):
```java
interface ReceiptRepository extends JpaRepository<Receipt, Long> {
}
```
No custom query needed — `save(...)` alone is sufficient for this task's own scope.

**`LedgerEntry`** (entity, `receipt.ledger` package, Finding 11 — PK is `seq`):
```java
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "seq") private Long seq;
    @Column(name = "receipt_id", nullable = false, unique = true) private Long receiptId;
    @Column(name = "prev_hash", nullable = false, length = 64) private String prevHash;
    @Column(name = "entry_hash", nullable = false, unique = true, length = 64) private String entryHash;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "payload", nullable = false) private Map<String, Object> payload;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static LedgerEntry create(Long receiptId, String prevHash, String entryHash,
            Map<String, Object> payload, Instant createdAt) { ... }
    // getters only
}
```
`receiptId` is a scalar `Long`, never `@ManyToOne` to `Receipt` (Finding 10, L12).

**`LedgerEntryRepository`** (package-private, hosts the advisory-lock native query — Finding 3):
```java
interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Optional<LedgerEntry> findTopByOrderBySeqDesc();

    /** Serializes every ledger append through one critical section (Phase 3/4, Kimi Finding 3)
     * - a fixed, namespaced advisory-lock key, not derived from any row, so it protects the
     * genesis case too (no row exists yet for the very first append). Held until the enclosing
     * transaction commits or rolls back ({@code pg_advisory_xact_lock}, not the session-scoped
     * {@code pg_advisory_lock}). */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext('themistra.payment.ledger.append')::bigint)",
            nativeQuery = true)
    void acquireAppendLock();
}
```

**`LedgerService`** (new `@Component`, `receipt.ledger` package, frozen signature from Phase 4):
```java
@Component
public class LedgerService {
    private static final String GENESIS_PREV_HASH = "0".repeat(64);

    public LedgerService(LedgerEntryRepository repository, Clock clock) { ... }

    public LedgerEntry append(Long receiptId, Map<String, Object> payload) {
        repository.acquireAppendLock();   // (1) first statement, Finding 3
        String prevHash = repository.findTopByOrderBySeqDesc()
                .map(LedgerEntry::getEntryHash)
                .orElse(GENESIS_PREV_HASH);
        String canonicalJson = CanonicalJson.serialize(payload);
        String entryHash = sha256(canonicalJson + prevHash);
        return repository.save(LedgerEntry.create(receiptId, prevHash, entryHash, payload, clock.instant()));
    }

    private static String sha256(String input) { ... }  // mirrors ReceiptDigest's own private helper
}
```

**`ReceiptIssuedPayload`** (record, `receipt.event` package — frozen fields, Phase 4):
```java
public record ReceiptIssuedPayload(
        UUID invoiceUuid, UUID paymentUuid, UUID receiptUuid, UUID merchantAccountUuid,
        String chain, String txHash, String amount, String tokenContractAddress,
        String kmsKeyId, String receiptUri, Instant occurredAt) {}
```

**`ReceiptStore`** (one-line change, Finding 12):
```java
public static Map<String, Object> toCanonicalMap(ReceiptPayload payload, AttestationResult.Signed signed)
```
(widened from `private` to `public static`; body unchanged.)

**`PaymentRepository`** (new method, mirrors `markHeldIfFinalized`'s exact shape, Findings 2/4):
```java
@Modifying
@Query("UPDATE Payment p SET p.state = :newState, p.attestedAt = :now, p.updatedAt = :now "
       + "WHERE p.id = :id AND p.state = :expectedCurrentState")
int markAttestedIfFinalized(@Param("id") Long id,
        @Param("expectedCurrentState") PaymentState expectedCurrentState,
        @Param("newState") PaymentState newState, @Param("now") Instant now);
```

**`PaymentService`** (new method):
```java
public boolean markAttestedIfFinalized(Long paymentId, String triggerEventKey, UUID receiptUuid, Instant now)
```
Calls `repository.markAttestedIfFinalized(paymentId, PaymentState.FINALIZED, PaymentState.ATTESTED, now)`;
on success (`> 0`), calls `verificationRecordService.record(paymentId, PaymentState.FINALIZED,
PaymentState.ATTESTED, triggerEventKey, null, Map.of("receiptUuid", receiptUuid.toString()))`
(Finding 13).

**`ReceiptService`** (constructor gains `ReceiptStore`/`ReceiptRepository`/`LedgerService`;
`issueIfEligible`'s `Signed` branch restructured per the frozen brief's own illustrative sketch
— exact final shape at Phase 6):
```java
public ReceiptService(PaymentService paymentService, InvoiceService invoiceService,
                       CryptoClient cryptoClient, ReceiptStore receiptStore,
                       ReceiptRepository receiptRepository, LedgerService ledgerService,
                       PlatformTransactionManager transactionManager, Clock clock)
```

## Private methods

- `ReceiptService`: a private `persistSignedReceipt(PaymentReceiptView payment,
  InvoiceReceiptView invoice, ReceiptPayload payload, String digest, UUID receiptUuid,
  AttestationResult.Signed signed, String triggerEventKey)` → `ReceiptIssueResult` — the whole
  Finding 7 insert-ordered sequence (store, then one transaction: lock, `Receipt`, `LedgerEntry`,
  `ATTESTED`-or-abort, `VerificationRecord`, publish), wrapped in the Finding 2/6 try/catch
  converting any failure to `ReceiptIssueResult.Failed`.
- `ReceiptService`: a private `buildLedgerPayload(ReceiptPayload payload,
  AttestationResult.Signed signed, ReceiptStorageLocation location)` → `Map<String,Object>` —
  `ReceiptStore.toCanonicalMap(payload, signed)` plus `s3Bucket`/`s3KeyPrefix` (Finding 5).
- `ReceiptService`: a private `receiptUri(ReceiptStorageLocation location)` → `String` —
  `"s3://" + location.bucket() + "/" + location.s3KeyPrefix() + ".json"`.
- `LedgerService`: a private `sha256(String)` — mirrors `ReceiptDigest`'s own identical
  `MessageDigest`/`HexFormat` helper (duplicated, not shared — the two classes' own inputs
  differ enough, and `ReceiptDigest`'s own helper is `private`, same cross-package-access
  reasoning as Finding 12, not worth a second visibility change for a 6-line method).

## Entities used

`Receipt`/`LedgerEntry` (new, this task's own module). `Payment`/`Invoice` still never imported
directly by `receipt`/`receipt.ledger` — `ReceiptService` only ever sees `PaymentReceiptView`/
`InvoiceReceiptView` (T17, unchanged).

## Repositories used

`ReceiptRepository`/`LedgerEntryRepository` (new, via `ReceiptService`/`LedgerService`
respectively, never directly from a controller). `PaymentRepository` (via `PaymentService`).

## Services used

`PaymentService`, `InvoiceService`, `CryptoClient`, `ReceiptStore` (T18, now has its first real
caller), `LedgerService` (new), `VerificationRecordService` (transitively, via
`PaymentService.markAttestedIfFinalized`), `OutboxPublisher` (new direct dependency for
`ReceiptService` — confirmed not previously injected there, since T17 never published anything).

## Migration

`V9__payment_app_receipts_grant.sql` (Finding 8, renamed from the Phase 2 TIB's own sketch):
```sql
-- payment_app needs INSERT+SELECT on receipts for the first time this task (T19) - the first
-- real caller of the Receipt entity. ledger_entries already has this same grant (V2) since it
-- was scoped correctly from the start, even though nothing used it until now.
GRANT INSERT, SELECT ON payments.receipts TO payment_app;
```

## Unit/integration tests required

**`LedgerServiceTest`** — likely a **real Testcontainers test**, not a mocked-repository unit
test, since AC4 (the concurrency guarantee) cannot be meaningfully proven against a mock:
1. `shouldAppendLedgerEntryWithPrevHashChainingToHead` (named) — insert a genesis entry
   (`prevHash` = 64 zeros), then a second entry, assert the second's `prevHash` equals the
   first's `entryHash`.
2. `shouldComputeEntryHashAsShaTwoFiftySixOfCanonicalPayloadConcatenatedWithPrevHash` — an
   independent `MessageDigest` call ties the real `entryHash` to its own expected value (mirrors
   T16's own Phase 11 golden-digest-tie test precedent).
3. A genuine two-connection concurrency test proving the advisory lock serializes two concurrent
   `append(...)` calls for different `receiptId`s into a single, unbroken chain (no fork) —
   the real test of AC4.

**`ReceiptServiceTest`** (existing file, T17, 16 methods — extended):
4. `shouldPersistReceiptLedgerEntryAndAttestedTransitionOnSignedResult` —
   `shouldTransitionToAttestedAfterReceiptStoredAndLedgerAppended` (named, R24).
5. `shouldEmitReceiptIssuedAfterAttestation` (named, R25) — exact payload field assertions
   against the frozen 11-field shape.
6. `shouldAbortAndReturnFailedWhenMarkAttestedIfFinalizedReturnsZeroRows` (Finding 2, AC6).
7. `shouldReturnFailedWhenReceiptStoreThrows` / `...WhenLedgerServiceThrows` (Finding 6 — the
   orphan-S3 disclosure's own regression coverage, proving the method fails safely even though
   it can't undo an already-written S3 object).

**`PaymentServiceTest`/`PaymentRepository` test coverage**: new tests for
`markAttestedIfFinalized`, mirroring `markHeldIfFinalized`'s own existing test shapes exactly,
plus a dedicated `attested_at`-is-set assertion (Finding 4).

**A real-grant proof** (mirrors T06's/T08's own precedent) — extend
`SchemaBaselineMigrationIntegrationTest` with `receipts` moving from the ungranted set to the
granted set, `INSERT, SELECT` only, same shape as every other table's own entry there.

## Execution order

1. `Receipt`/`ReceiptRepository` — no dependency.
2. `LedgerEntry`/`LedgerEntryRepository`/`LedgerService` — no dependency on 1 (receives
   `receiptId` as a plain `Long` parameter).
3. `V9` migration — must land before any test touching `receipts`/`ledger_entries` via a real
   `payment_app` connection runs.
4. `ReceiptStore.toCanonicalMap` visibility widening — trivial, no dependency.
5. `PaymentRepository.markAttestedIfFinalized`/`PaymentService.markAttestedIfFinalized` —
   depends on nothing new.
6. `ReceiptIssuedPayload` — no dependency, can be done any time.
7. `ReceiptService`'s restructuring — depends on 1, 2, 4, 5, 6, plus the already-existing
   `ReceiptStore`/`OutboxPublisher`.
8. All test files (deferred to Phase 10, per the standard rule).

## Traceability check

Every file above traces directly to the frozen brief's own "Files to Create"/"Files to Modify"
lists (Phase 4). No file is added that the brief does not authorize.
