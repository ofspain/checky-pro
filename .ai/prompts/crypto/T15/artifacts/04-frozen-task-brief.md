STATUS: FROZEN

# crypto · T15 · Phase 4 — Frozen Task Brief

**Human Approval gate.** Approved 2026-09-07. All 9 Phase 3 (Kimi design challenge) findings are
accepted and folded in below — an unusually clean review, including one genuine correction to the
Phase 2 TIB's own scoping mistake (Finding 1).

## Design-challenge resolution log

| # | Finding | Severity | Disposition | Change made |
|---|---|---|---|---|
| 1 | `AddressValidator` excluded from scope despite L8 ("address validation is mandatory... rejected at the boundary") being a LOCKED decision, not merely R18's own text | High | **ACCEPTED** | `WatchService.register` now calls `AddressValidator.isValidEvmAddress`/`isValidTronAddress` (dispatched by the already-validated `chain`) on both `address` and `tokenContractAddress`; either failing → `400`. `TokenValidator`/allowlist checking (L7, a separate LOCKED decision about business/token identity, not address structural validity) remains explicitly out of scope — R18 does not implicate it and no test name in `package.md` §8 covers it here. |
| 2 | `expectedAmount` validation didn't rule out fractional/scientific-notation strings, which would parse then fail at the DB (`NUMERIC(78,0)`, scale 0) | Medium | **ACCEPTED** | `expectedAmount` must be a positive, scale-0 integer decimal string (no decimal point, no exponent); `"1.5"`, `"1e18"`, `"-0"`, etc. → `400`. |
| 3 | `POST` non-idempotency risk was scoped out but not surfaced as an operational risk, and not locked in by a test | Medium | **ACCEPTED** | Scope/Out language expanded (see below); added required test: two identical `POST`s produce two distinct `watchId`s and two `Watch`/`ChainCursor` row pairs. |
| 4 | Concurrent `DELETE`s on the same watch could both read `REGISTERED` and both write, double-setting `unregistered_at` | Medium | **ACCEPTED** | Existence check (`WatchRepository.findByWatchId`) still drives the 404 case; the actual transition is a single atomic conditional `UPDATE ... WHERE watch_id=? AND status='REGISTERED'`, returning `204` regardless of rows affected — naturally race-safe, no extra locking. |
| 5 | `created_at`/`unregistered_at` should come from the injected `Clock`, not the DDL's `DEFAULT now()`, per agents.md and every other entity's own convention | Low | **ACCEPTED** | `Watch.createdAt`/`unregisteredAt` and `ChainCursor.updatedAt` are set explicitly from the injected `Clock` in application code; the DDL's `DEFAULT now()` remains only as an unused defensive fallback. |
| 6 | `EXPIRED` status has no transition in this task and no required test for `DELETE` against one | Low | **ACCEPTED** | Added required test: seed an `EXPIRED` watch directly via the repository, assert `DELETE` returns `204` with no state change. Added a sentence confirming this task never sets `EXPIRED` itself — a future scheduler/task's job. |
| 7 | No upper-bound length validation on `address`/`tokenContractAddress` (DB caps both at `VARCHAR(128)`) — oversize input would 500 instead of 400 | Low | **ACCEPTED** | Added `@Size(max = 128)` to both fields on `RegisterWatchRequest`. |
| 8 | The inserted `ChainCursor`'s `watch_id`/`chain` relationship to the new `Watch` was only implied, not stated as a requirement — the schema itself enforces neither | Low | **ACCEPTED** | Added as an explicit acceptance criterion (AC2 below). |
| 9 | `WatchExceptionHandler` should be `@Order(Ordered.HIGHEST_PRECEDENCE)`, matching `ApiKeyExceptionHandler`'s own precedent (cited but not fully specified in the TIB) | Low | **ACCEPTED** | Added as an explicit Constraint. |

## Frozen brief

### Task

Implement `WatchService` (register/unregister) and `WatchController`
(`POST`/`DELETE /internal/v1/watches`), persisting `Watch` and its `ChainCursor` (R18/R19).

### Purpose

