# auth · T24 — Task Implementation Brief (TIB)

## Task
Implement `ApiKeyService`: create (gated on `MERCHANT` role + confirmed MFA), list, revoke, exchange (constant-time secret verification), generating keys in the form `ck_live_<suffix>.<secret>`.

## Purpose
T23 gave this module a persistable `ApiKey` row with no behavior on top of it. T24 is where merchant API keys become real: a merchant can create one, see their own keys, revoke one, and — the piece T25's HTTP endpoint will sit on — present a key and have it validated. T31's absence from this task's scope (JWT minting) keeps T24 a pure service-layer task; T25/T26 wire HTTP around it.

## Scope

**In:**
- `create(accountUuid, name)`: verifies `MERCHANT` role + confirmed MFA, generates an L7-compliant key, persists the hash, returns the plaintext exactly once, records `api_key.created`.
- `list(accountUuid)`: returns the caller's own keys, metadata only, no hash/secret material.
- `revoke(accountUuid, keyUuid)`: revokes a key the caller owns, idempotent, records `api_key.revoked`.
- `exchange(presentedKey)`: parses `prefix.secret`, looks up by prefix, constant-time-compares the presented key's hash against the stored hash, rejects revoked/expired/malformed/mismatched keys uniformly, updates `last_used_at` on success, returns the validated key's owning account (no JWT).
- A new `V7` migration widening `api_keys.prefix` to `VARCHAR(32)` (human-authorized — see Locked Decisions).
- A new `ApiKeyRepository` method resolving an account UUID to its internal id (T23 deliberately omitted this as speculative; T24 is the first real caller).
- Two new `ApiKey`/`ApiKeyRepository`-level conditional update methods: touching `last_used_at`, and revoking-if-still-active (idempotent).

**Out:**
- `POST /api-keys`, `GET /api-keys`, `DELETE /api-keys/{keyUuid}` HTTP endpoints — T26.
- `POST /api-keys/token` HTTP endpoint and actual JWT minting (`ApiKeyTokenIssuer`) — T25.
- `@PreAuthorize`/controller-level authorization — doesn't exist yet (no controller); `create(...)` independently re-verifies its own preconditions instead (see Constraints).
- Any `EventTopics`/outbox lifecycle event beyond the audit mirror `AuditService.record` already provides — no requirement in this task's scope (R30/R32/R33) calls for one, and the `design.md` snippet showing an `"api-key"` aggregate-type mapping was never actually added to `EventTopics.java`; treated as stale, not implemented.
- A maximum-active-keys-per-merchant cap — `package.md` §11 Q3 leaves this genuinely unresolved by the spec author; T24 enforces no cap.
- Caller-selectable scopes — `create(...)` always sets `scopes = ["merchant.api"]`; no scope vocabulary beyond this exists anywhere in the spec.

