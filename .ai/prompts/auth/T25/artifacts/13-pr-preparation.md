<!-- MODEL: Claude Sonnet — Phase 13 (PR / Commit Preparation). -->

# auth · T25 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Task ready for merge preparation. No code changed in this phase.

---

## Commit Title

```
Add API-key exchange endpoint (T25)
```

## Commit Message

```
Add API-key exchange endpoint (T25)

Add POST /api-keys/token (public): validate a presented merchant API
key, update its last_used_at, mint a 10-minute RS256 access token via
a new ApiKeyTokenIssuer, and audit every attempt.

No SAS grant runs for a key exchange, so TokenClaimsCustomizer can't
apply here (it requires a RegisteredClient + Authentication that don't
exist for this flow). ApiKeyTokenIssuer assembles the full L9 claim
set itself instead, against a JwtEncoder bean newly declared in
JwksConfig - the same NimbusJwtEncoder/JWKSource SAS would otherwise
build internally, so no existing grant changes signing behavior. The
endpoint uses a custom "ApiKey" Authorization scheme rather than
Bearer, so the presented key is never handed to the resource-server
JWT filter and mis-decoded before reaching the controller.

Two issues surfaced during review needed fixes beyond the original
plan:
- The new JwtEncoder had no explicit JWK selector, so it would throw
  during any live key-rotation window (CURRENT + PREVIOUS both
  configured) instead of signing with CURRENT as intended - this
  would have broken every token the service issues, not only this
  endpoint. Fixed with an explicit selector.
- CSRF protection only exempted /api/**, which matches none of this
  service's actual paths, so the endpoint (and, pre-existingly, four
  other public self-service POST endpoints) would 403 every real
  anonymous caller. Fixed by exempting this endpoint's own path;
  the pre-existing gap on the other four is logged as a follow-up,
  not fixed here.

Testcontainers-backed tests are written and compile cleanly but could
not be executed this session (no Docker daemon available); the
Docker-independent unit test suite is green.

Refs: spec/auth-service/tasks.md task 25, R31/R32/R33/R43/R46/R48,
L1/L7/L8/L9/L11/L12/L13.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

*(The generated template specifies "Claude Opus 4.8 (1M context)" in this trailer; substituted with the model that actually did the work, same substitution flagged at T16's Phase 13.)*

---

## Files Changed

**Production — created:**
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyController.java`
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyTokenIssuer.java`
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyExceptionHandler.java`
- `services/auth/src/main/java/com/themistra/auth/apikey/dto/ApiKeyTokenResponse.java`

**Production — modified:**
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyProperties.java` — `tokenTtlMinutes` field
- `services/auth/src/main/java/com/themistra/auth/common/PublicEndpoints.java` — `POST /api-keys/token` entry
- `services/auth/src/main/java/com/themistra/auth/common/ProblemTypes.java` — `API_KEY_EXCHANGE_REJECTED` URI
- `services/auth/src/main/java/com/themistra/auth/token/JwksConfig.java` — `jwtEncoder` bean
- `services/auth/src/main/java/com/themistra/auth/token/SecurityChainsConfig.java` — CSRF exemption (Phase 9 gate-approved exception to this file's freeze)
- `services/auth/src/main/resources/application.properties` — `themistra.auth.api-key.token-ttl-minutes`

**Tests — created:**
- `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyTokenIssuerTest.java`
- `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyControllerTest.java`
- `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyExceptionHandlerTest.java`
- `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyExchangeIntegrationTest.java`
- `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyPropertiesTest.java`
- `services/auth/src/test/java/com/themistra/auth/token/JwksConfigTest.java`

No file under `spec/` touched (confirmed: `git diff 4a1abea...HEAD -- spec/auth-service/` is empty). No Flyway migration added. No file outside the two lists above was modified.

---

## Summary

Implements task 25 of `spec/auth-service/tasks.md`: a public `POST /api-keys/token` endpoint that lets a merchant trade a long-lived `ck_live_…` API key for a short-lived, standards-shaped RS256 access token, so downstream resource servers validate one artifact and never see API-key material. Built on top of T24's already-complete `ApiKeyService.exchange` (validation, `last_used_at`, audit — unmodified by T25); this task adds the HTTP surface, the token issuer, and the supporting config/wiring.

Four blockers identified at the Phase 3/4 design-challenge gate were resolved and frozen before implementation began (declaring a `JwtEncoder` bean rather than reusing `TokenClaimsCustomizer`; a fixed `client_id` literal with no seeded `RegisteredClient`; a new `acr` value; a custom `ApiKey` Authorization scheme with `Bearer` explicitly rejected). Two additional defects were found during Phase 7/8 review and fixed under an explicit Phase 9 human-gate decision: an ambiguous-JWK-selection bug in the new encoder bean that would have broken token issuance service-wide during any key rotation, and a CSRF configuration gap that would have made the endpoint (and four pre-existing sibling endpoints) unreachable by any real anonymous caller. Both fixes are minimal, additive, and confined to files already in scope or explicitly gate-approved for a one-line exception.

## Testing Performed

- `mvn -pl services/auth -am compile` and `test-compile` — clean.
- Docker-independent unit/slice tests: **42/42 pass** — `ApiKeyTokenIssuerTest` (7), `ApiKeyControllerTest` (15), `ApiKeyExceptionHandlerTest` (2), `ApiKeyPropertiesTest` (6), `JwksConfigTest` (1), plus regression re-checks `TokenClaimsCustomizerTest` (8) and `ApiKeyHasherTest` (3) — confirming the new `JwtEncoder` bean and `PublicEndpoints`/`ProblemTypes` changes don't disturb existing behavior.
- `ApiKeyExchangeIntegrationTest` (11 tests, Testcontainers + real filter chain via `TestRestTemplate`, mirroring `SasLoginIntegrationTest`'s established pattern) is written, compiles cleanly, and covers both named tests plus every boundary/supporting item in the frozen brief's Required Tests list — **not executed this session**, Docker daemon unavailable (`docker info` fails). Fails only with the same `ApplicationContext failure` every other Testcontainers-backed class in this module currently produces in this sandbox; not a defect in the new test.
- Two review passes applied before this phase: a self-review (Phase 7) and an independent Kimi review (Phase 8), both resolved at a human gate (Phase 9); a further Kimi test review (Phase 11) added 5 new tests and strengthened 4 existing ones.
- **Before merge:** run `SasLoginIntegrationTest` (confirms the `jwkSelector` fix doesn't disturb SAS's own grants) and `ApiKeyExchangeIntegrationTest` with a working Docker daemon.

## Specification References

- **Task:** `spec/auth-service/tasks.md`, task 25 — API-key exchange endpoint.
- **Requirements:** R31, R32, R33 (scoped) — plus R43, R46, R48 (referenced by the frozen brief).
- **LOCKED decisions:** L8, L11 (scoped) — plus L1, L7, L9, L12, L13 (referenced by the frozen brief).
- **Full design-decision trail:** `artifacts/03-design-challenge.md` (Kimi, 15 findings) → `artifacts/04-frozen-task-brief.md` (D1–D5, human-decided) → `artifacts/09-review-resolution.md` (CSRF + `jwkSelector` fixes, human-decided) → `artifacts/12-specification-verification.md` (PASS).

---

**Phase 13 complete — pipeline finished, all 14 phases.** No commit run (per established session rhythm — only an explicit "commit it" triggers that). Branch remains `spec/service-specs-and-ai-framework`; `main` untouched throughout.
