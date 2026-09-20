<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review) for crypto · T09. -->

# crypto · T09 · Phase 8 — Independent Review Findings

**Scope:** Independent adversarial review of the implemented quorum code (`ProviderAnswer`, `QuorumOutcome`, `QuorumEvaluator`, `QuorumDecision`, `QuorumDecisionRepository`, `HeldFactAlerter`, `QuorumDecisionService`) and the Phase 7 self-review.

**Directive:** Do not rewrite code. Return findings as **Issue · Evidence · Recommendation · Confidence.**

---

## Issue 1 — `HeldFactAlerter` fires before `QuorumDecisionRepository.save`, so a persistence failure leaves an alert with no corresponding row

**Evidence:**
- `QuorumDecisionService.evaluate` (`:55-61`) calls `alerter.alert(...)` on `HELD` before `repository.save(decision)`.
- If `save` throws (e.g., violated `uq_quorum_tx_fact` unique constraint on accidental re-evaluation, or a transient DB failure), the alert has already been emitted.
- The result is an ops alert for a `HELD` decision that was never durably recorded, breaking the auditability requirement that every alert corresponds to a persisted decision.

**Recommendation:**
Move the alert to after a successful `save`, or check the return value of `save` before alerting. If the alert is intentionally best-effort and fire-and-forget, document that explicitly in `HeldFactAlerter`'s Javadoc and in the runbook so ops knows an alert without a matching row is possible.

**Confidence:** High

---

## Issue 2 — `QuorumDecisionService.evaluate` has no explicit null guard on `answers` and no null guards on identity fields

**Evidence:**
- `QuorumDecisionService.evaluate` accepts `List<ProviderAnswer<T>> answers` and immediately passes it to `rejectDuplicateProviders(answers)` (`:64-72`).
- If `answers` is `null`, `rejectDuplicateProviders` throws an unnamed `NullPointerException` rather than the deliberately-worded `IllegalArgumentException` that `QuorumEvaluator.validate` would produce.
- `chain`, `txHash`, and `factType` are passed straight to `QuorumDecision.create` with no validation; `QuorumDecision.create` sets them silently, so invalid nulls would only surface at JPA persist time or as a DB constraint failure.

**Recommendation:**
Add `Objects.requireNonNull(answers, "answers")` at the top of `evaluate`, and either add `Objects.requireNonNull` guards for `chain`/`txHash`/`factType` or document that the caller must supply non-null values. Prefer failing fast with named arguments.

**Confidence:** High

---

## Issue 3 — `QuorumDecision.create` narrows `int` counts to `short` without a range check

**Evidence:**
- `QuorumDecision.java:88-89` casts `agreeingCount` and `providerCount` from `int` to `short` with a plain narrowing cast.
- The current caller (`QuorumDecisionService`) always passes values in `[1, 3]`, so this is not an active bug.
- However, `create` is a public static factory with no documented range contract. A future caller passing a value outside `[-32768, 32767]` would silently get a truncated, incorrect count persisted to the database.

**Recommendation:**
Add explicit range checks in `create` and throw `IllegalArgumentException` if `agreeingCount` or `providerCount` is negative or exceeds `Short.MAX_VALUE`. Alternatively, change the factory parameters to `short` to make the contract explicit.

**Confidence:** High

---

## Issue 4 — `FactType` string conversion is duplicated across `observation` and `quorum` packages with no compiler-enforced link

**Evidence:**
- `QuorumDecision.FactTypeDbConverter` (`:130-142`) duplicates the exact lowercase mapping already implemented in `observation.FactType.DbConverter`.
- The original converter is package-private in `observation`, and the frozen brief forbids modifying `FactType.java`, so the duplication is deliberate.
- If a future task changes `FactType`'s value set or its database representation, only code review would catch the `quorum/` copy falling out of sync.

**Recommendation:**
No action required within this task's scope, but flag for the next task that adds a third `fact_type` consumer: promote the converter to a shared, public location rather than adding a third copy.

**Confidence:** High

---

## Issue 5 — `QuorumEvaluator` rejects any list other than exactly 3 answers, which may be too rigid for degraded-provider scenarios

