<!-- MODEL: Claude Sonnet — Phase 0 (Repository Understanding). -->

# auth · T29 · Phase 0 — Repository Understanding

## 1. Architecture summary

`auth-service` is a Spring Boot 3.5.4 / Java 21 module built on Spring Authorization Server (SAS)
1.5.1. SAS's default filter chain (`@Order(1)`) exposes the standard OAuth2/OIDC endpoints,
including `/oauth2/revoke`, wired via `OAuth2AuthorizationServerConfigurer`. A second,
application-owned chain (`@Order(2)`) covers this service's own REST endpoints (`/accounts/**`,
`/api-keys/**`). Persistence is Postgres via Spring Data JPA + Flyway. `refresh_token_family` /
`refresh_token_archive` (V2, D-003) are dedicated tables — separate from SAS's own
`oauth2_authorization` table — that track refresh-token rotation lineage and reuse detection,
independent of how SAS itself serializes/stores an authorization (D-016).

The single `OAuth2AuthorizationService` bean in the context is `ReuseDetectingAuthorizationService`
(`token/ReuseDetectingAuthorizationService.java`), a decorator around a delegate
`JdbcOAuth2AuthorizationService`. It is the ONLY object SAS's revocation, token, and introspection
endpoints ever talk to — there is no separate revocation-specific hook point.

## 2. Existing code this task touches

- **`token/ReuseDetectingAuthorizationService.java`** (existing, will need to change). Currently:
  - `save(authorization)` → delegates, then `trackRefreshTokenIfPresent(authorization)`, which
    either starts a new family (`RefreshTokenTracker.trackIssuance`) or records a rotation
    (`RefreshTokenTracker.trackRotation`) based on whether a family already exists for that
    `authorization.getId()`.
  - `remove(authorization)` → delegates only, no family interaction at all.
  - `findByToken(token, tokenType)` → runs the reuse check when `tokenType` is `null` or
    `REFRESH_TOKEN`, then falls through to the delegate regardless of outcome (only `REUSE_DETECTED`
    causes a `null` return / authorization purge).
  - **Currently does NOT mark any family revoked when `/oauth2/revoke` is called.**

- **`token/RefreshTokenTracker.java`** (existing, likely needs a new method or reused method).
  Already has `revokeAllForPrincipal(principalName, reason)` (T07, password reset — revokes every
  unrevoked family for a principal) but nothing keyed by a single `authorizationId`. No existing
  "revoke the one family for this authorization" method.

- **`token/RefreshTokenFamily.java`** (existing, unchanged expected). `revoke(reason, now)` is
  already idempotent (no-ops if `revokedAt` already set) — same idempotency `SessionService` (T28)
  relies on.

- **`token/RefreshTokenFamilyRepository.java`** (existing, package-private). Already has
  `findByAuthorizationId(String)` (used by `familyMissingFor`/`trackRotation`) — the exact lookup
  T29 will need, keyed by the authorization's id rather than family id or principal name.

- **`audit/AuditService.java`** — already injected into `ReuseDetectingAuthorizationService`; used
  today only for `token.reuse_detected`. A new `session.revoked`-style audit event (matching T28's
  `SessionService.recordAudit` naming) is likely in scope per R43's blanket "every revoke is
  audited" requirement, though T29's own task statement doesn't mention audit explicitly.

## 3. How SAS actually drives `/oauth2/revoke` (traced from SAS 1.5.1 sources)

`OAuth2TokenRevocationAuthenticationProvider.authenticate(...)`:
1. `authorization = authorizationService.findByToken(presentedToken, null)` — tokenType is
   `null`, so `ReuseDetectingAuthorizationService.findByToken` treats this as a refresh-token-shaped
   lookup and runs `tracker.checkAndRegisterPresentation(...)` even for a plain revoke call. This is
   pre-existing behavior, not something T29 introduces, but worth flagging: a revoke call presenting
   an already-rotated (archived) refresh token would currently be treated as reuse and purge the
   authorization — need to confirm in Phase 1/2 whether that's acceptable/intended for the revoke
   path specifically.
2. If found, SAS builds `authorization = OAuth2Authorization.from(authorization).invalidate(token).build()`
   then calls **`authorizationService.save(authorization)`** — NOT `.remove(...)`. Revocation, in
   SAS's own model, is "save with the token(s) marked invalidated," not "delete the row."
