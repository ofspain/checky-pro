# crypto · T19 · Phase 5 — Implementation Plan

Every file below traces directly to the frozen brief's (`artifacts/04-frozen-task-brief.md`) Files to
Create section. No file outside that list is planned.

## Files to create

1. `services/crypto/src/main/resources/db/migration/V9__crypto_app_screening_results_grant.sql`
2. `services/crypto/src/main/java/com/themistra/crypto/screening/ScreeningOutcome.java`
3. `services/crypto/src/main/java/com/themistra/crypto/screening/ScreeningResult.java`
4. `services/crypto/src/main/java/com/themistra/crypto/screening/ScreeningResultRepository.java`
5. `services/crypto/src/main/java/com/themistra/crypto/screening/ScreeningClient.java`
6. `services/crypto/src/main/java/com/themistra/crypto/screening/FailClosedScreeningClient.java`
7. `services/crypto/src/test/java/com/themistra/crypto/screening/ScreeningOutcomeTest.java`
8. `services/crypto/src/test/java/com/themistra/crypto/screening/ScreeningResultTest.java`
9. `services/crypto/src/test/java/com/themistra/crypto/screening/ScreeningResultRepositoryIntegrationTest.java`
10. `services/crypto/src/test/java/com/themistra/crypto/screening/FailClosedScreeningClientTest.java`
11. `services/crypto/src/test/java/com/themistra/crypto/screening/ScreeningModuleBoundaryTest.java`

## Files to modify

None (frozen brief: "Files to Modify: None").

## Public methods (signatures)

- `ScreeningOutcome` — `enum ScreeningOutcome { CLEARED, BLOCKED, ERROR }`, no methods beyond the
  implicit `values()`/`valueOf(String)`.
- `ScreeningResult`:
  - `static ScreeningResult create(String chain, String address, String txHash, ScreeningOutcome outcome, String provider, String rawResponseJson, Instant screenedAt)`
  - `Long id()`
  - `String chain()`
  - `String address()`
  - `String txHash()`
  - `ScreeningOutcome outcome()`
  - `String provider()`
  - `String rawResponse()`
  - `Instant screenedAt()`
