<!-- MODEL: Claude Sonnet — Phase 2 (Task Implementation Brief). -->

# auth · T28 · Phase 2 — Task Implementation Brief (TIB)

## Task

**Session listing/revocation.** Add `GET /accounts/me/sessions` and `DELETE /accounts/me/sessions/{familyId}` / `DELETE /accounts/me/sessions`. Query `refresh_token_family`; on revoke, remove the live SAS authorization via `OAuth2AuthorizationService`. *(Verbatim, `spec/auth-service/tasks.md` task 28.)*

## Purpose

Let a user see and end their own active sessions (one refresh-token family = one continuous login lineage) — both individually and all at once — with a revoked session's tokens genuinely unusable immediately, not just marked revoked in a row nobody checks again.

## Scope

**In:** `GET /accounts/me/sessions`, `DELETE /accounts/me/sessions/{familyId}`, `DELETE /accounts/me/sessions`, all authenticated. A new `token/SessionService.java` (module-boundary reasoning below). Audit for every revoke.

**Out:** Resolving `design.md` O3 (device-label source) or touching `ReuseDetectingAuthorizationService.trackRefreshTokenIfPresent` (issuance-time code) — T28 returns whatever `deviceLabel` already is (`null`, today) and does not attempt to populate it (OQ1, TIB decision below). R39 (SAS `/oauth2/revoke` also revoking a family) — a distinct requirement, not scoped here. Any Flyway migration, config key, or contract file.

## Business Rules

- **R36** — list active sessions with device label, created, last-rotated.
- **R37** — revoke one family by id; remove its live SAS authorization.
- **R38** — revoke all families; remove all their live SAS authorizations.
- **R43** *(referenced)* — every revoke audited.
- **R46** *(referenced)* — 4xx is `application/problem+json`, no detail.

## Locked Decisions

None constrain this task.

