# crypto · T09 · Phase 1 — Specification Extraction

## Business Rules

- **R1.** When a verification fact is needed, the system fetches it from N independent providers and
  treats it as true only when at least 2-of-3 agree.
- **R2.** If providers disagree on a fact, the system marks the fact `HELD`, alerts ops, and does not
  emit a downstream event for it.
- **R3.** If a fact is `HELD` due to disagreement, the system never auto-resolves it in any party's
  favor; resolution is manual only.

## Locked Decisions

- **L1.** 2-of-3 quorum, no single-provider truth — every emitted fact requires ≥2-of-3 independent
  providers to agree; this is the service's reason to exist, not a tunable that can be disabled.
- **L2.** Disagreement → `HELD`, ops-alerted, never auto-resolved — never silently resolved and never
  resolved in any party's favor.

## Files involved

**Existing, to read/extend (no modification unless explicitly named):**
- `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql` — `quorum_decisions` table
  already shipped (T02, frozen; lines 121-133): `id, chain, tx_hash, fact_type, outcome, agreeing_count,
  provider_count, decided_at`, `UNIQUE (chain, tx_hash, fact_type)`, `CHECK outcome IN
  ('AGREED','HELD','UNKNOWN_TOKEN')`.
- `services/crypto/src/main/resources/db/migration/V2__crypto_app_role_and_grants.sql:34` —
  `crypto_app` has `INSERT, SELECT` only on `chain.quorum_decisions` (no `UPDATE`/`DELETE`).
- `services/crypto/src/main/java/com/themistra/crypto/observation/FactType.java` (T08) — candidate
  fact-type vocabulary; reuse-vs-new-type is a Phase 2 design decision (see Phase 0 artifact).
- `services/crypto/src/main/java/com/themistra/crypto/common/ClockConfig.java` (T04) — injectable
  `Clock` bean, expected for `QuorumDecision.decidedAt`.
