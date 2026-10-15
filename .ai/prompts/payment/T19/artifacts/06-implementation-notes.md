# payment · T19 · Phase 6 — Implementation Notes

Consumes: `artifacts/05-implementation-plan.md`

## What changed

**New files:**
- `receipt/Receipt.java`/`ReceiptRepository.java` — the `receipts` table entity/repository.
- `receipt/ledger/LedgerEntry.java`/`LedgerEntryRepository.java`/`LedgerService.java` — the
  hash-chain entity, its repository (also hosting the advisory-lock native query), and the
  append service.
- `receipt/event/ReceiptIssuedPayload.java` — the `receipt.issued` outbox event body, fields
  frozen to `design.md`'s own pinned schema (Phase 4 Finding 1).
- `V9__payment_app_receipts_grant.sql` — `INSERT, SELECT` on `receipts` for `payment_app`.

**Modified:**
- `receipt/ReceiptService.java` — `issueIfEligible`'s own `Signed` branch restructured into a
  new private `persistSignedReceipt(...)`: `receiptStore.store(...)` first (outside any
  transaction), then one transaction inserting the `Receipt`, appending the ledger entry,
  transitioning the payment to `ATTESTED` (aborting on 0 rows, Finding 2), and publishing
  `receipt.issued` — exactly the frozen brief's own insert order (Finding 7). New constructor
  params: `ReceiptStore`, `ReceiptRepository`, `LedgerService`, `OutboxPublisher`. Class Javadoc
  extended with the S3-orphan-window disclosure (Finding 6).
- `receipt/ReceiptStore.java` — `toCanonicalMap` widened from `private` to `public static`
  (Finding 12), no behavior change.
- `payment/PaymentRepository.java`/`PaymentService.java` — new `markAttestedIfFinalized`,
  mirroring `markHeldIfFinalized`'s exact shape, also setting `attestedAt` (Finding 4).

**Mechanically fixed (not new test content, kept the module buildable/accurate):**
- `test/.../receipt/ReceiptServiceTest.java` — the 4 existing Signed-path tests needed the new
  constructor dependencies mocked and a `stubSuccessfulPersistence()` helper (mirrors T18's own
  Phase 10 precedent for the identical Mockito-strict-stubbing shape).
- `test/.../ArchitectureTest.java` — `FEATURE_MODULES` needed `"receipt"` added. Confirmed by a
  real run: `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` failed outright
  ("is annotated @Entity but does not reside in any module listed in FEATURE_MODULES") the moment
  `Receipt`/`LedgerEntry` existed — the rule's own fail-fast design working exactly as intended
  (not silently un-enforced).
- `test/.../consumer/ChainEventConsumerIdempotencyAndOrderingIntegrationTest.java` — its own
  narrow `@EntityScan` needed `com.themistra.payment.receipt` added; confirmed by a real context-
  load failure ("Not a managed type: class com.themistra.payment.receipt.Receipt") the moment
  `ReceiptService` (now depending on `ReceiptRepository`) was wired into that test's own
  deliberately-scoped context for the first time.
- `test/.../SchemaBaselineMigrationIntegrationTest.java` — `receipts` moved from
  `UNGRANTED_TABLES` to `GRANTED_TABLES` (it gets the identical `INSERT, SELECT` shape as
  `ledger_entries`/`verification_records`); the hardcoded Flyway-version-count assertion updated
  from 8 to 9.

## Deviations forced by reality

**`LedgerService.append(...)` needed `@Transactional` added — not in the Phase 5 plan's own
sketch, found only by real Testcontainers verification, not assumed.** Without it, each of the
three repository calls inside `append` (`acquireAppendLock`, `findTopByOrderBySeqDesc`, `save`)
ran in its own independently-auto-committed micro-transaction (Spring Data's default per-query-
method transaction wrapping) — `pg_advisory_xact_lock`'s own transaction-scoped release meant the
lock was acquired and immediately released again before the head was even read, giving AC4's own
concurrency guarantee zero actual protection. Added `@Transactional` (default `REQUIRED`
propagation — joins `ReceiptService`'s own already-open transaction in production use, or opens
its own when called directly).