**Evidence:**
- `QuorumEvaluator.validate` (`:39-50`) throws `IllegalArgumentException` if `answers.size() != 3`.
- The frozen brief originally described a generic algorithm that could handle 2 or 4 answers; the Phase 3 design challenge accepted exactly-3 to avoid tie ambiguity.
- If one provider is degraded or omitted, a future caller with only 2 valid answers cannot evaluate quorum at all, even though 2 matching answers would logically constitute agreement.
- `ProviderHealth` / `chain.provider.degraded` (task 10) may need to drop a provider from quorum evaluation; this code cannot accommodate that.

**Recommendation:**
Document that the current implementation supports exactly 3 providers and that degraded-provider omission (e.g., evaluating with 2 answers when the third is unhealthy) is a future task requiring either a rule change or a separate evaluator path. If task 10 needs this, escalate to the author now.

**Confidence:** Medium

---

## Issue 6 — `HeldFactAlerter` logs the raw `answers` list, including whatever values providers returned

**Evidence:**
- `HeldFactAlerter.alert` (`:23-26`) logs `answers` via its `toString()`, which includes each `ProviderAnswer.value`.
- Current values are blockchain facts (`Boolean`, `BigDecimal`, `String`) and are not secrets.
- If a future caller ever passes a value containing provider-internal metadata, an address-poisoning flag, or any PII, it would be emitted in the error log.

**Recommendation:**
Log only the provider names and a summary of disagreement (e.g., distinct values count), not the full value objects. If full values are needed for debugging, log them at `debug`/`trace` level, not `error`, so production log retention does not retain potentially sensitive detail by default.

**Confidence:** Low-Medium

---

## Issue 7 — No pre-flight check for an existing `(chain, txHash, factType)` decision before attempting `save`

**Evidence:**
- `QuorumDecisionRepository` exposes `findByChainAndTxHashAndFactType`, but `QuorumDecisionService` never calls it.
- `V1__chain_baseline.sql` defines a unique constraint `uq_quorum_tx_fact` on `(chain, tx_hash, fact_type)`.
- A second evaluation of the same fact (e.g., a bug in a future watcher or a reorg-driven re-evaluation) will fail at the DB layer after the alert has already fired.
- The unique constraint makes the table single-decision-per-fact, but this is not reflected in the service API or error handling.

**Recommendation:**
Either (a) query for an existing decision at the start of `evaluate` and throw a clear `IllegalStateException` before any alert or save, or (b) document that re-evaluation is unsupported and that callers must not invoke the service twice for the same fact. If task 18 requires re-evaluation, the schema and service will need to change.

**Confidence:** Medium

---

## Issue 8 — `QuorumDecisionService` does not surface the agreed value to callers

**Evidence:**
- `QuorumDecisionService.evaluate` returns a `QuorumDecision` that contains only `outcome`, `agreeingCount`, and `providerCount`.
- The actual value the providers agreed on is not returned or stored anywhere in the quorum package.
- Downstream tasks (finality policy, event emission, attestation) will need the agreed value; they will have to re-derive it from the raw observations or from the caller's own state.

**Recommended brief amendment:**
Document that the agreed value is intentionally not part of this task's output and that downstream consumers must recompute it from `observations` or retain it from the original fan-out. Consider adding the agreed value to `QuorumDecision` in a future schema change if recomputation proves burdensome.

**Confidence:** Low-Medium

---

## Summary table

| # | Issue | Severity | Confidence |
|---|-------|----------|------------|
| 1 | Alert fires before persistence | Medium-High | High |
| 2 | Weak/null guards in coordinator | Medium | High |
| 3 | `int`→`short` narrowing unchecked | Low | High |
| 4 | Duplicated `FactType` converter | Low | High |
| 5 | Exactly-3-answers rule too rigid | Medium | Medium |
| 6 | `HeldFactAlerter` logs full values | Low-Medium | Low-Medium |
| 7 | No pre-flight duplicate-decision check | Medium | High |
| 8 | Agreed value not returned/stored | Low-Medium | High |

(End of independent review.)
