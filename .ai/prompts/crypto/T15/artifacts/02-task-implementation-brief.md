# crypto · T15 · Phase 2 — Task Implementation Brief

## Task

Implement `WatchService` (register/unregister) and `WatchController`
(`POST`/`DELETE /internal/v1/watches`), persisting `Watch` and its `ChainCursor` (R18/R19).

## Purpose

Give the Payment Service the API it uses to start and stop tracking an on-chain payment address for an
invoice — the entry point for every downstream watcher/quorum/finality/attest pipeline this service has
already built (T06-T14), none of which yet has a real caller.

## Scope

**In:**
- `Watch` entity/repository, `ChainCursor` entity/repository (JPA, validated against the already-frozen
  `watches`/`chain_cursors` tables — no schema change).
- `WatchService.register(...)` — generates `watchId`, persists `Watch` (`status = REGISTERED`) and one
  `ChainCursor` row per registration, atomically.
- `WatchService.unregister(watchId)` — transitions an existing `REGISTERED` watch to `UNREGISTERED`.
- `WatchController` — `POST /internal/v1/watches` (200, VERBATIM response shape) and
  `DELETE /internal/v1/watches/{watchId}` (204).
- Request/response DTOs, a domain exception, and a `@RestControllerAdvice` for this module's own errors
  (mirrors `services/auth`'s per-module pattern — Phase 0 finding).
- Structural request validation only: required fields, `chain ∈ {ETHEREUM, TRON}`, `expectedAmount` a
  well-formed positive decimal string, `expiresAt` after the current time (checked in `WatchService`
  against the injected `Clock`, not via bean-validation `@Future` — see Constraints).
- A new grant migration for `watches`/`chain_cursors` (currently ungranted — Phase 0 finding).

**Out:**
- `Watcher.java`/`WatcherRegistry.java` (task 16) — nothing this task builds assumes a running watcher.
- `ReorgDetector.java` and any real cursor *advancement* — `ChainCursor` is a placeholder row here (see
  Dependencies/Constraints); only task 16's watcher gives it a real value.
- Any `chain.tx.*` event emission.
- Semantic address/token validation (`AddressValidator`, `TokenValidator`) — R18's own text says
  "register... and begin watching," not "validate"; wiring either in is a separate design decision this
  task does not make.
- POST-retry idempotency (e.g. deduping on `invoiceUuid`) — the frozen schema has no unique constraint
  enabling it and R18 does not require it; a retried `POST` creates a second, independent watch. Not
  this task's job to invent a mechanism the schema doesn't support.
- Any change to `contracts/` — none of the named contract files exist in this repository yet.

## Business Rules

- **R18.** `POST /internal/v1/watches` with valid parameters registers a watch, begins watching the
  address, and returns a `watchId` with status `REGISTERED`.
- **R19.** `DELETE /internal/v1/watches/{watchId}` stops watching and returns `204`.

## Locked Decisions

- **L6.** Reorg is a first-class transition — `ChainCursor` exists to support the future walk-back; this
  task only creates its initial row, never performs a walk-back.
- **L15.** Module boundaries — `watch/` imports no other feature module's entity; shared plumbing only
  via `common`.

## Dependencies

- `Watch`/`ChainCursor` map onto the already-frozen `watches`/`chain_cursors` tables
  (`V1__chain_baseline.sql`).
- `java.time.Clock` (`common.ClockConfig`) for all timestamps and the `expiresAt`-in-the-future check.
- No `ChainAdapter` dependency. **Design decision (resolves Phase 1's Open Question 1):** `ChainAdapter`
  has no "current block number" method independent of an existing tx hash, and it is a VERBATIM, frozen
  interface (`design.md` §4c) — adding one is out of this task's scope and a materially larger change
  than warranted. `ChainCursor.lastBlock` is therefore seeded with the sentinel value `-1` at
  registration (never a real chain position; block numbers are never negative in reality, so `-1` cannot
  be confused with a genuine cursor value the way `0` — a real genesis block number on both launch
  chains — could be). `lastFinalizedBlock` is left `null`. Task 16's watcher is expected to overwrite
  both with real values on its first run for this watch; this task's own row is a placeholder only.
- No dependency on `AddressValidator`/`TokenValidator` (out of scope, see above).

## Inputs

- `POST` body: `{ invoiceUuid: UUID, chain: string, address: string, tokenContractAddress: string,
  expectedAmount: decimal-string, expiresAt: ISO-8601 instant }` (VERBATIM shape, `design.md` §4c).
  `expectedAmount` is a decimal string on the wire (agents.md: never a JSON number), parsed to
  `BigDecimal` server-side.
- `DELETE` path variable: `watchId` (UUID).

## Outputs

- `POST` success: `200 { watchId: UUID, status: "REGISTERED" }` (VERBATIM).
- `DELETE` success: `204`, empty body.
- **Design decision (resolves Phase 1's Open Question 2 — DELETE semantics):** `watchId` matching no
  row at all → `404` (a genuine caller error, e.g. a typo'd UUID, surfaced rather than silently
  swallowed — mirrors `services/auth`'s own `ApiKeyNotFoundException` → 404 precedent). `watchId`
  matching a row already `UNREGISTERED`/`EXPIRED` → `204`, no-op, no state change (idempotent-retry-safe
  — a caller re-sending a `DELETE` after a timed-out-but-actually-successful first attempt gets the same
  success response, not an error). Both are RFC 9457 `problem+json` where non-2xx.
- **Design decision (resolves Phase 1's Open Question 3 — validation scope):** malformed/missing
  required fields, an unrecognized `chain`, a non-positive or unparseable `expectedAmount`, or a past
  `expiresAt` → `400` `problem+json`, no watch created.

## State Changes

- `INSERT` into `watches` (one row per successful `POST`).
- `INSERT` into `chain_cursors` (one placeholder row per successful `POST`, same transaction).
- `UPDATE` `watches.status`/`unregistered_at` (one row per successful `DELETE` against a `REGISTERED`
  watch; no-op update for an already-`UNREGISTERED`/`EXPIRED` watch).
- No `DELETE` SQL statement anywhere — "unregister" is a status transition, never a row removal (mirrors
  the append-only/soft-transition ethos of every other mutable entity in this service).

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/watch/Watch.java`
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatchStatus.java` (enum: `REGISTERED`,
  `UNREGISTERED`, `EXPIRED`)
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatchRepository.java`
- `services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursor.java`
- `services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursorRepository.java`
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatchService.java`
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatchController.java`
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatchNotFoundException.java`
- `services/crypto/src/main/java/com/themistra/crypto/watch/WatchExceptionHandler.java`
- `services/crypto/src/main/java/com/themistra/crypto/watch/dto/RegisterWatchRequest.java`
- `services/crypto/src/main/java/com/themistra/crypto/watch/dto/RegisterWatchResponse.java`
- `services/crypto/src/main/resources/db/migration/V6__crypto_app_watches_grant.sql`

## Files to Modify

- `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java` —
  remove `watches`/`chain_cursors` from `UNGRANTED_TABLES` (T10/T11 precedent).

## Files NOT to Modify

- `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql` — VERBATIM, frozen.
- `adapter/ChainAdapter.java` and every adapter file — VERBATIM, frozen; no new method added.
- `common/ResourceServerConfig.java`, `common/PublicEndpoints.java` — already fully wired for this
  endpoint (Phase 0 finding); no change needed.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R18).** Valid `POST` persists a `Watch` (`status = REGISTERED`, freshly generated `watchId`)
  and returns `200 { watchId, status: "REGISTERED" }`.
- **AC2 (task statement).** The same request also persists exactly one `ChainCursor` row
  (`lastBlock = -1` sentinel, `lastFinalizedBlock = null`), in the same transaction as the `Watch` write.
- **AC3 (R19).** `DELETE` on a `REGISTERED` watch transitions it to `UNREGISTERED`, sets
  `unregistered_at`, returns `204`.
- **AC4 (Outputs).** `DELETE` on an unknown `watchId` returns `404`; on an already-non-`REGISTERED`
  watch returns `204` with no state change.
- **AC5 (Outputs).** Invalid `POST` input (missing field, unrecognized `chain`, non-positive/unparseable
  `expectedAmount`, past `expiresAt`) returns `400`, no rows written.
- **AC6 (agents.md).** The new grant migration gives `crypto_app` exactly `INSERT, SELECT, UPDATE` on
  `watches` and `INSERT, SELECT` on `chain_cursors` — no broader privilege.
- **AC7 (L15).** `watch/` imports no other feature module's entity.
- **AC8 (R27, already satisfied).** `/internal/v1/watches` requires `internal.crypto:write` — inherited,
  not new work in this task.

## Required Tests

- `shouldRegisterWatchAndReturnWatchId` (`package.md` §8, named) — AC1.
- `shouldUnregisterWatchOnDelete` (`package.md` §8, named) — AC3.
- A `ChainCursor` row is created alongside the `Watch` row on registration, with the documented sentinel
  values — AC2.
- `DELETE` on an unknown `watchId` → `404` — AC4.
- `DELETE` on an already-`UNREGISTERED` watch → `204`, no state change (idempotent retry) — AC4.
- Invalid `POST` bodies (missing field, bad `chain`, non-positive `expectedAmount`, past `expiresAt`)
  each → `400`, no row written — AC5.
- Migration-grant integration test update confirming `watches`/`chain_cursors` are no longer in
  `UNGRANTED_TABLES` and that `crypto_app` has exactly the AC6 privileges — AC6.
- A source-scan module-boundary test for `watch/` (mirrors T10/T11/T14 precedent) — AC7.

## Constraints

- **Performance:** not a concern at this task's scope (single-row-pair writes, no batch operation).
- **Security:** relies entirely on the already-existing `ResourceServerConfig` scope check; this task
  adds no new security logic. Error responses never leak stack traces or internal detail (agents.md).
- **Thread-safety:** `WatchService` is a stateless Spring singleton; all state lives in the database.
- **Transaction:** `WatchService.register` is `@Transactional` — `Watch` and `ChainCursor` inserts commit
  or roll back together. `WatchService.unregister` is `@Transactional` (single-row update, no atomicity
  concern beyond the standard one).
- **Module boundaries:** `watch/` depends only on `common` (Clock) — no import of `observation`,
  `provider`, `quorum`, `token`, `finality`, or `events`.
- **Null handling:** repository lookups return `Optional`; `WatchService.unregister` throws
  `WatchNotFoundException` (mapped to 404) rather than returning `null`/`Optional.empty()` to the
  controller layer.
- **Validation-response shape must be verified empirically, not assumed.** Whether Spring Boot 3.5.4's
  default `@Valid`/`MethodArgumentNotValidException` handling already produces a compliant
  `application/problem+json` body, or whether `WatchExceptionHandler` must add an explicit mapping, is
  to be confirmed by direct testing in Phase 6/7 — mirrors this pipeline's established practice of
  verifying framework/library behavior via direct execution rather than assuming it (T10 `ObjectMapper`,
  T11 Hibernate `SqlTypes`, T12 `Base58Check`).

## Open Questions

No blockers. Phase 1's three flagged gaps are resolved above as design decisions (`ChainCursor` sentinel
value, `DELETE` semantics, validation scope) — subject to Phase 3 challenge and Phase 4 sign-off like any
other implementer-proposed resolution in this pipeline.
