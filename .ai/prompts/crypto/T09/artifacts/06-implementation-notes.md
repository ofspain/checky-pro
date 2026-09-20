# crypto · T09 · Phase 6 — Implementation Notes

## What changed

Seven new files under `services/crypto/src/main/java/com/themistra/crypto/quorum/`, exactly as
planned (Phase 5) — no file the frozen brief did not authorize was touched, and no existing file was
modified.

- **`ProviderAnswer.java`** — `record ProviderAnswer<T>(String provider, T value)`, compact constructor
  rejects `null` `provider`/`value` via `Objects.requireNonNull`.
- **`QuorumOutcome.java`** — enum `AGREED, HELD, UNKNOWN_TOKEN`, copied VERBATIM per design.md §4c.
- **`QuorumEvaluator.java`** — pure logic, `<T extends Comparable<T>> Result evaluate(List<T> answers)`.
  `validate` rejects `null`, wrong-size (≠3), and any `null` element. `largestMatchingGroupSize` does
  pairwise `compareTo() == 0` comparison across exactly 3 elements — no general grouping algorithm
  needed given the fixed size. Nested `record Result(QuorumOutcome outcome, int agreeingCount, int
  providerCount)` — not a new top-level file, per the plan's own reasoning (mirrors `FactType.DbConverter`
  being nested inside its owner).
- **`QuorumDecision.java`** — JPA entity mapping `quorum_decisions` exactly as shipped. Fully immutable
  post-construction, package-private no-arg constructor, public static `create(...)`. `outcome` uses
  `@Enumerated(EnumType.STRING)` (the DB's own `CHECK` constraint lists the exact uppercase enum names,
  confirmed by reading `V1__chain_baseline.sql:133` directly). `agreeingCount`/`providerCount` map to
  `short` (direct `SMALLINT` mapping — this is the first entity in the codebase to touch a `SMALLINT`
  column).
- **`QuorumDecisionRepository.java`** — package-private `JpaRepository<QuorumDecision, Long>` plus one
  derived finder.
- **`HeldFactAlerter.java`** — `@Component`, single error-level structured log line naming
  `chain`/`txHash`/`factType` and every `(provider, value)` pair.
- **`QuorumDecisionService.java`** — the coordinator (Phase 5-named). `evaluate(chain, txHash, factType,
  List<ProviderAnswer<T>> answers)`: rejects duplicate-provider input first, extracts values, calls
  `QuorumEvaluator`, alerts on `HELD`, persists via `QuorumDecisionRepository.save`, returns the
  persisted decision. Not `@Transactional` (mirrors T08's `ObservationLog` reasoning: `SimpleJpaRepository.save`
  is already individually transactional).

## Mapping to plan and acceptance criteria

| AC | Satisfied by |
|---|---|
| AC1 (exactly-3, non-null, `compareTo()==0`) | `QuorumEvaluator.validate` + `largestMatchingGroupSize` |
| AC2 (HELD → alert + persist, no event) | `QuorumDecisionService.evaluate` — alerts iff `HELD`; no Kafka/outbox call anywhere in this task's code |
| AC3 (never auto-resolve) | `QuorumDecision` has no mutator; `QuorumDecisionRepository` is never called with anything but `save` on a freshly-created instance |
| AC4 (counts persisted exactly) | `QuorumDecision.create(..., result.agreeingCount(), result.providerCount(), ...)` |
| AC5 (no UPDATE/DELETE) | No such call exists in `quorum/`; DB grant enforces it structurally |
| AC6 (exhaustive matrix) | `largestMatchingGroupSize`'s 3-branch logic covers all 5 meaningful 3-element patterns; tests are Phase 10 |
| AC7 (alerter iff HELD) | The single `if (result.outcome() == QuorumOutcome.HELD)` branch in `QuorumDecisionService.evaluate` |
| AC8 (duplicate-provider rejected) | `QuorumDecisionService.rejectDuplicateProviders`, called before any collaborator |
| AC9 (scale-invariant amount comparison) | `compareTo() == 0` instead of `equals()` in `QuorumEvaluator` |

## Deviation forced by reality

**`FactType.DbConverter` is package-private to `observation`, unreachable from `quorum/`.** The frozen
brief (Phase 4, Amendment resolving Phase 1's open question) decided `QuorumDecision.factType` would
reuse `observation.FactType` directly, and separately listed `observation/FactType.java` under "Files
NOT to Modify." Neither Phase 2 nor Phase 4 anticipated that `FactType`'s own JPA converter
(`FactType.DbConverter`) is a package-private nested class, not accessible from a sibling package — this
surfaced only as a compiler error during implementation (`mvn -pl services/crypto compile`).

Two fixes were possible: (a) widen `FactType.DbConverter` to `public` in `observation/FactType.java`, or
(b) duplicate the identical lowercase-mapping converter locally within `quorum/`. **Chose (b)** —
`QuorumDecision.FactTypeDbConverter`, a nested package-private static class inside `QuorumDecision.java`,
byte-for-byte the same conversion logic as `FactType.DbConverter` — because the frozen brief's explicit
"Files NOT to Modify" commitment for `observation/FactType.java` was human-approved at the Phase 4 gate,
and widening a T08-frozen file's visibility, however small, would violate that commitment without a new
approval. This is a small, deliberate DRY tradeoff (two copies of the same six-line mapping) in favor of
respecting the approved file boundary; `FactType` itself (the enum, and the entity field type) is still
genuinely reused, only its converter is duplicated.

`mvn -pl services/crypto compile` and `mvn -pl services/crypto test-compile` both succeed cleanly after
this fix, with zero other warnings.