Give the Payment Service the API it uses to start and stop tracking an on-chain payment address for an
invoice — the entry point for every downstream watcher/quorum/finality/attest pipeline this service has
already built (T06-T14), none of which yet has a real caller.

### Scope

**In:**
- `Watch` entity/repository, `ChainCursor` entity/repository (JPA, validated against the already-frozen
  `watches`/`chain_cursors` tables — no schema change).
- `WatchService.register(...)` — generates `watchId`, validates the request (structural fields, `chain
  ∈ {ETHEREUM, TRON}`, scale-0 positive `expectedAmount`, `expiresAt` after now, `address`/
  `tokenContractAddress` structurally valid per `AddressValidator` for the given chain — Finding 1),
  then persists `Watch` (`status = REGISTERED`) and one `ChainCursor` row per registration, atomically.
- `WatchService.unregister(watchId)` — 404 if no such watch exists at all; otherwise an atomic
  conditional `UPDATE` transitions a `REGISTERED` watch to `UNREGISTERED` and is a no-op (still `204`)
  for a watch already in any other status (Finding 4).
- `WatchController` — `POST /internal/v1/watches` (200, VERBATIM response shape) and
  `DELETE /internal/v1/watches/{watchId}` (204/404).
- Request/response DTOs, a domain exception, and a `@RestControllerAdvice` for this module's own errors
  (mirrors `services/auth`'s per-module pattern).
- A new grant migration for `watches`/`chain_cursors` (currently ungranted).

**Out:**
- `Watcher.java`/`WatcherRegistry.java` (task 16) — nothing this task builds assumes a running watcher.
- `ReorgDetector.java` and any real cursor *advancement* — `ChainCursor` is a placeholder row here; only
  task 16's watcher gives it a real value.
- Any `chain.tx.*` event emission.
- `TokenValidator`/allowlist checking (L7) — a separate LOCKED decision from L8's structural address
  validity; not implicated by R18 or by this task's own named tests.
- **`POST`-retry idempotency (documented risk, Finding 3).** The frozen schema has no unique constraint
  enabling deduplication (e.g. on `invoice_uuid`), and R18 does not require it. A retried `POST` without
  reusing the first response's `watchId` creates a second, independent watch — with the operational
  consequence of multiple watchers/events/attestations for the same Payment invoice once task 16+ exist.
  This is not this task's job to solve; the Payment Service is responsible for storing and reusing the
  returned `watchId`. Locked in by a required test (below), not just documented.
- Any change to `contracts/` — none of the named contract files exist in this repository yet.

### Business Rules

- **R18.** `POST /internal/v1/watches` with valid parameters registers a watch, begins watching the
  address, and returns a `watchId` with status `REGISTERED`.
- **R19.** `DELETE /internal/v1/watches/{watchId}` stops watching and returns `204`.

### Locked Decisions

- **L6.** Reorg is a first-class transition — `ChainCursor` exists to support the future walk-back; this
  task only creates its initial row, never performs a walk-back.
- **L8.** Address validation is mandatory; invalid addresses are rejected at the boundary — this
  endpoint IS that boundary for a watch's `address`/`tokenContractAddress` (Finding 1).
- **L15.** Module boundaries — `watch/` imports no other feature module's entity except calling
  `token.AddressValidator` (an existing, stable, sibling-package pure predicate — the same
  cross-feature-utility-class pattern already established by other modules calling into `common`;
  Phase 5/6 must confirm this specific cross-module call doesn't violate L15's own "no feature module
  imports another feature module's entity" wording — `AddressValidator` is not an entity, so it should
  be permissible, but this is called out explicitly for Phase 5/6 to confirm, not assumed).

### Dependencies

- `Watch`/`ChainCursor` map onto the already-frozen `watches`/`chain_cursors` tables.
- `java.time.Clock` (`common.ClockConfig`) for all timestamps (Finding 5) and the `expiresAt`-in-the-
  future check.
- `java.util.UUID` — `watches.watch_id` has no DB default; generated in application code.
- `com.themistra.crypto.token.AddressValidator` (T12) — newly wired in (Finding 1), its first real
  caller anywhere in this codebase.
- No `ChainAdapter` dependency — `ChainCursor.lastBlock` is seeded with the sentinel value `-1` (never
  a real chain position, chosen over `0` since block `0` is a real genesis block number on both launch
  chains); `lastFinalizedBlock` left `null`. Task 16's watcher is expected to overwrite both on its
  first run for this watch.
- No dependency on `TokenValidator`/allowlist (out of scope, see above).

### Inputs

- `POST` body: `{ invoiceUuid: UUID, chain: string, address: string, tokenContractAddress: string,
  expectedAmount: decimal-string, expiresAt: ISO-8601 instant }` (VERBATIM shape, `design.md` §4c).
  `expectedAmount` is a decimal string on the wire, parsed to `BigDecimal` server-side, required to be a
  positive scale-0 integer (Finding 2).
- `DELETE` path variable: `watchId` (UUID).

### Outputs

- `POST` success: `200 { watchId: UUID, status: "REGISTERED" }` (VERBATIM).
- `DELETE` success: `204`, empty body — for a `REGISTERED` watch (real transition) or any
  non-`REGISTERED`/nonexistent-status watch that nonetheless has a row (idempotent no-op).
- `DELETE` on a `watchId` matching no row at all → `404` `problem+json`.
- `POST` validation failure (missing field, unrecognized `chain`, non-positive/fractional/scientific-
  notation `expectedAmount`, past `expiresAt`, oversize `address`/`tokenContractAddress`, or either
  address structurally invalid per `AddressValidator` for its chain) → `400` `problem+json`, no rows
  written.

### State Changes

- `INSERT` into `watches` and `chain_cursors` (one row pair per successful `POST`, same transaction);
  the `ChainCursor` row's `watch_id` equals the generated `watchId` and its `chain` equals the request
  `chain` (AC2, Finding 8).
- A single atomic conditional `UPDATE watches SET status='UNREGISTERED', unregistered_at=? WHERE
  watch_id=? AND status='REGISTERED'` per `DELETE` on an existing watch (Finding 4) — race-safe by
  construction, no separate lock needed.
- No `DELETE` SQL statement anywhere.

### Files to Create

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

### Files to Modify

- `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java` —
  remove `watches`/`chain_cursors` from `UNGRANTED_TABLES`.

### Files NOT to Modify

- `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql` — VERBATIM, frozen.
- `adapter/ChainAdapter.java` and every adapter file — VERBATIM, frozen; no new method added.
- `common/ResourceServerConfig.java`, `common/PublicEndpoints.java` — already fully wired.
- `token/AddressValidator.java` — consumed, not modified.
- Any file under `spec/`.

### Acceptance Criteria

- **AC1 (R18).** Valid `POST` persists a `Watch` (`status = REGISTERED`, freshly generated `watchId`)
  and returns `200 { watchId, status: "REGISTERED" }`.
- **AC2 (task statement, Finding 8).** The same request persists exactly one `ChainCursor` row with
  `watch_id` equal to the generated `watchId`, `chain` equal to the request `chain`, `lastBlock = -1`,
  `lastFinalizedBlock = null` — same transaction as the `Watch` write.
- **AC3 (R19).** `DELETE` on a `REGISTERED` watch transitions it to `UNREGISTERED` (via the atomic
  conditional `UPDATE`, Finding 4), sets `unregistered_at` from the injected `Clock` (Finding 5),
  returns `204`.
- **AC4 (Outputs, Finding 4/6).** `DELETE` on an unknown `watchId` returns `404`; on an
  `UNREGISTERED`- or `EXPIRED`-status watch returns `204` with no state change.
- **AC5 (Outputs, Finding 2/7).** Invalid `POST` input (missing field, unrecognized `chain`,
  non-positive/fractional/scientific-notation `expectedAmount`, past `expiresAt`, oversize
  `address`/`tokenContractAddress`) returns `400`, no rows written.
- **AC6 (L8, Finding 1).** A structurally invalid `address` or `tokenContractAddress` for the request's
  `chain` (per `AddressValidator`) returns `400`, no rows written.
- **AC7 (agents.md).** The new grant migration gives `crypto_app` exactly `INSERT, SELECT, UPDATE` on
  `watches` and `INSERT, SELECT` on `chain_cursors` — no broader privilege.
- **AC8 (L15).** `watch/` imports no other feature module's *entity*; its one cross-module dependency
  (`token.AddressValidator`) is a stateless predicate, not an entity.
- **AC9 (R27, already satisfied).** `/internal/v1/watches` requires `internal.crypto:write` — inherited,
  not new work in this task.

### Required Tests

- `shouldRegisterWatchAndReturnWatchId` (`package.md` §8, named) — AC1.
- `shouldUnregisterWatchOnDelete` (`package.md` §8, named) — AC3.
- `ChainCursor` row created alongside `Watch` with the documented `watch_id`/`chain`/sentinel values —
  AC2.
- `DELETE` on an unknown `watchId` → `404` — AC4.
- `DELETE` on an already-`UNREGISTERED` watch → `204`, no state change — AC4.
- `DELETE` on a manually-seeded `EXPIRED` watch → `204`, no state change (Finding 6) — AC4.
- Invalid `POST` bodies: missing field, bad `chain`, non-positive `expectedAmount`, fractional
  `expectedAmount` (`"1.5"`), scientific-notation `expectedAmount` (`"1e18"`), past `expiresAt`, oversize
  `address` (>128 chars) — each → `400`, no row written (Finding 2/7) — AC5.
- Structurally invalid `address`/`tokenContractAddress` for the given chain (reusing T12's own known-bad
  EIP-55/Base58Check vectors) → `400`, no row written (Finding 1) — AC6.
- Two identical `POST` requests produce two distinct `watchId`s and two `Watch`/`ChainCursor` row pairs
  (Finding 3, documents the accepted non-idempotency risk in executable form).
- Migration-grant integration test update confirming `watches`/`chain_cursors` are no longer in
  `UNGRANTED_TABLES` and that `crypto_app` has exactly the AC7 privileges.
- A source-scan module-boundary test for `watch/` (mirrors T10/T11/T14 precedent), allow-listing the one
  `token.AddressValidator` import per AC8.

### Constraints

- **Performance:** not a concern at this task's scope.
- **Security:** relies entirely on the already-existing `ResourceServerConfig` scope check; no new
  security logic. Error responses never leak stack traces or internal detail (agents.md).
- **Thread-safety:** `WatchService` is a stateless Spring singleton; all state lives in the database.
- **Transaction:** `WatchService.register` is `@Transactional` (atomic `Watch` + `ChainCursor` insert).
  `WatchService.unregister` relies on the atomic conditional `UPDATE` (Finding 4) for race-safety rather
  than a separate lock.
- **Module boundaries:** `watch/` depends only on `common` (Clock) and `token.AddressValidator`
  (Finding 1) — no import of `observation`, `provider`, `quorum`, `finality`, `events`, or any other
  module's entity.
- **Null handling:** repository lookups return `Optional`; `WatchService.unregister` throws
  `WatchNotFoundException` (mapped to 404) rather than returning `null`/`Optional.empty()` to the
  controller layer.
- **`WatchExceptionHandler` is `@Order(Ordered.HIGHEST_PRECEDENCE)`** (Finding 9), matching
  `ApiKeyExceptionHandler`'s own established precedent.
- **Timestamps are Clock-sourced, not DB-defaulted** (Finding 5) — `Watch.createdAt`/`unregisteredAt`
  and `ChainCursor.updatedAt` are set explicitly from the injected `Clock` in application code.
- **Validation-response shape must be verified empirically, not assumed.** Whether Spring Boot 3.5.4's
  default `@Valid` failure handling already produces a compliant `application/problem+json` body, or
  whether `WatchExceptionHandler` must add an explicit mapping, is to be confirmed by direct testing in
  Phase 6/7, mirroring this pipeline's established verify-don't-assume discipline.

### Open Questions

No blockers. All 9 Phase 3 findings resolved above.
