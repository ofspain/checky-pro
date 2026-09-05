<!-- MODEL: Claude Sonnet — Phase 2 (Task Implementation Brief). -->

# auth · T29 · Phase 2 — Task Implementation Brief

## Task

SAS revoke integration — ensure `ReuseDetectingAuthorizationService` revokes the associated
`RefreshTokenFamily` when SAS's `/oauth2/revoke` endpoint is called with a refresh token.

## Purpose

Today, revoking a refresh token via the standard OAuth2 revocation endpoint (RFC 7009) only marks
the token invalidated inside SAS's own `oauth2_authorization` row — it never touches this service's
own `refresh_token_family` bookkeeping. This leaves `GET /accounts/me/sessions` (T28) showing a
session as still active after the client has already revoked it via the standard endpoint, and
leaves the family's own revocation state (used by reuse detection) out of sync with reality.

## Scope

**In:** the internal logic change inside `ReuseDetectingAuthorizationService.save(...)` (and a new
supporting method on `RefreshTokenTracker`) that detects a refresh-token invalidation and revokes
the matching family.

**Out:** any new HTTP endpoint, DTO, or exception handler; any change to
`RefreshTokenFamilyRepository`'s query surface (the needed lookup already exists); any change to the
existing reuse-detection (`findByToken`) behavior; the scheduled-cleanup job (T30); rate limiting
(T31); contracts (T33/T34).

## Business Rules

- **R39.** A refresh-token invalidation via `/oauth2/revoke` SHALL also revoke the family.

## Locked Decisions

None — confirmed at Phase 0 and Phase 1; no `L`-numbered decision constrains this task.

## Dependencies

`OAuth2Authorization` / `OAuth2Authorization.Token` (`getRefreshToken()`, `isInvalidated()`),
`RefreshTokenTracker`, `RefreshTokenFamilyRepository.findByAuthorizationId` (existing), `Clock`
(existing), `AuditService` / `RecordAuditEventRequest` (existing, resolution below).

## Inputs

The `OAuth2Authorization` instance passed into `ReuseDetectingAuthorizationService.save(...)` by
SAS's `OAuth2TokenRevocationAuthenticationProvider`, with its refresh token's `INVALIDATED_METADATA_NAME`
metadata flag set to `true`.

## Outputs

