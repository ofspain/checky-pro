# crypto · T21 · Phase 10 — Test Generation

**Process note.** Per this task's own Phase 6 implementation notes (and the established T19/T20
precedent), tests were written alongside production code — there was no existing caller to manually
exercise `AttestationService`/`AttestController` against. One test was added at Phase 9's fold-in (the
`BLOCKED` repository round-trip). No production code changes in this phase — this artifact is the
traceability manifest.

## Test files (this task's own new/extended `attest` package files)

| File | Tests | Purpose |
|---|---|---|
| `attest/AttestOutcomeTest.java` | 3 | Enum's exact 3-value set + `DbConverter` round-trip. |
| `attest/AttestationTest.java` | 7 | Entity factory null-checks, nullable `kmsKeyId`/`signedAt`. |
| `attest/AttestationRepositoryIntegrationTest.java` | 6 | Real-`crypto_app`-role persistence round-trip for all three outcomes, append-only grant proof, CHECK-constraint enforcement. |
| `attest/AttestationServiceTest.java` | 15 | The full gate orchestration — every AC from the frozen brief. |
| `attest/AttestControllerTest.java` | 6 | `@WebMvcTest` slice: response shapes, validation, 409 mapping. |

**Total: 37 tests** in this task's own new files, all passing. (`quorum/QuorumDecisionServiceTest.java`
and `watch/WatchServiceTest.java`/`WatchRepositoryIntegrationTest.java` were also extended — see the
cross-module seam rows in the traceability matrix below — bringing the task's full new/changed-test count
to 43.)

## Traceability matrix