## Business Rules
- **R30.** `MERCHANT` role + confirmed MFA required to create; key format `ck_live_`-prefixed; only a SHA-256 hash of the full key stored; plaintext returned exactly once; `api_key.created` audited.
- **R32.** A successful exchange updates `last_used_at`.
- **R33.** Revoked, expired, malformed, or hash-mismatched keys all fail exchange identically (uniform outward signal — one exception type, matching `AccountExceptionHandler.onVerificationTokenRejected`'s established pattern).

## Locked Decisions
- **L7.** `ck_live_` + 24-char suffix + `.` + 32-char secret; SHA-256 of the full key stored. **Resolution (human decision, this phase):** the `api_keys.prefix` column will be widened via a new `V7` migration (`VARCHAR(16)` → `VARCHAR(32)`) rather than shrinking L7's suffix — L7 is implemented exactly as specified.
- **L12.** No `Account` entity import; account interaction via `AccountService`'s public methods or a repository-level UUID→id resolver.

## Dependencies
- `apikey.ApiKey` / `ApiKeyRepository` (T23).
- `mfa.MfaService.hasConfirmedTotpEnrollment(UUID)`.
- `authz.RoleService.resolveEffectiveRoles(UUID)`.
- `audit.AuditService.record(RecordAuditEventRequest)`.
- `common.Hashing.sha256(String)` + a new constant-time comparison (`java.security.MessageDigest.isEqual(byte[], byte[])`, not yet used anywhere in this codebase).
- `Clock`, `SecureRandom` (constructor-injected / field, matching `MfaService`'s pattern).
- Config: `themistra.auth.api-key.prefix=ck_live_` (existing).

## Inputs
- `create`: caller's account UUID (from the eventual authenticated principal — T26's job to supply; T24's method signature just takes a `UUID`), a key name.
- `list`/`revoke`: caller's account UUID, plus `keyUuid` for revoke.
- `exchange`: the raw presented key string (`ck_live_<suffix>.<secret>`).

## Outputs
- `create`: the plaintext key (once) + key metadata (uuid, name, created timestamp) — no DTO shape mandated by this task (T26 will define the HTTP response DTO); a plain service-level result type is sufficient.
- `list`: a list of key metadata, no hash/secret.
- `revoke`: none (void) or the revoked key's metadata — TBD Phase 5, no behavioral difference.
- `exchange`: the validated key's owning account UUID + granted scopes (enough for T25 to mint a JWT) — not a JWT itself.

## State Changes
- New `V7` migration: `ALTER TABLE api_keys ALTER COLUMN prefix TYPE VARCHAR(32)` (additive/widening only, no data loss).
- New `api_keys` rows on `create`; `last_used_at` updated on successful `exchange`; `revoked_at` set on `revoke`.
- New `auth_audit` rows (+ Kafka mirror) on `create`/`revoke`, and — per this codebase's general audit mandate (`agents.md`) and `MfaService.confirm`'s `mfa.failed`-on-wrong-code precedent — on every `exchange` rejection.

## Files to Create
- `services/auth/src/main/resources/db/migration/V7__widen_api_key_prefix.sql`
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyService.java`
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyHasher.java` (constant-time compare)
- Exception classes for this module's failure modes (`ApiKeyNotAuthorizedException` or similar for the create-time role/MFA gate, one uniform exchange-rejection exception for R33) — exact naming/count is a Phase 5 decision.
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyExceptionHandler.java` (`@RestControllerAdvice`, matching every other module's convention).
- `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyServiceTest.java` and/or a Testcontainers integration test — Phase 5 decides unit vs. integration split.

## Files to Modify
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKey.java` — add mutators/conditional-update support for `lastUsedAt` and `revokedAt` (exact shape — plain mutator vs. repository-level conditional update — a Phase 5 decision, informed by `RecoveryCodeRepository.markUsed`'s established race-safety precedent).
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyRepository.java` — add a UUID→internal-`accountId` resolver (mirroring `MfaEnrollmentRepository.findAccountIdByUuid`), and whatever conditional `@Modifying` methods `lastUsedAt`/`revokedAt` updates need.

## Files NOT to Modify
- `ApiKey`'s existing mapped columns/annotations from T23 — only additive changes (new methods/mutators), no changes to existing column mappings.
- Any file outside `apikey/` and the one new migration file.
- `spec/`.

## Acceptance Criteria
(AC1–AC9 as enumerated in Phase 1 extraction; restated by ID only, not repeated here — see `01-specification-extraction.md`.)

## Required Tests
- `shouldCreateApiKeyAndShowPlaintextExactlyOnce` (R30, named test).
- `shouldRejectRevokedOrUnknownApiKeyWithUniform401` (R33, named test — "401" in the name refers to the eventual HTTP behavior T25 provides; at this service layer, the test proves the uniform exception/rejection, not the HTTP status itself).
- Boundary tests per Phase 1's list: create without `MERCHANT`; create with `MERCHANT` but no/unconfirmed MFA; exchange with correct prefix + wrong secret; exchange with unknown prefix (both must reject identically); revoke of a non-owned key; list exposes no secret material.

## Constraints
- **Security:** constant-time comparison is mandatory for the exchange secret check (task statement, explicit) — `String.equals`/`Arrays.equals` on the raw or hashed secret is not acceptable. Only a SHA-256 hash of the full key is ever persisted; the plaintext key exists only transiently inside `create(...)`'s return value.
- **Authorization:** since no controller/`@PreAuthorize` exists yet, `create(...)` independently verifies both the `MERCHANT` role (via `RoleService.resolveEffectiveRoles`) and confirmed MFA (via `MfaService.hasConfirmedTotpEnrollment`) itself — defense-in-depth, matching `MfaService.requireActiveAccount`'s established precedent of a service re-checking preconditions rather than trusting the caller.
- **Transactional:** `create`/`revoke` are standard `@Transactional`; `exchange`'s `last_used_at` update should follow this codebase's conditional-`@Modifying`-with-rows-affected-check convention (`RecoveryCodeRepository.markUsed`, `MfaEnrollmentRepository.confirmIfUnconfirmed`) rather than a plain load-mutate-save, for the same race-safety reasons already established there.
- **Idempotency:** `revoke` on an already-revoked key must not error — no-op, matching `RoleService.assignRole`/`removeRole`'s idempotent pattern.
- **Module boundaries (L12):** no `Account` entity import.
- **Null handling:** `exchange` on a malformed presented key (no `.` separator, empty prefix/secret) must fail via the same uniform rejection as every other R33 cause, never a `NullPointerException`/`ArrayIndexOutOfBoundsException` escaping to the caller.

## Open Questions
No blockers remaining for this phase. All five items Phase 1 flagged are now resolved:
- L7 vs. schema width → resolved via `V7` migration (human decision, this phase).
- MERCHANT+MFA enforcement layer → resolved: `create(...)` enforces both itself.
- `EventTopics` "api-key" mapping → resolved: not needed, no requirement in scope calls for it.
- Max-keys/scopes cap (`package.md` §11 Q3) → resolved for this task's purposes: no cap, fixed single scope `merchant.api`.
- Exchange-failure audit event → resolved: yes, record one, matching `MfaService`'s established precedent.

These are proposed resolutions, not yet human-approved beyond the L7/schema decision — Phase 3 should challenge any that don't hold up, and Phase 4 is the actual approval gate for the rest.