- `services/crypto/src/main/java/com/themistra/crypto/adapter/ChainAdapter.java`,
  `adapter/model/*` (T05) — design.md §4c: "the `quorum` module fans a fact out across the provider
  adapters for a chain and compares" — the conceptual source of the per-provider answers
  `QuorumEvaluator` compares, though the task statement calls the evaluator itself "pure logic," so the
  exact input type (raw adapter results vs. an already-fetched value collection) is a Phase 2 decision.
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxEvent.java` /
  `OutboxEventRepository.java` — pattern precedent only (protected no-arg ctor, static factory,
  getters-only), **not consumed at runtime**: R2 explicitly excludes a downstream event on `HELD`, and
  T09's own task statement lists no event on `AGREED` either (Kafka `chain.tx.*` events are task 15+).

**New, per design.md §6 (`quorum/` package):**
- `quorum/QuorumEvaluator.java` — pure 2-of-3 comparison logic.
- `quorum/QuorumOutcome.java` — enum `AGREED, HELD, UNKNOWN_TOKEN` (design.md §4c, VERBATIM — copy
  exactly; `UNKNOWN_TOKEN` is unreachable from this task's own logic since token-allowlist validation is
  task 11, but the enum's third value must still exist as specified).
- `quorum/QuorumDecision.java` / `quorum/QuorumDecisionRepository.java` — persistence, mapping
  `quorum_decisions` exactly as shipped.
- `quorum/HeldFactAlerter.java` — ops alert on `HELD` (L2). No alerting/paging client exists anywhere in
  this codebase yet (Phase 0 finding) — exact mechanism is a Phase 2 design decision, not extractable
  from the spec text itself (see Open Questions).

## Dependencies

- `Clock` bean (`common/ClockConfig`, T04).
- `chain.quorum_decisions` table (T02, frozen schema, `INSERT, SELECT`-only grant).
- `observation.FactType` enum (T08) — candidate reuse, decision deferred to Phase 2.
- No new `@ConfigurationProperties` record is expected: the 2-of-3 threshold is LOCKED (L1: "not a
  tunable"), and no other quorum-specific config key is named anywhere in `package.md`/`design.md`.
- No contract file (`contracts/api/crypto-internal.yaml`, `contracts/events/chain/*`) is touched by this
  task — T09 emits no HTTP response and no Kafka event; the header's listed contracts are the section's
  general scope, not specific to this task (same conclusion T08 already reached for the identical
  header list).
- `FakeChainAdapter` (T05) — the established scripted-provider-answer test double ("can agree, disagree,
  lag, and reorg"), the likely mechanism for scripting the exhaustive agreement-matrix tests this task
  requires, pending Phase 2's confirmation of `QuorumEvaluator`'s exact input signature.

## Acceptance Criteria

- **AC1 (R1, L1).** A fact is evaluated `AGREED` when at least 2 of the N (3) provider answers for that
  fact match; `HELD` otherwise. The threshold is fixed at 2-of-3, not configurable (L1; also `design.md`
  §4b-O1's own recommendation: "fixed 2-of-3 for launch").
- **AC2 (R2, L2).** On disagreement, the outcome is persisted as `HELD` via `QuorumDecisionRepository`,
  and `HeldFactAlerter` is invoked — no downstream (Kafka) event is emitted for a `HELD` fact.
- **AC3 (R3, L2).** No code path in `QuorumEvaluator`/`HeldFactAlerter`/`QuorumDecision` resolves a
  `HELD` outcome automatically, in any party's favor or otherwise — resolution is manual only (out of
  this task's own scope; this task's job is only to never attempt it).
- **AC4 (design.md §6).** `QuorumDecision` persists `agreeing_count` and `provider_count` alongside the
  `outcome`, matching `quorum_decisions`' own shipped columns exactly.
- **AC5 (grant-enforced, same discipline as T08 AC3).** No code path in `QuorumDecision`/
  `QuorumDecisionRepository` produces an `UPDATE` or `DELETE` against `chain.quorum_decisions` — the
  entity is fully immutable post-construction (mirrors `Observation`'s own T08 shape, forced by the
  identical `INSERT, SELECT`-only grant).
- **AC6 ("Unit-test the agreement matrix exhaustively," task statement).** Every provider-answer
  combination for N=3 (all-agree, 2-1 splits in each direction, all-disagree, i.e. every partition of 3
  answers) is covered by a test proving the correct `AGREED`/`HELD` outcome and correct
  `agreeingCount`/`providerCount`.

## Tests required

- `shouldTreatFactAsTrueOnlyWhenTwoOfThreeProvidersAgree` (package.md §8, named) — AC1.
- `shouldHoldFactAndAlertWhenProvidersDisagree` (package.md §8, named) — AC2.
- `shouldNeverAutoResolveDisagreementInPayersFavor` (package.md §8, named) — AC3.
- Exhaustive agreement-matrix tests covering every 3-provider-answer partition (all match, exactly 2
  match in each possible pairing, all three distinct/no match) — AC6.
- A test asserting `agreeingCount`/`providerCount` are recorded correctly on both `AGREED` and `HELD`
  outcomes — AC4.
- A test asserting `QuorumDecision` has no mutator beyond construction (AC5), mirroring
  `ObservationTest.hasNoPublicMutatorBeyondConstruction`.
- A test (Docker-gated, mirroring `ObservationRepositoryIntegrationTest`) asserting an attempted
  `UPDATE`/`DELETE` against a persisted `QuorumDecision` fails at the database layer (AC5).
- A test asserting `HeldFactAlerter` is invoked exactly when the outcome is `HELD`, and never when
  `AGREED` (AC2).

## Open Questions

No genuine blockers for this task's own scope. Two items are noted as **non-blocking Phase 2 design
decisions** (not cited in `package.md` §11's own Q1–Q9 list, so not treated as extraction-phase
blockers):
- Exact mechanism for `HeldFactAlerter`'s "ops alert" (structured log vs. a Micrometer metric vs. an
  external integration) — no precedent exists in this codebase yet (Phase 0 finding).
- Whether `QuorumDecision.factType` reuses `observation.FactType` or defines its own type, given
  `quorum/` and `observation/` are sibling packages per design.md §6.

`package.md` §11's own Q1 ("is N fixed at 3 with 2-of-3, or configurable per chain?") is **not** a
blocker for this task: both the task statement ("pure 2-of-3 logic") and design.md §4b-O1's own
recommendation ("fixed 2-of-3 for launch") settle it for T09's own scope — Q1 remains open only for the
separate, later concern of *which* 3 commercial providers are wired per chain (`ProviderSet`, not named
in this task).
