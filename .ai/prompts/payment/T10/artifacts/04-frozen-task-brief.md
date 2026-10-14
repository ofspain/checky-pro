# payment · T10 · Phase 4 — Frozen Task Brief (Human Approval gate)

Consumes `artifacts/02-task-implementation-brief.md` and `artifacts/03-design-challenge.md`
(Kimi 2.7). All 5 findings are Low/Low-Medium severity implementation-detail refinements, none a
genuine architectural trade-off on the scale of T09's own `HELD`-edge decision — dispositioned
directly below, no `AskUserQuestion` pause needed this task.

## Finding 1 — `recordIgnored`'s self-transition shape is not self-describing

**ACCEPTED, option (b) — changed design.** `recordIgnored` now stores `fromState = NULL` (not
`currentState`), `toState = currentState`. No information is lost: `toState` alone already
carries "the state it remained in," so a redundant `fromState = toState` added nothing but
ambiguity. `from_state IS NULL` is now the unambiguous, schema-level signal for "no real
transition occurred" — simpler to query than `from_state = to_state` and matches why the column
is nullable in the first place.

## Finding 2 — Append-only guard covers only 2 of 3 new production files

**ACCEPTED and FIXED.** The source-text check (AC5) now scans all three new production files
(`VerificationRecord.java`, `VerificationRecordRepository.java`, `VerificationRecordService.java`),
not just two. A full ArchUnit rule was considered and rejected as disproportionate for this
severity — a source-text check is adequate and consistent with this codebase's own established
style for similar "nothing in this file does X" guards (e.g. `InvoiceExpirySweepJobTest`'s own
`sweepMethodIsNotAnnotatedTransactional`).

## Finding 3 — No test proves the JSONB `detail` mapping actually works

