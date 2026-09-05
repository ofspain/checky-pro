<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). -->

# auth · T28 · Phase 1 — Specification Extraction

## Business Rules

- **R36** — An authenticated user calling `GET /accounts/me/sessions` gets their active refresh-token families back, each with device label, creation time, and last-rotation time.
- **R37** — An authenticated user calling `DELETE /accounts/me/sessions/{familyId}` gets that family revoked and its live SAS authorization removed.
- **R38** — An authenticated user calling `DELETE /accounts/me/sessions` (no `familyId`) gets *all* their families revoked and all their live SAS authorizations removed.
- **R43** *(referenced, cross-cutting)* — every security-relevant action is audited; session revocation qualifies.
- **R46** *(referenced, cross-cutting)* — 4xx responses are RFC 9457 `application/problem+json`, no internal detail.

## Locked Decisions

**None.** This task's own header confirms no LOCKED decision constrains it — the first task in this recent sequence (T25–T28) without one. Correspondingly more design latitude for Phase 2/3/4 to resolve than usual.

## Files Involved

**Existing, to read/extend (no modification expected to the domain logic itself):**
- `token/RefreshTokenFamily.java` — entity; `familyId`, `principalName`, `deviceLabel`, `createdAt`, `rotatedAt`, `revokedAt`, and the existing `revoke(reason, now)` mutator.
- `token/RefreshTokenFamilyRepository.java` (package-private) — `findByPrincipalNameAndRevokedAtIsNull` (R36's exact query, already exists). No method yet to find one family by `familyId` scoped to a specific owner (needed for R37).
- `token/RefreshTokenTracker.java` — `revokeAllForPrincipal(principalName, reason)` (R38's exact bulk operation, already exists). No method yet for revoking a *single* family by id.
- `token/ReuseDetectingAuthorizationService.java` / the `OAuth2AuthorizationService` interface — `findById(String)`/`remove(OAuth2Authorization)`, the task statement's named mechanism for purging a live SAS authorization on revoke.
- `account/AccountController.java` — the established "act on my own resource" pattern (`/accounts/me`, `/accounts/me/password`); `/accounts/me/sessions` is a natural extension of this same base path, mirroring how T25/T26 both extended `ApiKeyController` rather than starting new controller classes.
- `account/AccountExceptionHandler.java` — the module-scoped `@RestControllerAdvice` pattern to extend if a new session-specific rejection exception is needed.

**New, not named anywhere in `design.md`'s file map (§6 has no entry for sessions at all — confirmed by direct search, consistent with "no LOCKED decision" for this task):**
- A response DTO for the session-list items (exact shape not specified beyond R36's three named fields plus, presumably, the `familyId` itself so a client can target a specific `DELETE`).
- Some new orchestration logic connecting "revoke the family" to "also remove the SAS authorization" — whether this lives in a new service class, an extension of `RefreshTokenTracker`, or directly in the controller is a genuine Phase 2 design decision, not resolved here.

## Dependencies

- `RefreshTokenFamilyRepository`, `RefreshTokenTracker`, `OAuth2AuthorizationService` (autowire the interface — only one bean exists in the context, the `ReuseDetectingAuthorizationService` decorator).
- `Authentication` → `UUID.fromString(authentication.getName())`, matching every other self-service endpoint's established caller-identity pattern.
- `AuditService` — for the new audit event(s) this task's revoke operations should record (R43, referenced; no session-revocation-specific audit event type exists yet).
- No new config key. No new migration expected (`refresh_token_family`/`refresh_token_archive` tables already exist).

## Acceptance Criteria

- **AC1** — `GET /accounts/me/sessions` requires authentication; returns only the caller's own active families. *(R36)*
- **AC2** — Each returned session includes `familyId` (needed to target a `DELETE`), device label, creation time, and last-rotation time. *(R36)*
- **AC3** — `DELETE /accounts/me/sessions/{familyId}` requires authentication; revokes only a family the caller owns; an unowned or nonexistent `familyId` gets a uniform rejection (no enumeration oracle, matching `ApiKeyService.revoke`'s established precedent). *(R37)*
- **AC4** — A successful single-family revoke also removes the corresponding live SAS authorization — not just the family row. *(R37, task statement)*
- **AC5** — `DELETE /accounts/me/sessions` (no path segment) revokes every active family the caller owns and removes every corresponding live SAS authorization. *(R38, task statement)*
- **AC6** — Every revoke action (single or bulk) is audited. *(R43, referenced)*
- **AC7** — Rejections are RFC 9457, no internal detail. *(R46, referenced)*

## Tests Required

**Named (verbatim, per this task's header — note: mismapped in `package.md` §8 itself, see Open Questions):** `shouldListActiveSessions`, `shouldRevokeSingleSessionFamily`, `shouldRevokeAllSessionFamilies`.

Unlike T27, these three names are **not** already used elsewhere in this codebase (confirmed: no `token`/`account` test file currently exists for this domain's HTTP layer) — so, unlike T27's situation, these three should likely be written as three genuinely new test methods, not reconciled against pre-existing coverage. This is a meaningfully different situation from T27's and should not be assumed to follow the same "one flow test, distinct name" pattern without Phase 2 confirming it.

**Boundary / supporting (implied, not yet decided how to split by phase):**
1. `GET /accounts/me/sessions` with zero active sessions → empty list, not an error.
2. `GET /accounts/me/sessions` never returns another account's sessions.
3. `GET /accounts/me/sessions` excludes already-revoked families (matches the existing repository query's filter) — or does it? Genuinely open, see Phase 0's note.
4. `DELETE /accounts/me/sessions/{familyId}` for an unowned family vs. a nonexistent one → identical rejection shape.
5. `DELETE /accounts/me/sessions/{familyId}` on an already-revoked family → idempotent success, matching `RefreshTokenFamily.revoke`'s existing no-op-on-already-revoked behavior.
6. `DELETE /accounts/me/sessions` with zero active families → succeeds trivially (nothing to revoke), not an error.
7. Both revoke variants actually remove the SAS authorization, not just mark the family revoked — verifiable via `OAuth2AuthorizationService.findById(authorizationId)` returning null afterward.
8. Both revoke variants are audited exactly once per revoked family.

## Open Questions

- **OQ1 (genuine, needs a Phase 2 design decision):** the `deviceLabel` field this task must return is hardcoded to `null` everywhere it's ever set (`ReuseDetectingAuthorizationService.trackRefreshTokenIfPresent`) — `design.md` §4b's **O3** (an OPEN, not LOCKED, decision) explicitly says the device-label source is unresolved by the spec author. This task's own statement says nothing about populating it at issuance time. Proposed default for Phase 2 to ratify or override: T28 returns whatever `deviceLabel` currently is (i.e., always `null` today) and does **not** attempt to resolve O3 or touch issuance-time code — that's a different task's scope. Not a blocker; flagged for explicit confirmation, not silent assumption.
- **OQ2 (non-blocking, structural):** `package.md` §8's traceability table maps this task's three named tests to R32/R33/R34 — wrong; the correct matches by content are R36/R37/R38, which this task's own header already states correctly. Same recurring bug flagged at every prior task; `spec/` immutable, flagged for the spec author, not fixed here.
- **OQ3 (non-blocking, structural):** whether `GET /accounts/me/sessions` should list only non-revoked families (matches the existing repository query, and R36's literal "active" wording) or all families including revoked ones (matching `ApiKeyService.list`'s "show everything, let the timestamp field speak for itself" precedent) — genuinely two defensible readings, Phase 2's call.
- **No true blockers.** All underlying domain operations either already exist (`findByPrincipalNameAndRevokedAtIsNull`, `revokeAllForPrincipal`) or compose cleanly from existing pieces (`OAuth2AuthorizationService.findById`/`remove`).

---

**Phase 1 complete — specification extracted.** Proceed to Phase 2 (Task Implementation Brief) on approval.
