<!-- MODEL: Claude Sonnet — Phase 13 (PR / Commit Preparation). -->

# auth · T28 · Phase 13 — PR / Commit Preparation

Phase 12 verdict was **PASS**. This task is ready for merge. Branches off `main`; `main` stays deployable throughout.

---

## Commit title

```
auth: add session listing and revocation (T28)
```

## Commit message

```
auth: add session listing and revocation (T28)

Add GET /accounts/me/sessions and DELETE /accounts/me/sessions[/{familyId}]
so a caller can list their active refresh-token sessions and revoke one or
all of them. Revoking a session removes its live SAS authorization via
OAuth2AuthorizationService before marking the refresh_token_family row
revoked, since a revoked-but-not-removed authorization would otherwise
still work — the reuse-detection service only blocks REUSE_DETECTED
outcomes, not a revoked family that was never rotated again.

Bulk revoke is best-effort per family: one family's failure (authorization
removal, save, or audit) is logged and does not stop the rest.

Also fixes a pre-existing UnnecessaryStubbingException in
ReuseDetectingAuthorizationServiceTest, unrelated to this task but
surfaced by its independent review.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Production code**
- `services/auth/src/main/java/com/themistra/auth/token/SessionService.java` (new)
- `services/auth/src/main/java/com/themistra/auth/token/dto/SessionResponse.java` (new)
- `services/auth/src/main/java/com/themistra/auth/token/SessionNotFoundException.java` (new)
- `services/auth/src/main/java/com/themistra/auth/token/SessionExceptionHandler.java` (new)
- `services/auth/src/main/java/com/themistra/auth/token/RefreshTokenFamilyRepository.java` (modified — added `findByFamilyIdAndPrincipalName`)
- `services/auth/src/main/java/com/themistra/auth/common/ProblemTypes.java` (modified — added `SESSION_NOT_FOUND`)
- `services/auth/src/main/java/com/themistra/auth/account/AccountController.java` (modified — added `listSessions`, `revokeSession`, `revokeAllSessions`; constructor now also takes `SessionService`)

**Tests**
- `services/auth/src/test/java/com/themistra/auth/token/SessionServiceTest.java` (new, 15 tests)
- `services/auth/src/test/java/com/themistra/auth/token/SessionExceptionHandlerTest.java` (new, 2 tests)
- `services/auth/src/test/java/com/themistra/auth/token/SessionIntegrationTest.java` (new, 8 tests — Docker-blocked, unexecuted)
- `services/auth/src/test/java/com/themistra/auth/account/AccountControllerTest.java` (modified — 14 call sites updated for the new constructor param, 4 new tests)
- `services/auth/src/test/java/com/themistra/auth/token/ReuseDetectingAuthorizationServiceTest.java` (modified — 1-line `lenient()` fix for a pre-existing, unrelated stubbing bug)

## Summary

Implements requirements R36–R38 (session listing/revocation) with no LOCKED decisions in scope. `SessionService` lists an account's active `refresh_token_family` rows and revokes one or all of them, always removing the corresponding SAS `OAuth2Authorization` before marking the family row revoked — an ordering derived from tracing `ReuseDetectingAuthorizationService.findByToken`, where a revoked-but-not-rotated family's token is not blocked by reuse detection and would otherwise keep working. Bulk revoke is deliberately not `@Transactional`; each family is processed independently so one failure doesn't roll back or block the rest, relying on Spring Data's automatic per-call transaction on `save(...)`. Every revoke — success or partial — is audited exactly once per family. Uniform 404s (no body detail) are returned for both unowned and nonexistent families; revoking an already-revoked owned family returns 204 idempotently.

## Testing performed

- `mvn -pl services/auth -am clean compile test-compile` — clean, no errors.
- Unit tests executed and green: `SessionServiceTest` 15/15, `SessionExceptionHandlerTest` 2/2, `AccountControllerTest` 18/18, `ApiKeyExceptionHandlerTest` 5/5, `ReuseDetectingAuthorizationServiceTest` 8/8 (48/48 total across the T28-relevant classes).
- `SessionIntegrationTest` (8 tests, including all three named tests `shouldListActiveSessions`, `shouldRevokeSingleSessionFamily`, `shouldRevokeAllSessionFamilies`) is written and compiles cleanly but has **not executed** — Docker/Testcontainers has been unavailable this entire session, now spanning four consecutive tasks (T25–T28). This is an accumulating pre-merge risk: whoever has Docker available should run the full `apikey`/`token` integration suite in dependency order (T25's `SasLoginIntegrationTest`/`ApiKeyExchangeIntegrationTest` first) before this branch merges to `main`.
- No `ArchitectureTest` violation expected (no cross-module entity imports introduced) but not independently re-run this session for the same Docker-related reason.

## Specification references

- **Task:** T28 — Session listing/revocation (`spec/auth-service/tasks.md`, task 28)
- **Requirements:** R36, R37, R38
- **LOCKED decisions:** none scoped to this task
- **Named tests satisfied (written):** `shouldListActiveSessions`, `shouldRevokeSingleSessionFamily`, `shouldRevokeAllSessionFamilies` (all three in `SessionIntegrationTest`, Docker-blocked — see Testing performed)

---

**Phase 13 complete — PR preparation written. T28 is ready for merge pending an integration-test run once Docker is available.**
