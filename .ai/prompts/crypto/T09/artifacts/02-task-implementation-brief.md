# crypto · T09 · Phase 2 — Task Implementation Brief (TIB)

## Task

Quorum evaluator. Implement `QuorumEvaluator` (pure 2-of-3 logic): `AGREED` needs ≥2 matching;
disagreement → `HELD` + `HeldFactAlerter` + persisted `QuorumDecision`; never auto-resolve (L1, L2,
R1–R3). Unit-test the agreement matrix exhaustively.

## Purpose

The arbitration core the entire platform exists to provide (package.md: "this service's single largest
correctness risk"): the rule that no single provider's answer ever becomes fact. Every other quorum-
adjacent task (T08's observation log, T10's provider health) surrounds this one — T09 is the actual
2-of-3 decision itself, and the point at which disagreement is deliberately never resolved
automatically, in any direction.

## Scope

**In:**
- **`QuorumEvaluator`** — genuinely pure logic (design.md §6's own label: "2-of-3, pure logic"), no
  injected dependencies, no I/O. Public contract: given a list of already-fetched provider answers for
  one fact (generic over the answer's value type — `Boolean` for `EXISTENCE`/`FINALITY`, `BigDecimal`
  for `AMOUNT`, `String` for `TOKEN`, an integral type for `CONFIRMATIONS`), determine the largest group
  of mutually-`equals()`-matching answers. `agreeingCount` = that largest group's size; `providerCount`
  = the total answers given; outcome = `AGREED` when `agreeingCount ≥ 2`, else `HELD`. **Resolves Phase
  1 Open Question ("exact input shape"):** the evaluator does not call `ChainAdapter`s itself and does
  not know about `chain`/`txHash`/`factType` — fetching answers is the caller's job (a future task; no
  such caller exists in this task's own scope, mirroring T08's Observation precedent). Agreement is
  purely `equals()`-based; this task introduces **no per-fact-type comparison logic** (no tolerance
  window for `CONFIRMATIONS`, no rounding for `AMOUNT`) — the caller is responsible for supplying
  already-normalized, directly-comparable values (matches agents.md's `NUMERIC`/`BigDecimal`-exact money
  rule: `AMOUNT` agreement is exact equality, not "close enough").
- A generic algorithm, not one hardcoded to exactly 3 answers: `agreeingCount ≥ 2` is evaluated against
  whatever list size the caller supplies (design.md §4b-O1 recommends fixed 2-of-3 for launch, but the
  algorithm itself does not assume `providerCount == 3` — a 2-answer or 4-answer list is handled by the
  same rule with no special-casing, which is what makes the "N fixed at 3 vs. configurable per chain"
  question, Q1, a non-blocker for this task specifically).
- **A new coordinating class (exact name Phase 5 — same "functionally necessary, not spec-named"
  situation `ObservationLog` was in for T08; design.md §6's `quorum/` package list names no such class
  either, exactly as it named none for `observation/` before T08 needed one anyway)** that: takes
  `(chain, txHash, factType, List<T> answers)`, calls `QuorumEvaluator` for the pure outcome/counts,
  calls `HeldFactAlerter` when the outcome is `HELD`, builds and persists a `QuorumDecision` via
  `QuorumDecisionRepository` in both cases, and returns the persisted decision.
- **`QuorumOutcome`** — enum `AGREED, HELD, UNKNOWN_TOKEN` (design.md §4c, VERBATIM — copy exactly,
  including the value this task never produces at runtime).
- **`QuorumDecision`** — JPA entity mapping `quorum_decisions` exactly as shipped (T02): `id` (DB
  IDENTITY), `chain`, `txHash`, `factType`, `outcome`, `agreeingCount`, `providerCount`, `decidedAt`.
  **Fully immutable post-construction** — no setters — matching `crypto_app`'s actual `INSERT,
  SELECT`-only grant, the same discipline `Observation` (T08) already established for the identical
  grant shape. Package-private no-arg constructor for JPA, public static `create(...)` factory
  (mirrors `Observation.create(...)`/`OutboxEvent.create(...)` exactly).
- **`QuorumDecisionRepository`** — `JpaRepository<QuorumDecision, Long>`, package-private, derived-query
  finders only (mirrors `ObservationRepository`).
- **`HeldFactAlerter`** — invoked only on `HELD`. No alerting/paging integration exists anywhere in this
  codebase yet (Phase 0 finding). **Resolves Phase 1 Open Question:** this task implements the alert as
  a structured, distinctly-leveled log line (mirrors `ObservationSnapshotStore`'s own "logged distinctly"
  precedent for its S3-failure case, T08) — no external paging/webhook integration is built in this
  task's own scope; agents.md's "paged metrics" observability aspiration is a platform-level concern for
  a future metrics/alerting task, not something T09 must wire end-to-end. `HeldFactAlerter` is kept as
  its own small class (rather than an inline log call in the coordinator) specifically so a real paging
  integration can replace its internals later without touching the coordinator or `QuorumEvaluator`.
- `decidedAt` set from the existing injected `Clock` bean (`common/ClockConfig`, T04), matching
  `Observation.observedAt`/`OutboxEvent.createdAt`'s established discipline.
- **`factType` reuses `observation.FactType`** (T08) rather than introducing a second, parallel enum.
  **Resolves Phase 1 Open Question:** `quorum_decisions.fact_type` and `observations.fact_type` share
  the identical five-value vocabulary (`V1__chain_baseline.sql`'s own column comments name the same
  five values for both tables) — introducing a second enum would duplicate that vocabulary with no
  behavioral difference and risk the two drifting apart. `observation/` and `quorum/` are sibling
  packages (design.md §6), and T08's `L15` ("no cross-feature-module import") governs imports of
  *entities*/*services* across feature packages, not a small, stable, spec-fixed enum with an
  already-established JPA converter pattern (`FactType.DbConverter`) this task can reuse directly rather
  than reimplementing.

**Out:**
- Fetching provider answers from `ChainAdapter`s — no such orchestration exists in this task's own
  scope; `QuorumEvaluator`'s caller (a future task) is responsible for the fan-out.
- `UNKNOWN_TOKEN` production logic (token-allowlist validation is task 11) — the enum value exists per
  the VERBATIM artifact, but no code path in this task ever produces it.
- Any `chain.*` Kafka event emission — R2 explicitly forbids an event on `HELD`; no event is emitted on
  `AGREED` either in this task's own scope (task 15+ owns `chain.tx.*` events).
- `ProviderHealth`/`chain.provider.degraded` (task 10).
- Manual `HELD`-resolution tooling/UI — R3 only requires this task never *attempt* auto-resolution;
  building the manual-resolution path itself is out of scope (not named anywhere in `tasks.md`).
- Any change to `V1__chain_baseline.sql`/`V2__crypto_app_role_and_grants.sql` (T02, frozen).

## Business Rules

- **R1.** A fact is treated as true only when at least 2-of-3 independent providers agree.
- **R2.** Provider disagreement → `HELD`, ops-alerted, no downstream event emitted.
- **R3.** A `HELD` fact is never auto-resolved, in any party's favor or otherwise.

## Locked Decisions

- **L1.** 2-of-3 quorum, no single-provider truth — not a tunable that can be disabled.
- **L2.** Disagreement → `HELD`, ops-alerted, never auto-resolved.

## Dependencies

- `chain.quorum_decisions` (T02, fixed schema, `INSERT, SELECT`-only grant).
- `observation.FactType` (T08) — reused directly, see Scope.
- `Clock` bean (`common/ClockConfig`, T04).
- No new `@ConfigurationProperties` record — the 2-of-3 threshold is LOCKED, not configurable.
- No new external library dependency — this task adds no new `pom.xml` entry.

## Inputs

- `(chain, txHash, factType, List<T> answers)` into the coordinator; `List<T> answers` alone into the
  pure `QuorumEvaluator`. No real caller exists in this task's own scope — its own tests are the only
  caller, exactly as T08's `Observation`/`ObservationSnapshotStore` had no real caller until this task.

## Outputs

- A persisted `chain.quorum_decisions` row (`outcome`, `agreeingCount`, `providerCount`, `decidedAt`)
  for every evaluation, `AGREED` or `HELD`.
- A distinctly-logged alert (via `HeldFactAlerter`) for every `HELD` outcome, never for `AGREED`.

## State Changes

New rows in `chain.quorum_decisions` (insert-only, one per evaluated fact — `UNIQUE (chain, tx_hash,
fact_type)` means a second evaluation of the identical `(chain, txHash, factType)` triple would violate
that constraint at the database layer; this task's own scope evaluates each fact once and does not
implement re-evaluation/retry logic, which is left to a future task if ever needed).

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumEvaluator.java`
- `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumOutcome.java`
- `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumDecision.java`
- `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumDecisionRepository.java`
- `services/crypto/src/main/java/com/themistra/crypto/quorum/HeldFactAlerter.java`
- The coordinating class (exact name Phase 5) under `quorum/`.

## Files to Modify

None expected. (No `pom.xml` change — no new dependency. No `application.properties` change — no new
config surface.)

## Files NOT to Modify

- `V1__chain_baseline.sql`, `V2__crypto_app_role_and_grants.sql`, `V3__crypto_app_outbox_grant.sql`
  (T02) — frozen; `chain.quorum_decisions`' shape and grants are consumed exactly as shipped.
- `observation/FactType.java` (T08) — consumed/reused, not modified.
- `common/ClockConfig.java` (T04) — consumed, not modified.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R1, L1).** `QuorumEvaluator` returns `AGREED` when the largest matching-answer group has size
  ≥2; `HELD` otherwise — deterministic, no I/O.
