# payment · T10 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T10 — Verification record |
| **Consumes** | `artifacts/11-test-review-resolution.md` |

## Verdict: **PASS**

## Traceability matrix

| AC | Requirement | Implementation | Test coverage |
|---|---|---|---|
| AC1 | `CONFIRMING → SEEN` reversal recorded (R11, named) | `VerificationRecordService.record` | `shouldWalkBackwardConfirmingToSeenOnReorg`, `verify(repository).save(...)` added (Phase 11 Gap 3) |
| AC2 | `SEEN → WATCHING` reversal recorded (R12, named) | same | `shouldWalkBackwardSeenToWatchingOnReorgBeforeConfirm` |
| AC3 | Ignored stale event recorded with `fromState = null` (R14, named, Phase 3/4 Finding 1) | `VerificationRecordService.recordIgnored` | `shouldIgnoreOutOfOrderOrStaleChainEvents`; real-DB counterpart `shouldPersistRecordWithNullDetailAndNullFromState` (Testcontainers, unrun this session) |
| AC4 | An arbitrary forward transition recorded identically | `record` makes no forward/reversal distinction | `shouldRecordAnOrdinaryForwardTransition`, `shouldRecordTransitionWithNullFromState` (Phase 11 Gap 5), `shouldPersistConfirmationCountWhenProvided` |
| AC5 | Append-only — no delete/update anywhere in this task's own code (L7) | no such call exists | `verificationPackageProductionFilesShouldNeverCallDeleteOrUpdate`, widened to all 3 files (Phase 3 Finding 2), mutation-verified |
| AC6 | `verification` in `ArchitectureTest.FEATURE_MODULES` (L12) | `FEATURE_MODULES` updated, Javadoc corrected (Phase 3 Finding 5) | `ArchitectureTest`'s own 3 pre-existing canaries, re-run clean |
| AC7 | Null handling exactly matches real `NOT NULL` columns | `Objects.requireNonNull` on `paymentId`/`toState` only | 4 null-argument tests, mutation-verified; `shouldRecordTransitionWithNullFromState` proves `fromState` is correctly *not* required |
| AC8 | JSONB `detail` round-trips (Phase 3/4 Finding 3) | `@JdbcTypeCode(SqlTypes.JSON)`, `Map<String, Object>` | `VerificationRecordRepositoryIntegrationTest` (2 tests, Testcontainers, unrun this session — Docker unavailable) |
| — (Phase 8 Finding 1) | `detail` must not be aliasable | `VerificationRecord.create`/`getDetail()` both defensively copy | `shouldNotBeAffectedByCallerMutatingTheDetailMapAfterRecording`, `getDetailShouldReturnAnUnmodifiableMap` (Phase 11 Gap 1), both mutation-verified |

## Required Tests (frozen brief)

All named/implied tests present: 3 named tests, 1 general-transition test, append-only guard
(all 3 files), null-argument tests, the `ArchitectureTest` regression check, and the JSONB
round-trip integration test. 13 tests in `VerificationRecordServiceTest`, 2 in
`VerificationRecordRepositoryIntegrationTest` — 15 net-new tests, all passing where runnable
locally.

## Scope discipline

`git diff --stat 37ac60e1..HEAD -- services/payment` (T09's own final commit to this task's own
HEAD) touches exactly 6 files — all named in the frozen brief's own Files to Create/Modify list,
zero deviation: `VerificationRecord.java`, `VerificationRecordRepository.java`,
`VerificationRecordService.java` (created), `VerificationRecordServiceTest.java`,
`VerificationRecordRepositoryIntegrationTest.java` (created), and `ArchitectureTest.java`
(modified, as planned). No migration touched (the existing T02 grant was sufficient, confirmed).
Nothing under `spec/`. No `Payment` entity/repository/service built (confirmed out of scope,
held throughout).

## Locked decisions

- **L1** — `fromState`/`toState` are `PaymentState`, `@Enumerated(STRING)`. Held.
- **L5** — `triggerEventKey` carries the deterministic key, unvalidated passthrough by design
  (Phase 4 Finding 4). Held.
- **L7** — append-only; `payment_app`'s existing `INSERT`+`SELECT`-only grant untouched; zero
  delete/update calls anywhere in this task's own code. Held, both by direct code read and by
  the widened source-text guard (Phase 3 Finding 2).
- **L11** — a reversal recorded as a normal row, not an error case. Held.
- **L12** — `verification` imports no entity from any other feature package; `PaymentState` (an
  enum) is the only cross-feature import. Held, confirmed both by direct import-list reads and
  by `ArchitectureTest`'s own 3 passing canaries.

## Open items carried forward (not blockers for this task)

- **`VerificationRecordRepositoryIntegrationTest`** (this task's own new Testcontainers class)
  and **`InvoiceRepositoryIntegrationTest`** (T08, confirmed broken) both remain unrunnable/
  unfixed locally this session — Docker unavailable throughout. The former is a new, disclosed
  carried-forward gap (same pattern as every prior task); the latter is a pre-existing, already
  logged follow-up, re-acknowledged at Phase 8/9/11, not this task's own defect.
- **`Payment`/`PaymentRepository`/`PaymentService`** still do not exist — confirmed, again, to be
  task 11's own scope. Task 11 is the natural next task that will actually call
  `VerificationRecordService.record`/`recordIgnored` for the first time, from real
  `chain.tx.*` event handling.
- **A known, low-risk, deliberately-unfixed test gap** (Phase 11 Gap 4): the append-only
  source-text guard would not catch a method-reference-style delete call — no such calling
  convention exists anywhere in this codebase today, so this is accepted as disproportionate to
  guard against now.

## Test suite status

196/196 non-Docker-dependent tests passing (`mvn -pl services/payment -am test
-Dtest='!SchemaBaselineMigrationIntegrationTest,!InvoiceRepositoryIntegrationTest,!VerificationRecordRepositoryIntegrationTest'`,
up from 183 at the start of this task).

## Verdict rationale

All 8 acceptance criteria (plus 1 review-driven addition) are implemented and test-covered. Two
full Kimi review passes (Phase 3 design challenge, Phase 8 independent review, Phase 11 test
review) together found one real production defect (the `detail` map aliasing gap, fixed
immediately) and a handful of genuine test-coverage refinements — one of which (the
`getDetail()` unmodifiability test) surfaced a real bug in its own first draft, caught via
mutation verification before it could ship as a false-positive-proof test. All 5 LOCKED decisions
in scope (L1, L5, L7, L11, L12) held. No file outside this task's own disclosed scope touched.
The carried-forward items above are accepted, named limitations/follow-ups, not defects of this
task's own output. **PASS.** Proceeding to Phase 13.