**TIB design decisions (no LOCKED decision to defer to — flagged for Phase 3/4, not silently assumed):**
- **D-A (module home):** the domain logic (`list`, `revokeOne`, `revokeAll`) lives in a new `token/SessionService.java`, not in `account`, because `RefreshTokenFamilyRepository` is package-private to `token` — only a `token`-package class can use it directly. `AccountController` (in `account`) calls this new service, mirroring the existing pattern of `TokenClaimsCustomizer` (in `token`) already depending on `authz.RoleService` — cross-module *service* dependencies are established; only cross-module *entity* imports are forbidden (L12).
- **D-B (controller home):** the three endpoints are added to the existing `AccountController` (not a new controller), since `/accounts/me/sessions` is a natural extension of the `/accounts/me` resource family `AccountController` already owns — mirrors T25/T26 extending `ApiKeyController` rather than fragmenting one resource's endpoints across files.
- **D-C (exception ownership):** a new `token/SessionNotFoundException` and a new, small `token/SessionExceptionHandler` (`@RestControllerAdvice`, global regardless of which package's controller triggers it — same mechanism already relied on for `InvalidAccountStateException` resolving correctly across module boundaries at T26's own Phase 4 gate). Kept in `token`, not folded into `account/AccountExceptionHandler`, so the module that owns the exception also owns its translation.
- **D-D (list scope, resolves Phase 1 OQ3):** `GET /accounts/me/sessions` returns only non-revoked families (matches R36's literal "active" wording and the existing repository query's own filter) — not a superset including revoked ones.
- **D-E (bulk revoke, cannot reuse `RefreshTokenTracker.revokeAllForPrincipal` as-is):** that existing method (used by T07's password-reset flow) only marks rows revoked — it does **not** remove live SAS authorizations, which R38 explicitly requires. `SessionService.revokeAll` therefore does its own iteration (fetch active families, revoke each, remove each corresponding SAS authorization, audit each) rather than calling the existing method and bolting authorization-removal on afterward. `RefreshTokenTracker.revokeAllForPrincipal` itself is untouched — T07's behavior must not change.
- **D-F (audit event naming):** one `session.revoked` audit row per family revoked, for both the single and bulk endpoints (bulk = N rows, one per family) — consistent with `ApiKeyService`'s one-row-per-entity-action precedent.

## Dependencies

`RefreshTokenFamilyRepository` (needs a new method to find one family, or filter the existing list, by `familyId` — Phase 5 detail), `OAuth2AuthorizationService` (autowire the interface; `findById`/`remove`), `AuditService`, `Clock`. `AccountController`, `AccountController`'s established `Authentication` → `UUID` pattern. No new repository beyond one new query method on the existing one; no new entity.

## Inputs

- `GET /accounts/me/sessions` — authenticated, no body.
- `DELETE /accounts/me/sessions/{familyId}` — authenticated, `familyId` path variable (UUID).
- `DELETE /accounts/me/sessions` — authenticated, no body.

## Outputs

- `GET` → **200**, array of `{familyId, deviceLabel, createdAt, rotatedAt}` (D-D: active only).
- `DELETE .../{familyId}` → **204** on success, including idempotent success on an already-revoked family (matches `RefreshTokenFamily.revoke`'s existing no-op behavior).
- `DELETE .../sessions` (bulk) → **204**, even if the caller had zero active sessions (nothing to do is still success).
- **404** — `SessionNotFoundException` for `{familyId}` not owned by the caller or not existing at all (single exception type, no enumeration hint, mirrors `ApiKeyNotFoundException`).

## State Changes

- `refresh_token_family.revoked_at`/`revoked_reason` set (existing entity behavior, unmodified).
- The corresponding SAS `oauth2_authorization` row removed via `OAuth2AuthorizationService.remove(...)` — **new** state change this task introduces.
- One `auth_audit` row + outbox mirror per family revoked.

## Files to Create

- `token/SessionService.java`
- `token/dto/SessionResponse.java`
- `token/SessionNotFoundException.java`
- `token/SessionExceptionHandler.java`

## Files to Modify

- `account/AccountController.java` — add `listSessions`, `revokeSession`, `revokeAllSessions`.
- `common/ProblemTypes.java` — add `SESSION_NOT_FOUND`.
- `token/RefreshTokenFamilyRepository.java` — add one query method to resolve a family by id scoped to (or checked against) a principal.

## Files NOT to Modify

`token/RefreshTokenTracker.java` (D-E — used as-is, not extended or changed; its bulk-revoke method is T07's, left alone), `token/RefreshTokenFamily.java`, `ReuseDetectingAuthorizationService.java`, `AuthorizationServiceConfig.java`, `account/AccountExceptionHandler.java` (D-C), `account/AccountService.java`; all Flyway migrations; `spec/**`.

## Acceptance Criteria

- **AC1** — `GET /accounts/me/sessions` authenticated, own sessions only, active-only (D-D). *(R36)*
- **AC2** — Each item has `familyId`, `deviceLabel`, `createdAt`, `rotatedAt`. *(R36)*
- **AC3** — `DELETE .../{familyId}` for an unowned/nonexistent family → uniform 404. *(R37)*
- **AC4** — A successful single revoke removes the live SAS authorization, verifiable via `OAuth2AuthorizationService.findById` returning null afterward. *(R37)*
- **AC5** — `DELETE .../sessions` revokes every active family and removes every corresponding authorization. *(R38)*
- **AC6** — Every revoke (single or bulk, per family) is audited exactly once. *(R43)*
- **AC7** — 404 body carries no internal detail. *(R46)*
- **AC8** — `ArchitectureTest` stays green: no new class imports `PublicEndpoints` or a foreign-module entity.

## Required Tests

**Named (verbatim):** `shouldListActiveSessions`, `shouldRevokeSingleSessionFamily`, `shouldRevokeAllSessionFamilies` — three genuinely new tests (not already covered elsewhere, unlike T27's situation).

**Boundary:** empty list; cross-account isolation; unowned-vs-nonexistent 404 identity; idempotent re-revoke; bulk revoke with zero sessions; authorization actually removed (both variants); audit row count.

## Constraints

- **Security** — no response ever includes another account's session; 404 carries no detail.
- **Transaction** — bulk revoke's per-family (row-revoke + authorization-removal + audit) should be considered as a unit; audit itself is already `REQUIRES_NEW` (independent commit) per existing `AuditService` design — Phase 5 to detail exact transaction boundaries.
- **Module boundaries** — `SessionService`/`SessionExceptionHandler` stay in `token`; `AccountController` depends on them as a normal cross-module service dependency (D-A), never imports a `token` entity.
- **Null handling** — `familyId` path variable framework-converts to UUID; a malformed value hits the same pre-existing `MethodArgumentTypeMismatchException` gap already logged at T26 (out of this task's scope to fix, same disposition).

## Open Questions

**No blockers.** D-A through D-F above are this TIB's own recommendations, made in the absence of any LOCKED decision — all available for Phase 3/4 to challenge or confirm, per this task's unusually open design space.

---

**Phase 2 complete — TIB written.** Proceed to Phase 3 (Design Challenge, Kimi 2.7) on approval.
