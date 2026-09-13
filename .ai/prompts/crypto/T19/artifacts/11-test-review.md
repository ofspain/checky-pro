<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# crypto · T19 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T19 — Screening client |
| **Spec section** | Screening, attestation, key custody |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim from `spec/crypto-service/tasks.md`, task 19):**
> **Screening client.** Add `ScreeningClient` interface with a **fail-closed** stub and `ScreeningResult` persistence (L12, R21). Wire the real vendor once Q2 is answered.

**Scoped requirement IDs:** `R21`
**Scoped LOCKED decisions:** `L12`
**Named tests (`package.md` §8):** `shouldReturnBlockedFromAttestOnSanctionedCounterparty`
**Contracts:** `contracts/api/crypto-internal.yaml`, `contracts/events/chain/`, `contracts/events/chain/tx-finalized.v1.schema.json`
**Standing rules:** `spec/crypto-service/agents.md` is authoritative.

---

Phase 10 reports 21 passing screening tests and a traceability matrix that maps them to AC1–AC6. The suite is solid on the happy-path/fail-closed floor, but several specification edges are either asserted indirectly or not asserted at all. Findings below are recommendations only — no code changes were made.

---

## Gap 1: `ScreeningOutcomeTest` does not verify the DB-converted string values

**Why it matters.** `ScreeningOutcomeTest.hasExactlyTheThreeValuesTheCheckConstraintAllows` asserts that `ScreeningOutcome.values()` contains `CLEARED`, `BLOCKED`, `ERROR`. That only checks Java enum identity. The contract with `V1__chain_baseline.sql`’s `chk_screening_outcome` is that the *database string literals* are exactly `'CLEARED'`, `'BLOCKED'`, `'ERROR'`. If someone later renames an enum constant (e.g., `ERROR` → `FAILED`) and updates the migration, the test would need to be edited too, but the test as written would not catch a drift where the enum name and the CHECK literal diverge.

**Suggested test.** Add a test that exercises `ScreeningOutcome.DbConverter.convertToDatabaseColumn(...)` for each value and asserts the produced strings equal the CHECK constraint literals, or read `V1__chain_baseline.sql` and assert the three enum names appear inside the `IN (...)` clause.

---

## Gap 2: No test that an exception from `Repository.save` propagates unchanged

**Why it matters.** `ScreeningClient` Javadoc (L12-T19b) states: *“An exception thrown by `#screen` (including a failed `ScreeningResult` persistence) is not caught internally and propagates to the caller, which must treat it as fail-closed.”* `FailClosedScreeningClientTest` verifies `save` is *not* called for null inputs, but it never verifies the *absence* of a try/catch around `screeningResultRepository.save(result)`. A future refactor could wrap `save` in a catch-that-returns-ERROR and no test would fail, violating the “exceptions propagate” contract.

**Suggested test.** In `FailClosedScreeningClientTest`, stub `screeningResultRepository.save(...)` to throw a runtime exception (e.g., `DataAccessException` or `IllegalStateException`) and assert that `client.screen(...)` throws the same exception without wrapping or swallowing it.

---

## Gap 3: AC8 observability is exercised only incidentally

**Why it matters.** Phase 10 notes that the `warn` log is “exercised incidentally by every test” and that this is consistent with the frozen brief’s disposition of no dedicated log-capture test. However, the incidentally-visible log line is not *asserted*. The current log statement in `FailClosedScreeningClient` includes `chain`, `address`, and `txHash`; a regression that dropped one of those fields (especially `address`, which is the sanctioned-party identifier) would not be caught. `agents.md` mandates structured logs and security-aware logging; a missing field in a compliance-relevant audit log is a meaningful gap.

**Suggested test.** Add one `FailClosedScreeningClientTest` that uses a `ListAppender`/`Logger` capture to assert the `WARN` event is emitted exactly once per call and that the formatted message contains the non-null `chain`, `address`, and `txHash` passed in.

---

## Gap 4: `deleteFailsAtTheDatabaseLevel` could pass for a JPA-level reason

