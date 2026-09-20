<!-- MODEL: Claude Sonnet — Phase 13 (PR / Commit Preparation). -->

# auth · T26 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Task ready for merge preparation. No code changed in this phase.

---

## Commit Title

```
Add API-key CRUD controller (T26)
```

## Commit Message

```
Add API-key CRUD controller (T26)

Add POST /api-keys, GET /api-keys, and DELETE /api-keys/{keyUuid} to
the existing ApiKeyController (T25 had already scoped that class to
just the /token exchange endpoint). The service layer for all three
operations was already complete (T24's ApiKeyService.create/list/
revoke) - this task is the HTTP surface plus two currently-unmapped
exceptions.

POST /api-keys and GET /api-keys return ApiKeyService's own
CreateApiKeyResult/ApiKeyMetadata records directly rather than
introducing parallel response DTOs: both already have no hash field
by construction, so wrapping them would only add a file to keep in
sync for no safety benefit. design.md's file tree names a separate
ApiKeyResponse.java for this; that tree is illustrative, not literal.

ApiKeyNotFoundException and ApiKeyNotAuthorizedException (both
existing since T24) had no HTTP mapping anywhere before this task -
they would have fallen through to the generic 500 handler. New
mappings in ApiKeyExceptionHandler translate them to 404 and 403
respectively, each with a dedicated ProblemTypes constant and no
variable detail.

CreateApiKeyRequest.name carries its own bean validation
(@NotBlank, @Size(max = 100)), matching ApiKeyService's own limit -
without it, a bad name would 500 instead of 400, since
IllegalArgumentException has no handler either.

One pre-existing, service-wide gap surfaced during review and is
deliberately not fixed here: a malformed keyUuid path segment on
DELETE returns 500, not 400, because MethodArgumentTypeMismatchException
has no handler anywhere in this service - the same exposure already
exists on every admin endpoint using @PathVariable UUID. A test now
documents this actual (undesirable) behavior so a future fix doesn't
go unnoticed; the real fix belongs in a dedicated cross-cutting task
touching the shared ApiExceptionHandler, not this one.

Testcontainers-backed tests are written and compile cleanly but could
not be executed this session (no Docker daemon available); the
Docker-independent unit test suite is green.

Refs: spec/auth-service/tasks.md task 26, R30/R34/R35/R43/R46, L7/L12.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

*(The generated template specifies "Claude Opus 4.8 (1M context)" in this trailer; substituted with the model that actually did the work, same substitution as T16/T25's Phase 13.)*

---

## Files Changed

**Production — created:**
- `services/auth/src/main/java/com/themistra/auth/apikey/dto/CreateApiKeyRequest.java`

**Production — modified:**
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyController.java` — `create`, `list`, `revoke` added
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyExceptionHandler.java` — `onNotFound`, `onNotAuthorized` added
- `services/auth/src/main/java/com/themistra/auth/common/ProblemTypes.java` — `API_KEY_NOT_FOUND`, `API_KEY_NOT_AUTHORIZED` added

**Tests — created:**
- `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyCrudIntegrationTest.java`

**Tests — modified (extended, same classes T26's own production changes touch):**
- `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyControllerTest.java`
- `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyExceptionHandlerTest.java`

No file under `spec/` touched (confirmed: `git diff b76aafc...HEAD -- spec/auth-service/` is empty, `b76aafc` being T25's own final commit). No Flyway migration added. `ApiKeyService.java`, `ApiKeyRepository.java`, `ApiKey.java`, `ApiKeyHasher.java`, `ApiKeyExchangeRejectedException.java`, `ApiKeyNotFoundException.java`, `ApiKeyNotAuthorizedException.java`, `ApiKeyTokenIssuer.java`, `AccountExceptionHandler.java`, `InvalidAccountStateException.java`, `PublicEndpoints.java` — all unmodified.

**Note on commit boundaries:** this session's actual git commits interleave T25's own Phase 11 leftovers (`ApiKeyPropertiesTest.java`, `JwksConfigTest.java` — new files created during T25's test review but not committed until a later, T26-labeled commit) with T26's real changes, since femi commits periodically on his own schedule rather than strictly per-task. The list above reflects actual task ownership per this pipeline's own artifacts (05/06/09/10/11), not raw commit-boundary attribution.

---

## Summary

Implements task 26 of `spec/auth-service/tasks.md`: self-service HTTP access to the API-key lifecycle `ApiKeyService` (T24) already fully implements. A merchant can now create a key, list their own keys, and revoke one, all without operator involvement.

Design work (Phase 3/4) resolved eight ambiguities in the initial task-implementation brief before any code was written — most consequentially, reusing `ApiKeyService`'s own existing result records as HTTP response bodies instead of introducing parallel DTOs (D2/D3), and explicitly deciding to accept the pre-existing `InvalidAccountStateException` → 409-with-detail behavior as-is rather than adding a local override (D6). Self-review and an independent Kimi review (Phase 7/8) both surfaced the same finding — a malformed `keyUuid` path segment 500s rather than 400s, a pre-existing, service-wide gap affecting every other `@PathVariable UUID` endpoint in this service — which was deliberately left unfixed within this task's scope and is now covered by a test that documents the actual behavior rather than silently omitting it.

## Testing Performed

- `mvn -pl services/auth -am compile` and `test-compile` — clean.
- Docker-independent unit/slice tests: **56/56 pass** — `ApiKeyControllerTest` (26, 11 new this task), `ApiKeyExceptionHandlerTest` (5, 3 new), `ApiKeyTokenIssuerTest` (7), `ApiKeyPropertiesTest` (6), `JwksConfigTest` (1), `TokenClaimsCustomizerTest` (8), `ApiKeyHasherTest` (3) — confirming the new controller/handler additions don't disturb T25's existing `exchange`/`onExchangeRejected` behavior.
- `ApiKeyCrudIntegrationTest` (12 tests, Testcontainers + real filter chain via `TestRestTemplate`, authenticated with a real JWT minted through the already-wired `ApiKeyTokenIssuer` bean) covers both named tests plus every boundary/supporting item in the frozen brief's Required Tests list, including a dedicated test documenting the known malformed-`keyUuid` limitation — **not executed this session**, Docker daemon unavailable. Fails only with the same `ApplicationContext failure` every other Testcontainers-backed class in this module currently produces; not a defect in the new tests.
- Two review passes applied before this phase: a self-review (Phase 7) and an independent Kimi review (Phase 8), both resolved directly (Phase 9 human gate needed for the one code change, the rest deferred with reasons); a further Kimi test review (Phase 11) added/strengthened 6 tests.
- **Before merge:** run `ApiKeyCrudIntegrationTest` and `ArchitectureTest` with a working Docker daemon.

## Specification References

- **Task:** `spec/auth-service/tasks.md`, task 26 — API-key CRUD controller.
- **Requirements:** R30, R34, R35 (scoped) — plus R43, R46 (referenced by the frozen brief).
- **LOCKED decisions:** L7 (scoped) — plus L12 (referenced).
- **Full design-decision trail:** `artifacts/03-design-challenge.md` (Kimi, 8 findings) → `artifacts/04-frozen-task-brief.md` (D1–D8, human-decided) → `artifacts/09-review-resolution.md` (Javadoc fix, human-decided) → `artifacts/12-specification-verification.md` (PASS).

---

**Phase 13 complete — pipeline finished, all 14 phases.** No commit run (per established session rhythm — only an explicit "commit it" triggers that). Branch remains `spec/service-specs-and-ai-framework`; `main` untouched throughout.