- `ScreeningResultRepository` — `interface ScreeningResultRepository extends JpaRepository<ScreeningResult, Long>` (no declared finder methods — nothing in scope needs one).
- `ScreeningClient` — `interface ScreeningClient { ScreeningOutcome screen(String chain, String address, String txHash); }`, with the Javadoc contract from the frozen brief (persist-then-return; exceptions propagate and are fail-closed for the caller).
- `FailClosedScreeningClient`:
  - `public static final String PROVIDER_NAME = "fail-closed-stub";` (public constant, Phase 3 Finding #7)
  - `public FailClosedScreeningClient(ScreeningResultRepository screeningResultRepository, Clock clock)` (constructor injection, matching every other component's convention)
  - `@Override public ScreeningOutcome screen(String chain, String address, String txHash)`

## Private methods

- `ScreeningResult` — none beyond the private no-arg constructor field assignments already covered by `create`.
- `FailClosedScreeningClient` — none; `screen(...)` is small enough (build entity, save, log, return) to stay a single method, matching `TxLifecyclePublisher`'s own per-event-type method size, not `Watcher`'s larger decomposed methods (no comparable branching complexity here).
- `ScreeningOutcome`'s nested `DbConverter`:
  - `@Converter static class DbConverter implements AttributeConverter<ScreeningOutcome, String>` with `convertToDatabaseColumn`/`convertToEntityAttribute`. **Verified directly against `V1__chain_baseline.sql`'s literal `CONSTRAINT chk_screening_outcome CHECK (outcome IN ('CLEARED','BLOCKED','ERROR'))`**: the constraint's own string values are already uppercase and match `ScreeningOutcome.name()` exactly — unlike `observation.FactType.DbConverter` (which lowercases, because `chain.observations.fact_type`'s column comment documents lowercase values), this converter needs **no case transformation**: `convertToDatabaseColumn` returns `outcome.name()` as-is, `convertToEntityAttribute` returns `ScreeningOutcome.valueOf(dbValue)` as-is.

## Entities used

- `ScreeningResult` (new, this task).

## Repositories used

- `ScreeningResultRepository` (new, this task).

## Services used

- None beyond `FailClosedScreeningClient` itself (a `@Component`, not a `@Service` — it implements a
  client interface, matching how `token.AddressValidator`/`finality.EthereumFinalityPolicy` are plain
  `@Component`s, not `@Service`s, since "service" in this codebase's convention denotes an orchestrating
  layer like `WatchService`/`AttestationService`, which this is not).
- `common.ClockConfig`'s `Clock` bean, injected into `FailClosedScreeningClient`.

## Unit/integration tests required

- `ScreeningOutcomeTest` (unit) — AC1: `assertThat(ScreeningOutcome.values()).containsExactly(CLEARED, BLOCKED, ERROR)`.
- `ScreeningResultTest` (unit) — AC4: `create(...)` throws `NullPointerException` for each non-nullable
  parameter in turn (`chain`, `address`, `outcome`, `provider`, `screenedAt`); succeeds with `txHash =
  null` and `rawResponse = null`; accessors return exactly what was passed in.
- `FailClosedScreeningClientTest` (unit, mocked `ScreeningResultRepository` + fixed `Clock`) — AC2/AC3/AC8:
  - always returns `ERROR` for a normal `(chain, address, txHash)` triple.
  - always returns `ERROR` when `txHash` is `null`.
  - always returns `ERROR` for an obviously malformed address string (e.g. `"not-an-address"`) — proves
    no validation rejects it (Phase 3 Finding #5).
  - persists exactly one `ScreeningResult` per call, captured via `ArgumentCaptor`, asserting
    `outcome() == ERROR`, `provider() == PROVIDER_NAME`, `rawResponse() == null`,
    `screenedAt() == <the fixed clock's instant>`, and that `chain()`/`address()`/`txHash()` match the
    call's inputs.
  - never invokes any HTTP/RPC client (AC7 — structural: the class has no such dependency to verify a
    call on, so this is confirmed by the class's own field list, not a runtime assertion).
- `ScreeningResultRepositoryIntegrationTest` (Testcontainers Postgres, connected as the real `crypto_app`
  role — mirrors `TokenAllowlistRepositoryIntegrationTest`'s own role-connection pattern) — AC5:
  - saves and reads back a fully-populated `ScreeningResult` (non-null `txHash`, non-null `rawResponse`).
  - saves and reads back a `ScreeningResult` with `txHash = null` and `rawResponse = null`.
  - confirms no `UPDATE`/`DELETE` grant exists — attempting either as `crypto_app` fails (mirrors
    `TokenAllowlistRepositoryIntegrationTest`'s own append-only-grant verification, if that precedent
    test exists; otherwise a direct JDBC `UPDATE ... TO crypto_app` attempt asserted to throw).
- `ScreeningModuleBoundaryTest` (unit, plain source scan) — AC6: every `.java` file under
  `screening/` is scanned; any import starting with `com.themistra.crypto.` other than
  `com.themistra.crypto.screening` or `com.themistra.crypto.common` fails the test (fully forbidden list,
  no allow-listed exceptions needed — mirrors `ReorgModuleBoundaryTest`'s precedent for a module with no
  legitimate cross-module dependency).

## Execution order

1. **Schema/migration:** `V9__crypto_app_screening_results_grant.sql` — grant `INSERT, SELECT` on
   `chain.screening_results` to `crypto_app`. Verify it applies cleanly against the existing `V1`-`V8`
   chain (no new table/column, so no ordering conflict is possible).
2. **Domain enum:** `ScreeningOutcome` (with its nested `DbConverter`) — no dependencies on anything else
   in this task.
3. **Entity:** `ScreeningResult` — depends on `ScreeningOutcome`.
4. **Repository:** `ScreeningResultRepository` — depends on `ScreeningResult`.
5. **Interface:** `ScreeningClient` — depends on `ScreeningOutcome` only (no dependency on the entity or
   repository — the interface's contract is documented in Javadoc, not enforced by its signature, per
   the frozen brief's Phase 3 Finding #2 disposition).
6. **Implementation:** `FailClosedScreeningClient` — depends on `ScreeningClient`, `ScreeningResult`,
   `ScreeningResultRepository`, `common.ClockConfig`'s `Clock`.
7. **Tests**, in the same dependency order: `ScreeningOutcomeTest` → `ScreeningResultTest` →
   `ScreeningResultRepositoryIntegrationTest` → `FailClosedScreeningClientTest` →
   `ScreeningModuleBoundaryTest` (boundary test last, since it scans the finished package's full file
   set).

## Open Questions

No blockers (unchanged from the frozen brief).
