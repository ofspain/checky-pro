<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review) for crypto · T09. -->

# crypto · T09 · Phase 11 — Test Review Findings

**Scope:** Review the Phase 10 test suite (`QuorumEvaluatorTest`, `QuorumDecisionTest`, `HeldFactAlerterTest`, `QuorumDecisionServiceTest`, `QuorumDecisionRepositoryIntegrationTest`) against the frozen brief's acceptance criteria and `spec/crypto-service/agents.md`.

**Directive:** Do not rewrite production code or tests. Return gaps as **Gap · Why it matters · Suggested test.**

---

## Gap 1 — `QuorumDecisionService.evaluate` null guards for `txHash` and `factType` are not tested

**Why it matters:** The production method guards all four parameters with `Objects.requireNonNull`. Tests exist for null `chain` and null `answers`, but not for null `txHash` or null `factType`. A regression that accidentally removed those two guards would not be caught.

**Suggested test:** Add `rejectsANullTxHash` and `rejectsANullFactType` to `QuorumDecisionServiceTest`, mirroring `rejectsANullChain`.

---

## Gap 2 — `ProviderAnswer` compact-constructor null guards are not tested

**Why it matters:** `ProviderAnswer` rejects null `provider` and null `value` at construction. If this guard is removed, `QuorumDecisionService.rejectDuplicateProviders` and `extractValues` could silently misbehave or NPE elsewhere.

**Suggested test:** Add `ProviderAnswerTest` with two tests asserting `NullPointerException` (with the offending field name) for null `provider` and null `value`.

---

## Gap 3 — `QuorumDecision.create` does not reject null identity/value fields

**Why it matters:** `QuorumDecision.create` currently accepts null `chain`, `txHash`, `factType`, `outcome`, and `decidedAt` silently; JPA/database constraints catch them only at persist time. Failing fast at construction makes unit tests clearer.

**Suggested test:** Add parameterized null-guard tests to `QuorumDecisionTest` for each constructor argument, asserting `NullPointerException` with the parameter name. If the production code is intentionally permissive, document it instead.

---

## Gap 4 — No semantic validation that `agreeingCount` cannot exceed `providerCount`

**Why it matters:** `QuorumDecision.create` range-checks each count independently but allows a nonsensical state such as `agreeingCount=3, providerCount=2`. The `QuorumEvaluator` never produces such a result, but `QuorumDecision.create` is a public factory.

**Suggested test:** Add `createRejectsAgreeingCountGreaterThanProviderCount` to `QuorumDecisionTest` and corresponding validation in `create`.

---

## Gap 5 — No service-level test for scale-invariant `BigDecimal` amount agreement

**Why it matters:** The evaluator has a dedicated BigDecimal test, but `QuorumDecisionService` wires the evaluator to persistence. A regression in how `ProviderAnswer<BigDecimal>` is passed through `extractValues` to the evaluator would not be caught at service level.

**Suggested test:** Add a `QuorumDecisionServiceTest` case with three `ProviderAnswer<BigDecimal>` values (`1.0`, `1.00`, `2.0`) and assert the persisted decision is `AGREED` with `agreeingCount=2`.

---

## Gap 6 — Race condition on concurrent evaluation of the same fact is not tested

**Why it matters:** `QuorumDecisionService.rejectExistingDecision` calls `findByChainAndTxHashAndFactType`, then later `repository.save`. Two threads evaluating the same fact concurrently could both see no existing decision, then both attempt `save`, causing one to fail with a unique-constraint violation. The current pre-flight check is not atomic with the insert.

**Suggested test:** Add a concurrency test (or document the accepted risk) that runs two simultaneous evaluations of the same fact and asserts at least one fails cleanly with `IllegalStateException` or the constraint violation is handled gracefully rather than propagating as a raw `DataIntegrityViolationException`.

---

## Gap 7 — Ordering between `rejectExistingDecision` and `rejectDuplicateProviders` is not tested

**Why it matters:** Production code runs the existing-decision check before duplicate-provider rejection. The duplicate-provider test stubs no existing decision, so it does not prove that order. A refactor could swap them without a test failing.

**Suggested test:** Add `existingDecisionIsCheckedBeforeDuplicateProviderRejection` that stubs `findByChainAndTxHashAndFactType` to return an existing decision *and* supplies duplicate providers, asserting the thrown exception is the `IllegalStateException` for an existing decision, not the `IllegalArgumentException` for duplicates.

---

## Gap 8 — `HeldFactAlerter.alert` failure after a successful save is not tested

