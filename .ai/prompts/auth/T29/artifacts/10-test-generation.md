<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T29 · Phase 10 — Test Generation

Test manifest for the resolved implementation (`artifacts/09-review-resolution.md`). No production
code changed in this phase. All tests below were written and executed.

## `RefreshTokenTrackerTest.java` (extended — 3 new tests, 16 total)

Direct, mocked-repository coverage of the new `revokeForAuthorization` method, mirroring how every
other tracker method is already tested in isolation in this file.

| Test | Verifies |
|---|---|
| `revokeForAuthorizationRevokesExistingUnrevokedFamilyAndReturnsTrue` | AC1 (R39) — active family gets `revoked_at`/`revoked_reason="OAUTH2_REVOKE"` set, `familyRepository.save` called, returns `true`. |
| `revokeForAuthorizationIsANoOpOnAlreadyRevokedFamilyAndReturnsFalse` | AC2 — already-revoked family untouched (original reason preserved), no `save` call, returns `false`. |
| `revokeForAuthorizationReturnsFalseWhenNoFamilyExists` | Boundary — unknown `authorizationId` returns `false`, no exception, no `save` call. |

## `ReuseDetectingAuthorizationServiceTest.java` (extended — 7 new tests + 1 existing test
strengthened, 15 total)

Decorator-level coverage with mocked `tracker`/`auditService`/`delegate`. Added a new
`invalidatedRefreshTokenAuthorization(principalName)` helper alongside the existing
`authorizationWithRefreshToken(tokenValue)`, since the revoke path never needs a raw token value —
only the `isInvalidated()` flag.

| Test | Verifies |
|---|---|
| `saveDoesNotRevokeWhenOnlyAccessTokenInvalidated` *(new)* | AC3 — an ordinary rotation-shaped save (refresh token present, not invalidated) never calls `revokeForAuthorization`, never audits. |
| `saveSkipsTrackingWhenAuthorizationHasNoRefreshToken` *(strengthened)* | AC3 — added `verify(tracker, never()).revokeForAuthorization(...)` to the existing no-refresh-token test. |
| `saveRevokesFamilyAndAuditsWhenRefreshTokenIsInvalidated` *(new)* | AC1/AC7 — active family: `revokeForAuthorization` called with `"OAUTH2_REVOKE"`, exactly one `session.revoked` audit row with `accountUuid`/`actorUuid` both equal to the parsed principal UUID (Finding 3). |
| `saveNeverTracksIssuanceOrRotationWhenRefreshTokenIsInvalidated` *(new)* | **Kimi Phase 8 Finding 1 regression** — a revoke-shaped save never calls `familyMissingFor`/`trackIssuance`/`trackRotation`, proving the mutual-exclusivity fix directly. |
| `saveDoesNotAuditWhenRevokeForAuthorizationReportsNoOp` *(new)* | AC2 / Finding 2 (Phase 3) — `revokeForAuthorization` returning `false` (no-op) never triggers an audit call. |
| `saveAuditsWithNullAccountWhenPrincipalIsNotAUuid` *(new)* | AC7 boundary — non-UUID principal → `accountUuid`/`actorUuid` both `null`, no exception. |
| `saveSwallowsAuditFailureWithoutPropagating` *(new)* | AC8 / D2 — `auditService.record` throwing does not propagate out of `save(...)`. |
| `saveSwallowsRevokeFailureWithoutPropagatingOrAuditing` *(new)* | **Kimi Phase 8 Finding 2 regression** — `tracker.revokeForAuthorization` throwing does not propagate out of `save(...)` and never reaches the audit call. |

## `RefreshTokenFamilyIntegrationTest.java` (extended — 2 new tests, 4 total; D3-scoped)

Spring-context-level tests (Testcontainers Postgres) calling the real `OAuth2AuthorizationService`
bean's `save(...)` directly with a manually-invalidated refresh token, per D3 — not a full HTTP
`/oauth2/revoke` round-trip (would need SAS client-credential plumbing out of scope). New
`@Autowired` fields: `OAuth2AuthorizationService`, `RegisteredClientRepository`,
`AuthClientsProperties` (same package, no new import needed for the last one).

| Test | Verifies |
|---|---|
| `savingAnInvalidatedRefreshTokenRevokesTheFamily` | R39/AC1 end-to-end — issue a real authorization (decorator creates the family), then save an `OAuth2Authorization.from(...).invalidate(refreshToken).build()` copy (mirrors SAS's own revocation-provider shape exactly, traced at Phase 0/3), then confirm the family is genuinely revoked via `checkAndRegisterPresentation` returning `UNKNOWN` for its former current hash. |
| `savingAnInvalidatedRefreshTokenForAnUntrackedAuthorizationDoesNotCreateAPhantomFamily` | **Kimi Phase 8 Finding 1 regression, full-stack** — an authorization whose very first save is already invalidated (the decorator has never tracked it before) must not create a family at all; asserts `familyMissingFor(...)` stays `true` afterward. |

## Verification performed

- `mvn -pl services/auth -am clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest='ReuseDetectingAuthorizationServiceTest,RefreshTokenTrackerTest'`
  — **31/31 pass** (16 + 15; 8 pre-existing decorator tests + 13 pre-existing tracker tests, all
  still green, plus 10 new tests across both files).
- `mvn -pl services/auth test -Dtest='RefreshTokenFamilyIntegrationTest'` — compiles clean; all 4
  tests (2 pre-existing + 2 new) fail identically on the same pre-existing
  `Could not find a valid Docker environment` `ApplicationContext` failure as every other
  Testcontainers-backed test this entire session (Docker unavailable) — not a compile or logic
  error. This is now the **fifth** consecutive task (T25–T29) carrying a fully-written-but-never-
  executed integration suite.

No named `package.md` §8 test maps to this task (confirmed at Phase 1) — all test names above were
proposed fresh through Phases 2/5/9/10.

---

**Phase 10 complete — test manifest written.** Proceed to Phase 11 (Kimi test review) on approval.
