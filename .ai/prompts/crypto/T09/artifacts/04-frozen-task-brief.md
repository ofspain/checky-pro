# crypto · T09 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Human-approved 2026-09-03. Downstream phases (5+) may not renegotiate this brief. Supersedes
`artifacts/02-task-implementation-brief.md`, with the Phase 3 (Kimi) design-challenge amendments below
folded in.

## Task

Quorum evaluator. Implement `QuorumEvaluator` (pure 2-of-3 logic): `AGREED` needs ≥2 matching;
disagreement → `HELD` + `HeldFactAlerter` + persisted `QuorumDecision`; never auto-resolve (L1, L2,
R1–R3). Unit-test the agreement matrix exhaustively.

## Purpose

The arbitration core the entire platform exists to provide: the rule that no single provider's answer
ever becomes fact. The point at which disagreement is deliberately never resolved automatically, in
any direction.

## Scope

**In:**
- **`QuorumEvaluator`** — pure logic, no injected dependencies, no I/O. **Amendment #1: generic type
  bound changed to `<T extends Comparable<T>>`; grouping uses `compareTo() == 0`, not `equals()`.**
  This fixes `BigDecimal.equals()`'s scale sensitivity (`"1.0"` vs `"1.00"` would otherwise wrongly
  disagree) while remaining behaviorally identical to `equals()`-based grouping for `Boolean`, `String`,
  and integral types, whose natural ordering is consistent with equality. **Amendment #3+#10 (merged):
  the evaluator requires exactly 3 answers** — matches L1's literal "2-of-3" wording (not a generalized
  N-of-M rule); a list of any other size is rejected (`IllegalArgumentException`), not silently
  evaluated. This also eliminates Kimi's raised 2-2-tie-with-4-providers scenario structurally: with
  exactly 3 elements grouped by `compareTo()==0`, the only possible groupings are `{3}`, `{2,1}`
  (three ways), or `{1,1,1}` — a tie is impossible. **Amendment #2: `null` elements in the answers list
  are rejected** (`IllegalArgumentException`, distinct message from the empty-list case) — a provider
  that cannot answer a specific fact is represented by its *absence* from the list, never by a `null`
  placeholder. Public contract: given exactly 3 non-null, `Comparable` provider answer values for one
  fact, determine the largest `compareTo()==0`-matching group; `agreeingCount` = that group's size;
  `providerCount` = 3 (fixed); outcome = `AGREED` when `agreeingCount ≥ 2`, else `HELD`. The evaluator
  does not call `ChainAdapter`s and does not know about `chain`/`txHash`/`factType` — fetching answers
  remains the caller's job (no such caller exists in this task's own scope). **Documentation
  clarification (Amendment #4): `AGREED` denotes that ≥2 providers converged on the *same value*, not
  that the fact is boolean-true** — for `EXISTENCE`, two providers agreeing the transaction does *not*
  exist is a correct, expected `AGREED` outcome. The named test
  `shouldTreatFactAsTrueOnlyWhenTwoOfThreeProvidersAgree` (package.md §8) exercises the
  `EXISTENCE`-agrees-true scenario specifically; it does not imply `AGREED` is only ever produced for a
  `true`/positive value.
- **`ProviderAnswer<T>`** (new small record type, Amendment #6) — `record ProviderAnswer<T>(String
  provider, T value)`, pairing each answer with its provider identity. **The coordinator (not the pure
  evaluator) validates no two `ProviderAnswer`s share the same `provider` name** before extracting the
  plain `List<T>` of values passed to `QuorumEvaluator`. This closes the gap where a buggy caller could
  submit two answers from the same provider and artificially manufacture agreement.
- **A new coordinating class (exact name Phase 5)** that: takes `(chain, txHash, factType,
  List<ProviderAnswer<T>> answers)`, rejects duplicate-provider input, extracts values and calls
  `QuorumEvaluator` for the pure outcome/counts, calls `HeldFactAlerter` — **with the full
  `List<ProviderAnswer<T>>` (Amendment #6/#8), not just counts, so the alert can name which providers
  disagreed and what each said** — when the outcome is `HELD`, builds and persists a `QuorumDecision`
  via `QuorumDecisionRepository` in both cases, and returns the persisted decision.
- **`QuorumOutcome`** — enum `AGREED, HELD, UNKNOWN_TOKEN` (design.md §4c, VERBATIM).
- **`QuorumDecision`** — JPA entity mapping `quorum_decisions` exactly as shipped (T02): `id`, `chain`,
  `txHash`, `factType`, `outcome`, `agreeingCount`, `providerCount`, `decidedAt`. Fully immutable
  post-construction — no setters. Package-private no-arg constructor, public static `create(...)`
  factory. **Documentation note (Amendment #5, partial accept): this entity does not, and will not in
  this task, store the agreed-upon value itself** — only `outcome`/`agreeingCount`/`providerCount`,
  exactly as `V1__chain_baseline.sql` shipped it. Downstream tasks that need the actual agreed value
  (finality policy, attest, event emission) must recompute it themselves (e.g., from `observations` via
  `(chain, txHash, factType)`, or by re-deriving from whatever triggered the evaluation) — **no
  recomputation contract is specified here**; the first task that actually needs the agreed value must
  define it. A schema column for the agreed value was considered and explicitly **rejected for this
  task**: no consumer of it exists yet in this task's own scope, and guessing its shape (a single
  `TEXT`/`JSONB` column spanning `Boolean`/`BigDecimal`/`String`/integer-typed facts) risks getting it
  wrong before a real consumer defines the requirement.
- **`QuorumDecisionRepository`** — `JpaRepository<QuorumDecision, Long>`, package-private, derived-query
  finders only.
- **`HeldFactAlerter`** — invoked only on `HELD`, receiving the full `List<ProviderAnswer<T>>` so its
  log line can name each disagreeing provider and value. Implemented as a structured, distinctly-leveled
  log line. **Documentation clarification (Amendment #8): this is an explicitly interim
  implementation** — no external paging/webhook integration exists anywhere in this codebase yet;
  agents.md's "paged metrics" observability aspiration is a platform-level concern for a future
  metrics/alerting task and is **not** claimed as satisfied by this task alone. `HeldFactAlerter` is
  kept as its own class specifically so a real paging integration can replace its internals later
  without touching the coordinator or `QuorumEvaluator`.
- `decidedAt` set from the existing injected `Clock` bean (`common/ClockConfig`, T04).
- **`factType` reuses `observation.FactType`** (T08) directly — no second, parallel enum.
- **Documentation note (Amendment #9, cross-task, R4/L3 ordering — partial accept):** this task does not
  call `ObservationLog.record(...)` internally and does not enforce, in code, that observations were
  persisted before a `QuorumDecision` is written. **The caller contract is:** whatever future
  orchestrator wires provider responses together (most likely the watcher layer, task 15/16) must call
  `ObservationLog.record(...)` for each raw provider response *before* invoking this task's quorum
  coordinator — this is the same cross-task ordering gap T08's own frozen brief (Amendment #10) already
  flagged as belonging to "task 9 or a later end-to-end integration test." Internalizing the
  `ObservationLog` call into this task's coordinator was considered and **rejected**: it would reach
  back into T08's already-shipped boundary and expand this task beyond its own frozen statement (which
  names only `QuorumEvaluator`/`HeldFactAlerter`/`QuorumDecision`). The full R4 ordering guarantee
  remains open for the future orchestrating task to close, exactly as T08 already anticipated.
- **Documentation note (Amendment #7, cross-task, reorg re-evaluation):** `chain.quorum_decisions` has
  `UNIQUE (chain, tx_hash, fact_type)` and an `INSERT, SELECT`-only grant — a fact can be decided exactly
  once per launch scope; this task implements no re-evaluation/retry/update path. A chain reorg
  invalidating a previously-decided fact (task 18, `ReorgDetector`) will need either a schema change
  (removing or relaxing the unique constraint) or a separate "quorum decision revisions" mechanism —
  **flagged now as a known cross-task dependency so task 18 does not silently attempt an `UPDATE` this
  service's own DB grant would reject.**

**Out:**
- Fetching provider answers from `ChainAdapter`s.
- `UNKNOWN_TOKEN` production logic (task 11).
- Any `chain.*` Kafka event emission.
- `ProviderHealth`/`chain.provider.degraded` (task 10).
- Manual `HELD`-resolution tooling/UI.
- A schema column for the agreed value (considered, rejected — see Amendment #5 above).
- Internalizing `ObservationLog.record` calls into this task's coordinator (considered, rejected — see
  Amendment #9 above).
- Any change to `V1__chain_baseline.sql`/`V2__crypto_app_role_and_grants.sql` (T02, frozen).
- Re-evaluation/revision handling for reorgs (task 18's concern — see Amendment #7 above).

## Business Rules

- **R1.** A fact is treated as true only when at least 2-of-3 independent providers agree (exactly 3
  answers required per Amendment #3/#10; "AGREED" means value consensus, not boolean truth, per
  Amendment #4).
- **R2.** Provider disagreement → `HELD`, ops-alerted (interim log-based, per Amendment #8), no
  downstream event emitted.
- **R3.** A `HELD` fact is never auto-resolved, in any party's favor or otherwise.

## Locked Decisions

- **L1.** 2-of-3 quorum, no single-provider truth — not a tunable that can be disabled; implemented as
  exactly-3-answers, not a generalized N-of-M rule (Amendment #3/#10).
- **L2.** Disagreement → `HELD`, ops-alerted, never auto-resolved.

## Amendments folded in from Phase 3 (Kimi) design challenge, human-approved 2026-09-03

All 10 Phase 3 findings accepted, 2 with a partial rejection of one sub-option each (both documented
above and in the numbered list):

1. `QuorumEvaluator` generic bound changed to `Comparable<T>`, grouping via `compareTo()==0` — fixes
   `BigDecimal` scale sensitivity (Kimi Issue 1).
2. `null` elements in the answers list rejected; absence, not `null`, represents "no answer" (Kimi
   Issue 2).
3. Exactly-3-answers requirement — eliminates the 2-2-tie scenario structurally (Kimi Issue 3, merged
   with #10).
4. Documentation clarification: `AGREED` means consensus, not boolean truth (Kimi Issue 4).
5. Documented recomputation-contract gap for the agreed value; **schema column rejected** — no consumer
   defined in this task's scope (Kimi Issue 5, partial accept).
6. `ProviderAnswer<T>` introduced at the coordinator boundary; duplicate-provider input rejected (Kimi
   Issue 6).
7. Documented `quorum_decisions`' single-decision-per-fact limitation as task 18's cross-task concern
   (Kimi Issue 7).
8. `HeldFactAlerter` explicitly framed as an interim, log-based implementation; alert content enriched
   via `ProviderAnswer` (Kimi Issue 8).
9. Documented the caller contract for R4/L3 observation-before-decision ordering; **internalizing
   `ObservationLog` into this task's coordinator rejected** — out of this task's own scope (Kimi Issue 9,
   partial accept).
10. Merged into #3 — exactly-3-answers requirement resolves the generalized-N concern (Kimi Issue 10).

**10 accepted (8 in full, 2 with one sub-option each rejected), 0 findings dismissed outright.**

## Dependencies

- `chain.quorum_decisions` (T02, fixed schema, `INSERT, SELECT`-only grant).
- `observation.FactType` (T08) — reused directly.
- `Clock` bean (`common/ClockConfig`, T04).
- No new `@ConfigurationProperties` record, no new external library dependency.

## Inputs

- `(chain, txHash, factType, List<ProviderAnswer<T>> answers)` into the coordinator, where `T extends
  Comparable<T>`; the coordinator extracts a plain, non-null, exactly-3-element `List<T>` for the pure
  `QuorumEvaluator`. No real caller exists in this task's own scope.

## Outputs

- A persisted `chain.quorum_decisions` row (`outcome`, `agreeingCount`, `providerCount`, `decidedAt`)
  for every evaluation, `AGREED` or `HELD`.
- A distinctly-logged alert (via `HeldFactAlerter`, carrying per-provider detail) for every `HELD`
  outcome, never for `AGREED`.

## State Changes

New rows in `chain.quorum_decisions` (insert-only, one per evaluated fact — the `UNIQUE (chain, tx_hash,
fact_type)` constraint means this task's own scope evaluates each fact exactly once; re-evaluation is
explicitly out of scope, see Amendment #7).

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumEvaluator.java`
- `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumOutcome.java`
- `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumDecision.java`
- `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumDecisionRepository.java`
- `services/crypto/src/main/java/com/themistra/crypto/quorum/HeldFactAlerter.java`
- `services/crypto/src/main/java/com/themistra/crypto/quorum/ProviderAnswer.java`
- The coordinating class (exact name Phase 5) under `quorum/`.

## Files to Modify

None expected.

## Files NOT to Modify

- `V1__chain_baseline.sql`, `V2__crypto_app_role_and_grants.sql`, `V3__crypto_app_outbox_grant.sql`
  (T02) — frozen.
- `observation/FactType.java` (T08) — consumed/reused, not modified.
- `observation/ObservationLog.java` (T08) — referenced in documentation only (Amendment #9); not called,
  not modified.
- `common/ClockConfig.java` (T04) — consumed, not modified.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R1, L1).** `QuorumEvaluator` returns `AGREED` when the largest `compareTo()==0`-matching group
  among exactly 3 non-null answers has size ≥2; `HELD` otherwise. Any list not of size exactly 3, or
  containing a `null` element, is rejected with `IllegalArgumentException`.
- **AC2 (R2, L2).** On `HELD`, the coordinator invokes `HeldFactAlerter` (with full per-provider detail)
  and persists a `QuorumDecision` row with `outcome = HELD`; no Kafka event is emitted.
- **AC3 (R3, L2).** No code path anywhere in `quorum/` flips a persisted `HELD` decision to `AGREED` (or
  vice versa) or otherwise resolves it automatically.
- **AC4 (schema-conformance).** `QuorumDecision` persists `agreeingCount` and `providerCount` exactly as
  `QuorumEvaluator` computed them.
- **AC5 (grant-enforced).** No code path in `QuorumDecision`/`QuorumDecisionRepository` produces an
  `UPDATE` or `DELETE` against `chain.quorum_decisions`.
- **AC6 ("Unit-test the agreement matrix exhaustively").** Every distinct 3-answer agreement pattern is
  covered: all three match (AGREED, agreeingCount=3), each of the three possible 2-1 splits (AGREED,
  agreeingCount=2), and all three distinct/no pairwise match (HELD, agreeingCount=1).
- **AC7 (HeldFactAlerter invocation discipline).** `HeldFactAlerter` is invoked if and only if the
  outcome is `HELD`.
- **AC8 (Amendment #6, duplicate-provider rejection).** The coordinator rejects (fails fast) a
  `List<ProviderAnswer<T>>` containing two entries with the same `provider` name.
- **AC9 (Amendment #1, scale-invariant comparison).** Two `BigDecimal` `AMOUNT` answers with the same
  numeric value but different scale (e.g., `"1.0"` and `"1.00"`) are treated as matching.

## Required Tests

- `shouldTreatFactAsTrueOnlyWhenTwoOfThreeProvidersAgree` (package.md §8, named) — AC1.
- `shouldHoldFactAndAlertWhenProvidersDisagree` (package.md §8, named) — AC2, AC7.
- `shouldNeverAutoResolveDisagreementInPayersFavor` (package.md §8, named) — AC3.
- Exhaustive agreement-matrix tests on `QuorumEvaluator` directly: all-match, each 2-1 split, all-distinct
  — AC1, AC6.
- A test asserting a list of size other than 3 (0, 1, 2, or 4+) is rejected — AC1.
- A test asserting a `null` element in the list is rejected — AC1.
- A test asserting `BigDecimal("1.0")` and `BigDecimal("1.00")` are treated as matching — AC9.
- A test asserting `agreeingCount`/`providerCount` on the persisted `QuorumDecision` match what
  `QuorumEvaluator` computed, on both `AGREED` and `HELD` — AC4.
- A test asserting `QuorumDecision` has no mutator beyond construction — AC5.
- A test asserting `HeldFactAlerter` is invoked exactly on `HELD`, never on `AGREED` — AC7.
- A test asserting duplicate-provider input to the coordinator is rejected — AC8.
- A test (Docker-gated, mirroring `ObservationRepositoryIntegrationTest`) asserting an attempted
  `UPDATE`/`DELETE` against a persisted `QuorumDecision` fails at the database layer — AC5.

## Constraints

- **Transaction:** `QuorumDecisionRepository.save` is the only database write; `SimpleJpaRepository.save`'s
  own individual `@Transactional` is sufficient, no explicit `@Transactional` needed on the coordinator.
- **Thread-safety:** `QuorumEvaluator` is stateless and trivially thread-safe; the coordinator holds only
  injected, individually thread-safe collaborators.
- **Money (agents.md):** `AMOUNT`-typed answers are `BigDecimal`, compared via `compareTo()==0` (scale-
  invariant exact value equality, per Amendment #1) — no rounding or tolerance window.
- **Secrets:** no secret material is handled anywhere in this task's scope.
- **Null handling:** `QuorumEvaluator` rejects a `null`/empty/wrong-size answers list and any `null`
  element within it, fast, via `IllegalArgumentException` with a distinguishing message per case.

## Open Questions

No blockers. All Phase 3 findings are resolved above (folded as design/documentation amendments, with
two sub-options explicitly rejected and reasoned).
