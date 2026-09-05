<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) for crypto · T09. -->

# crypto · T09 · Phase 3 — Design Challenge Findings

**Scope:** Adversarial review of the Phase 2 Task Implementation Brief for the quorum evaluator (T09) before it is frozen.

**Directive:** Do not redesign and do not implement. Surface hidden assumptions, ambiguous rules, missing edge cases, and conflicts with locked decisions / `spec/crypto-service/agents.md`. Each finding: **Issue · Severity · Evidence · Recommended brief amendment.**

---

## Issue 1 — `BigDecimal.equals()` is scale-sensitive, risking false `HELD` decisions for `AMOUNT`

**Severity:** High

**Evidence:**
- The brief states agreement is purely `equals()`-based and that `AMOUNT` answers are `BigDecimal` values.
- `BigDecimal.equals()` considers scale: `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` returns `false`.
- Provider adapters may return the same nominal amount with different scales (e.g., one parses a hex uint256 to a plain `BigInteger`-derived `BigDecimal`, another serializes/deserializes through a decimal string that retains trailing zeros).
- The brief says the caller is responsible for supplying "already-normalized, directly-comparable values," but it never defines what normalization means or where it happens.

A false disagreement on amount would trigger an unnecessary `HELD` state, blocking downstream attestation and alerting ops.

**Recommended brief amendment:**
Specify the normalization rule for `AMOUNT` answers (e.g., always use `BigDecimal.stripTrailingZeros()` or always compare via `compareTo() == 0` rather than `equals()`). State which component — the adapter, the caller, or `QuorumEvaluator` — is responsible for ensuring scale-invariant comparison.

---

## Issue 2 — `null` individual answer values are not addressed

**Severity:** High

**Evidence:**
- The brief says `QuorumEvaluator` rejects a `null` or empty *answers list* but does not discuss `null` *elements* within the list.
- If a provider genuinely cannot answer a specific fact (e.g., token info unavailable for a native-TRX transfer), the caller might represent that as `null`.
- `null` elements in the list would cause `equals()` to throw `NullPointerException` when compared against non-null answers, or would be silently counted as a distinct "answer" value if the algorithm uses a map keyed by value.

**Recommended brief amendment:**
Define whether `null` answers are allowed and how they are treated. Options: (a) reject the entire list if any element is null; (b) ignore nulls and evaluate only non-null answers; (c) treat null as a legitimate answer value that can agree with other nulls. Document the chosen behavior in AC1 and add corresponding tests.

---

## Issue 3 — A 2-2 split with four providers is reported as `AGREED` with an arbitrary winning value

**Severity:** Medium-High

**Evidence:**
- The brief says the algorithm generalizes beyond 3 answers: `AGREED` when the largest matching group has size ≥ 2.
- With four providers and answers `[A, A, B, B]`, the largest group has size 2, so the outcome is `AGREED`.
- The evaluator must choose which value represents the "agreed" fact. In a tie, the choice depends on iteration order (typically the first group of size 2 encountered).
- R1's wording is "at least 2-of-3 agree," which implies a clear majority. A 2-2 tie is not a clear majority, yet the algorithm would report agreement.

**Recommended brief amendment:**
Clarify the intended behavior for tied largest groups. If the launch design is fixed at 3 providers, state that 4-provider evaluation is a future concern. If N may vary, require that the largest group strictly exceed the next-largest group (i.e., a genuine plurality) before reporting `AGREED`, or document that ties are broken by first-seen value and that this is acceptable only for 3-provider deployments.

---

## Issue 4 — `AGREED` can mean "providers agree the fact is false," which conflicts with the named test's wording

**Severity:** Medium

**Evidence:**
- The named test from `package.md` §8 is `shouldTreatFactAsTrueOnlyWhenTwoOfThreeProvidersAgree`.
- The brief's AC1 says `AGREED` when the largest matching group has size ≥ 2, regardless of the value itself.
- For the `EXISTENCE` fact type, two providers could agree that a transaction `exists=false`. Under the algorithm, this is `AGREED` (the providers agree), but the fact being "true" is ambiguous: the truth is that the transaction does not exist.
- This creates a naming/semantic mismatch between "AGREED" (consensus reached) and "fact is true" (boolean value).

**Recommended brief amendment:**
Rename or reframe the named-test mapping. `AGREED` should mean "providers reached consensus on a value," not "the value is true." Update the brief to state that `AGREED` on `EXISTENCE=false` is the correct, expected outcome when providers concur a transaction is not observed, and ensure downstream consumers interpret `AGREED` as consensus, not boolean truth.

---

## Issue 5 — `QuorumDecision` does not store the agreed-upon value

**Severity:** Medium

**Evidence:**
- `QuorumDecision` stores `outcome`, `agreeingCount`, and `providerCount`, but not the actual value the providers agreed on.
- Downstream tasks (finality policy, attest, event emission) will need to know the agreed value, not just that consensus existed.
- The observation log contains raw responses, but deriving the agreed value requires re-running the quorum logic or querying observations and inferring the majority value.

**Recommended brief amendment:**
Add a `value` or `agreedValue` column to `QuorumDecision` (requires schema change, so escalate carefully since V1 is frozen) or explicitly document that downstream tasks must recompute the agreed value from `observations` using `(chain, txHash, factType)`. If the latter, specify the exact recomputation contract so all consumers behave identically.

---

