<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). -->

# auth · T29 · Phase 1 — Specification Extraction

## Business Rules

- **R39.** WHEN the SAS `/oauth2/revoke` endpoint receives a valid refresh token, THEN the decorated
  `ReuseDetectingAuthorizationService` SHALL also revoke the associated family.
- **R43** *(referenced, not scoped)*. WHEN any security-relevant action occurs (the list explicitly
  includes "token reuse"; family revocation via `/oauth2/revoke` is a token-lifecycle event of the
  same class), THEN the system SHALL append an `auth_audit` row and mirror a reduced event via the
  outbox. Whether this task's own revocation must independently satisfy R43 (vs. relying on some
  other already-audited path) is listed under Open Questions, not assumed.
- **R46** *(referenced, not scoped)* — 4xx uniformity/no-detail rule. **Not applicable to this
  task's own surface**: T29 adds no new HTTP endpoint or exception handler; `/oauth2/revoke`'s
  response shape is entirely SAS's own (RFC 7009), unmodified by this task.

## Locked Decisions

**None.** Confirmed at Phase 0 and re-confirmed here against `design.md`: no `L`-numbered LOCKED
decision names or constrains this task. (`D-003`/`D-016`, cited in existing code comments, are
entries in `services/auth/docs/architecture/auth-decisions.md` — outside the `spec/auth-service/`
package this pipeline draws LOCKED decisions from, and not phrased as constraints on this task;
treated as background context only, per Phase 0.)

## Files involved

**Existing files to read/extend:**
- `token/ReuseDetectingAuthorizationService.java` — `save(...)` override is the only place SAS's
  `/oauth2/revoke` flow ever lands (traced in Phase 0: SAS calls `findByToken` then `save(...)`,
  never `remove(...)`, for a revoke). This is almost certainly the file to modify.
- `token/RefreshTokenTracker.java` — owns all family-mutation logic today
  (`trackIssuance`/`trackRotation`/`revokeAllForPrincipal`/`checkAndRegisterPresentation`); a new
  method here (e.g., something keyed by `authorizationId`) is the likely extension point, to keep
  `ReuseDetectingAuthorizationService` a thin decorator that never touches
  `RefreshTokenFamilyRepository` directly (matches the class's own existing division of
  responsibility — it currently has zero direct repository access).
- `token/RefreshTokenFamilyRepository.java` — already exposes `findByAuthorizationId(String)`,
  the exact lookup needed; package-private, so any new logic must live inside `token`.
- `token/RefreshTokenFamily.java` — `revoke(reason, now)` already idempotent; no change expected.
- `audit/AuditService.java` / `audit/RecordAuditEventRequest.java` — already injected into
  `ReuseDetectingAuthorizationService`, used today for `token.reuse_detected`; reusable as-is if an
  audit event is confirmed in scope (Open Question).

**No new files expected.** Unlike T25-T28, this task adds no controller, DTO, or exception
handler — it is a pure internal-logic change to an existing decorator/tracker pair.

## Dependencies

- `org.springframework.security.oauth2.server.authorization.OAuth2Authorization` — specifically its
  `getRefreshToken()` accessor and the per-token `isInvalidated()` metadata flag (SAS 1.5.1,
  `OAuth2Authorization.Token`), traced in Phase 0 as the signal that distinguishes a
  `/oauth2/revoke`-of-a-refresh-token save from an ordinary rotation save.
