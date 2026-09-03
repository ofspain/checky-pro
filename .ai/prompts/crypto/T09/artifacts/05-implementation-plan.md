# crypto · T09 · Phase 5 — Implementation Plan

Coordinator class name chosen: **`QuorumDecisionService`** (distinct from `QuorumEvaluator`, mirrors
`ObservationLog`'s naming precedent — names the persisted concept it produces).

## Files to create

All seven trace directly to the frozen brief's "Files to Create" list — no additional file added:

1. `services/crypto/src/main/java/com/themistra/crypto/quorum/ProviderAnswer.java`
2. `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumOutcome.java`
3. `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumEvaluator.java`
4. `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumDecision.java`
5. `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumDecisionRepository.java`
6. `services/crypto/src/main/java/com/themistra/crypto/quorum/HeldFactAlerter.java`
7. `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumDecisionService.java` (the
   coordinator)

`QuorumEvaluator`'s result value (`outcome`, `agreeingCount`, `providerCount`) is a **nested record
inside `QuorumEvaluator.java`** (`QuorumEvaluator.Result`), not a new top-level file — mirrors
`FactType.DbConverter` (T08), a nested static type inside its owning class rather than a file the
frozen brief didn't authorize.

Test files (all under `services/crypto/src/test/java/com/themistra/crypto/quorum/`):
8. `QuorumEvaluatorTest.java`
9. `QuorumDecisionTest.java`
10. `HeldFactAlerterTest.java`
11. `QuorumDecisionServiceTest.java`
12. `QuorumDecisionRepositoryIntegrationTest.java` (Docker-gated, mirrors
    `ObservationRepositoryIntegrationTest`)

## Files to modify

None. (Frozen brief: "Files to Modify: None expected.")

## Public methods (signatures)

**`ProviderAnswer<T>`** (record):
```java
public record ProviderAnswer<T>(String provider, T value) {
    public ProviderAnswer {
        // compact constructor: Objects.requireNonNull on both components (Amendment #2 — no null
        // answer values; a provider name is likewise never null)
    }
}
```

**`QuorumOutcome`** (enum, VERBATIM per design.md §4c):
```java
public enum QuorumOutcome { AGREED, HELD, UNKNOWN_TOKEN }
```

**`QuorumEvaluator`**:
```java
public class QuorumEvaluator {
    public <T extends Comparable<T>> Result<T> evaluate(List<T> answers);

    public record Result<T>(QuorumOutcome outcome, int agreeingCount, int providerCount) {}
}
```
Note: `Result` does not carry the agreed value itself (frozen brief Amendment #5 — no agreed-value
storage in this task's scope), only the outcome and counts; the `<T>` type parameter on `Result` is
therefore unused by its own fields and will be dropped in favor of a non-generic `Result` record during
implementation if Phase 6 finds the unused parameter triggers a compiler/linter warning — a naming
detail, not a behavioral one.

**`QuorumDecision`**:
```java
@Entity
@Table(name = "quorum_decisions", schema = "chain")
public class QuorumDecision {
    public static QuorumDecision create(String chain, String txHash, FactType factType,
            QuorumOutcome outcome, int agreeingCount, int providerCount, Instant decidedAt);

    public Long id();
    public String chain();
    public String txHash();
    public FactType factType();
    public QuorumOutcome outcome();
    public short agreeingCount();
    public short providerCount();
    public Instant decidedAt();
}
```
`agreeingCount`/`providerCount` map to `short` (Hibernate's direct mapping to `SMALLINT`) — this is the
first entity in this codebase to map a `SMALLINT` column; no existing precedent to mirror, decided here
as the most direct type match rather than widening to `int`/`Integer`.
`outcome` uses `@Enumerated(EnumType.STRING)`, **not** a custom `AttributeConverter` like
`FactType.DbConverter`: `quorum_decisions.outcome`'s own `CHECK` constraint
(`V1__chain_baseline.sql:133`) lists `'AGREED'`, `'HELD'`, `'UNKNOWN_TOKEN'` — the exact uppercase
`QuorumOutcome.name()` values — so no case-conversion is needed, unlike `fact_type`'s lowercase DB
convention.

**`QuorumDecisionRepository`** (package-private):
```java
interface QuorumDecisionRepository extends JpaRepository<QuorumDecision, Long> {
    Optional<QuorumDecision> findByChainAndTxHashAndFactType(String chain, String txHash, FactType factType);
}
```

**`HeldFactAlerter`**:
```java
@Component
public class HeldFactAlerter {
    public <T> void alert(String chain, String txHash, FactType factType, List<ProviderAnswer<T>> answers);
}
```
Logs a single structured, error-level line naming `chain`/`txHash`/`factType` and every
`(provider, value)` pair — the interim "ops alert" implementation (Amendment #8).

**`QuorumDecisionService`** (the coordinator):
```java
@Component
public class QuorumDecisionService {
    public QuorumDecisionService(QuorumEvaluator evaluator, QuorumDecisionRepository repository,
            HeldFactAlerter alerter, Clock clock);

    public <T extends Comparable<T>> QuorumDecision evaluate(String chain, String txHash,
            FactType factType, List<ProviderAnswer<T>> answers);
}
```

## Private methods

- `QuorumEvaluator`:
  - `private <T extends Comparable<T>> void validate(List<T> answers)` — rejects `null` list, wrong
    size (≠3), and any `null` element (`IllegalArgumentException`, distinct messages).
  - `private <T extends Comparable<T>> int largestMatchingGroupSize(T a, T b, T c)` — pairwise
    `compareTo()==0` comparison across exactly 3 elements (all-match → 3, exactly one matching pair →
    2, no matching pair → 1); no general grouping/sorting algorithm is needed since the exactly-3
    constraint (Amendment #3/#10) makes pairwise comparison exhaustive and simplest.
- `QuorumDecisionService`:
  - `private <T> void rejectDuplicateProviders(List<ProviderAnswer<T>> answers)` — `IllegalArgumentException`
    if any two entries share a `provider` name (Amendment #6).
  - `private <T> List<T> extractValues(List<ProviderAnswer<T>> answers)` — maps to the plain value list
    `QuorumEvaluator.evaluate` consumes.

## Entities used

- `QuorumDecision` (new, this task).
- `observation.FactType` (T08, reused — not a new entity, an existing `@Convert`-backed enum field
  type).

## Repositories used

- `QuorumDecisionRepository` (new, this task) — `.save(...)` only; no update/delete call anywhere.

## Services used

- `Clock` bean (`common/ClockConfig`, T04) — injected into `QuorumDecisionService` for `decidedAt`.
- No other existing service/component is consumed. `QuorumEvaluator` and `HeldFactAlerter` are new
  collaborators consumed by `QuorumDecisionService`, not pre-existing ones.

## Unit / integration tests required

**`QuorumEvaluatorTest`** (plain JUnit, no mocks — pure logic):
- `shouldTreatFactAsTrueOnlyWhenTwoOfThreeProvidersAgree` (named test) — 2-of-3 agree on `true`.
- Exhaustive matrix: all-3-match (`AGREED`, count=3); three distinct 2-1-split cases (`AGREED`,
  count=2, one per which-pair-matches); all-3-distinct (`HELD`, count=1).
- Rejects a list of size 0, 1, 2, and 4 (`IllegalArgumentException`).
- Rejects a `null` list and a list containing a `null` element.
- `BigDecimal("1.0")` vs `BigDecimal("1.00")` treated as matching (scale-invariant, Amendment #1).

**`QuorumDecisionTest`** (plain JUnit):
- Every field round-trips exactly as given via `create(...)`.
- No public mutator beyond construction (reflection-based, mirrors `ObservationTest`).

**`HeldFactAlerterTest`** (Logback `ListAppender`, mirrors `ObservationSnapshotStoreTest`'s pattern):
- `alert(...)` logs at error level and includes `chain`/`txHash`/`factType`/each provider name and
  value in the formatted message.

**`QuorumDecisionServiceTest`** (`@ExtendWith(MockitoExtension.class)`, mocked
`QuorumDecisionRepository`/`HeldFactAlerter`, real `QuorumEvaluator` instance — pure, cheap, no need to
mock it):
- `shouldHoldFactAndAlertWhenProvidersDisagree` (named test) — `HeldFactAlerter` invoked, `QuorumDecision`
  persisted with `outcome=HELD`.
- `shouldNeverAutoResolveDisagreementInPayersFavor` (named test) — asserts a `HELD` decision, once
  persisted, is never followed by any repository call other than the original `save` (no update path
  exists to assert against, so this test asserts the absence of any second interaction with the mocked
  repository after the first `save`).
- `HeldFactAlerter` invoked if and only if outcome is `HELD` (never on `AGREED`).
- Duplicate-provider input rejected before either collaborator is called
  (`verifyNoInteractions(repository, alerter)`).
- `agreeingCount`/`providerCount` on the persisted `QuorumDecision` match `QuorumEvaluator`'s computed
  values, for both `AGREED` and `HELD`.
- `decidedAt` uses the injected fixed `Clock`, not wall-clock time.

**`QuorumDecisionRepositoryIntegrationTest`** (Docker-gated Testcontainers, mirrors
`ObservationRepositoryIntegrationTest` exactly — narrow `@Configuration`, `@EntityScan`/
`@EnableJpaRepositories` scoped to this entity/repository, static `PostgreSQLContainer`, Flyway migrate
+ `crypto_app` password):
- A saved `QuorumDecision` round-trips every field, including the `FactType` conversion and
  `QuorumOutcome` enum mapping, against a real Postgres.
- An attempted `UPDATE`/`DELETE` against a persisted `QuorumDecision` fails at the database layer
  (`DataIntegrityViolationException`), proving `crypto_app`'s `INSERT, SELECT`-only grant.
- A second insert for the same `(chain, txHash, factType)` violates `uq_quorum_tx_fact` (documents the
  Amendment #7 constraint directly, at the DB layer).

## Execution order

1. `ProviderAnswer.java`, `QuorumOutcome.java` — no dependencies on anything else in this task.
2. `QuorumEvaluator.java` (+ `QuorumEvaluatorTest.java`) — pure logic, no dependency on the entity/repo;
   implement and test in isolation first, per the task statement's own emphasis ("Unit-test the
   agreement matrix exhaustively").
3. `QuorumDecision.java` (+ `QuorumDecisionTest.java`) — depends only on `FactType` (T08, existing) and
   `QuorumOutcome` (step 1).
4. `QuorumDecisionRepository.java` — depends on `QuorumDecision` (step 3).
5. `HeldFactAlerter.java` (+ `HeldFactAlerterTest.java`) — depends on `ProviderAnswer`/`FactType` only.
6. `QuorumDecisionService.java` (+ `QuorumDecisionServiceTest.java`) — composes steps 2, 4, 5, and
   `Clock` (T04, existing).
7. `QuorumDecisionRepositoryIntegrationTest.java` — exercises steps 3-4 against a real Postgres
   (Docker-gated; expected to compile but not execute in this environment, per every prior task this
   session).
8. Full `mvn -pl services/crypto test-compile` then targeted `mvn -pl services/crypto test -Dtest=...`
   for the five new unit-scope test classes, then a full `mvn -pl services/crypto -am test` regression
   pass.
