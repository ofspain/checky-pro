<!-- MODEL: Claude Sonnet — Phase 0 (Repository Understanding). -->

# auth · T27 · Phase 0 — Repository Understanding

## 1. Architecture Summary

`auth-service` is the platform's OIDC/OAuth2 identity issuer (Spring Boot 3.5.4, Java 21, Spring Authorization Server), package-by-feature under `com.themistra.auth`. The full API-key subsystem this task tests spans three prior tasks in the `apikey` module: T24 built the service layer (`ApiKeyService.create/list/revoke/exchange`, persistence), T25 built the public token-exchange endpoint (`POST /api-keys/token`), and T26 built the self-service CRUD endpoints (`POST /api-keys`, `GET /api-keys`, `DELETE /api-keys/{keyUuid}`) — all on the same `ApiKeyController` class. Every operation is audited (`auth_audit` row + outbox mirror to `auth.security.audit`) inside the service layer. Two Spring Security filter chains exist; `/api-keys/token` is the only public path in this module (`PublicEndpoints`), the CRUD endpoints require an authenticated JWT on the `@Order(2)` resource-server chain.

## 2. Existing Code This Task Touches

**Nothing in `src/main` — this is a test-only task** (the task statement is literally "Test create→exchange→revoke→exchange-fails flow with Testcontainers"; `agents.md`/pipeline convention treats a task like this as test-writing in Phase 6, not Phase 10, since there is no production code to implement).

**Fully complete, all frozen, spanning three prior tasks:**
- `apikey/ApiKeyService.java` (T24) — `create`, `list`, `revoke`, `exchange`, all audited.
- `apikey/ApiKeyController.java` (T25 + T26) — `POST /api-keys` (create), `GET /api-keys` (list), `DELETE /api-keys/{keyUuid}` (revoke), `POST /api-keys/token` (exchange). All four HTTP operations this task's flow needs already exist and work.
- `apikey/ApiKeyExceptionHandler.java` — maps `ApiKeyExchangeRejectedException` (401), `ApiKeyNotFoundException` (404), `ApiKeyNotAuthorizedException` (403).
- `apikey/ApiKeyTokenIssuer.java` (T25) — mints the exchange JWT.

**Existing tests already covering pieces of this flow, at different layers — important for Phase 1/2 to avoid duplicating:**
- `ApiKeyServiceIntegrationTest` (T24) — service-layer only (direct method calls on `ApiKeyService`, no HTTP). Its own `shouldRejectRevokedOrUnknownApiKeyWithUniform401` already does create→revoke→exchange-fails as one of several sub-assertions, but never through HTTP and never asserting an exchange *succeeded* before the revoke.
- `ApiKeyExchangeIntegrationTest` (T25) — HTTP-layer, but scoped to `/api-keys/token` in isolation; every test creates its own fresh key via direct `ApiKeyService.create` calls (not via `POST /api-keys`), then exercises only the exchange endpoint.
- `ApiKeyCrudIntegrationTest` (T26) — HTTP-layer, scoped to create/list/revoke; its `shouldListAndRevokeOwnApiKeys` does create→revoke→list (confirming `revokedAt` becomes non-null), but never calls `POST /api-keys/token` at all, so nothing proves a revoked key's *exchange* actually fails through the real endpoint after having previously succeeded.
- `ApiKeyPersistenceIntegrationTest` (T24) — entity/column-mapping only, unrelated to this task's HTTP-flow scope.

**The genuine gap T27 fills:** no existing test drives the *complete* lifecycle — create via `POST /api-keys` → exchange via `POST /api-keys/token` (succeeds) → revoke via `DELETE /api-keys/{keyUuid}` → exchange the same key again via `POST /api-keys/token` (now fails, uniform 401) — as one continuous sequence through the real HTTP endpoints and the real filter chain. Each existing test proves one operation works; none proves the *sequence* — specifically, that a key valid a moment ago is actually, immediately, unusable after revocation, observed at the HTTP boundary rather than inferred from separate isolated tests.

## 3. Established Patterns

- **Testcontainers integration style:** `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Import(TestcontainersConfiguration.class)` + `TestRestTemplate` is the established pattern for anything exercising the real filter chain (`SasLoginIntegrationTest` T20/22, `ApiKeyExchangeIntegrationTest` T25, `ApiKeyCrudIntegrationTest` T26) — no `MockMvc` precedent exists anywhere in this module.
- **Authentication for the CRUD endpoints in a test:** T26 established minting a real signed JWT directly via the already-wired `ApiKeyTokenIssuer` bean (`apiKeyTokenIssuer.issue(accountUuid, scopes)`) rather than the full interactive login dance — works because none of the CRUD endpoints require a specific role/authority at the filter level.
- **Account/MFA seeding:** every integration test in this module independently reimplements `seedMerchantWithConfirmedMfa` (register → activate → assign `MERCHANT` role → begin/confirm TOTP enrollment) and its own RFC 6238 reference TOTP generator, deliberately duplicated per file rather than shared, matching this module's established discipline (`ApiKeyServiceIntegrationTest`, `ApiKeyExchangeIntegrationTest`, `ApiKeyCrudIntegrationTest` all do this independently).
- **No per-test rollback** in this integration-test style — every test uses a unique email to avoid collisions within the shared Testcontainers-backed schema for the whole test class run.
- **Error handling / audit:** already fully covered by the existing test files at each individual operation's level; T27 is about proving the *sequence*, not re-testing each operation's own edge cases again.

## 4. Testing Conventions

- Plain JUnit for anything not touching the schema or filter chain (not applicable here — this task is inherently full-stack by its own task statement, "with Testcontainers").
- `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate` + `TestcontainersConfiguration` for the real thing, per the established pattern above.
- **Known environment limitation, carried forward from every task since T25:** Docker/Testcontainers has been unavailable this entire session (`docker info` fails). A test written for T27 should be expected to compile but not execute under the same constraint unless this changes before the task completes.

## 5. Known Gaps / Unknowns

- **Whether T27 should be a new file or added to one of the three existing integration test classes is not decided in this phase.** Genuinely open — Phase 1/2 will need to weigh: a new file keeps the "prove the full lifecycle" intent self-contained and easy to find, matching the `POST /api-keys/token` (new file, T25) and `POST/GET/DELETE /api-keys` (new file, T26) precedent of one new integration-test file per task; but the flow necessarily touches all three existing files' territory (create, exchange, revoke), so there's a real argument for locating it accordingly. Flagged for Phase 2's TIB.
- **`contracts/api/auth.yaml`/`token-claims.md` still don't exist** — same gap noted at T25/T26's Phase 0/1, still not blocking (this task doesn't need contract conformance, only behavioral proof).
- **`package.md` §8's traceability table** — the task header's own scoped IDs (R30/R31/R33/R35) are correct; `package.md` itself remains stale for these same four named tests (same recurring bug flagged at every prior related task). Not blocking, not fixed (spec immutable).
- **I do not know** whether T27 is expected to assert anything about the outbox/audit trail across the full sequence (e.g., exactly one `api_key.created`, one `api_key.exchanged` SUCCESS, one `api_key.revoked`, one `api_key.exchange_failed` row, in that order) or whether it's scoped purely to the HTTP-observable behavior (status codes, response bodies, and the final 401). R43 (audit) isn't in this task's scoped requirement list, suggesting the latter, but this is a Phase 1/2 call, not decided here.

---

**Phase 0 complete — repository understanding written.** Proceed to Phase 1 (Specification Extraction) on approval.
