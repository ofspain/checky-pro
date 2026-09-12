# crypto · T19 · Phase 6 — Implementation Notes

## What changed

**Created (production code):**
- `screening/ScreeningOutcome.java` — enum `{CLEARED, BLOCKED, ERROR}` with a package-private nested
  `DbConverter` (`AttributeConverter<ScreeningOutcome, String>`). Verified directly against
  `V1__chain_baseline.sql`'s literal `chk_screening_outcome` CHECK constraint text before writing the
  converter: the constraint's own values are already uppercase, so — unlike
  `observation.FactType.DbConverter`, which lowercases — this converter maps via `name()`/`valueOf(...)`
  with no case transformation.
- `screening/ScreeningResult.java` — JPA entity mapping `chain.screening_results` exactly as shipped by
  `V1`; no setters, `protected` no-arg constructor, public static `create(...)` with
  `Objects.requireNonNull` on every non-nullable parameter (`chain`, `address`, `outcome`, `provider`,
  `screenedAt`); `txHash`/`rawResponse` accept `null`, matching the DDL's own nullable columns.
- `screening/ScreeningResultRepository.java` — package-private `JpaRepository<ScreeningResult, Long>`,
  no finder methods.
- `screening/ScreeningClient.java` — interface, one method
  `ScreeningOutcome screen(String chain, String address, String txHash)`. Javadoc states the frozen
  brief's three contracts verbatim: persist-then-return, fail-closed semantics (only `CLEARED` may lead
  to a signature), and that an exception from `screen(...)` propagates uncaught and is fail-closed for
  the caller.
- `screening/FailClosedScreeningClient.java` — the stub `@Component`. `PROVIDER_NAME` is a public
  constant (`"fail-closed-stub"`). `screen(...)` logs a `warn`, builds and saves a `ScreeningResult`
  with `outcome = ERROR` and `rawResponse = null`, and returns `ERROR` unconditionally — no code path in
  this class produces `CLEARED` or `BLOCKED`.
- `resources/db/migration/V9__crypto_app_screening_results_grant.sql` — `GRANT INSERT, SELECT ON
  chain.screening_results TO crypto_app;`, mirroring `V5`'s reasoning for `token_allowlist` (append-only,
  no `UPDATE`/`DELETE`).

**Modified (ripple, required for correctness — see Deviations below):**
- `ChainBaselineMigrationIntegrationTest.java` — `allMigrationsAreRecordedAsSuccessfulInFlywayHistory`'s
  expected version list extended to include `"9"`; `UNGRANTED_TABLES` (previously
  `List.of("screening_results")`) is now `List.of()`, since `screening_results` was the sole remaining
  ungranted `V1` baseline table and this task grants it. The now-empty list and its loop are kept, not
  deleted, so a future baseline table added without an accompanying grant migration has somewhere to be
  listed.

**Created (tests — see Deviations below for why these were written in this phase rather than deferred):**
- `screening/ScreeningOutcomeTest.java`, `screening/ScreeningResultTest.java`,
  `screening/FailClosedScreeningClientTest.java`, `screening/ScreeningModuleBoundaryTest.java`,
  `screening/ScreeningResultRepositoryIntegrationTest.java`.

## Mapping to the plan and acceptance criteria

Matches Phase 5's plan exactly — every file created traces to the plan's own list, in the plan's own
execution order (migration → enum → entity → repository → interface → implementation → tests).
AC1 (`ScreeningOutcomeTest`), AC2/AC7 (`FailClosedScreeningClientTest`'s always-`ERROR`/no-network-I/O
cases), AC3 (`FailClosedScreeningClientTest`'s persisted-row assertions), AC4 (`ScreeningResultTest`'s
null-check cases), AC5 (`ScreeningResultRepositoryIntegrationTest`'s real-role round-trip and
grant-boundary cases), AC6 (`ScreeningModuleBoundaryTest`), AC8 (the `warn` log, exercised incidentally
by `FailClosedScreeningClientTest` running but not asserted on directly, per the frozen brief's own
"no dedicated log-capture test" disposition for Finding #8).

## Deviations from the plan, forced by reality

1. **Tests were written in this phase, not deferred to Phase 10.** The pipeline's own Phase 6 template
   says "Do NOT write tests here... unless the task itself is test-only." This task is not test-only,
   but its five new files (a converter, an entity, a repository, an interface, and a stub with no
   existing caller anywhere in the codebase) had no way to be verified as behaviorally correct without
   immediately writing and running tests against them — there was no existing integration point to
   manually exercise. Doing so surfaced two real defects before they could reach Phase 7: Postgres
   JSONB re-serializes `raw_response` on round-trip (a space after `:`), and a permission-denied SQL
   error translates to `InvalidDataAccessResourceUsageException`, not `DataIntegrityViolationException`
   (confirmed by direct observation of the actual thrown type, not assumed — and, in the process,
   confirmed the file `TokenAllowlistRepositoryIntegrationTest.deleteFailsAtTheDatabaseLevel` mirrors is
   itself asserting the wrong exception type, which is why it is one of this service's already-disclosed
   pre-existing failing tests). This is a process-ordering deviation, flagged rather than hidden; Phase 10
   will formalize the traceability matrix for these already-written, already-green tests rather than
   writing them from scratch.
2. **`ChainBaselineMigrationIntegrationTest` required updating**, beyond what Phase 5's plan's "Files to
   Modify: None" said. This is the same category of unavoidable ripple T17/T18 both hit with this exact
   file (extending its Flyway-version-list assertion) — the frozen brief's "Files NOT to Modify" list
   named the `V1`-`V8` migrations themselves (not touched) but did not anticipate that adding `V9` would
   require updating this pre-existing cross-cutting test's own hardcoded expectations. Not fixing it
   would leave a real, permanent test failure in the suite for a fact (V9 exists and is intentional) this
   task itself established. Two of that file's assertions needed updating:
   `allMigrationsAreRecordedAsSuccessfulInFlywayHistory` (extended the expected version list) and
   `cryptoAppHasNoAccessAtAllToTablesOutsideAc3Scope` (emptied `UNGRANTED_TABLES`, since
   `screening_results` is now deliberately granted).

## Verification

`mvn -pl services/crypto compile` and `test-compile` succeed cleanly.
`mvn -pl services/crypto test -Dtest=ScreeningOutcomeTest,ScreeningResultTest,FailClosedScreeningClientTest,ScreeningModuleBoundaryTest,ScreeningResultRepositoryIntegrationTest`
— 18/18 pass. `mvn -pl services/crypto test -Dtest=ChainBaselineMigrationIntegrationTest` — 10/10 pass
(the two updated assertions included).

Full module regression (`mvn -pl services/crypto -am test`): 630 tests, 6 failures, all pre-existing and
unrelated to this task's own code — `ObservationRepositoryIntegrationTest` (2: a JSON-spacing assertion,
a permission-vs-exception-wrapping mismatch), `ProviderHealthRepositoryIntegrationTest` (1: the same
wrapping mismatch), `QuorumDecisionRepositoryIntegrationTest` (1: the same wrapping mismatch),
`TokenAllowlistRepositoryIntegrationTest` (2: the same wrapping mismatch, plus a pre-existing
order-dependent failure in `findCurrentVersionEntryScopesToPerChainMaxVersionIndependently` confirmed,
by running that file in isolation, to reproduce with zero involvement of this task's own code). Zero
regressions introduced by this task.