## Issue 6 — Duplicate answers from the same provider are not detected or deduplicated

**Severity:** Medium

**Evidence:**
- The brief says `QuorumEvaluator` receives "a list of already-fetched provider answers" but does not require that each answer comes from a distinct provider.
- A buggy caller could pass two answers from the same provider, artificially inflating `agreeingCount` and producing a false `AGREED`.
- The evaluator has no way to detect this because it only sees values, not provider identities.

**Recommended brief amendment:**
Require the coordinator (or the evaluator's input contract) to ensure each answer is paired with its provider identity, and that no provider appears more than once per evaluation. Alternatively, specify that the evaluator trusts the caller to provide one answer per provider and that duplicate-provider detection is the caller's responsibility.

---

## Issue 7 — The unique constraint on `(chain, txHash, factType)` prevents re-evaluation after reorgs or new provider responses

**Severity:** Medium

**Evidence:**
- `V1__chain_baseline.sql` defines `CONSTRAINT uq_quorum_tx_fact UNIQUE (chain, tx_hash, fact_type)`.
- The brief says this task evaluates each fact once and does not implement re-evaluation/retry logic.
- Task 18 (`ReorgDetector`) will need to handle invalidated transactions. A reorg may require a new quorum decision for the same `(chain, txHash, factType)` after the chain state changes.
- The unique constraint means a later re-evaluation cannot insert a new `QuorumDecision` without first deleting the old one, but AC5 forbids `DELETE`.

**Recommended brief amendment:**
Document that the current `QuorumDecision` table is append-only and single-decision-per-fact for launch, and that re-evaluation/reorg handling (task 18) will require either a schema change to remove the unique constraint or a separate "quorum decision revisions" mechanism. Flag this as a cross-task dependency now so task 18 does not silently violate AC5.

---

## Issue 8 — `HeldFactAlerter` as a log line may not satisfy R2's "alert ops" requirement

**Severity:** Medium

**Evidence:**
- R2 states: "IF the providers disagree on a fact, THEN the system SHALL mark the fact `HELD`, alert ops, and SHALL NOT emit a downstream event for it."
- The brief implements `HeldFactAlerter` as a structured log line, with the rationale that no paging integration exists yet.
- A log line alone does not "alert ops" in any actionable sense unless an external log-based alerting system is already wired, which is not part of this task's scope.

**Recommended brief amendment:**
Explicitly state that log-based alerting is an interim implementation and that a future task will replace `HeldFactAlerter`'s internals with a real paging/webhook integration. Ensure `agents.md`'s "paged metrics" rule is not claimed as satisfied by this task alone.

---

## Issue 9 — No cross-task guarantee that observations are persisted before the quorum decision

**Severity:** Medium

**Evidence:**
- R4 / L3 require every provider response to be persisted to the observation log *before* the quorum decision.
- The brief scopes `QuorumEvaluator`/coordinator to evaluating already-fetched answers and persisting the decision. It does not persist observations.
- A future caller must call `ObservationLog.record(...)` for each provider response *before* calling the quorum coordinator. If that ordering is not enforced by API design, a bug in the caller could evaluate quorum before logging observations.

**Recommended brief amendment:**
Specify the caller's contract: observations must be persisted before the coordinator is invoked. Consider designing the coordinator's API to accept provider responses (not just values) and delegate observation persistence to `ObservationLog` internally, making the ordering impossible to violate. If that is out of scope, document the cross-task ordering requirement clearly for the future orchestrator (task 16 watcher layer).

---

## Issue 10 — The `>=2` threshold generalizes to any N, which may conflict with R1's "2-of-3" wording

**Severity:** Low-Medium

**Evidence:**
- R1 says: "treat it as true only when at least 2-of-3 agree."
- The brief generalizes the rule to `agreeingCount >= 2` regardless of `providerCount`, so 2-of-4 or 2-of-5 would also be `AGREED`.
- If Q1 decides on 4 providers for a chain, the brief's rule may not match the intent of R1.

**Recommended brief amendment:**
Clarify whether the threshold is strictly "2-of-3" (i.e., `providerCount == 3` and `agreeingCount >= 2`) or "any N with at least 2 agreeing" (the current algorithm). If the latter, update references to R1 in the brief to reflect the generalized rule.

---

## Summary table

| # | Issue | Severity | Recommended brief amendment |
|---|-------|----------|------------------------------|
| 1 | `BigDecimal.equals()` scale sensitivity | High | Define amount normalization/comparison |
| 2 | Null individual answers undefined | High | Specify null-element handling |
| 3 | 2-2 split with 4 providers arbitrarily `AGREED` | Medium-High | Define tie-breaking/plurality rule |
| 4 | `AGREED` on false semantics | Medium | Reframe named test / consensus meaning |
| 5 | `QuorumDecision` stores no agreed value | Medium | Add value column or recomputation contract |
| 6 | Duplicate-provider answers undetected | Medium | Require distinct-provider input contract |
| 7 | Unique constraint blocks re-evaluation | Medium | Flag cross-task reorg dependency |
| 8 | Log-only alerting may not satisfy R2 | Medium | State interim log-based alerting |
| 9 | Observation-before-decision ordering unenforced | Medium | Specify caller contract or internalize persistence |
| 10 | `>=2` threshold generalizes beyond 2-of-3 | Low-Medium | Clarify N/threshold intent |

(End of design challenge.)
