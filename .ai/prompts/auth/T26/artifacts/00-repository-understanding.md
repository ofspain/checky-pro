<!-- MODEL: Claude Sonnet — Phase 0 (Repository Understanding). -->

# auth · T26 · Phase 0 — Repository Understanding

## 1. Architecture Summary

`auth-service` is the platform's OIDC/OAuth2 identity issuer (Spring Boot 3.5.4, Java 21, Spring Authorization Server), package-by-feature under `com.themistra.auth`. Each module (`account`, `authn`, `authz`, `audit`, `mfa`, `apikey`, `token`, `events`, `common`) owns its own entities/repositories/services/API; `ArchitectureTest` (ArchUnit) enforces no cross-module entity imports and other module-boundary rules (L12). Persistence is PostgreSQL via Flyway (`V1`–`V7` so far, immutable) and JPA; internal `bigint identity` PKs never leave the service, external identity is a UUID column. Every security-relevant state change is audited via `AuditService` (`auth_audit` row + outbox mirror to `auth.security.audit`, same transaction). Two Spring Security filter chains exist: `@Order(1)` is the SAS protocol chain (`/oauth2/**`, `/login`, OIDC); `@Order(2)` (`SecurityChainsConfig.applicationChain`) is this service's own resource-server chain, covering `PublicEndpoints`-listed paths as `permitAll` and everything else as JWT-authenticated. `PublicEndpoints.java` is the CI-enforced exhaustive unauthenticated-path registry (L11); `ArchitectureTest` restricts who may reference it.

## 2. Existing Code This Task Touches

