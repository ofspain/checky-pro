# crypto · T19 · Phase 9 — Review Resolution

Human-approved dispositions for the Phase 7 self-review and Phase 8 (Kimi) independent review. Phase 8
independently confirmed all 5 Phase 7 findings (matching them 1-for-1); only the 5 new Phase 8 findings
(#6-#10) required a fresh decision.

| # | Finding | Disposition | Change made |
|---|---|---|---|
| 1 | `screen(...)` logs/reads the clock before validating `chain`/`address` (Self-Review #1 = Phase 8 #1) | **ACCEPTED** | `FailClosedScreeningClient.screen`: added `Objects.requireNonNull(chain, "chain")` and `Objects.requireNonNull(address, "address")` as the first two statements, before the `log.warn` call. |
| 2 | Interface Javadoc doesn't document `chain`/`address` non-nullability (Self-Review #2 = Phase 8 #2) | **ACCEPTED** | `ScreeningClient.screen`'s Javadoc: `@param chain`/`@param address` now state "must not be null"; added `@throws NullPointerException if chain or address is null`. |
| 3 | Warn log omits `address` (Self-Review #3 = Phase 8 #3) | **ACCEPTED** | `FailClosedScreeningClient.screen`'s log statement now includes `address={}` alongside `chain`/`txHash`. |
| 4 | No test for `screen(null, ...)`/`screen(..., null, ...)` (Self-Review #4 = Phase 8 #4) | **ACCEPTED** | Added `rejectsNullChainBeforeLoggingOrPersistingAnything` and `rejectsNullAddressBeforeLoggingOrPersistingAnything` to `FailClosedScreeningClientTest`, each asserting the `NullPointerException` and that `screeningResultRepository.save(...)` is never invoked. |
| 5 | `ScreeningResult` Javadoc mischaracterizes `token_allowlist` as "audit-trail" (Self-Review #5 = Phase 8 #5) | **ACCEPTED** | Reworded the class Javadoc's comparison to "every other `INSERT, SELECT`-only table in this service", dropping the "audit-trail" framing for that list. |
| 6 | `ScreeningModuleBoundaryTest`'s forbidden-prefix list isn't exhaustive | **ACCEPTED** | Rewrote the test as a true allow-list: every `com.themistra.crypto.*` import must start with `com.themistra.crypto.screening.` or `com.themistra.crypto.common.`, or the test fails — no longer dependent on a maintained forbidden-list. |
| 7 | `ScreeningResult.create` accepts empty/blank strings | **REJECTED** | No entity in this codebase (e.g. `TokenAllowlist.create`, `Observation.create`) enforces blank-string checks on its `String` fields — only `null`-checks. Adding this here would be new, inconsistent scope beyond what the frozen brief or established convention calls for. |
| 8 | No JPA-level (vs. raw-JDBC) test that a loaded entity can't be mutated and flushed | **REJECTED** | `ScreeningResult` has zero setters, so no ordinary code path can construct the scenario without reflection hacks; such a test would exercise Hibernate's own dirty-checking mechanics, not this task's code. The existing raw-JDBC `UPDATE`-denial test (`ScreeningResultRepositoryIntegrationTest.updateFailsAtTheDatabaseLevel`) already proves the database-level guarantee, mirroring `TokenAllowlistRepositoryIntegrationTest`'s identical precedent for the same situation. |
| 9 | `FailClosedScreeningClient`'s constructor doesn't null-check its injected dependencies | **REJECTED** | Inconsistent with this codebase's own established precedent: `ReorgDetector`'s constructor (`reorg/ReorgDetector.java`) accepts `OutboxPublisher`/`Clock` with no `Objects.requireNonNull` either — constructor-injected Spring beans are not defensively null-checked anywhere in this service; only actual business-method parameters are. |
| 10 | No test proves two consecutive calls persist two independent rows | **ACCEPTED** | Added `twoConsecutiveCallsPersistTwoIndependentRowsRatherThanReusingOrOverwritingTheFirst` to `FailClosedScreeningClientTest`, capturing both `save(...)` invocations and asserting they are two distinct `ScreeningResult` instances with the expected per-call fields. |

## Files changed in this phase

- `screening/FailClosedScreeningClient.java` — null-guards moved to the top of `screen(...)`; `address`
  added to the log statement.
- `screening/ScreeningClient.java` — Javadoc updated (Finding #2).
- `screening/ScreeningResult.java` — Javadoc reworded (Finding #5).
- `screening/ScreeningModuleBoundaryTest.java` — rewritten as an allow-list (Finding #6).
- `screening/FailClosedScreeningClientTest.java` — 3 new tests (Findings #4, #10).

No public API, class name, or method signature changed. No refactoring beyond what each accepted
finding required.

## Verification

`mvn -pl services/crypto test -Dtest=ScreeningOutcomeTest,ScreeningResultTest,FailClosedScreeningClientTest,ScreeningModuleBoundaryTest,ScreeningResultRepositoryIntegrationTest`
— 21/21 pass (was 18/18 before this phase's 3 new tests).
