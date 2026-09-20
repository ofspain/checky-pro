# crypto · T15 · Phase 1 — Specification Extraction

## Business Rules

- **R18.** WHEN the Payment Service calls `POST /internal/v1/watches` with a valid service token and
  watch parameters, THEN the system SHALL register a watch, begin watching the address, and return a
  `watchId` with status `REGISTERED`.
- **R19.** WHEN the Payment Service calls `DELETE /internal/v1/watches/{watchId}`, THEN the system
  SHALL stop watching and return `204`.

## Locked Decisions

Derived from `design.md` §4a (none were cited inline in this task's header):

- **L6.** Reorg is a first-class transition; a reorg walks the watcher cursor/checkpoint backward and
  emits `chain.tx.reorged`. No forward-derived state survives a reorg that invalidates it. (Directly
  constrains `ChainCursor`'s existence/shape — its actual walk-back logic is a future task, `reorg/`.)
- **L15.** Module boundaries — package-by-feature under `com.themistra.crypto`; no feature module
  imports another feature module's entity; shared plumbing lives only in `common`. Constrains the new
  `watch/` package's own imports.
- **L13.** Secrets discipline — no committed secret; validated `@ConfigurationProperties` fail startup
  on missing/invalid config in non-local profiles. Applies only if this task introduces any new
  configuration (undetermined until Phase 2).

L7/L8/L9 (token identity, address validation, address-poisoning) are **not** asserted as applicable
here — R18's own literal text says only "register a watch... and begin watching the address," never
"validate." Whether register-time validation via `TokenValidator`/`AddressValidator` is in this task's
scope is listed under Open Questions, not assumed.

## Files involved

**Existing, to read/extend (no modification unless explicitly named):**
- `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql` — `watches` (lines 7-21) and
  `chain_cursors` (lines 63-70) tables, VERBATIM baseline; read-only, not modified.
- `services/crypto/src/main/java/com/themistra/crypto/common/ResourceServerConfig.java`,
  `PublicEndpoints.java` — security is already fully wired for `/internal/v1/watches`; read-only
  pattern reference, not modified.
- `services/crypto/src/test/java/com/themistra/crypto/common/ResourceServerConfigIntegrationTest.java`
  — already implements the named test `shouldRequireInternalScopeForWatchAndAttestEndpoints` (R27)
  against a synthetic controller mirroring this exact path shape; this task does not need to re-prove
  scope enforcement, only exercise its own business logic against the real controller.
- `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java` — its
  `UNGRANTED_TABLES` list will need `watches`/`chain_cursors` removed once a grant migration exists
  (T10/T11 precedent).
- `services/crypto/src/main/java/com/themistra/crypto/provider/ProviderHealth.java` — update-in-place
  entity pattern precedent (`Watch`'s unregister transition mirrors this).
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyController.java` /
  `ApiKeyExceptionHandler.java` — cross-service pattern precedent only (hand-written DTOs, per-module
  `@RestControllerAdvice` + `ProblemDetail`); not part of this module, read-only reference.

**New, per `design.md` §4c/§6 (`watch/` package) — this task's own named subset only:**
- `watch/Watch.java`, `watch/WatchRepository.java`
- `watch/WatchService.java` (register/unregister — R18/R19)
- `watch/ChainCursor.java`, `watch/ChainCursorRepository.java`
- `watch/WatchController.java` (`POST`/`DELETE /internal/v1/watches`)
- A new Flyway grant migration (`V6__crypto_app_watches_grant.sql` or similar) — `watches`/`chain_cursors`
  currently have no grant to `crypto_app` at all (Phase 0 finding).
- Request/response DTOs and a domain exception + `@RestControllerAdvice` for this module's own errors
  (exact shape is a Phase 2 decision).

**Explicitly NOT in this task's own scope** (per the task statement's own literal wording and `design.md`
§6's `watch/` package listing):
- `watch/Watcher.java`, `watch/WatcherRegistry.java` — task 16 (long-running watcher, multi-replica
  assignment).
- `reorg/ReorgDetector.java` — a future task; `ChainCursor` is data-only here.
- Any `chain.tx.*` event emission.

## Dependencies

- `com.themistra.crypto.adapter.Chain` (`ETHEREUM`, `TRON`) — very likely constrains the `chain` request
  field, mirroring every other chain-scoped input in this service.
- `java.time.Clock` (`common.ClockConfig`) — for `created_at`/`unregistered_at`/any `expires_at`
  comparison.
- `java.util.UUID` — `watches.watch_id` has no DB default; must be generated in application code.
- No dependency on `TokenValidator`/`AddressValidator`/`AddressPoisoningDetector` is assumed — open
  question, not a confirmed dependency.
- No dependency on any `ChainAdapter` method is confirmed either — see Open Questions (`ChainCursor`
  initial value).
- `contracts/api/crypto-internal.yaml`, `contracts/events/chain/`,
  `contracts/events/chain/tx-finalized.v1.schema.json` — none of these files exist anywhere in this
  repository yet (confirmed via `find`, same finding as T14); this task touches no contract file, and
  request/response shapes come from `design.md` §4c's VERBATIM text instead.

## Acceptance Criteria

- **AC1 (R18).** `POST /internal/v1/watches` with a valid body (`invoiceUuid`, `chain`, `address`,
  `tokenContractAddress`, `expectedAmount`, `expiresAt`) persists a new `Watch` row with
  `status = REGISTERED` and a freshly generated `watchId`, and returns `200` with
  `{ watchId, status: "REGISTERED" }` (VERBATIM response shape, `design.md` §4c).
- **AC2 (task statement).** Registration also persists a `ChainCursor` row associated with the new
  watch (exact initial-value semantics: Open Question).
- **AC3 (R19).** `DELETE /internal/v1/watches/{watchId}` for a `REGISTERED` watch transitions it to
  `UNREGISTERED` (sets `unregistered_at`) and returns `204`.
- **AC4 (scope, agents.md).** The new grant migration gives `crypto_app` only the privileges this task's
  own read/write pattern needs on `watches`/`chain_cursors` — no blanket `ALL PRIVILEGES` (T02/T10/T11
  precedent).
- **AC5 (L15).** The `watch/` package imports no other feature module's entity; shared plumbing only via
  `common`.
- **AC6 (R27, already satisfied — not new work).** `/internal/v1/watches` requires the
  `internal.crypto:write` scope — inherited for free from `ResourceServerConfig` (T03); this task's own
  test suite does not need to re-prove it.

## Tests required

- `shouldRegisterWatchAndReturnWatchId` (`package.md` §8, named) — AC1.
- `shouldUnregisterWatchOnDelete` (`package.md` §8, named) — AC3.
- A test confirming a `ChainCursor` row is created alongside the `Watch` row on registration — AC2.
- A test for `DELETE` against an unknown/nonexistent `watchId` (exact expected status: Open Question,
  to be resolved at Phase 2 before this test can be written concretely).
- Migration-grant integration test update (`ChainBaselineMigrationIntegrationTest`'s `UNGRANTED_TABLES`)
  removing `watches`/`chain_cursors` once granted — AC4.
- A source-scan module-boundary test for `watch/` (mirroring T10/T11/T14's own precedent) — AC5.

`shouldRequireInternalScopeForWatchAndAttestEndpoints` (R27) is already implemented (T03) against a
synthetic controller of the identical path shape — not re-required here.

## Open Questions

No `package.md` §11 Q-item names watch registration specifically (Q1/Q2/Q3/Q6/Q7 are provider/screening/
KMS; Q5 is watcher transport, task 16's concern, not this task's). Three genuine implementer-facing gaps
remain, matching this pipeline's established pattern of implementer-proposed resolution + Kimi challenge
+ human sign-off:

- **`ChainCursor`'s initial `last_block` source is unresolved.** `ChainAdapter` has no "current block
  number" method independent of an existing transaction hash (`getFinalityStatus` requires a mined
  `txHash`, which a brand-new registration does not have). The task statement says to persist
  `ChainCursor` at registration, but the mechanism to obtain a real initial block number is not
  available anywhere in this codebase yet. Phase 2 must propose a resolution (e.g., a placeholder/null
  initial cursor state populated for real once the watcher, task 16, first runs; or a scoped exception
  to add exactly one new read-only `ChainAdapter` method) rather than silently picking one.
- **`DELETE` semantics for an unknown or already-unregistered `watchId`** are not stated in R19's
  literal text (only the success path is specified). Phase 2 must propose whether this is idempotent
  (`204` regardless) or a `404`.
- **Whether request-field validation is in scope** — R18's own text says "register a watch... and begin
  watching the address," not "validate the address/token/chain." Whether `chain` is constrained to
  `ETHEREUM`/`TRON`, whether `address`/`tokenContractAddress` are checked via the existing
  `AddressValidator`/`TokenValidator`, and whether a past `expiresAt` is rejected, are all Phase 2
  design decisions, not yet resolved.
