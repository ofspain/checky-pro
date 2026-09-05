# auth · T28 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Approved by femi at the Phase 4 human gate, 2026-08-16. Consumes `artifacts/02-task-implementation-brief.md` (TIB) and `artifacts/03-design-challenge.md` (Kimi 2.7, 6 findings). Downstream phases may not renegotiate this brief.

---

## Task

T28 — **Session listing/revocation.** Add `GET /accounts/me/sessions` and `DELETE /accounts/me/sessions/{familyId}` / `DELETE /accounts/me/sessions`. Query `refresh_token_family`; on revoke, remove the live SAS authorization via `OAuth2AuthorizationService`.
(Verbatim, `spec/auth-service/tasks.md` task 28.)

## Purpose

Let a user see and end their own active sessions — individually or all at once — with a revoked session's tokens genuinely, immediately unusable, not merely marked revoked in a row nobody checks again.

---

## Phase 4 decisions (gate outcomes)

### D1 — Repository query must not break idempotent revoke (resolves Kimi #1)

**Decided:** the new repository method is `findByFamilyIdAndPrincipalName` (no `revokedAt` filter). `SessionService.revokeOne` loads the row, verifies ownership via the principal match, then calls the already-idempotent `family.revoke(...)`. Only a genuinely nonexistent or unowned `familyId` throws `SessionNotFoundException` → 404; an already-revoked-but-owned family still returns 204.

### D2 — Null-safe SAS authorization removal (resolves Kimi #2)

**Decided:** `SessionService` treats a `null` result from `OAuth2AuthorizationService.findById(authorizationId)` as "already gone" — a no-op, not an error — and continues to revoke the family row and audit. Mirrors the exact defensive pattern this codebase's own `ReuseDetectingAuthorizationService.findByToken` already uses (`if (compromised != null) { delegate.remove(compromised); }`) for the identical null-return contract.

### D3 — Bulk revoke is best-effort (resolves Kimi #3)

**Decided:** `SessionService.revokeAll` iterates every active family independently; a failure on any one family (revoke, authorization removal, or audit) is logged and the loop continues to the next family rather than aborting the batch. Returns 204 after attempting all of them.

**Why:** matches the user's actual intent ("log me out everywhere") better than an all-or-nothing transaction — a transient failure on one family should still log the user out of everything else that succeeded, not leave every session intact because one cleanup step hiccupped.

### D4 — Active-session definition stays simple (resolves Kimi #4)

**Decided:** "active" means `revoked_at IS NULL` — the existing `findByPrincipalNameAndRevokedAtIsNull` query, unchanged. No join against `oauth2_authorization` to exclude expired-but-uncleaned families. A small, temporary window where a technically-expired-but-not-yet-cleaned-up family briefly still appears as "active" is an accepted, low-impact edge case (the scheduled cleanup job, a different task/requirement, already exists to remove these eventually).

### D5 — Non-UUID principal (resolves Kimi #5)

**Decided:** same disposition as T26's D7 — accepted as a named, pre-existing, service-wide limitation, not fixed here. `UUID.fromString(authentication.getName())` on a non-UUID principal 500s identically to how `GET /accounts/me` already would.

### D6 — `deviceLabel` is null today (resolves Kimi #6)

**Decided:** documented, not treated as a defect. `design.md`'s O3 (device-label source) remains unresolved by the spec author; T28 returns whatever `deviceLabel` currently is. A new AC makes this explicit so a `deviceLabel: null` response is not mistaken for a bug during review or testing.

---

## Phase 3 findings — disposition

| # | Finding | Disposition |
|---|---|---|
| 1 | Repository query would break idempotent 204 | **Accepted → D1.** |
| 2 | `findById` null-safety | **Accepted → D2.** |
| 3 | Bulk revoke failure semantics unspecified | **Accepted → D3** (best-effort). |
| 4 | Active-session definition ambiguous | **Accepted → D4** (kept simple). |
| 5 | Non-UUID principal | **Accepted, named limitation → D5.** No code change. |
| 6 | `deviceLabel` always null | **Accepted, documented → D6.** No code change beyond a new AC. |

No findings rejected.

---

## Scope (final)

**In:** `GET /accounts/me/sessions`, `DELETE /accounts/me/sessions/{familyId}`, `DELETE /accounts/me/sessions` — all authenticated. `token/SessionService.java`, `token/dto/SessionResponse.java`, `token/SessionNotFoundException.java`, `token/SessionExceptionHandler.java`. `AccountController` extended with three endpoints. One new `RefreshTokenFamilyRepository` query method. One new `ProblemTypes` constant.