- **AC2 (R2, L2).** On `HELD`, the coordinator invokes `HeldFactAlerter` and persists a `QuorumDecision`
  row with `outcome = HELD`; no Kafka event is emitted (no such call exists anywhere in this task's
  code).
- **AC3 (R3, L2).** No code path anywhere in `quorum/` flips a persisted `HELD` decision to `AGREED` (or
  vice versa) or otherwise resolves it automatically — enforced structurally by `QuorumDecision` having
  no mutator and `QuorumDecisionRepository` never being called with an update-style operation.
- **AC4 (design.md §6, schema-conformance).** `QuorumDecision` persists `agreeingCount` and
  `providerCount` exactly as `QuorumEvaluator` computed them.
- **AC5 (grant-enforced, mirrors T08 AC3).** No code path in `QuorumDecision`/`QuorumDecisionRepository`
  produces an `UPDATE` or `DELETE` against `chain.quorum_decisions`.
- **AC6 ("Unit-test the agreement matrix exhaustively," task statement).** Every distinct 3-answer
  agreement pattern is covered: all three match (AGREED, agreeingCount=3), each of the three possible
  2-1 splits (AGREED, agreeingCount=2), and all three distinct/no pairwise match (HELD, agreeingCount=1).
- **AC7 (HeldFactAlerter invocation discipline).** `HeldFactAlerter` is invoked if and only if the
  outcome is `HELD` — never on `AGREED`.

