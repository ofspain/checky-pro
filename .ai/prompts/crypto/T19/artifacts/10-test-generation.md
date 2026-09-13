# crypto · T19 · Phase 10 — Test Generation

**Process note.** Per Phase 6's own implementation notes, this task's tests were written alongside
production code in Phase 6 (there was no existing caller to manually exercise the five new files
against) and extended in Phase 9 per the human-approved review resolution. No production code changes
in this phase — this artifact is the traceability manifest the phase template calls for, covering the
already-written, already-green test suite plus confirming no gap remains.

## Test files

| File | Tests | Purpose |
|---|---|---|
| `screening/ScreeningOutcomeTest.java` | 1 | Locks the enum's exact 3-value set against the DDL CHECK constraint. |
| `screening/ScreeningResultTest.java` | 7 | Entity factory null-checks and accessor fidelity. |
| `screening/ScreeningResultRepositoryIntegrationTest.java` | 4 | Real-`crypto_app`-role persistence round-trip and grant-boundary proof (Testcontainers). |
| `screening/FailClosedScreeningClientTest.java` | 8 | Every fail-closed/audit-trail behavior of the stub, plus the Phase 9 null-guard and multi-call additions. |
| `screening/ScreeningModuleBoundaryTest.java` | 1 | L15 module-boundary allow-list. |

**Total: 21 tests**, all passing.

## Traceability matrix

| Test | AC / Requirement | What it proves |
|---|---|---|
| `ScreeningOutcomeTest.hasExactlyTheThreeValuesTheCheckConstraintAllows` | AC1 (R21/L12) | `ScreeningOutcome` has exactly `{CLEARED, BLOCKED, ERROR}`, matching `chk_screening_outcome`. |
| `FailClosedScreeningClientTest.alwaysReturnsErrorForANormalInput` | AC2 (L12) | Stub returns `ERROR` for a normal input. |
| `FailClosedScreeningClientTest.alwaysReturnsErrorWhenTxHashIsNull` | AC2 | Stub handles the DDL's nullable `tx_hash` correctly. |
| `FailClosedScreeningClientTest.alwaysReturnsErrorForAMalformedAddressSinceThisClassPerformsNoFormatValidation` | AC2; frozen brief Phase 3 Finding #5 (scope) | No address-format validation — a malformed address is screened (and fails closed) as-is. |
| `FailClosedScreeningClientTest.neverReturnsClearedOrBlocked` | AC2 | Explicit negative assertion against both non-`ERROR` outcomes. |
| `FailClosedScreeningClientTest.persistsExactlyOneScreeningResultPerCallWithTheExpectedFields` | AC3 (audit trail) | One `ScreeningResult` persisted per call, with `outcome=ERROR`, `provider=PROVIDER_NAME` (AC-linked to Finding #7 disposition). |
| `FailClosedScreeningClientTest.twoConsecutiveCallsPersistTwoIndependentRowsRatherThanReusingOrOverwritingTheFirst` | AC3 (Phase 9 Finding #10) | Two calls persist two independent rows, not a reused/mutated one. |
| `FailClosedScreeningClientTest.rejectsNullChainBeforeLoggingOrPersistingAnything` | Phase 9 Finding #1/#4 | Null `chain` throws `NullPointerException` before any log/persist side effect; `save` never invoked. |
| `FailClosedScreeningClientTest.rejectsNullAddressBeforeLoggingOrPersistingAnything` | Phase 9 Finding #1/#4 | Same, for `address`. |
| `ScreeningResultTest.createRejectsNullChain` / `createRejectsNullAddress` / `createRejectsNullOutcome` / `createRejectsNullProvider` / `createRejectsNullScreenedAt` | AC4 (schema fidelity) | Every non-nullable DDL column is factory-guarded. |
| `ScreeningResultTest.createAcceptsNullTxHashAndNullRawResponse` | AC4 | The DDL's only nullable columns are actually accepted as `null`. |
| `ScreeningResultTest.accessorsReturnExactlyWhatWasPassedIn` | AC4 | No transposition/mapping bug between constructor args and accessors. |
| `ScreeningResultRepositoryIntegrationTest.savesAndReadsBackAFullyPopulatedRow` | AC5 (real-role persistence) | Full round-trip through the real `crypto_app` connection after `V9`. |
| `ScreeningResultRepositoryIntegrationTest.savesAndReadsBackARowWithNullTxHashAndNullRawResponse` | AC5 | Same, for the nullable-column case. |
| `ScreeningResultRepositoryIntegrationTest.deleteFailsAtTheDatabaseLevel` | AC5 (grant boundary) | `V9` grants no `DELETE` — real DB-enforced, not just SQL-text inspection. |
| `ScreeningResultRepositoryIntegrationTest.updateFailsAtTheDatabaseLevel` | AC5 (grant boundary) | `V9` grants no `UPDATE` — proven via raw JDBC since the entity has no mutator to attempt one through JPA. |
| `ScreeningModuleBoundaryTest.noMainSourceFileInScreeningImportsAnyThemistraCryptoPackageOutsideScreeningOrCommon` | AC6 (L15) | `screening/` imports nothing outside itself and `common` — an exhaustive allow-list, not a forgettable forbidden-list (Phase 9 Finding #6). |

**AC7 (no network I/O)** is structural — `FailClosedScreeningClient` has no HTTP/RPC client field or import
to exercise a call on; confirmed by direct inspection of the class's dependency list (`ScreeningResultRepository`,
`Clock`), not by a runtime test, per the frozen brief's own disposition.

**AC8 (observability)** — the `warn` log statement is exercised incidentally by every
`FailClosedScreeningClientTest` test running (visible in test output), consistent with the frozen brief's
Finding #8 disposition of no dedicated log-capture test.

**Named test `shouldReturnBlockedFromAttestOnSanctionedCounterparty` (R21):** not owned by this task — no
`/attest` endpoint exists yet (task 21). This task's suite proves the fail-closed floor
(`FailClosedScreeningClientTest.neverReturnsClearedOrBlocked` and the always-`ERROR` tests) that the
eventual `BLOCKED` behavior will be built on, per the frozen brief's own disposition (Phase 4).

## Verification run

`mvn -pl services/crypto test -Dtest=ScreeningOutcomeTest,ScreeningResultTest,FailClosedScreeningClientTest,ScreeningModuleBoundaryTest,ScreeningResultRepositoryIntegrationTest,ChainBaselineMigrationIntegrationTest`
— 31/31 pass (21 screening + 10 baseline-migration).

Full module regression (`mvn -pl services/crypto -am test`): unchanged from Phase 6/9 — same 6
pre-existing, disclosed, unrelated failures in `ObservationRepositoryIntegrationTest`,
`ProviderHealthRepositoryIntegrationTest`, `QuorumDecisionRepositoryIntegrationTest`, and
`TokenAllowlistRepositoryIntegrationTest`; zero regressions from this task.

## Gaps

None identified beyond what Phase 7/8/9 already surfaced and resolved.