**A genuine, only-partially-explained anomaly was found and investigated at length, but not
fully root-caused, while verifying AC4 with a real Testcontainers test (written, run, and
deleted before this commit — not part of the committed test suite):** calling
`ledgerService.append(...)` from a bare `java.util.concurrent.ExecutorService` background thread
(not a Spring-managed thread) caused Hibernate to issue a spurious `UPDATE ledger_entries SET ...
WHERE seq = ?` immediately after the correct `INSERT` within the same transaction — rejected by
`payment_app`'s own intentionally-INSERT-only grant (L7). The identical code, called directly on
the test's own main thread (including a "warm-up" call before the executor-thread call, ruling
out a one-time lazy-initialization race), never exhibited this — only one, zero, or several
calls after. Investigated: confirmed `@Transactional` was already in place when this was found
(not the missing-transactional-boundary bug above, a separate issue); confirmed it is not about
multiple concurrent threads specifically (reproduced with exactly one executor-thread call);
confirmed the actual hash-chain computation itself is correct (the genesis and chained-entry
cases, called directly, produced real, correct SHA-256 values verified against a real Postgres).
**Not resolved; disclosed as a carried-forward risk, not silently dropped** — the real production
concurrency scenario is two genuine Spring-managed Kafka-listener-container threads (not a bare
`ExecutorService`), a materially different call path this scratch harness did not represent;
proving AC4 under that specific, representative condition is flagged for Phase 10/a future task
to revisit with a real concurrent-Kafka-message test if it becomes a priority, rather than
asserted here on the strength of an inconclusive diagnostic.

## Mapping to the frozen brief / acceptance criteria

- **AC1** — `persistSignedReceipt` inserts `Receipt`, appends the `LedgerEntry`, transitions to
  `ATTESTED`, records a `VerificationRecord` — all in the one transaction.
- **AC2** — `LedgerService.append`'s own `entryHash = sha256(canonicalJson + prevHash)`; genesis
  `prevHash` = 64 zeros when `findTopByOrderBySeqDesc()` is empty. Verified against a real
  Postgres this phase (see above) — not just unit-asserted.
- **AC3** — `receipt.issued` published via `outboxPublisher.publish(...)` as the last statement
  inside the same transaction block.
- **AC4** — the advisory lock is acquired (now correctly, inside one `@Transactional` method);
  the deeper concurrency-under-load proof is the disclosed, not-fully-resolved risk above.
- **AC5** — no change to `ReceiptStore`'s own no-overwrite guarantee; only its canonicalizer's
  visibility changed.
- **AC6** — `markAttestedIfFinalized` returning `false` throws `IllegalStateException` inside the
  transaction (rolling back the `Receipt`/`LedgerEntry`/outbox rows already staged), caught by
  `persistSignedReceipt`'s own outer `catch (RuntimeException e)`, converted to
  `ReceiptIssueResult.Failed`.

## Verification performed this phase

- `mvn -o -pl services/payment -am compile`: clean.
- `mvn -o -pl services/payment -am test-compile`: one expected break
  (`ReceiptServiceTest`'s own constructor call), fixed mechanically (see above), then clean.
- A throwaway JUnit+Testcontainers smoke test (written, run, deleted before this commit) proved,
  against a real Postgres: a clean genesis entry (`prevHash` = 64 zeros, a real SHA-256
  `entryHash`), a second entry whose `prevHash` exactly equals the first's `entryHash`, and the
  anomaly described above under executor-thread invocation.
- `mvn -o -pl services/payment -am test` (full suite, Docker up —
  `services/auth/compose.local.yaml`): three real build/wiring breaks diagnosed and fixed this
  phase (`ArchitectureTest.FEATURE_MODULES`, the idempotency test's own `@EntityScan`, the
  `@JdbcTypeCode(SqlTypes.CHAR)` mapping for all three `CHAR(64)` hash columns — mirroring
  crypto-service's own already-solved `Attestation.receiptDigest` precedent, confirmed by reading
  that class directly rather than guessing). After all fixes: **386/386 passing**, unchanged from
  the T18 baseline — no new test content yet (`LedgerServiceTest`/`ReceiptServiceTest` additions
  are Phase 10's own job).
