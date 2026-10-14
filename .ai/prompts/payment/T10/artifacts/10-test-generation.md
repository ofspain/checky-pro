# payment · T10 · Phase 10 — Test Generation

Created `VerificationRecordServiceTest.java` (11 tests) and
`VerificationRecordRepositoryIntegrationTest.java` (2 tests, Testcontainers) per the frozen
brief.

## `VerificationRecordServiceTest` (11 tests)

- `shouldWalkBackwardConfirmingToSeenOnReorg` (AC1, named).
- `shouldWalkBackwardSeenToWatchingOnReorgBeforeConfirm` (AC2, named).
- `shouldIgnoreOutOfOrderOrStaleChainEvents` (AC3, named) — asserts `fromState == null` (Finding
  1's revised design), not the originally-planned `fromState == toState`.
- `shouldRecordAnOrdinaryForwardTransition` (AC4).
- `shouldPersistConfirmationCountWhenProvided` — an extra, not individually required, covering
  the `confirmations` field at least once with a non-null value.
- 4 null-argument tests (AC7) across `record`/`recordIgnored`.
- `shouldNotBeAffectedByCallerMutatingTheDetailMapAfterRecording` — the regression guard for
  Phase 8 Kimi Finding 1's own fix, using an `ArgumentCaptor` to prove a post-call mutation to
  the caller's own map does not affect what was captured for persistence.
- `verificationPackageProductionFilesShouldNeverCallDeleteOrUpdate` (AC5) — widened from the
  Phase 2 brief's own narrower two-file plan to all three new production files, per Phase 3
  Finding 2.

**Found and fixed one real test-authoring bug before any of this was mutation-verified**: the
service field was originally constructed as an inline field initializer
(`private final VerificationRecordService service = new VerificationRecordService(repository,
...)`), which captures `repository`'s value at object-construction time — *before*
`MockitoExtension` injects the `@Mock` field into the already-constructed test instance,
producing a `NullPointerException` on every test (`this.repository` is null). Moved construction
into `@BeforeEach`, mirroring `InvoiceServiceTest`'s own identical, correct precedent — caught
immediately by actually running the tests, not assumed correct from the plan alone.

## `VerificationRecordRepositoryIntegrationTest` (2 tests, Testcontainers)

- `shouldRoundTripDetailJsonbMapping` (AC8) — a non-trivial, nested `detail` map (a string, a
  number, a nested map) round-trips through a real `payment_app` connection.
- `shouldPersistRecordWithNullDetailAndNullFromState` — the `recordIgnored`-shaped row (null
  `detail`, null `fromState`) round-trips too.

Both seed a real `payments.invoices` → `payments.payments` row first (mirroring
`SchemaBaselineMigrationIntegrationTest`'s own `seedPaymentAsAdmin` helper exactly) — a genuine
foreign-key requirement (`verification_records.payment_id → payments.payments.id`) the Phase 5
plan's own placeholder `paymentId` literals (`1L`, `2L`) would have violated; caught while writing
the test, before it could ever run and fail against a real database. **Cannot be run locally this
session** — Docker unavailable — same disclosed, carried-forward gap as every prior
Testcontainers-backed test this pipeline.

## Mutation verification performed this phase

Each mutation introduced via `Edit` (never `git checkout --`), confirmed red, reverted via
`Edit`:

| Mutation | Test(s) that caught it |
|---|---|
| `recordIgnored` reverted to `fromState = currentState` (the superseded design) | `shouldIgnoreOutOfOrderOrStaleChainEvents` |
| `VerificationRecord.create`'s defensive copy removed | `shouldNotBeAffectedByCallerMutatingTheDetailMapAfterRecording` |
| Both `Objects.requireNonNull` calls removed from `record` | both null-argument tests for `record` |
| A `repository.delete(...)` call added to `recordIgnored` | `verificationPackageProductionFilesShouldNeverCallDeleteOrUpdate` |

(The two Testcontainers-backed tests themselves are not mutation-verified this session — cannot
run locally at all, Docker unavailable.)

## Verification performed this phase

- `mvn -pl services/payment -am test-compile` — clean (including the new Testcontainers class).
- `mvn -pl services/payment -am test -Dtest='VerificationRecordServiceTest'` — 11/11.
- Full non-Docker suite: `mvn -pl services/payment -am test
  -Dtest='!SchemaBaselineMigrationIntegrationTest,!InvoiceRepositoryIntegrationTest,!VerificationRecordRepositoryIntegrationTest'`
  — **194/194 passing** (183 carried forward + 11 new).

## Net result

All of the frozen brief's own Required Tests are present and mutation-verified where runnable
locally. Proceeding to Phase 11 — per the Kimi handoff/wait discipline, this is Kimi 2.7's job;
stopping here to hand off.