- `OAuth2AuthorizationService` (interface T29's class implements) — no interface change; T29 stays
  within the existing `save`/`remove`/`findById`/`findByToken` contract.
- `RefreshTokenTracker`, `RefreshTokenFamilyRepository`, `RefreshTokenFamily` (all existing, `token`
  package).
- `AuditService`, `RecordAuditEventRequest` (existing, `audit` package) — only if Open Question on
  audit scope resolves to "yes."
- `Clock` — already a constructor dependency of `RefreshTokenTracker`; any new revoke-timestamping
  logic must go through it, not `Instant.now()`.
- No new config keys, no new migration, no new contract file changes expected.

## Acceptance Criteria

- **AC1 (R39).** When `/oauth2/revoke` is called with a refresh token whose authorization has an
  active (unrevoked) `RefreshTokenFamily`, that family is marked revoked as a direct result of the
  revoke call.
- **AC2 (R39).** The revocation is idempotent: calling `/oauth2/revoke` again with the same
  already-invalidated token (or a second revoke call against an already-revoked family) does not
  error and does not double-process.
- **AC3 (R39, scoping).** Revoking only the **access** token via `/oauth2/revoke`
  (`token_type_hint=access_token`, or presenting the access token value) does **not** revoke the
  family — R39's trigger is explicitly "called with a refresh token," and the SAS-source-traced
  cascade (Phase 0) confirms access-token-only invalidation never touches the refresh token's
  invalidated flag, so this should fall out naturally rather than need special-casing — but must be
  asserted, not assumed.
- **AC4 (R39, non-regression).** Ordinary refresh-token rotation via `POST /oauth2/token`
  (`grant_type=refresh_token`) continues to behave exactly as today — new family tracked via
  `trackRotation`, family NOT marked revoked — proving the new logic doesn't fire on a normal
  issuance/rotation `save()`.
- **AC5 (R39, non-regression).** The existing reuse-detection path (`findByToken` →
  `REUSE_DETECTED` → `family.revoke("REUSE_DETECTED", ...)`) continues to work unchanged; T29's new
  logic must not double-revoke, error, or otherwise interfere when both paths could theoretically
  touch the same family in one request lifecycle (see Open Questions).
- **AC6 (R43, conditional on Open-Question resolution).** If audit is confirmed in scope: exactly
  one audit row is recorded per family revoked via this path, distinguishable in outcome/event-type
  from `token.reuse_detected`.

## Tests required

**No named `package.md` §8 test maps to this task** — confirmed independently at Phase 1 (grepped
§8 directly: the only R36-R41-adjacent rows are `shouldCleanupExpiredTokensAndFamilies` → R36 and
`shouldConformToAuthOpenApiContract` → R40/R41, both wrongly numbered per the now-familiar §8
mismapping pattern, and neither is actually this task's scenario regardless). Phase 2 must name new
test(s) from scratch, same situation T28 was in for its own three.

Implied boundary/regression tests (from the ACs above, subject to Phase 2/3 refinement):
1. `/oauth2/revoke`-style save (refresh token invalidated) on an authorization with an active family
   → family becomes revoked.
2. Same, but the family is already revoked → no error, stays revoked, no double side-effects.
3. `/oauth2/revoke`-style save presenting only the access token invalidated (refresh token
   untouched) → family NOT revoked.
4. Ordinary rotation `save()` (new refresh token, nothing invalidated) → existing `trackRotation`
   behavior unchanged, family NOT revoked.
5. First-ever issuance `save()` (`trackIssuance` path) → unaffected, no interaction with new logic.
6. Regression: full existing `ReuseDetectingAuthorizationServiceTest` suite (8 tests) stays green.
7. If audit is in scope: exactly one audit row per revoke-via-this-path, correctly attributed.
8. Integration-level (Docker-permitting): a real `POST /oauth2/revoke` HTTP call against the actual
   SAS filter chain results in the family row being marked revoked in the database — the genuine
   end-to-end proof, following T28's `SessionIntegrationTest` real-`OAuth2Authorization`-seeding
   pattern.

## Open Questions

- **OQ1 (carried from Phase 0, genuine blocker for Phase 2/3 design, not this phase to resolve).**
  How should T29's new revoke-triggered family revocation compose with the existing reuse-detection
  path inside `findByToken`? SAS's revoke flow calls `findByToken` first (Phase 0 trace) — if the
  presented refresh token happens to be an archived/superseded one, `checkAndRegisterPresentation`
  already treats it as `REUSE_DETECTED` and revokes the family via a completely different code path
  (with `"REUSE_DETECTED"` as the revoke reason) before SAS's revocation provider would ever reach
  `save()`. Need Phase 2/3 to confirm this pre-empting behavior is acceptable as-is (a stale-token
  revoke attempt getting treated as a reuse/theft signal rather than an ordinary revoke) or whether
  it needs adjustment.
- **OQ2.** Is a new `RefreshTokenTracker` method the right shape (e.g.
  `revokeForAuthorization(authorizationId, reason)`, mirroring `revokeAllForPrincipal`'s existing
  naming), or should `ReuseDetectingAuthorizationService.save(...)` call
  `RefreshTokenFamilyRepository` directly? The former keeps the class's existing "decorator never
  touches the repository directly" pattern; the latter would be the first exception to it. Not
  decided here — Phase 2/3's call.
- **OQ3.** Does R43's blanket audit requirement apply to this specific trigger, and if so, what
  `event_type` string should be used — reuse `session.revoked` (T28's naming), a new
  `session.revoked_via_oauth2_revoke`, or something else? T29's own task statement does not mention
  audit at all, unlike T28's brief which was explicit about it. Flagged, not assumed.
- **OQ4.** What `reason` string should be recorded on `RefreshTokenFamily.revoke(reason, ...)` for
  this trigger — a new value (e.g. `"OAUTH2_REVOKE"`) distinct from T07's `"USER_REVOKED_ALL"`,
  T28's `"USER_REVOKED"`/`"USER_REVOKED_ALL"`, and the reuse path's `"REUSE_DETECTED"`? No existing
  precedent value fits; a new one is expected but the exact string isn't specified anywhere in the
  spec package.

None of these four are true blockers to *starting* Phase 2 (a Task Implementation Brief can propose
answers for Phase 3/4 to challenge/confirm, per this pipeline's established pattern for tasks with
no LOCKED decisions — see T28's own D1-D6 precedent) — logged here so Phase 2 addresses each
explicitly rather than silently picking a default.

---

**Phase 1 complete — specification extraction written.** Proceed to Phase 2 (Task Implementation
Brief) on approval.
