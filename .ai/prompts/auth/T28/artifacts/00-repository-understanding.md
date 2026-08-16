<!-- MODEL: Claude Sonnet — Phase 0 (Repository Understanding). -->

# auth · T28 · Phase 0 — Repository Understanding

## 1. Architecture Summary

`auth-service` is the platform's OIDC/OAuth2 identity issuer (Spring Boot 3.5.4, Java 21, Spring Authorization Server). Refresh-token lifecycle tracking lives in its own module, `token`, deliberately independent of SAS's own `oauth2_authorization` persistence (D-003/D-016): `RefreshTokenFamily` records one row per issued refresh-token lineage (one family = one continuous "session" from a client's perspective, surviving rotations), `RefreshTokenArchiveEntry` records superseded token hashes for replay detection. `ReuseDetectingAuthorizationService` decorates SAS's own `OAuth2AuthorizationService` (a `JdbcOAuth2AuthorizationService` delegate) — it is the **only** `OAuth2AuthorizationService` bean in the application context (`AuthorizationServiceConfig`), so any code needing to touch a live SAS authorization (find it, remove it) autowires this same interface type and gets the decorator transparently.

## 2. Existing Code This Task Touches

**Already fully built (frozen, prior tasks; not to be modified beyond what this task explicitly needs):**
- `token/RefreshTokenFamily.java` — entity. Fields directly relevant to this task: `familyId` (UUID, the external identifier — this is almost certainly the task's `{familyId}` path variable), `principalName` (the account UUID string for interactive grants — matches `Authentication.getName()`'s convention exactly), `deviceLabel`, `createdAt`, `rotatedAt` (R36's three required response fields), `revokedAt`/`revokedReason` (already has a `revoke(reason, now)` mutator, idempotent by design).
- `token/RefreshTokenFamilyRepository.java` — package-private. `findByPrincipalNameAndRevokedAtIsNull(String)` already exists and is exactly R36's "active... families" query (no new query needed for listing). **No existing method to find one family by `familyId` scoped to a specific `principalName`** — `findById(UUID)` (inherited `JpaRepository`) exists but doesn't check ownership; a new repository method or a service-level ownership check will be needed for `DELETE /accounts/me/sessions/{familyId}` (R37).
- `token/RefreshTokenTracker.java` — pure domain logic (`trackIssuance`, `trackRotation`, `revokeAllForPrincipal`, `checkAndRegisterPresentation`). **`revokeAllForPrincipal(String principalName, String reason)` already exists and is exactly R38's bulk-revoke operation** — no new domain logic needed there either, just a caller. No existing method to revoke a *single* family by id (only "all for a principal") — new, small logic needed for R37.
- `token/ReuseDetectingAuthorizationService.java` — the sole `OAuth2AuthorizationService` implementation in the app context; exposes `findById(String)` and `remove(OAuth2Authorization)` (inherited interface methods, delegated straight through) — these are exactly the two calls the task statement names ("remove the live SAS authorization via `OAuth2AuthorizationService`").
- `token/AuthorizationServiceConfig.java` — declares the single `OAuth2AuthorizationService` bean; any new service class can simply constructor-inject `OAuth2AuthorizationService` (the interface) to reach it.
- `account/AccountController.java` — the established pattern for "act on my own resource" endpoints (`GET /accounts/me`, `POST /accounts/me/password`), deriving the caller via `UUID.fromString(authentication.getName())`. `/accounts/me/sessions` is a natural extension of this same base path (`@RequestMapping("/accounts")`) — mirrors how T25/T26 both extended `ApiKeyController` rather than starting a new controller class for closely related endpoints on one resource family.

**Nothing new exists yet:** no controller endpoint, no DTO, at `/accounts/me/sessions`.

## 3. Established Patterns

- **Ownership checks:** `ApiKeyService.revoke`'s pattern (T24/T26) — resolve the caller's identity, look up the target row, verify ownership, throw a single uniform not-found-shaped exception if it doesn't exist *or* isn't owned (no enumeration oracle between the two causes). The direct precedent for R37's `DELETE /accounts/me/sessions/{familyId}`.
- **Idempotent revoke:** `RefreshTokenFamily.revoke(...)` is already idempotent (a no-op on an already-revoked family) — matches `ApiKey`/`ApiKeyRepository.revokeIfActive`'s established idempotency discipline.
- **Error handling:** RFC 9457 via `ProblemDetail`, module-scoped `@RestControllerAdvice` (`AccountExceptionHandler` already exists for the `account` module, where this task's endpoint likely lives).
- **Authorization removal on revoke:** the task statement is explicit that revoking a family must also purge the corresponding live SAS authorization, not just mark the family row revoked — otherwise a still-valid access/refresh token pair could keep working even after the user believes they've "logged out" that session. `family.getAuthorizationId()` is exactly the id `OAuth2AuthorizationService.findById(...)` needs.
- **Audit:** every security-relevant action is audited (R43, cross-cutting) — session revocation is squarely "security-relevant"; no existing audit event type for this exists yet (`token.reuse_detected` is the only session-adjacent one, for a different cause).

## 4. Testing Conventions

- Plain JUnit + Mockito for controller-level unit tests, direct construction — established precedent (`AccountControllerTest`, `ApiKeyControllerTest`).
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` + `TestcontainersConfiguration` for anything touching the real filter chain or schema — no `MockMvc` precedent anywhere in this module.
- **Known environment limitation, carried forward from T25/T26/T27:** Docker/Testcontainers has been unavailable this entire multi-day session (`docker info` fails). Any Testcontainers-backed test written for T28 should be expected to compile but not execute under the same constraint unless this changes.

## 5. Known Gaps / Unknowns

- **`deviceLabel` is always `null` today.** `ReuseDetectingAuthorizationService.trackRefreshTokenIfPresent` hardcodes `tracker.trackIssuance(authorization.getId(), authorization.getPrincipalName(), null, hash)` — nothing in this codebase ever populates a real device label. `design.md` §4b lists this explicitly as **O3, an OPEN (not LOCKED) decision**: "Session/device label source... Propose a default and how it is surfaced in `GET /accounts/me/sessions`" — genuinely unresolved by the spec author. R36 requires the response to include "device label," but until O3 is resolved and something populates the column, every session will report a `null` label. **This task (T28) is scoped to listing/revocation, not to issuance** — populating `deviceLabel` would mean touching `ReuseDetectingAuthorizationService.trackRefreshTokenIfPresent`, which is arguably out of this task's stated scope (`tasks.md`'s own task 28 wording says nothing about device labels at issuance time). Flagged as a genuine open question for Phase 1/2, not resolved here.
- **No LOCKED decision governs this task** (confirmed by this task's own header: "none — no LOCKED decision constrains this task") — meaning there is more design latitude here than in T25/T26, and correspondingly more for Phase 3/4 to weigh in on.
- **`contracts/api/auth.yaml`/`token-claims.md` still don't exist** — same gap noted at every task since T25; not blocking.
- **I do not know** whether `GET /accounts/me/sessions` should include revoked families (matching `list()`'s "show everything, let revokedAt speak for itself" pattern already established for API keys) or exclude them (matching R36's literal "active... families" wording, which `findByPrincipalNameAndRevokedAtIsNull` already filters for). The existing repository method already filters to non-revoked, which is a strong signal toward "active only," but this is a Phase 1/2 call, not decided here.
- **I do not know** the exact response DTO shape beyond R36's three named fields (device label, creation time, last-rotation time) plus presumably the `familyId` itself (needed for the client to target a specific `DELETE .../{familyId}` call) — not specified further in `requirements.md`/`design.md`.
- **R39** ("SAS `/oauth2/revoke` also revokes the associated family") is explicitly **not** in this task's scoped requirement list (R36/R37/R38 only) — confirmed by re-reading `requirements.md`, R39 is a distinct, separate requirement about the `/oauth2/revoke` *endpoint's* behavior, not the self-service session-management endpoints this task adds. Out of scope, not to be conflated.

---

**Phase 0 complete — repository understanding written.** Proceed to Phase 1 (Specification Extraction) on approval.