**ACCEPTED and FIXED (Medium — the most valuable finding this phase).** Adds
`VerificationRecordRepositoryIntegrationTest`, a new Testcontainers-backed test (mirroring
`InvoiceRepositoryIntegrationTest`'s own `TestConfig`/`@SpringBootTest` pattern) that saves a
`VerificationRecord` with a non-null `detail` map via a real `payment_app` connection and asserts
the retrieved row's `detail` round-trips byte-for-byte. Per this session's own established T08
lesson ("write the test anyway even if Docker is down — reasoning through it surfaces defects a
mocked test never would"), this test is written now and will be run the next time Docker (or
Kimi's own Docker-enabled environment) is available — same disclosed, carried-forward gap as
every prior Testcontainers-backed test this pipeline. **No `@Modifying` custom query method
exists on `VerificationRecordRepository`** (plain inherited `JpaRepository` methods only), so this
class is not at risk of the exact `TransactionRequiredException` bug T08's own
`InvoiceRepositoryIntegrationTest` has — `SimpleJpaRepository`'s own inherited `save`/`findById`
already carry their own transactional wrapper.

## Finding 4 — No stated policy on `triggerEventKey` shape validation

**ACCEPTED, documented, no code change.** `triggerEventKey` is explicitly, intentionally an
unvalidated passthrough — this service trusts its caller completely (mirrors the Scope/Out
decision that it also never second-guesses the `(fromState, toState)` pair against
`PaymentStateMachine`). Adding format validation now, with no real producer of this key existing
yet (task 11's own scope), would be speculative. If task 11 later needs format validation, that
is the natural place to add it, against a real key-construction call site.

## Finding 5 — `FEATURE_MODULES`'s own Javadoc will go stale

**ACCEPTED and FIXED.** The Javadoc comment is updated in the same edit that adds
`"verification"` to the list, naming all four modules and this task's own addition explicitly.

---

## Frozen Brief

## Task

Add `VerificationRecord` (append-only) and record every transition, reversal, and ignored stale
event with the trigger event key (R11, R12, R14).

## Purpose

The durable, append-only audit trail for every payment-state-movement decision this service ever
makes, so an auditor or a future debugging session can reconstruct exactly why a payment is in
its current state.

## Scope

**In:**
- `VerificationRecord` entity, mapping `verification_records` exactly (`paymentId` as a plain
  `Long`; `detail` via Hibernate 6's native `@JdbcTypeCode(SqlTypes.JSON)`, `Map<String, Object>`).
- `VerificationRecordRepository` (plain `JpaRepository`, no custom query).
- `VerificationRecordService`: `record(...)` (transitions and reversals alike) and
  `recordIgnored(...)` — **stores `fromState = NULL`** (Finding 1's own decided change), not
  `currentState`.
- `VerificationRecordRepositoryIntegrationTest` (new, Finding 3) proving the JSONB mapping.
- `ArchitectureTest.FEATURE_MODULES` gains `"verification"`, with its own Javadoc updated to list
  all four modules (Finding 5).

**Out:**
- `Payment`/`PaymentRepository`/`PaymentService` — task 11.
- `ChainEventConsumer`, `processed_events`, staleness detection — task 11.
- Any real call site for `record(...)`/`recordIgnored(...)` — tasks 11/12.
- Validating `(fromState, toState)` against `PaymentStateMachine` — never this service's job.
- Validating `triggerEventKey`'s own shape (Finding 4) — an unvalidated passthrough, by design.

## Business Rules

- R11 — recording half only: `CONFIRMING → SEEN` reversal, with trigger event key.
- R12 — recording half only: `SEEN → WATCHING` reversal, with trigger event key.
- R14 — recording half only: an ignored event recorded with `fromState = NULL`,
  `toState = currentState`, no state change implied.

## Locked Decisions

- **L1** — `fromState`/`toState` are `PaymentState`, `@Enumerated(STRING)`.
- **L5** — `triggerEventKey` carries the deterministic key; no dedupe here.
- **L7** — append-only; existing `INSERT`+`SELECT`-only grant; no update/delete anywhere in this
  task's own production code (now checked across all three new files, Finding 2).
- **L11** — a reversal is a normal row, not an error case.
- **L12** — `verification` imports no entity from any other feature package.

## Dependencies

- `PaymentState` (T09).
- `Clock` (`ClockConfig`, T04).
- Hibernate 6's native `@JdbcTypeCode(SqlTypes.JSON)` — no new Maven dependency.

## Inputs

`record(Long paymentId, PaymentState fromState, PaymentState toState, String triggerEventKey,
Integer confirmations, Map<String, Object> detail)`; `recordIgnored(Long paymentId, PaymentState
currentState, String triggerEventKey, Map<String, Object> detail)`.

## Outputs

Both return the persisted `VerificationRecord`.

## State Changes

A new `verification_records` row per call. Never an update or delete.

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecord.java`
- `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecordRepository.java`
- `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecordService.java`
- `services/payment/src/test/java/com/themistra/payment/verification/VerificationRecordServiceTest.java`
- `services/payment/src/test/java/com/themistra/payment/verification/VerificationRecordRepositoryIntegrationTest.java`

## Files to Modify

- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` — add
  `"verification"` to `FEATURE_MODULES`, update its own Javadoc.

## Files NOT to Modify

- `V1__payments_baseline.sql` / `V2__payment_app_role_and_grants.sql` (immutable; grant already
  sufficient).
- Any file under `spec/`.
- `invoice`/`payment`/`events` packages (read-only reference to `PaymentState` only).

## Acceptance Criteria

- **AC1 (R11, named)** — `record(paymentId, CONFIRMING, SEEN, triggerEventKey, null, null)`
  persists correctly.
- **AC2 (R12, named)** — `record(paymentId, SEEN, WATCHING, triggerEventKey, null, null)`
  persists correctly.
- **AC3 (R14, named, revised per Finding 1)** — `recordIgnored(paymentId, currentState,
  triggerEventKey, detail)` persists a row with `fromState == null`, `toState == currentState`.
- **AC4 (general "every transition")** — an arbitrary forward pair persists correctly too.
- **AC5 (L7, Finding 2 — widened)** — no `delete`/`deleteById`/`deleteAll` call anywhere across
  all three new production files (source-text check, not a false structural "no delete method"
  claim).
- **AC6 (L12, Finding 5)** — `verification` is in `FEATURE_MODULES`; all 3 existing ArchUnit
  canaries still pass; the Javadoc is accurate.
- **AC7 (defensive)** — `NullPointerException` for a null `paymentId` or null
  `toState`/`currentState` only.
- **AC8 (Finding 3, new)** — the JSONB `detail` mapping round-trips a real `Map<String, Object>`
  through a real Postgres connection (Testcontainers; cannot run locally this session, Docker
  unavailable — disclosed, carried-forward, same as every prior Testcontainers-backed test).

## Required Tests

- `shouldWalkBackwardConfirmingToSeenOnReorg` (named, R11).
- `shouldWalkBackwardSeenToWatchingOnReorgBeforeConfirm` (named, R12).
- `shouldIgnoreOutOfOrderOrStaleChainEvents` (named, R14) — now asserting `fromState == null`.
- One forward-transition test (AC4).
- A source-text test over all three production files (AC5).
- `ArchitectureTest`'s own 3 canaries, re-run (AC6).
- Null-argument tests for the two `NOT NULL` parameters on each method (AC7).
- `VerificationRecordRepositoryIntegrationTest` — a real JSONB round-trip (AC8).

## Constraints

- **Transactions**: `record`/`recordIgnored` are `@Transactional` (default `REQUIRED`
  propagation).
- **Module boundaries (L12)**: `verification` imports only `PaymentState` across feature
  boundaries.
- **Null handling (AC7)**: only `paymentId` and `toState`/`currentState` are validated.
- **No second enforcement of L1**: never references `PaymentStateMachine`.
- **`triggerEventKey` is an unvalidated passthrough** (Finding 4) — intentional, not an
  oversight.
- **Append-only (L7)**: no update/delete anywhere in this task's own production code.

## Open Questions

No blockers. All 5 Phase 3 findings dispositioned above.