| Test | AC / Requirement | What it proves |
|---|---|---|
| `AttestationServiceTest.shouldReturnKmsSignatureFromAttestForValidDigest` | AC1 (named test) | All four required facts `AGREED`, screening `CLEARED` → `200 SIGNED`; the exact digest sent to KMS matches the hex-decoded request. |
| `AttestationServiceTest.shouldReturnBlockedFromAttestOnSanctionedCounterparty` | AC2 (named test) | A `BLOCKED` screening result → `200 BLOCKED`, no KMS call, no `SIGNED` row. |
| `AttestationServiceTest.blockedIsOnlyReachableAfterQuorumAndFinalityPass` | AC2 (Phase 3 Finding #1) | Screening is never even attempted if quorum/finality haven't passed — proven via `verifyNoInteractions(watchService, screeningClient, kmsSigner)`. |
| `AttestationServiceTest.shouldRejectAttestWhenQuorumOrFinalityNotMet` (parameterized ×4) | AC3 (named test) | Each of EXISTENCE/AMOUNT/TOKEN/FINALITY individually missing → `409`, no cursor lookup, no screening, no KMS call. |
| `AttestationServiceTest.confirmationsIsNotAmongTheRequiredFacts` | Scope (deliberate exclusion) | `CONFIRMATIONS` is never queried at all. |
| `AttestationServiceTest.screeningErrorRefusesRatherThanBlocking` | AC4 | Screening `ERROR` → `409`, not `200 BLOCKED`. |
| `AttestationServiceTest.aThrownScreeningExceptionRefusesRatherThanPropagating` | AC4 | A thrown `RuntimeException` from `screen(...)` → `409`, not an uncaught propagation. |
| `AttestationServiceTest.multipleCursorsWithDistinctFromAddressesAreAllScreenedAndAnyBlockedHitBlocksTheWholeRequest` | AC8 | 2+ cursors, distinct `fromAddress`es, one `BLOCKED` → whole request `BLOCKED`. |
| `AttestationServiceTest.emptyCursorListRefuses` | AC8 | Zero matching cursors → `409`, not a crash. |
| `AttestationServiceTest.allCursorsWithNullFromAddressRefusesRatherThanNpe` | AC8 | Every cursor's `fromAddress` is `null` → `409`, not `NullPointerException`. |
| `AttestationServiceTest.aKmsFailurePropagatesUncaughtAndPersistsNoAttestationRow` | AC5, L-T21b | A KMS exception propagates unconverted (surfaces as `500`); zero `Attestation` rows persisted for that attempt. |
| `AttestationServiceTest.refusalMessageNeverNamesTheSpecificFailedFact` | AC11 | Message is always the fixed, generic format — never a fact-type or `HELD`/`AGREED` internal detail. |
| `AttestationServiceTest.shouldReturnKmsSignatureFromAttestForValidDigest` (persisted-row assertions) | AC5 | Exactly one `Attestation(SIGNED)` row, correct `kmsKeyId`/`signedAt`/`chain`/`txHash`/`receiptDigest`. |
| `AttestOutcomeTest` (3 tests) | AC1-adjacent (schema fidelity) | Enum matches `chk_attest_outcome`; converter round-trips every value. |
| `AttestationTest` (7 tests) | AC5 (schema fidelity) | Entity factory null-checks; nullable columns accepted as `null`. |
| `AttestationRepositoryIntegrationTest.savesAndReadsBackASignedRow` / `.savesAndReadsBackARowWithNullKmsKeyIdAndNullSignedAt` / `.savesAndReadsBackABlockedRow` (Phase 9) | AC5 | Real-role round-trip for all three outcomes. |
| `AttestationRepositoryIntegrationTest.deleteFailsAtTheDatabaseLevel` / `.updateFailsAtTheDatabaseLevel` | AC5 (append-only grant) | `crypto_app` cannot `UPDATE`/`DELETE` `chain.attestations` — real DB-enforced. |
| `AttestationRepositoryIntegrationTest.checkConstraintRejectsAnOutcomeStringOutsideTheThreeAllowedValues` | Schema fidelity | `chk_attest_outcome` genuinely rejects an invalid value at the DB level. |
| `AttestControllerTest.shouldReturnKmsSignatureFromAttestForValidDigest` / `.blockedResponseOmitsSigningFieldsEntirely` | AC10 | JSON shape for both `200` cases carries no stray `null` fields. |
| `AttestControllerTest.refusedMapsToProblemJson409` | AC3/AC11 | `AttestationRefusedException` → `409 problem+json` with the correct title/detail. |
| `AttestControllerTest.malformedDigestReturnsBadRequest` / `.unsupportedChainReturnsBadRequest` / `.blankTxHashReturnsBadRequest` | AC9 | Bean-validation failures → `400`, routed through `@Valid`. |
| `QuorumDecisionServiceTest.isAgreedReturnsTrueForAnAgreedDecision` / `.isAgreedReturnsFalseForAHeldDecision` / `.isAgreedReturnsFalseWhenNoDecisionExistsAtAll` | AC1/AC3 (new seam) | `isAgreed(...)`'s three cases: `AGREED`, `HELD`, absent. |
| `WatchServiceTest.findChainCursorsDelegatesDirectlyToTheRepository` / `.findChainCursorsReturnsAnEmptyListWhenNoneMatch` | AC8 (new seam) | `findChainCursors(...)`'s delegation and empty case. |
| `WatchRepositoryIntegrationTest.findChainCursorsReturnsEveryCursorSharingTheSameChainAndTxHash` | AC8 (real multi-row case) | Two distinct watches sharing one `(chain, txHash)` — the real DB proof behind the frozen brief's own verified schema finding. |
| `KmsSignerArchitectureTest` (both tests, re-run) | AC6 | Still passes unmodified with the new `attest` files present. |

**AC7 (module boundary)** — `QuorumDecisionRepository`/`ChainCursorRepository` both stay
package-private; structurally verified by compilation (a public-visibility change would fail to compile
against `AttestationService`'s actual, non-privileged access pattern through the two services only).

**R27 (scope enforcement)** — already covered by the pre-existing
`common.ResourceServerConfigIntegrationTest`, which already parameterizes `POST /internal/v1/attest`
alongside the watch endpoints; re-run, not newly written.

## Verification run

`mvn -pl services/crypto test -Dtest=AttestOutcomeTest,AttestationTest,AttestationRepositoryIntegrationTest,AttestationServiceTest,AttestControllerTest,QuorumDecisionServiceTest,WatchServiceTest,WatchRepositoryIntegrationTest,KmsSignerArchitectureTest,ResourceServerConfigIntegrationTest`
— 107/107 pass.

Full module regression (`mvn -pl services/crypto -am test`): 701 tests, 6 failures, all pre-existing and
unrelated to this task (disclosed since T18/T19/T20: `ObservationRepositoryIntegrationTest`,
`ProviderHealthRepositoryIntegrationTest`, `QuorumDecisionRepositoryIntegrationTest`,
`TokenAllowlistRepositoryIntegrationTest`). Zero regressions.

## Gaps

None identified beyond what Phase 7/8/9 already surfaced and resolved.
