# crypto · T19 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R21 — screening capability + audit trail (the primitive task 21's `/attest` `BLOCKED` response will be built on) | Yes | `ScreeningClient.screen` `screening/ScreeningClient.java:36`; `FailClosedScreeningClient.screen` `screening/FailClosedScreeningClient.java:38-49` | `FailClosedScreeningClientTest` (9 tests) | No | **Scope, disclosed since Phase 1/2/4:** the actual `{outcome: BLOCKED, reason}` HTTP response and compliance-queue placement are task 21's `AttestationService` to build; this task delivers the interface, the fail-closed stub, and the persisted audit trail R21/L12 depend on. |
| L12 — screening gates attestation, fails closed | Yes | `FailClosedScreeningClient.screen` never returns `CLEARED`/`BLOCKED` — only `ERROR` (`screening/FailClosedScreeningClient.java:48`) | `FailClosedScreeningClientTest.alwaysReturnsErrorForANormalInput`, `.neverReturnsClearedOrBlocked`, `.alwaysReturnsErrorWhenTxHashIsNull`, `.alwaysReturnsErrorForAMalformedAddressSinceThisClassPerformsNoFormatValidation` | No | None. |
| L12-T19a (new, Phase 4) — only `CLEARED` may lead to a signature; `BLOCKED`/`ERROR` are both fail-closed, non-retryable | Yes | Documented as the interface's own contract, `screening/ScreeningClient.java:15-18` | N/A (a documentation-level Locked Decision; enforced by the calling code task 21 will write, not testable in isolation here) | No | None — this is a forward-looking constraint on task 21's future caller, correctly scoped as documentation in this task. |
| L12-T19b (new, Phase 4) — an exception from `screen(...)` propagates uncaught, treated as fail-closed | Yes | No try/catch around `screeningResultRepository.save(...)`, `screening/FailClosedScreeningClient.java:47` | `FailClosedScreeningClientTest.anExceptionFromRepositorySavePropagatesUnwrappedRatherThanBeingCaughtAndTreatedAsError` (added Phase 11) | No | None. |
| Audit trail — every screening attempt persists exactly one `ScreeningResult` row before returning | Yes | `screening/FailClosedScreeningClient.java:44-47` | `FailClosedScreeningClientTest.persistsExactlyOneScreeningResultPerCallWithTheExpectedFields`, `.twoConsecutiveCallsPersistTwoIndependentRowsRatherThanReusingOrOverwritingTheFirst` | No | None. |
| Schema fidelity — `ScreeningResult` maps `chain.screening_results` exactly as shipped by `V1` | Yes | `screening/ScreeningResult.java:31-60`; column lengths (`chain` 32, `address` 128, `tx_hash` 128, `outcome` 16, `provider` 64) checked directly against `V1__chain_baseline.sql:87-97` | `ScreeningResultTest` (9 tests), `ScreeningResultRepositoryIntegrationTest` (6 tests) | No | None. |
| Grant correctness — `crypto_app` can `INSERT`/`SELECT`, never `UPDATE`/`DELETE`, on `chain.screening_results` | Yes | `V9__crypto_app_screening_results_grant.sql` | `ScreeningResultRepositoryIntegrationTest.deleteFailsAtTheDatabaseLevel` (extended Phase 11 with a still-present assertion), `.updateFailsAtTheDatabaseLevel`, `.checkConstraintRejectsAnOutcomeStringOutsideTheThreeAllowedValues` (added Phase 11) | No | None. |
| Module boundary — `screening/` has no legitimate cross-module dependency | Yes | No import outside `com.themistra.crypto.screening`/`com.themistra.crypto.common` anywhere in `screening/`'s main sources | `ScreeningModuleBoundaryTest` (rewritten Phase 9 into a true allow-list per Kimi Finding #6) | No | None. |
| No network I/O in the stub | Yes | `FailClosedScreeningClient` has no HTTP/RPC client field or import — structural fact, `screening/FailClosedScreeningClient.java:1-50` | N/A (structural; confirmed by direct inspection, not a runtime test, per the frozen brief's own disposition) | No | None. |
| Observability — the stub logs a `warn` when active | Yes | `screening/FailClosedScreeningClient.java:42-43` | Exercised incidentally by every `FailClosedScreeningClientTest` run; no dedicated log-capture test (Phase 4 Finding #8 disposition, reaffirmed at Phase 11 against Kimi's Gap 3) | No | Deliberate, human-approved, twice-reaffirmed disposition — not a gap. |
| Named test `shouldReturnBlockedFromAttestOnSanctionedCounterparty` (`package.md` §8) | Partial | N/A — no `/attest` endpoint exists yet | N/A | **Yes, by design** | Disclosed since Phase 1: this task builds the fail-closed floor (`FailClosedScreeningClientTest.neverReturnsClearedOrBlocked`) the eventual `BLOCKED` behavior depends on; full ownership of the named test belongs to task 21, which does not exist yet. |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. Every file the frozen brief (Phase 4) authorized exists and
compiles: `screening/ScreeningOutcome.java`, `ScreeningResult.java`, `ScreeningResultRepository.java`,
`ScreeningClient.java`, `FailClosedScreeningClient.java`, and `V9__crypto_app_screening_results_grant.sql`.
Phase 9's 10 consolidated review findings (self-review + Kimi, deduplicated to 5 shared + 5 Kimi-only) are
all closed — 6 accepted and fixed, 3 rejected with reasons grounded in this codebase's own established
precedent (`ReorgDetector`'s unguarded constructor, `TokenAllowlist`'s absence of blank-string checks).
Phase 11's 8 test-review gaps are all closed — 6 accepted and folded into the suite, 2 rejected (one
re-litigating an already-settled Phase 4 disposition, one re-raising an already-rejected Phase 9 finding).

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC8 (frozen brief) all have
implementation evidence and test coverage per the matrix above, including AC7 (no network I/O, a
structural rather than runtime-tested criterion, consistent with its own nature) and AC8 (observability,
deliberately not lock-tested, per a twice-reaffirmed human-approved disposition).

**(3) Does it violate any LOCKED decision?** No. L12's fail-closed guarantee is honored by
`FailClosedScreeningClient` having no code path that returns `CLEARED` or `BLOCKED`. The two new
task-scoped Locked Decisions this task itself introduced at Phase 4 (L12-T19a, L12-T19b) are both
documented in `ScreeningClient`'s own Javadoc and, where testable in isolation, verified. No frozen file
from a prior task (`V1`-`V8` migrations, `watch/`, `token/`, `quorum/`, `finality/`, `adapter/`,
`observation/`, `reorg/`, `events/`) was modified — the only cross-cutting ripple was
`ChainBaselineMigrationIntegrationTest` (Phase 6), whose own hardcoded Flyway-version-list and
ungranted-tables assertions needed updating to reflect `V9`'s existence, exactly the same category of
unavoidable ripple T17 and T18 both hit with this identical file.

**(4) Remaining risks?**
- The `reason` field R21's `{outcome, reason}` response shape implies has no home in the frozen `V1`
  schema and no accessor on `ScreeningClient` — disclosed at Phase 4 (Finding #3, document-only) as
  moot for this task specifically (the stub never returns `BLOCKED`) but deferred, unresolved, to
  whichever task wires the real vendor.
- No compliance-queue mechanism exists yet — `ScreeningResult`'s persisted audit trail is the raw
  material a queue could be built from, but building one is explicitly task 21's scope, not this one.
- Empty-string `chain`/`address` inputs are accepted and persisted as-is (Phase 8/9 Finding #7,
  Phase 11 Gap 6, both rejected as inconsistent with this codebase's own established
  null-only-validation convention) — a real, if low-probability, path to a meaningless audit row if a
  future caller passes one.
- Contract files (`contracts/api/crypto-internal.yaml`, `contracts/events/chain/*`) remain unmodified —
  this task introduces no new HTTP endpoint or event, so none was expected to change; the same disclosed
  "contracts not yet authored for future endpoints" gap every prior task in this package has already
  noted does not worsen here.

## Verdict

**PASS** — every in-scope requirement, acceptance criterion, and LOCKED decision (including the two new
ones this task itself introduced) is implemented, tested, and traced to evidence; both review phases'
findings are fully resolved with human-approved, precedent-grounded dispositions; the full module
regression (640 tests) shows zero regressions and only the same 6 pre-existing, disclosed, unrelated
failing tests.