**Why it matters.** The test calls `repository.delete(saved)` followed by `repository.flush()` and asserts `InvalidDataAccessResourceUsageException`. This does exercise Postgres, but it does not rule out the exception originating from Spring Data’s query construction rather than the DB grant. More importantly, the test does not assert that the row still exists after the denied delete — if a bug ever granted DELETE, the test would fail (good), but the failure mode is not characterized.

**Suggested test.** Either (a) assert `repository.findById(saved.id()).isPresent()` after catching the exception, or (b) add a raw-JDBC DELETE counterpart to the existing raw-JDBC UPDATE test in `ScreeningResultRepositoryIntegrationTest` and assert both `permission denied` and that the row remains.

---

## Gap 5: No DB-level assertion that `chk_screening_outcome` rejects an invalid string

**Why it matters.** The application-level `ScreeningOutcome` enum and `DbConverter` make it hard to insert an invalid value, but the specification’s safety net is the CHECK constraint in `V1__chain_baseline.sql`. No integration test proves the constraint is actually in place. A migration drift that removed or weakened the CHECK would not be caught.

**Suggested test.** In `ScreeningResultRepositoryIntegrationTest`, attempt a raw JDBC `INSERT INTO chain.screening_results (... outcome ...) VALUES (... 'INVALID' ...)` as `crypto_app` and assert it fails with a check-constraint violation.

---

## Gap 6: Empty-string `chain` / `address` inputs are not covered

**Why it matters.** `FailClosedScreeningClientTest` covers `null` chain and address, and a malformed address, but does not cover empty strings. The DDL requires `chain VARCHAR(32) NOT NULL` and `address VARCHAR(128) NOT NULL` — empty strings satisfy `NOT NULL` but are almost certainly not a valid screening input. The current stub would persist them, creating garbage audit rows. The brief says address-format validation belongs to `token.AddressValidator`, but empty inputs are a pre-validation concern.

**Suggested test.** Add parameterized tests in `FailClosedScreeningClientTest` for empty `chain` and empty `address` asserting an exception (e.g., `IllegalArgumentException`) before persistence, or, if the design decision is to allow empty strings, add a test that documents and locks that behavior.

---

## Gap 7: `ScreeningResultRepositoryIntegrationTest` does not round-trip `CLEARED`

**Why it matters.** `savesAndReadsBackAFullyPopulatedRow` uses `ScreeningOutcome.BLOCKED`; `savesAndReadsBackARowWithNullTxHashAndNullRawResponse` uses `ERROR`. The CHECK constraint allows `CLEARED`, and it is the only outcome that may lead to a signature downstream (L12-T19a). Not round-tripping it leaves the converter path for `CLEARED` untested in an integration context.

**Suggested test.** Add a third repository integration test that persists and reloads a row with `outcome = CLEARED` and asserts the enum value survives the round trip.

---

## Gap 8: No test that `screening_results` is append-only at the entity level

**Why it matters.** The class and spec emphasize append-only semantics: no setters, no UPDATE grant, a re-screen is a new row. `ScreeningResultTest` checks accessors, and the repository tests check DB-level UPDATE/DELETE denial, but no unit test asserts that the entity class exposes no mutators. A future refactor adding a setter would not fail any existing test.

**Suggested test.** Add an ArchUnit or reflection-based test (mirroring the boundary-test style) that asserts `ScreeningResult` has zero public non-constructor methods whose name starts with `set` and that all fields are `private`.

---

## Summary

- **High-value additions:** Gap 2 (exception propagation), Gap 3 (observability assertion), Gap 5 (CHECK constraint at DB level), Gap 8 (entity immutability guard).
- **Medium-value additions:** Gap 1 (enum↔DB string mapping), Gap 4 (row-still-exists assertion after denied DELETE), Gap 7 (`CLEARED` round-trip).
- **Lower-value / policy-dependent:** Gap 6 (empty-string inputs).

The existing 21 tests adequately cover AC1–AC6 and the frozen-brief dispositions for AC7/AC8 and the T21 named test. The gaps above are refinements rather than blockers.