**Out:** `design.md` O3 (device-label source) or `ReuseDetectingAuthorizationService.trackRefreshTokenIfPresent` (issuance-time code) — D6. R39 (SAS `/oauth2/revoke` also revoking a family) — a distinct requirement. `RefreshTokenTracker.revokeAllForPrincipal` (T07's, untouched — D-E from the TIB, unchanged by this gate). A guard for non-UUID principals — D5. Any Flyway migration, config key, or contract file. A cross-table join against `oauth2_authorization` for "active" — D4.

## Business Rules

- **R36** — list active sessions with device label, created, last-rotated.
- **R37** — revoke one family by id; remove its live SAS authorization.
- **R38** — revoke all families; remove all their live SAS authorizations.
- **R43** *(referenced)* — every revoke audited.
- **R46** *(referenced)* — 4xx is `application/problem+json`, no detail.

## Locked Decisions

None constrain this task.

## Dependencies

`RefreshTokenFamilyRepository` (+ new `findByFamilyIdAndPrincipalName`, D1), `OAuth2AuthorizationService` (interface, null-safe per D2), `AuditService`, `Clock`, `AccountController`'s established `Authentication` → `UUID` pattern.

## Inputs

- `GET /accounts/me/sessions` — authenticated, no body.
- `DELETE /accounts/me/sessions/{familyId}` — authenticated, `familyId` path variable (UUID).
- `DELETE /accounts/me/sessions` — authenticated, no body.

## Outputs

- `GET` → **200**, array of `{familyId, deviceLabel, createdAt, rotatedAt}` — active only (D4). `deviceLabel` is `null` for every item today (D6, not a defect).
- `DELETE .../{familyId}` → **204**, including idempotent success on an already-revoked family (D1).
- `DELETE .../sessions` → **204**, best-effort per family (D3); succeeds trivially with zero active sessions.
- **404** — `SessionNotFoundException`, `ProblemTypes.SESSION_NOT_FOUND`, no detail — only for a genuinely nonexistent or unowned `familyId`.

## State Changes

- `refresh_token_family.revoked_at`/`revoked_reason` set (existing entity behavior).
- The corresponding SAS `oauth2_authorization` row removed via `OAuth2AuthorizationService.remove(...)`, null-safely (D2).
- One `auth_audit` row + outbox mirror per family revoked (single or bulk).

## Files to Create

- `token/SessionService.java`
- `token/dto/SessionResponse.java`
- `token/SessionNotFoundException.java`
- `token/SessionExceptionHandler.java`

## Files to Modify

- `account/AccountController.java` — add `listSessions`, `revokeSession`, `revokeAllSessions`.
- `common/ProblemTypes.java` — add `SESSION_NOT_FOUND`.
- `token/RefreshTokenFamilyRepository.java` — add `findByFamilyIdAndPrincipalName` (D1).

## Files NOT to Modify

`token/RefreshTokenTracker.java`, `token/RefreshTokenFamily.java`, `ReuseDetectingAuthorizationService.java`, `AuthorizationServiceConfig.java`, `account/AccountExceptionHandler.java`, `account/AccountService.java`; all Flyway migrations; `spec/**`.

## Acceptance Criteria

- **AC1** — `GET /accounts/me/sessions` authenticated, own sessions only, active-only (D4). *(R36)*
- **AC2** — Each item has `familyId`, `deviceLabel`, `createdAt`, `rotatedAt`; `deviceLabel` is expected to be `null` today (D6). *(R36)*
- **AC3** — `DELETE .../{familyId}` for an unowned/nonexistent family → uniform 404; for an owned, already-revoked family → 204 (D1). *(R37)*
- **AC4** — A successful single revoke removes the live SAS authorization when one exists; a missing authorization is a no-op, not an error (D2). *(R37)*
- **AC5** — `DELETE .../sessions` revokes every active family and removes every corresponding authorization, best-effort per family (D3). *(R38)*
- **AC6** — Every revoke (single or bulk, per family) is audited exactly once. *(R43)*
- **AC7** — 404 body carries no internal detail. *(R46)*
- **AC8** — `ArchitectureTest` stays green: no new class imports `PublicEndpoints` or a foreign-module entity.

## Required Tests

**Named (verbatim):** `shouldListActiveSessions`, `shouldRevokeSingleSessionFamily`, `shouldRevokeAllSessionFamilies`.

**Boundary:** empty list; cross-account isolation; unowned-vs-nonexistent 404 identity; idempotent re-revoke (D1); revoke when the SAS authorization is already gone (D2); bulk revoke with zero sessions; bulk revoke where one family's processing fails but others still succeed (D3); audit row count per family.

## Constraints

- **Security** — no response ever includes another account's session; 404 carries no detail.
- **Transaction** — per-family processing in `revokeAll` is independent (D3) — no single transaction spanning all families; a failure on one must not affect others already committed.
- **Module boundaries** — `SessionService`/`SessionExceptionHandler` stay in `token`; `AccountController` depends on them as a normal cross-module service dependency, never imports a `token` entity.
- **Null handling** — a malformed `familyId` path segment hits the same pre-existing `MethodArgumentTypeMismatchException` gap already logged at T26 — out of this task's scope to fix, same disposition (D5-adjacent).

## Open Questions

**No blockers.** All six Phase 3 findings resolved at this gate (D1–D6).

---

**Phase 4 complete — brief FROZEN.** Proceed to Phase 5 (Implementation Plan).
