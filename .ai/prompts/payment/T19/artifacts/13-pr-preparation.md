# payment · T19 · Phase 13 — PR / Commit Preparation

Consumes: `artifacts/12-specification-verification.md` (**PASS**)

No code in this artifact — documentation only, per this phase's own instructions.

**Branching note:** same established practice as every prior task this session — executed
directly on the long-running `spec/service-specs-and-ai-framework` branch, not a fresh per-task
branch off `main`. `main` itself was never touched and stays deployable.

## Commit title

`payment T19: hash-chain ledger, ATTESTED transition, receipt.issued (R21/R23-R25)`

## Commit message

```
payment T19: hash-chain ledger, ATTESTED transition, receipt.issued (R21/R23-R25)

Wires T15-T18 together end to end for the first time. ReceiptService.issueIfEligible's own
Signed branch now persists in full: ReceiptStore.store(...) writes the signed JSON/PDF to S3
(outside any transaction, an outbound I/O call), then one single transaction inserts the
Receipt row, appends the hash-chain ledger entry (LedgerService.append - entry_hash =
SHA-256(canonical payload || prev_hash), genesis prev_hash = 64 zeros), transitions the payment
to ATTESTED (aborting the whole transaction if the payment is no longer FINALIZED), and
publishes receipt.issued - all atomically, per R24/R25's own "same transaction" text.

Kimi's own Phase 3 design challenge caught two things my own Phase 0-2 grounding missed
entirely: design.md pins an exact 11-field JSON Schema for receipt.issued (now frozen into
ReceiptIssuedPayload field-for-field), and the spec's own package map nests ledger/ and event/
under receipt/, not as top-level siblings. Both self-corrected and disclosed at Phase 4 rather
than silently fixed. The ledger-append concurrency race (two payments racing on the chain head)
is closed with a Postgres advisory transaction lock, encapsulated inside LedgerService.append
itself (a deliberate, documented deviation from the frozen brief's own illustrative "lock first"
sketch - pg_advisory_xact_lock releases only at the enclosing transaction's commit/rollback
regardless of where in the transaction it's acquired, so the exact placement has zero functional
effect either way).

The single most significant finding this task produced: a genuine, severe production defect
found and fixed while writing the Phase 11 test suite, not a theoretical risk. LedgerEntry's
JSONB payload field, mapped via Hibernate's @JdbcTypeCode(SqlTypes.JSON), made Hibernate's own
dirty-checking round-trip every value through JSON to take its "last known state" snapshot - a
boxed Short (tokenDecimals, always present in the real ledger payload via
ReceiptStore.toCanonicalMap) survives serialization but comes back as a different boxed numeric
type on that round-trip, failing equals() and causing Hibernate to spuriously re-UPDATE the row
immediately after every correct INSERT, inside the same transaction. payment_app has no UPDATE
grant on ledger_entries (L7, by design, append-only) - so this would have failed every single
real production ledger append. Confirmed via a controlled before/after experiment (a String
substitute for the one Short field passes; the real value reproduces the crash deterministically
every time). Fixed with @Immutable on LedgerEntry, which is also the semantically correct
annotation regardless of the bug: L7 itself says no UPDATE/DELETE ever. This retroactively
explains an earlier, unroot-caused "bare ExecutorService thread" anomaly from Phase 6/7's own
self-review - that scratch harness's own payload also contained a boxed Long; thread context was
always a red herring.

Self-review (Phase 7) found and fixed one further issue before Kimi's independent review ran:
LedgerService.append had no transaction boundary of its own, so the advisory lock was acquired
and immediately released again before the head was even read, giving the concurrency guarantee
zero actual protection - fixed with @Transactional. Independent review (Phase 8) added a cheap,
real fix (ReceiptStore.toCanonicalMap now returns an unmodifiable map) and correctly identified,
with corrected reasoning, that issueIfEligible is not safe to retry for the same payment (a
Javadoc warning, not a new guard - receipts.payment_id's own UNIQUE constraint already prevents
a second recorded receipt structurally). Two Phase 8 findings were rejected with direct evidence:
one directly contradicted Kimi's own Phase 3 recommendation of the exact same advisory-lock
expression one phase earlier; the other's underlying concern about lock-acquisition order didn't
survive checking how pg_advisory_xact_lock actually releases.

AC4's full concurrency guarantee under genuine Spring-managed concurrent load is deliberately
argued from pg_advisory_xact_lock's own documented, transaction-scoped Postgres semantics rather
than re-proven under synthetic load in this test suite - an explicit Phase 9/10 testing-strategy
decision, not a silent gap: a genuinely representative proof needs two real Kafka listener
threads, which belongs with task 26's own end-to-end test.

LedgerServiceTest (new, real Testcontainers): 7/7, including the real 17-field payload test that
surfaced the severe bug above, a second golden-hash tie test for a non-genesis entry, and a
reflection-based pin on the advisory-lock SQL expression. ReceiptServiceTest +5 (the two
remaining named tests, the abort-on-zero-rows guard, and both orphan-window failure directions).
PaymentServiceTest +3 and a new PaymentRepositoryIntegrationTest (+2, a real proof that
markAttestedIfFinalized's own JPQL sets state and attested_at correctly). A new
ReceiptRepositoryIntegrationTest (+1) and ReceiptIssuedPayloadSerializationTest (+1).
SchemaBaselineMigrationIntegrationTest's own functional grant-proof extended to receipts for
real, not just via information_schema.

Full suite: 405/405 passing (386 pre-task baseline + 19 new).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Production code:**
- `services/payment/src/main/java/com/themistra/payment/receipt/Receipt.java` (new).
- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptRepository.java` (new).
- `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerEntry.java` (new,
  `@Immutable` added at Phase 11 — the severe-bug fix).