## Required Tests

- `shouldTreatFactAsTrueOnlyWhenTwoOfThreeProvidersAgree` (package.md §8, named) — AC1.
- `shouldHoldFactAndAlertWhenProvidersDisagree` (package.md §8, named) — AC2, AC7.
- `shouldNeverAutoResolveDisagreementInPayersFavor` (package.md §8, named) — AC3.
- Exhaustive agreement-matrix tests on `QuorumEvaluator` directly (no mocks needed — pure logic) — AC1,
  AC6: all-match, each 2-1 split, all-distinct.
- A boundary test for fewer than 3 answers (e.g., 2 answers, both matching → `AGREED`; 1 answer → can
  never reach `agreeingCount ≥ 2`, so `HELD`) — proves the algorithm generalizes rather than hardcoding
  `providerCount == 3`.
- A test asserting `agreeingCount`/`providerCount` on the persisted `QuorumDecision` match what
  `QuorumEvaluator` computed, on both `AGREED` and `HELD` (AC4).
- A test asserting `QuorumDecision` has no mutator beyond construction (AC5), mirroring
  `ObservationTest.hasNoPublicMutatorBeyondConstruction`.
- A test asserting `HeldFactAlerter` is invoked exactly on `HELD`, never on `AGREED` (AC7), via
  Mockito `verify`/`verifyNoInteractions` on a mocked `HeldFactAlerter`.
- A test (Docker-gated, mirroring `ObservationRepositoryIntegrationTest`) asserting an attempted
  `UPDATE`/`DELETE` against a persisted `QuorumDecision` fails at the database layer (AC5).

## Constraints

- **Transaction:** the `QuorumDecisionRepository.save` call is the only database write; no S3 or
  external I/O is part of this task (unlike T08), so no connection-pool-hold-during-network-I/O concern
  exists — `SimpleJpaRepository.save`'s own individual `@Transactional` (confirmed by source reading in
  T08) is sufficient with no explicit `@Transactional` needed on the coordinator, mirroring T08's own
  resolved Phase 9 finding.
- **Thread-safety:** `QuorumEvaluator` is stateless (no injected dependencies, no shared mutable state)
  and trivially thread-safe; the coordinator holds only injected, individually-thread-safe collaborators
  (`Repository`, `Clock`, `HeldFactAlerter` itself expected to be stateless/thread-safe).
- **Money (agents.md):** `AMOUNT`-typed answers are compared for exact equality via `BigDecimal.equals()`
  semantics as supplied by the caller — this task performs no independent monetary parsing or rounding.
- **Secrets:** no secret material is handled anywhere in this task's scope.
- **Null handling:** `QuorumEvaluator` rejects a `null` or empty answers list fast (`IllegalArgumentException`,
  mirroring `ObservationSnapshotStore`'s `Objects.requireNonNull` discipline from T08) rather than
  silently computing a meaningless result — an empty list has no answers to compare, and evaluating it
  would either throw obscurely or produce a misleading `HELD` with `providerCount = 0`.

## Open Questions

No blockers. All Phase 1 open items (QuorumEvaluator's input shape, HeldFactAlerter's mechanism,
FactType reuse) are resolved above.
