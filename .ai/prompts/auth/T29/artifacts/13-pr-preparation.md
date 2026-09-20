<!-- MODEL: Claude Sonnet — Phase 13 (PR / Commit Preparation). -->

# auth · T29 · Phase 13 — PR / Commit Preparation

Phase 12 verdict was **PASS**. This task is ready for merge. Branches off `main`; `main` stays
deployable throughout.

---

## Commit title

```
auth: revoke refresh-token family on SAS /oauth2/revoke (T29)
```

## Commit message

```
auth: revoke refresh-token family on SAS /oauth2/revoke (T29)

ReuseDetectingAuthorizationService now revokes the matching refresh_token_family
whenever SAS's /oauth2/revoke endpoint invalidates a refresh token. SAS never
calls remove() to revoke - it calls save() with the token's invalidated flag
set - so the new logic lives inside the existing save() override, branching on
authorization.getRefreshToken().isInvalidated() to distinguish a revoke from an
ordinary rotation or issuance.

Independent review caught two real bugs before merge: the original save() order
would have created a phantom family (plus a misleading audit event) for any
authorization not yet tracked when a revoke arrived, and the new tracker call
had no protection against its own exceptions surfacing as a caller-visible
error for a SAS call that had already succeeded. Both are fixed, with dedicated
regression tests at the unit and integration level.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Production code**
- `services/auth/src/main/java/com/themistra/auth/token/RefreshTokenTracker.java` (modified —
  added `revokeForAuthorization`)
- `services/auth/src/main/java/com/themistra/auth/token/ReuseDetectingAuthorizationService.java`
  (modified — `save(...)` restructured into mutually-exclusive revoke/track branches; added
  `isRefreshTokenInvalidated`, `revokeFamilyForInvalidatedRefreshToken`, `auditSessionRevoked`,
  `parseAccountUuid`; `auditReuseDetected` refactored to reuse `parseAccountUuid`)

**Tests**
- `services/auth/src/test/java/com/themistra/auth/token/RefreshTokenTrackerTest.java` (modified —
  4 new tests)
- `services/auth/src/test/java/com/themistra/auth/token/ReuseDetectingAuthorizationServiceTest.java`
  (modified — 9 new tests, 1 existing test strengthened, 1 new test helper)
- `services/auth/src/test/java/com/themistra/auth/token/RefreshTokenFamilyIntegrationTest.java`
  (modified — 2 new tests, Docker-blocked)

## Summary

Implements R39: a refresh-token invalidation via SAS's standard `/oauth2/revoke` endpoint now also
revokes the corresponding `refresh_token_family`, keeping `GET /accounts/me/sessions` (T28) and
reuse-detection state in sync with tokens revoked through the standard OAuth2 mechanism, not just
through this service's own session-management endpoints. No LOCKED decision was in scope. The
implementation traces SAS 1.5.1's actual revocation-provider behavior (it invalidates via `save()`,
never `remove()`) to find the exact signal — `getRefreshToken().isInvalidated()` — that
distinguishes a revoke-shaped save from every other kind, so access-token-only revocations and
ordinary rotations/issuances are correctly left untouched. Independent review caught two genuine
bugs pre-merge: an ordering issue that would have created a phantom family for any untracked
authorization, and an unguarded exception path that could have surfaced a caller-visible error for
an already-successful SAS operation. Both are fixed and covered by dedicated tests at both the unit
and integration level. One trade-off was made explicitly at the human gate: a rare concurrent
double-revoke race could produce two audit rows instead of one, accepted as a documented residual
(AC7 amended accordingly) rather than adding locking not used elsewhere in this codebase for the
same class of operation.

## Testing performed

- `mvn -pl services/auth -am clean compile test-compile` — clean, no errors.
- Unit tests executed and green: `RefreshTokenTrackerTest` 17/17, `ReuseDetectingAuthorizationServiceTest`
  16/16 (33/33 total, up from 21 pre-existing).
- `RefreshTokenFamilyIntegrationTest` (4 tests, including 2 new for this task — one proving the
  revoke end-to-end against the real JDBC-backed store with reason/audit-row assertions, one
  proving the phantom-family fix at the full-stack level) compiles cleanly but has **not
  executed** — Docker/Testcontainers has been unavailable this entire session, now spanning five
  consecutive tasks (T25–T29). Whoever has Docker available should run the full accumulated
  integration suite in dependency order (T25's `SasLoginIntegrationTest`/`ApiKeyExchangeIntegrationTest`
  first) before this branch merges to `main`.
- No `ArchitectureTest` violation expected (all changes confined to `com.themistra.auth.token`) but
  not independently re-run this session for the same Docker-related reason.

## Specification references

- **Task:** T29 — SAS revoke integration (`spec/auth-service/tasks.md`, task 29)
- **Requirements:** R39
- **LOCKED decisions:** none scoped to this task
- **Named tests (`package.md` §8):** none map to this task; all test names were proposed fresh
  through Phases 2/5/9/10/11

---

**Phase 13 complete — PR preparation written. T29 is ready for merge pending an integration-test
run once Docker is available.**