None (no HTTP response — SAS's own revocation endpoint response is untouched by this task).

## State Changes

- The `RefreshTokenFamily` row matched by `authorization.getId()` (via
  `RefreshTokenTracker.findByAuthorizationId`, already used elsewhere in this class) has
  `revoked_at`/`revoked_reason` set — **only when** `authorization.getRefreshToken() != null &&
  authorization.getRefreshToken().isInvalidated()`. Reason string: **`"OAUTH2_REVOKE"`** (resolves
  Phase 1's OQ4 — distinct from T07's `"USER_REVOKED_ALL"`, T28's `"USER_REVOKED"`/
  `"USER_REVOKED_ALL"`, and the reuse path's `"REUSE_DETECTED"`).
- No change to `revoked_at` when the invalidated token is an access token only (refresh token
  untouched) — this is the natural, unmodified behavior of checking `isInvalidated()` specifically
  on the refresh token accessor, not something requiring separate logic (resolves AC3).
- No change to the existing `trackIssuance`/`trackRotation` call already present in
  `trackRefreshTokenIfPresent` — it continues to run on every `save()` unconditionally, exactly as
  today; on a revoke-triggered save the refresh token's hash is unchanged from the family's current
  hash (SAS's `invalidate()` mutates only per-token metadata, never the token value), so
  `trackRotation`'s existing same-hash-is-a-no-op guard already makes this a harmless no-op — no
  special-casing needed to avoid double bookkeeping.
- One `auth_audit` row via `AuditService`, event type **`"session.revoked"`** (resolves Phase 1's
  OQ3 — reusing T28's event type since both describe the same underlying fact, "a session/family was
  revoked," with the `reason` field distinguishing the trigger), outcome `SUCCESS`, `accountUuid`
  parsed from the family's `principalName` with the same UUID-parse-or-null fallback already used in
  `auditReuseDetected`.
- **Resolution of Phase 1's OQ1** (interaction with existing reuse-detection): accepted as-is, no
  change. If a presented token to `/oauth2/revoke` is archived/superseded, `findByToken`'s existing
  reuse check already revokes the family (reason `"REUSE_DETECTED"`) and returns `null` before SAS's
  revocation provider ever reaches `save()` — this is arguably correct behavior already (a stale
  token presented to any endpoint, including revoke, is itself a theft signal worth flagging), not a
  defect this task needs to fix. Flagged for Phase 3 to pressure-test, not silently assumed.
- **Resolution of Phase 1's OQ2** (shape): a new `RefreshTokenTracker` method,
  `revokeForAuthorization(String authorizationId, String reason)`, mirroring the existing
  `revokeAllForPrincipal` naming/shape (`@Transactional`, looks up via repository, calls
  `family.revoke(reason, clock.instant())`, no-ops if not found or already revoked) — keeps
  `ReuseDetectingAuthorizationService` a thin decorator with zero direct
  `RefreshTokenFamilyRepository` access, consistent with its current design.

## Files to Create

None.

## Files to Modify

- `services/auth/src/main/java/com/themistra/auth/token/ReuseDetectingAuthorizationService.java`
  — in `save(...)`, after the existing `trackRefreshTokenIfPresent(authorization)` call, detect a
  refresh-token invalidation and call the new tracker method; add the audit call.
- `services/auth/src/main/java/com/themistra/auth/token/RefreshTokenTracker.java` — add
  `revokeForAuthorization(String authorizationId, String reason)`.

## Files NOT to Modify

- `RefreshTokenFamily.java` (its existing `revoke(...)` is reused unchanged).
- `RefreshTokenFamilyRepository.java` (its existing `findByAuthorizationId` is reused unchanged).
- Any controller, DTO, or exception handler (T28's `SessionService`/`AccountController`/
  `SessionExceptionHandler` included) — this task's surface is entirely internal to `token`.
- `SecurityChainsConfig.java` / SAS configuration — `/oauth2/revoke`'s own wiring is untouched.

## Acceptance Criteria

- **AC1 (R39).** A `/oauth2/revoke`-style save (refresh token invalidated) on an authorization with
  an active family revokes that family with reason `"OAUTH2_REVOKE"`.
- **AC2 (R39).** Re-processing an already-revoked family via this path is a no-op (no exception, no
  reason/timestamp overwrite) — relies on `RefreshTokenTracker`'s existing idempotency pattern.
- **AC3 (R39).** A save where only the access token is invalidated (refresh token untouched, or
  authorization has no refresh token at all — e.g. client-credentials grants) does NOT revoke the
  family and does not throw (null-safe on `getRefreshToken()`).
- **AC4 (non-regression).** An ordinary token-endpoint rotation save (new refresh token, nothing
  invalidated) is unaffected — `trackRotation` behaves exactly as before T29.
- **AC5 (non-regression).** An ordinary first-issuance save (`trackIssuance` path) is unaffected.
- **AC6 (non-regression).** The existing reuse-detection path in `findByToken` is untouched and its
  own 8-test suite stays green.
- **AC7.** Exactly one `session.revoked` audit row is recorded per family revoked via this path,
  correctly attributed to the account UUID when `principalName` is UUID-shaped.

## Required Tests

Extend the existing `ReuseDetectingAuthorizationServiceTest` (mirrors its current
Mockito/`Clock.fixed` style):
1. Save with refresh token invalidated + active family → family revoked, reason `"OAUTH2_REVOKE"`,
   one audit call.
2. Save with refresh token invalidated + already-revoked family → no exception, no second audit
   call, `revoke` not re-invoked with a different reason (behavior owned by
   `RefreshTokenTracker`/`RefreshTokenFamily`'s existing idempotency — assert the observable
   outcome, not internal call counts if that's more brittle).
3. Save with only the access token invalidated → family untouched, no audit call for this path.
4. Save with no refresh token on the authorization at all (e.g., client-credentials shape) → no
   `NullPointerException`, no family interaction.
5. Ordinary rotation save (new refresh token, not invalidated) → existing `trackRotation`
   behavior unchanged (regression).
6. Ordinary first-issuance save → existing `trackIssuance` behavior unchanged (regression).
7. Full existing 8-test suite still green (regression).
8. Integration-level (Docker-permitting, following T28's `SessionIntegrationTest` pattern): seed a
   real `OAuth2Authorization` + `RefreshTokenFamily`, call `POST /oauth2/revoke` over real HTTP
   against the actual SAS filter chain with the refresh token, then assert the family row's
   `revoked_at`/`revoked_reason` directly via `EntityManager`.

No `package.md` §8 named test maps to this task (confirmed Phase 1) — test names above are proposed
fresh, for Phase 3/4 to confirm/adjust.

## Constraints

- **Transaction:** the new `revokeForAuthorization` method must be `@Transactional`, matching every
  other `RefreshTokenTracker` mutation method (`trackIssuance`, `trackRotation`,
  `revokeAllForPrincipal`).
- **Null handling:** `authorization.getRefreshToken()` can be `null` (e.g. client-credentials
  grants have no refresh token) — must guard before calling `.isInvalidated()`.
- **Thread-safety:** no new shared mutable state; same statelessness as the class's existing methods.
- **Module boundaries (L12):** all changes stay within `token`; no new cross-module imports.
- **Security:** must not weaken or alter the existing reuse-detection behavior in `findByToken` in
  any way — that logic is untouched by this task.
- **Idempotency:** re-invoking the new path against an already-revoked family must be a safe no-op,
  matching every other revoke path in this codebase (`SessionService`, `checkAndRegisterPresentation`).
- **Performance:** negligible — one additional conditional repository lookup only on the rare
  revoke-save path; the unconditional per-save `trackRefreshTokenIfPresent` hot path is unchanged.

## Open Questions

No blockers. (OQ1-OQ4 from Phase 1 all have a proposed resolution above, offered for Phase 3/4 to
challenge or confirm — none are treated as silently decided.)

---

**Phase 2 complete — Task Implementation Brief written.** Proceed to Phase 3 (Kimi design
challenge) on approval.