- `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerEntryRepository.java` (new).
- `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerService.java` (new).
- `services/payment/src/main/java/com/themistra/payment/receipt/event/ReceiptIssuedPayload.java` (new).
- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptService.java` — `Signed`
  branch restructured; new constructor dependencies; class Javadoc extended.
- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStore.java` —
  `toCanonicalMap` widened to `public static`, returns an unmodifiable map.
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentRepository.java` — new
  `markAttestedIfFinalized`.
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java` — new
  `markAttestedIfFinalized`.
- `services/payment/src/main/java/com/themistra/payment/common/config/ReceiptProperties.java` —
  `@Size(max = 219)` on `prefix`.
- `services/payment/src/main/resources/db/migration/V9__payment_app_receipts_grant.sql` (new).

**Tests:**
- `services/payment/src/test/java/com/themistra/payment/receipt/ledger/LedgerServiceTest.java`
  (new) — 7 tests.
- `services/payment/src/test/java/com/themistra/payment/receipt/ReceiptServiceTest.java` — 5 new
  tests.
- `services/payment/src/test/java/com/themistra/payment/payment/PaymentServiceTest.java` — 3 new
  tests.
- `services/payment/src/test/java/com/themistra/payment/payment/PaymentRepositoryIntegrationTest.java`
  (new) — 2 tests.
- `services/payment/src/test/java/com/themistra/payment/receipt/ReceiptRepositoryIntegrationTest.java`
  (new) — 1 test.
- `services/payment/src/test/java/com/themistra/payment/receipt/event/ReceiptIssuedPayloadSerializationTest.java`
  (new) — 1 test.
- `services/payment/src/test/java/com/themistra/payment/SchemaBaselineMigrationIntegrationTest.java`
  — `receipts` moved from `UNGRANTED_TABLES` to `GRANTED_TABLES`, Flyway version count 8→9, the
  functional grant-proof test extended and renamed.
- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` —
  `FEATURE_MODULES` gained `"receipt"`.
- `services/payment/src/test/java/com/themistra/payment/consumer/ChainEventConsumerIdempotencyAndOrderingIntegrationTest.java`
  — `@EntityScan` gained `com.themistra.payment.receipt`.

## Summary

T19 is where the attestation flow's pieces, built in isolation since T15, finally run together
for the first time — a payment can now genuinely reach `ATTESTED`. The hardest design question,
surfaced by Kimi's own Phase 3 review before any code existed, was a schema mismatch (a single
`s3_key` column for two S3 objects) and a package-map correction I'd gotten wrong at Phase 2. But
the single most consequential thing this task produced was found by its own test-writing work,
not by review: a genuine, severe Hibernate/JSON dirty-checking defect that would have broken
every real production attestation, caught only because Phase 11 insisted on testing the real
17-field payload shape instead of a minimal stand-in. That is this task's own clearest argument
for why "write the real test, not the simplest one that compiles" matters — a lesson this
pipeline has now demonstrated concretely more than once.

## Testing performed

- `mvn -o -pl services/payment -am compile`, `test-compile`: clean throughout every phase (after
  diagnosing and fixing three real build/wiring breaks at Phase 6: `ArchitectureTest.FEATURE_MODULES`,
  the idempotency test's own `@EntityScan`, and `@JdbcTypeCode(SqlTypes.CHAR)` on all three new
  `CHAR(64)` hash columns).
- `mvn -o -pl services/payment -am test -Dtest=LedgerServiceTest`: **7/7 passing**, real
  Testcontainers Postgres.
- `mvn -o -pl services/payment -am test -Dtest=ReceiptServiceTest`: **21/21 passing**.
- `mvn -o -pl services/payment -am test -Dtest=PaymentRepositoryIntegrationTest,ReceiptRepositoryIntegrationTest`:
  **3/3 passing**, real Testcontainers Postgres.
- Multiple mutation-verification passes across Phases 10-11 (`entry_hash` field order, the
  abort-on-zero guard, the `V9` grant, the `@Immutable` fix itself via a direct controlled
  before/after experiment) — each confirmed red/crashing before the real fix, green/clean after.
- `mvn -o -pl services/payment -am test` (full suite, Docker up —
  `services/auth/compose.local.yaml`): **405/405 passing** (386 pre-task baseline + 19 new).

## Specification references

- **Task:** T19 — Hash-chain ledger (`spec/payment-service/tasks.md`).
- **Requirement IDs:** R19 (fully assembled), R21, R23 (fully assembled), R24, R25.
- **LOCKED decisions:** L2 (the three durable steps now genuinely atomic together for the first
  time), L7 (append-only — now enforced at the Hibernate level too via `@Immutable`, not just the
  DB grant), L12 (continuity — `ArchitectureTest.FEATURE_MODULES` extended, confirmed enforced by
  its own fail-fast design catching the new entities before this task's own Phase 6 fix).
- **Process decisions (not numbered requirements):** the advisory-lock concurrency mechanism and
  the 17-field ledger payload shape, both proposed directly and approved via `AskUserQuestion` at
  Phase 4; AC4's testing-strategy scope boundary (argue from Postgres semantics, defer the fully
  representative proof to task 26), decided explicitly at Phase 9/10.
