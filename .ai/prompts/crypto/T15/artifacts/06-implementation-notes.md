# crypto · T15 · Phase 6 — Implementation Notes

Implemented all 13 files the frozen brief and Phase 5 plan authorized, plus the one authorized
modification. No test files written (Phase 6's own directive defers all test-writing to Phase 10).

## Files created

1. `V6__crypto_app_watches_grant.sql` — `GRANT INSERT, SELECT, UPDATE ON chain.watches` and
   `GRANT INSERT, SELECT ON chain.chain_cursors`, both `TO crypto_app` (AC7).
2. `watch/WatchStatus.java` — `REGISTERED`, `UNREGISTERED`, `EXPIRED`.
3. `watch/Watch.java` — `@Entity` on `chain.watches`; `register(...)` static factory; no
   `unregister()` instance mutator (the transition is a repository-level atomic conditional `UPDATE`,
   per Phase 3 Finding 4 — see Deviations below for the resulting accessor-naming note).
4. `watch/WatchRepository.java` — package-private; `findByWatchId`, `existsByWatchId`, and
   `markUnregisteredIfRegistered` (the `@Modifying @Query` conditional `UPDATE`).
5. `watch/ChainCursor.java` — `@Entity` on `chain.chain_cursors`; `placeholder(...)` static factory
   seeding the `-1` sentinel `lastBlock` and `null` `lastFinalizedBlock`.
6. `watch/ChainCursorRepository.java` — package-private; no methods beyond the inherited `save`.
7. `watch/WatchNotFoundException.java`, `watch/InvalidWatchRequestException.java` — package-private
   unchecked exceptions.
8. `watch/WatchExceptionHandler.java` — `@RestControllerAdvice`, `@Order(Ordered.HIGHEST_PRECEDENCE)`
   (Finding 9); maps both exceptions to `ProblemDetail` (404/400).
9. `watch/dto/RegisterWatchRequest.java`, `watch/dto/RegisterWatchResponse.java` — public records,
   VERBATIM wire shapes; request carries the bean-validation annotations Findings 2/7 called for
   (`@Pattern` on `chain`, `@Size(max=128)` on both address fields).
10. `watch/WatchService.java` — `register`/`unregister`, both `@Transactional`; private
    `parseExpectedAmount`/`validateExpiresAt`/`validateAddress` implement Findings 1/2's semantic
    validation.
11. `watch/WatchController.java` — `POST`/`DELETE /internal/v1/watches`, pure delegation to
    `WatchService`.

## File modified

- `ChainBaselineMigrationIntegrationTest.java` — `watches`/`chain_cursors` removed from
  `UNGRANTED_TABLES` (now `screening_results`, `shedlock` only); the Flyway-history version-list
  assertion extended to include `"6"`; the explanatory comment above `UNGRANTED_TABLES` updated to
  name T15/V6 alongside the existing T10/T11 entries.

## Mapping to the plan and acceptance criteria

- **AC1/AC2 (R18, task statement, Finding 8):** `WatchService.register` persists `Watch` and
  `ChainCursor` in one `@Transactional` method; `ChainCursor.placeholder` takes the same `chain` and
  the freshly generated `watchId` `WatchService.register` also passes to `Watch.register`, so both rows
  are guaranteed consistent by construction (not by any schema constraint, which enforces neither).
- **AC3/AC4 (R19, Finding 4/6):** `WatchService.unregister` checks existence first (404 via
  `WatchNotFoundException`), then issues the atomic conditional `UPDATE` unconditionally afterward —
  correct for the `REGISTERED` (real transition), `UNREGISTERED`, and `EXPIRED` (both no-op, 204) cases
  alike, since the `WHERE ... AND status = 'REGISTERED'` clause naturally excludes the latter two.
- **AC5 (Finding 2/7):** `parseExpectedAmount`'s regex `^[1-9][0-9]*$` rejects `"1.5"`, `"1e18"`,
  `"-0"`, `"0"`, and any leading zero; `@Size(max=128)` on the DTO rejects oversize addresses before
  they ever reach the database.
- **AC6 (Finding 1, L8):** `validateAddress` dispatches to `AddressValidator.isValidEvmAddress`/
  `isValidTronAddress` by the already-bean-validated `chain`; this is `AddressValidator`'s first real
  caller anywhere in this codebase.
- **AC7:** confirmed by direct reading of the migration file against the frozen brief's exact grant
  list.
- **AC8 (L15):** `watch/`'s only cross-module import is `com.themistra.crypto.token.AddressValidator`
  (a stateless predicate, not an entity) plus `java.time.Clock` from `common` — confirmed by inspection
  of every new file's import list; `WatchModuleBoundaryTest` (Phase 10) will make this permanent.

## Deviations from the plan

1. **Accessor naming: bare method names (`watch.chain()`), not JavaBean getters (`getChain()`), for
   `Watch`/`ChainCursor`.** The Phase 5 plan specified `getWatchId()`-style signatures, but re-checking
   the actual established entity convention in this codebase (`ProviderHealth`: `chain()`, `provider()`,
   `healthy()`, not `getChain()`/etc.) before writing code — this pipeline's own verify-before-relying
   discipline — showed every existing entity in this service uses bare accessor names. Followed that
   real precedent instead of the plan's own (incorrect) assumption; flagged here per Phase 6's own
   "flag deviations forced by reality" directive. No behavioral difference, no impact on any acceptance
   criterion — `WatchController`'s calls (`watch.watchId()`, `watch.status().name()`) already used the
   bare-name form, so the controller needed no adjustment for this.
2. **`@Table(schema = "chain")` added explicitly to both new entities**, matching `ProviderHealth`'s own
   precedent, even though the Phase 5 plan's method-signature listing didn't call this class-level
   annotation detail out explicitly. Same connection-init-sql `search_path` already covers this
   implicitly, but explicit `schema = "chain"` is the established, more defensive convention every
   other entity in this service already follows.

## Verification gap — now resolved (Docker became available after this phase was first written)

The original version of this note disclosed that the `UUID` ↔ PostgreSQL `uuid` mapping for
`Watch.watchId`/`invoiceUuid` and `ChainCursor.watchId` could not be verified end-to-end (Docker
unavailable in this environment at the time). Docker subsequently became available and this was
verified directly:

- `mvn -pl services/crypto test -Dtest=TokenAllowlistRepositoryIntegrationTest` — a full-Spring-context
  test that validates *every* entity's mapping at startup (`ddl-auto=validate`), not just the one under
  test — started cleanly with `Watch`/`ChainCursor` present on the classpath, confirming no schema-
  mapping mismatch for either new entity.
- A throwaway ad-hoc smoke test (not committed — not a Phase 10 deliverable) exercised
  `WatchService.register`/`unregister` end-to-end against a real Postgres 16 Testcontainers instance
  after applying `V6__crypto_app_watches_grant.sql`: `Watch`/`ChainCursor` persisted correctly with real
  UUID columns, the `AddressValidator` wiring correctly accepted a valid EIP-55 address, the atomic
  conditional `UPDATE` correctly transitioned `REGISTERED → UNREGISTERED`, a second `DELETE` was a
  correct no-op (idempotent), and an unknown `watchId` correctly threw `WatchNotFoundException`. Deleted
  after running, per this pipeline's Phase 10-owns-tests convention.

**Unrelated finding, disclosed but explicitly not fixed (out of T15's scope):** running the full module
suite with Docker available surfaced 6 pre-existing test failures in *other* tasks' own test files
(`ObservationRepositoryIntegrationTest` — a JSONB round-trip whitespace-formatting mismatch;
`ProviderHealthRepositoryIntegrationTest`, `QuorumDecisionRepositoryIntegrationTest`,
`TokenAllowlistRepositoryIntegrationTest.deleteFailsAtTheDatabaseLevel` — each expected
`DataIntegrityViolationException` for a permission-denied `DELETE` but got
`InvalidDataAccessResourceUsageException`, a Spring exception-translation mismatch;
`TokenAllowlistRepositoryIntegrationTest.findCurrentVersionEntryScopesToPerChainMaxVersionIndependently`
— an empty-`Optional` assertion failure). None touch `watch/`, any file this task created or modified,
or the new `V6` migration — these were previously reported as environment `Error`s (no Docker) rather
than real `Failure`s, and are pre-existing defects in T08/T09/T10/T11's own work, not a T15 regression.
Not fixed here per this pipeline's own scope discipline ("work only on T15, no unrelated refactoring").