**Already fully built (T24, frozen; not to be modified by T26 except as this task's own scope requires):**
- `apikey/ApiKey.java` — entity, `api_keys` table (from `V1`, `prefix` widened to `VARCHAR(32)` by `V7`).
- `apikey/ApiKeyRepository.java` — package-private; `findByAccountId`, `findByKeyUuid`, `findAccountIdByUuid`, `revokeIfActive`, etc. already exist and are exactly what a CRUD controller needs — no new repository method appears necessary for T26's three endpoints.
- `apikey/ApiKeyHasher.java`, `apikey/ApiKeyProperties.java` (now also carries `tokenTtlMinutes`, added by T25), `apikey/ApiKeyExchangeRejectedException.java`, `apikey/ApiKeyNotFoundException.java`, `apikey/ApiKeyNotAuthorizedException.java`.
- **`apikey/ApiKeyService.java`** — `create(UUID accountUuid, String name)`, `list(UUID accountUuid)`, `revoke(UUID accountUuid, UUID keyUuid)` are all already fully implemented, including: `create` independently re-verifies `MERCHANT` role + `ACTIVE` status + confirmed TOTP MFA (defense-in-depth, doesn't trust a caller-side check); `list` returns `ApiKeyMetadata` records with **no hash/secret field by construction**; `revoke` is idempotent (a second revoke of an already-revoked key is a silent no-op, no duplicate audit row) and throws `ApiKeyNotFoundException` uniformly whether the key doesn't exist at all or exists but isn't owned by the caller (no enumeration oracle between the two cases). All three already record their own audit events (`api_key.created`, `api_key.revoked`) via `AuditService`.

**Built by T25 (frozen for T26's purposes, but the same package/possibly the same controller class):**
- `apikey/ApiKeyController.java` — currently has exactly one endpoint, `POST /api-keys/token`, at path `/api-keys` + `@PostMapping("/token")`. **Open question for Phase 1/2, not decided here:** `design.md` §6's file tree lists a single `ApiKeyController.java` for the whole module (create/list/revoke/token together), but T25 built it scoped to only the token-exchange endpoint. T26 must decide whether to add `POST /api-keys`, `GET /api-keys`, `DELETE /api-keys/{keyUuid}` to this same existing class or introduce a second controller — a real decision, not pre-made by this phase.
- `apikey/ApiKeyExceptionHandler.java` — currently maps only `ApiKeyExchangeRejectedException` → 401. T26 will need new mappings for `ApiKeyNotFoundException` and `ApiKeyNotAuthorizedException` (both already exist as exception classes from T24, unmapped to any HTTP response yet — nothing currently converts them to RFC 9457 bodies).
- `apikey/dto/ApiKeyTokenResponse.java` — the only DTO that exists in `apikey/dto/` so far. `design.md`'s file tree also names `dto/CreateApiKeyRequest.java` and `dto/ApiKeyResponse.java`, neither of which exists yet — new for T26.
- `common/PublicEndpoints.java`, `common/ProblemTypes.java`, `token/JwksConfig.java`, `token/SecurityChainsConfig.java` — all modified by T25; none of T26's three endpoints are public (R34/R35 both say "authenticated user"), so **T26 should not need to touch `PublicEndpoints.java` at all** — a difference from T25.

**Established sibling patterns to follow (not part of this module, but the precedent for how an authenticated CRUD controller in this codebase is shaped):**
- `account/AccountController.java` — derives the caller from `Authentication` via `UUID.fromString(authentication.getName())`, never a path/body-supplied identifier, for any "act on my own resource" endpoint (`GET /accounts/me`, `POST /accounts/me/password`). The same pattern applies directly to `POST /api-keys` and `GET /api-keys` (R30/R34 are both scoped to "an authenticated user" acting on their own keys).
- `account/AccountExceptionHandler.java` — the exact shape for a module-scoped `@RestControllerAdvice`: one `@ExceptionHandler` method per domain exception, `ProblemDetail.forStatus(...)`, a `ProblemTypes` constant, a fixed title, `setDetail` only when the detail is safe (never for anything enumeration-sensitive).
- `RegisterAccountRequest`/`ChangePasswordRequest` (in `account/dto/`) — `record` types with `jakarta.validation` annotations (`@NotBlank`, etc.), consumed via `@Valid @RequestBody`.

## 3. Established Patterns

- **Persistence:** JPA for simple find/save (already fully sufficient here — `ApiKeyRepository` needs no new method). No new migration anticipated (L1 forbids touching `V1`–`V4`; nothing in R30/R34/R35 requires a schema change beyond what `V1`/`V7` already provide).
- **Authorization model in this module:** `ApiKeyService.create` is the only one of the three methods that independently re-verifies role/MFA state; `list`/`revoke` only verify **ownership** (the caller's own resolved `accountId`), not role membership — consistent with the fact that only a `MERCHANT` account can ever have created a key in the first place, so a non-merchant caller trivially sees/owns nothing. Worth carrying into Phase 1/2 rather than assuming a `@PreAuthorize("hasRole('MERCHANT')")` is required on the new endpoints — the service layer already covers the security-relevant case at `create`.
- **Error handling:** RFC 9457 via `ProblemDetail`, module-scoped `@RestControllerAdvice`, `ProblemTypes` constants — see `common/ProblemTypes.java` (already has `NOT_FOUND`, `INVALID_STATE`, etc. that may be directly reusable for `ApiKeyNotFoundException`/`ApiKeyNotAuthorizedException` rather than requiring new constants — a Phase 1/2 decision).
- **Configuration:** flat `application.properties`, validated `@ConfigurationProperties`. T26 does not appear to need any new configuration key.
- **Outbox/audit:** already fully wired inside `ApiKeyService` for all three operations; T26's controller layer does not call `AuditService` directly (matches every other controller in this codebase — audit calls live in the service layer, never the controller).
- **Module boundaries (L12):** no `apikey` class may import `PublicEndpoints` except from `token`/`common` (ArchUnit rule `only_token_module_references_public_endpoints`) — moot for T26 since none of its endpoints are public.

## 4. Testing Conventions

- Plain JUnit + Mockito for controller-level unit tests, direct construction (`new XController(mockService)`), no `MockMvc` — established precedent is `AccountControllerTest`/`ApiKeyControllerTest` (T25).
- Plain JUnit for exception-handler tests — `AccountExceptionHandlerTest`/`ApiKeyExceptionHandlerTest` (T25) construct the handler directly and assert on the returned `ProblemDetail`.
- `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` (real Postgres + Kafka) for anything touching the schema or the real filter chain — `ApiKeyServiceIntegrationTest` (T24) already covers `create`/`list`/`revoke` exhaustively at the service layer; T26's own integration test need only prove the HTTP layer (auth extraction, response shape, status codes), mirroring `ApiKeyExchangeIntegrationTest`'s (T25) `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` pattern for endpoints needing the real filter chain.
- Fixed `Clock` for anything time-dependent (not obviously needed for T26 — create/list/revoke don't do TTL arithmetic).
- **Known environment limitation, carried forward from every recent task:** Docker/Testcontainers has been unavailable all of T25's session (`docker info` fails); any Testcontainers-backed test written for T26 should be expected to compile-but-not-execute under the same constraint unless this changes.

## 5. Known Gaps / Unknowns

- **`contracts/api/auth.yaml` and `contracts/api/token-claims.md` do not exist** (`spec/auth-service/contracts/api/` is empty) — same gap already logged and deferred at T25 (owner: tasks 33/34). T26 cannot verify against these contracts either; not blocking.
- **`package.md` §8's test-to-requirement mapping table is wrong for both of T26's named tests** — the same recurring numbering bug flagged at nearly every prior task (T16, T17, T18, T21, T25...). It maps `shouldCreateApiKeyAndShowPlaintextExactlyOnce` → R27 (wrong; R27 is about `amr`/`acr` on a password-only login, unrelated to API keys) and `shouldListAndRevokeOwnApiKeys` → "R30 / R31" (wrong; R30 is create, R31 is token-exchange — neither is list/revoke). The correct matches, by content, are R30 and R34/R35 respectively — consistent with this task's own header, which already scopes R30/R34/R35 correctly. `spec/` is immutable; this is flagged for the spec author, not fixed here.
- **Whether T26 extends T25's existing `ApiKeyController` or introduces a second controller class is not decided in this phase** — genuinely open, to be resolved at Phase 1/2 (the TIB) with Phase 3/4 available to challenge/confirm it if needed.
- **I do not know** whether `GET /api-keys` should support pagination — `ApiKeyService.list` currently returns a plain `List<ApiKeyMetadata>` with no `Pageable` parameter, and R34 doesn't mention pagination. Not assumed either way; a Phase 1/2 call.
- **I do not know** the exact response shape `design.md`'s `ApiKeyResponse.java` DTO should have beyond "metadata but no secret material" (R34) — `ApiKeyService.ApiKeyMetadata` already has the fields (`keyUuid`, `name`, `scopes`, `createdAt`, `lastUsedAt`, `expiresAt`, `revokedAt`); whether `ApiKeyResponse` is a 1:1 mapping or narrows/renames fields is a Phase 1/2 decision.

---

**Phase 0 complete — repository understanding written.** Proceed to Phase 1 (Specification Extraction) on approval.