3. Traced `OAuth2Authorization.Builder.invalidate(token)` (SAS source,
   `OAuth2Authorization.java:501-518`): marking a **refresh token** invalidated also cascades to
   mark the authorization's access token (and unexpired auth code, if any) invalidated in the same
   call — but the reverse is NOT true: revoking only the access token does not touch the refresh
   token's invalidated flag. This is the mechanism by which "was this refresh-token invalidation
   (R39's trigger) vs. some other save" could be distinguished later:
   `authorization.getRefreshToken() != null && authorization.getRefreshToken().isInvalidated()`.

This means the family-revocation logic R39 wants would need to live inside (or be reachable from)
`ReuseDetectingAuthorizationService.save(...)`, since that is the only place a revoke ever lands —
there is no separate `revoke(...)` method on `OAuth2AuthorizationService` to override.

## 4. Established patterns to follow

- **Idempotent revoke, no exceptions on double-revoke**: `RefreshTokenFamily.revoke` and T28's
  `SessionService.revokeOne`/`revokeAll` both treat re-revoking as a harmless no-op.
- **Ordering discovered in T28**: when both "remove/invalidate the live token" and "mark the family
  revoked" must happen together, do the SAS-side action first, then the family-side mark — because a
  family marked revoked while its SAS-side token is still live is the actively dangerous state (a
  session that LOOKS dead but still works), whereas the reverse failure mode just means a retry is
  needed. For T29 specifically, SAS itself already performs its own invalidation via `save(...)`
  before our decorator even runs any family logic, so this ordering concern is naturally satisfied
  as long as family-revocation logic runs from inside/after the `save()` override completes SAS's
  own invalidation semantics.
- **Fixed `Clock` for all timestamping** — `RefreshTokenTracker`/`SessionService` both take a
  `Clock` bean, never `Instant.now()` directly, so tests can control time.
- **Package-private repositories, same-package callers only** — `RefreshTokenFamilyRepository` and
  `RefreshTokenTracker` are both only ever called from within `token`; `ReuseDetectingAuthorizationService`
  is already in that same package, so no cross-module boundary (L12) concern here.
- **Audit via `AuditService.record(RecordAuditEventRequest)`** — see
  `auditReuseDetected` in the same class for the exact call shape, including the
  UUID-shaped-principal-only attribution pattern (non-UUID principal → `accountUuid=null`, not
  guessed).

## 5. Testing conventions

- Unit tests: plain JUnit 5 + Mockito (`MockitoExtension`, strict stubs unless a specific stub
  needs `lenient()`), `Clock.fixed(...)` for determinism. `ReuseDetectingAuthorizationServiceTest`
  already exists and covers `save`/`findByToken`/reuse-detection with mocked
  `RefreshTokenTracker`/`AuditService`/delegate — T29 will extend this file's coverage for the new
  `save()`-triggered revocation path, mirroring its existing structure.
- Integration tests: `@SpringBootTest(RANDOM_PORT)` + Testcontainers Postgres, `TestRestTemplate`.
  T28's `SessionIntegrationTest` shows the pattern for seeding a real `OAuth2Authorization` via the
  actual `OAuth2AuthorizationService` bean and a real `RefreshTokenFamily` via its `start(...)`
  factory + raw `EntityManager` — directly reusable technique for proving an actual `/oauth2/revoke`
  HTTP call against this service's real filter chain (`@Order(1)`, client-authenticated) results in
  the family row being marked revoked.
- ArchUnit (`ArchitectureTest`) enforces L12 (no cross-module entity imports); T29 stays entirely
  within `token`, so no new exposure expected.
- **Environmental note carried forward from T25-T28**: Docker/Testcontainers has been unavailable
  for this entire multi-day session. Every integration test across four consecutive tasks compiles
  clean but has never executed. This will very likely also apply to whatever integration test T29
  needs (a real `/oauth2/revoke` HTTP round-trip needs the full SAS filter chain + a real client
  registration, which is Testcontainers-backed).

## 6. Known gaps / unknowns (flagged, not resolved — Phase 1/2's job)

- **No existing precedent for "revoke exactly one family by `authorizationId`."** `RefreshTokenTracker`
  only has `revokeAllForPrincipal` (by principal, not authorization) and the rotation/issuance
  pair (keyed by authorizationId, but those don't revoke). A new tracker method (or inline logic in
  the decorator using `RefreshTokenFamilyRepository.findByAuthorizationId` directly) is likely
  needed — I do not know which shape Phase 2/3 will prefer.
- **Distinguishing a `/oauth2/revoke`-triggered save from an ordinary rotation save inside
  `ReuseDetectingAuthorizationService.save(...)`.** The SAS-source-traced signal
  (`authorization.getRefreshToken().isInvalidated()`) looks like the right hook, but I have not
  verified there is no other legitimate SAS-internal flow that could also set that flag on a normal
  `save()` call (e.g., token-endpoint-driven revocation-on-rotation for reuse families, or an
  authorization-code invalidation that also happens to touch a refresh token in some grant type not
  yet exercised in this codebase). Flagged for Phase 3 (Kimi) to specifically pressure-test.
  Access-token-only revocation (client sends `token_type_hint=access_token` or the access token
  value itself) does NOT cascade to the refresh token per the traced `invalidate()` source, so R39's
  "called with a refresh token" scoping appears to align exactly with the
  `getRefreshToken().isInvalidated()` signal — but this needs Phase 1/2/3 confirmation, not assumed
  here.
- **Interaction with the existing reuse-check inside `findByToken`.** Since SAS calls `findByToken`
  first (step 1 of the traced flow) before ever calling `save`, an already-revoked-and-archived
  refresh token presented to `/oauth2/revoke` would hit the reuse-detection path (which itself
  already purges the authorization and would separately revoke the family via
  `checkAndRegisterPresentation`'s existing `family.revoke("REUSE_DETECTED", ...)` call) before SAS's
  revocation provider ever reaches `save()`. I do not know whether this pre-existing interaction is
  desired/acceptable as-is or needs any adjustment for T29 — flagged, not assumed.
- **Whether an audit event is required.** R43 is a blanket "every revoke is audited" requirement,
  and T28 added `session.revoked` for its own two revoke paths — whether R39's family-revocation
  should emit the same event type (or a distinct one, e.g. `session.revoked_via_oauth2_revoke`) is
  an open naming/scope question for Phase 1/2, not decided here.
- **No named §8 test maps to this task** (confirmed against `package.md` §8 — none of its rows
  reference T29's scenario), so Phase 1/2 will need to name new test(s) from scratch, same situation
  T28 was in for its own three tests.

I do not know how `RefreshTokenTracker`'s `checkAndRegisterPresentation` reuse-path revocation and
a new T29 revoke-path revocation should compose if a single `/oauth2/revoke` call could ever trigger
both in sequence — this needs explicit design attention in Phase 2/3, not silently assumed.

---

**Phase 0 complete — repository understanding written.** Proceed to Phase 1 (Specification
Extraction) on approval.