**Why it matters:** Alerting now happens after persistence. If `alerter.alert` throws, the `QuorumDecision` has already been saved (save is not in a surrounding transaction). The exception would propagate, but the caller might incorrectly assume the evaluation failed.

**Suggested test:** Add `decisionIsPersistedEvenIfAlerterThrows` that stubs `repository.save` to succeed and `alerter.alert` to throw, asserting the method propagates the alert exception but the saved decision is still returned. This documents the "alert is best-effort after persistence" semantics.

---

## Gap 9 — `HeldFactAlerter` does not assert that actual disagreement values appear in the log

**Why it matters:** The current test verifies provider names and fact metadata are logged but does not assert the log contains the conflicting values. The values are the main point of the alert for ops debugging.

**Suggested test:** Enhance `alertLogsAtErrorLevelWithChainTxHashFactTypeAndEveryProviderAnswer` to assert the formatted message contains the string representations of the values (e.g., `"true"`, `"false"`) in addition to provider names.

---

## Gap 10 — No direct test for the `QuorumOutcome` enum

**Why it matters:** `QuorumOutcome` is a VERBATIM artifact from `design.md` §4c. A regression that renamed or removed `UNKNOWN_TOKEN` would not be caught by the current tests, because no production code path references `UNKNOWN_TOKEN`.

**Suggested test:** Add `QuorumOutcomeTest` that asserts the enum contains exactly `AGREED`, `HELD`, and `UNKNOWN_TOKEN` in that order/name, matching `design.md` §4c and `V1__chain_baseline.sql`'s `CHECK` constraint.

---

## Gap 11 — No ArchUnit test enforcing no UPDATE/DELETE code paths at the Java level

**Why it matters:** The integration test proves the DB grant prevents `DELETE`, but it does not catch a future Java-level change that introduces a mutating method (e.g., a setter or a `deleteById` call) before it reaches the database.

**Suggested test:** Add an ArchUnit test asserting that no method in `com.themistra.crypto.quorum` invokes `QuorumDecisionRepository.delete*`, calls a setter on `QuorumDecision`, or generates an `UPDATE`/`DELETE` SQL statement.

---

## Gap 12 — `QuorumDecisionRepository.findByChainAndTxHashAndFactType` empty-result path is not tested

**Why it matters:** The integration test proves the finder returns a matching decision, but not that it returns `Optional.empty()` when no decision exists. `QuorumDecisionService.rejectExistingDecision` relies on the empty path.

**Suggested test:** Add `findByChainAndTxHashAndFactTypeReturnsEmptyWhenNoDecisionExists` to `QuorumDecisionRepositoryIntegrationTest`.

---

## Gap 13 — No test verifying `QuorumDecisionService` is not annotated `@Transactional`

**Why it matters:** The production Javadoc explains the deliberate absence of `@Transactional`. A future refactor could add it, widening transactional scope unnecessarily.

**Suggested test:** Add a reflection-based test asserting neither `QuorumDecisionService` nor `evaluate` carries `@Transactional`, with a comment referencing the design rationale.

---

## Summary table

| # | Gap | Risk | Suggested test approach |
|---|-----|------|-------------------------|
| 1 | Null `txHash`/`factType` untested | Guard regression | Mirror `rejectsANullChain` |
| 2 | `ProviderAnswer` null guards untested | Silent invalid objects | `ProviderAnswerTest` |
| 3 | `QuorumDecision.create` null fields untested | Late failure | Parameterized null tests |
| 4 | `agreeingCount > providerCount` untested | Nonsensical persisted state | Semantic validation test |
| 5 | Service-level BigDecimal agreement untested | Regression in value extraction | `ProviderAnswer<BigDecimal>` service test |
| 6 | Concurrent same-fact evaluation untested | Raw constraint violation | Concurrency test or documented risk |
| 7 | Pre-flight vs duplicate ordering untested | Wrong exception on combined failure | Combined stub test |
| 8 | Alerter failure after save untested | Confusing caller semantics | Stub alerter to throw |
| 9 | Logged disagreement values unasserted | Weak alert content | Assert values in log message |
| 10 | `QuorumOutcome` enum unvalidated | VERBATIM drift | Enum membership test |
| 11 | No Java-level no-UPDATE/DELETE ArchUnit test | Late DB-layer catch | ArchUnit rule |
| 12 | Finder empty-result path untested | `rejectExistingDecision` relies on it | Empty finder integration test |
| 13 | Non-`@Transactional` status unguarded | Scope regression | Reflection annotation test |

(End of test review.)
