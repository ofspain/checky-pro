# crypto · T21 · Phase 9 — Review Resolution

Human-approved dispositions for the Phase 7 self-review and Phase 8 (Kimi) independent review. Phase 8
independently confirmed all 3 of my own Phase 7 findings that warranted a decision (the fail-closed-stub
visibility item, the exception visibility mismatch, and the persist-then-throw duplication) and raised 2
new findings.

| # | Finding | Disposition | Change made |
|---|---|---|---|
| 1 | Every real `/attest` request currently resolves to `REFUSED`, since `FailClosedScreeningClient` never returns `CLEARED` (Self-Review #5 = Phase 8 #1) | **ACCEPTED, DOCUMENT-ONLY** | No code change — this is the correct, intended consequence of composing this task's own L12 fail-closed gate with T19's fail-closed stub, not a defect. Callout added to the Phase 13 PR description (below) so it isn't mistaken for a bug during smoke testing. |
| 2 | `AttestationRefusedException` was `public`, unlike the package-private precedent it mirrors (Self-Review #1 = Phase 8 #2) | **ACCEPTED** | Both the class and its constructor narrowed to package-private, matching `watch.WatchNotFoundException`/`InvalidWatchRequestException` exactly. |
| 3 | The "persist `REFUSED`, then throw" pattern was duplicated at four call sites (Self-Review #2 = Phase 8 #3) | **ACCEPTED** | Extracted a private `refuse(chain, txHash, receiptDigest)` helper that persists the row and *returns* the exception (rather than throwing directly), so every call site reads `throw refuse(...)` — this also cleanly satisfies Java's definite-assignment analysis at the one call site inside a `try/catch`, avoiding the awkward unreachable `return null;` an initial draft of this fix required. |
| 4 | `AttestExceptionHandler` and `WatchExceptionHandler` both claim `@Order(HIGHEST_PRECEDENCE)` | **REJECTED** | The two handlers cover disjoint exception types today; Spring's resolution order among same-`@Order` beans only matters if they ever overlap, which would itself be a design change warranting its own review at that time — not a preemptive fix now. |
| 5 | `AttestationRepositoryIntegrationTest` never round-trips the `BLOCKED` outcome specifically | **ACCEPTED** | Added `savesAndReadsBackABlockedRow`, mirroring the existing `SIGNED`/`REFUSED`-with-nulls tests. |

## Files changed in this phase

- `attest/AttestationRefusedException.java` — class and constructor narrowed to package-private.
- `attest/AttestationService.java` — `refuse(...)` helper extracted, used at all four gate-failure call
  sites; no behavioral change.
- `attest/AttestationRepositoryIntegrationTest.java` — one new test (`savesAndReadsBackABlockedRow`).

No public API, class name (other than the now-package-private exception), or method signature changed
beyond the internal `refuse(...)` refactor. No refactoring beyond what each accepted finding required.

## PR description callout (Finding #1)

> **Note:** with no real screening vendor wired yet (Q2 unanswered, T19's `FailClosedScreeningClient`
> unconditionally returns `ERROR`), every `/attest` request that reaches the screening gate in the
> current deployed system will resolve to `409 REFUSED` — `200 SIGNED` and `200 BLOCKED` are both
> reachable only in tests today. This is expected, disclosed behavior, not a defect in this task.

## Verification

`mvn -pl services/crypto test -Dtest=AttestOutcomeTest,AttestationTest,AttestationRepositoryIntegrationTest,AttestationServiceTest,AttestControllerTest,QuorumDecisionServiceTest,WatchServiceTest,WatchRepositoryIntegrationTest,KmsSignerArchitectureTest,ResourceServerConfigIntegrationTest`
— 107/107 pass (was 106/106 before this phase's new test). Full module regression
(`mvn -pl services/crypto -am test`): 701 tests, same 6 pre-existing, disclosed, unrelated failing
tests; zero regressions.
